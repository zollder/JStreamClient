package org.client.ui;

import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.text.DecimalFormat;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import javax.swing.Timer;

import org.client.MainApp;
import org.client.model.RtpPacket;
import org.client.service.FrameConverter;
import org.client.service.FrameSynchronizer;

public class StreamClientController
{
	@SuppressWarnings("unused")
	private MainApp application;

	private static final int RTSP_SERVER_PORT = 13569;
	private static final String SERVER_HOST = "localhost";

	/**--------------------------------------------------------------------------------------------
	 * State machine
	 * --------------------------------------------------------------------------------------------*/
	final static int INIT = 0;
	final static int READY = 1;
	final static int PLAYING = 2;

	/**--------------------------------------------------------------------------------------------
	 * RTSP variables
	 * --------------------------------------------------------------------------------------------*/
	private RtspService rtspService;
	private InetAddress serverIp;
	private Socket rtspSocket;			// RTSP messages socket (send/receive)
	static int currentState;			// RTSP states: INIT or READY or PLAYING

	/**--------------------------------------------------------------------------------------------
	 * RTCP variables
	 * --------------------------------------------------------------------------------------------*/
	RtcpService rtcpSender;
	DatagramSocket rtcpSocket;			// UDP socket for sending RTCP packets

	/**--------------------------------------------------------------------------------------------
	 * Statistics variables
	 * --------------------------------------------------------------------------------------------*/
	double statDataRate;				//Rate of video data received in bytes/s
	int statTotalBytes;					//Total number of bytes received in a session
	double statStartTime;				//Time in milliseconds when start is pressed
	double statTotalPlayTime;			//Time in milliseconds of video playing since beginning
	float statFractionLost;				//Fraction of RTP data packets from sender lost since the prev packet was sent
	int statLostPackets;				//Number of packets lost
	int statExpectedRtpCounter;			//Expected Sequence number of RTP messages within the session
	int statHighestSequenceNumber;		//Highest sequence number received in session

	private Timer timer;						// timer used to receive data from the UDP socket
	private TimerListener timerService;
	private FrameSynchronizer frameSynchronizer;
	private FrameConverter frameConverter;
	private Toolkit toolkit;

	/**--------------------------------------------------------------------------------------------
	 * UI variables
	 * --------------------------------------------------------------------------------------------*/
	@FXML private ImageView imageView;
	@FXML private Label bytesReceived;
	@FXML private Label packetsLost;
	@FXML private Label dataRate;

	/**--------------------------------------------------------------------------------------------
	 * Constructor, is called before #initialize()
	 --------------------------------------------------------------------------------------------*/
	public StreamClientController() {}

   /**--------------------------------------------------------------------------------------------
    * Initializes the controller class. This method is automatically called after the fxml file has been loaded.
    * @throws IOException
    * --------------------------------------------------------------------------------------------*/
	@FXML
	private void initialize()
	{
		updateStats(0, 0, 0);

		timerService = new TimerListener();
		timer = new Timer(20, timerService);
		timer.setInitialDelay(0);
		timer.setCoalesce(true);

		rtspService = new RtspService();
		rtcpSender = new RtcpService(this);
		frameSynchronizer = new FrameSynchronizer(100);

		toolkit = Toolkit.getDefaultToolkit();
	}

	/** Handles "setup" button operation.
	 *  Performs RTSP setup routine. */
	@FXML
	private void setup()
	{
		System.out.println("Setup Button pressed !");
		if (currentState == INIT)
		{
			// initialize RTPsocket to receive packets
			timerService.initialize(RtspService.RTP_RCV_PORT, 5);

			//init RTSP sequence number
			rtspService.rtspSequenceNumber = 1;

			//Send SETUP message to the server
			rtspService.sendRtspRequest("SETUP");

			//Wait for the response
			if (rtspService.parseServerResponse(currentState) != 200)
				System.out.println("Invalid Server Response");
			else
			{
				//change RTSP state and print new state
				currentState = READY;
				System.out.println("New RTSP state: READY");
			}
		}
	}

	/** Handles "play" button operation.
	 *  Initiates or resumes data transfer b/w server and client. */
	@FXML
	private void play()
	{
		System.out.println("Play Button pressed!");

		// initialize stats time
		statStartTime = System.currentTimeMillis();

		if (currentState == READY)
		{
			rtspService.rtspSequenceNumber++;
			rtspService.sendRtspRequest("PLAY");

			if (rtspService.parseServerResponse(currentState) != 200)
				System.out.println("Invalid Server Response");
			else
			{
				currentState = PLAYING;
				System.out.println("New RTSP state: PLAYING");

				timer.start();
				rtcpSender.startSend();
			}
		}
	}

	/** Handles "pause" button operation.
	 *  Suspends data transfer b/w server and client. */
	@FXML
	private void pause()
	{
		System.out.println("Pause Button pressed!");

		if (currentState == PLAYING)
		{
			rtspService.rtspSequenceNumber++;
			rtspService.sendRtspRequest("PAUSE");

			if (rtspService.parseServerResponse(currentState) != 200)
				System.out.println("Invalid Server Response");
			else
			{
				currentState = READY;
				System.out.println("New RTSP state: READY");

				timer.stop();
				rtcpSender.stopSend();
			}
		}
	}

	/** Handles "session" button operation.
	 *  Retrieves and shows session info. */
	@FXML
	private void session()
	{
		System.out.println("Sending DESCRIBE request");

		rtspService.rtspSequenceNumber++;
		rtspService.sendRtspRequest("DESCRIBE");

		if (rtspService.parseServerResponse(currentState) != 200)
			System.out.println("Invalid Server Response");
		else
			System.out.println("Received response for DESCRIBE");
	}

	/** Handles close operation. */
	@FXML
	private void close()
	{
		System.out.println("Close Button pressed !");

		rtspService.rtspSequenceNumber++;
		rtspService.sendRtspRequest("TEARDOWN");

		if (rtspService.parseServerResponse(currentState) != 200)
			System.out.println("Invalid Server Response");
		else
		{
			currentState = INIT;
			System.out.println("New RTSP state: INIT");

			timer.stop();
			rtcpSender.stopSend();
			Platform.exit();
		}
	}

	/**
	 * Establishes communication with the server.
	 * Blocks execution if the server is not available.
	 * @throws IOException
	 */
	public void connect() throws IOException
	{
		// initialize client
		setServerIp(InetAddress.getByName(SERVER_HOST));

		// Establish a TCP connection with the server to exchange RTSP messages (blocking)
		rtspSocket = new Socket(getServerIp(), RTSP_SERVER_PORT);

		// Establish a UDP connection with the server to exchange RTCP control packets
		// Set input and output stream filters and initial state.
		RtspService.rtspBufferedReader = new BufferedReader(new InputStreamReader(rtspSocket.getInputStream()));
		RtspService.rtspBufferedWriter = new BufferedWriter(new OutputStreamWriter(rtspSocket.getOutputStream()));
		currentState = INIT;
	}

	public void updateImageView(Image image)
	{
	}

	/**----------------------------------------------------------------------------------------------
	 * Handler for timer.
	 * ----------------------------------------------------------------------------------------------*/
	class TimerListener implements ActionListener
	{
		private Timer timer;

		private byte[] rcvBuffer;					// buffer used to store data received from the server
		private DatagramPacket rcvUdpPacket;		// UDP packet received from the server
		private DatagramSocket rtpSocket;			// UDP packets send/receive socket

		public TimerListener()
		{
			rcvBuffer = new byte[15000];
		}

		/** Initializes non-blocking RTP socket that will be used to receive data.
		 *  @param port - datagram socket port
		 *  @param timeout - socket timeout interval */
		public void initialize(int port, int timeout)
		{
			try
			{
				// construct a new DatagramSocket to receive RTP packets from the server, on port RTP_RCV_PORT
				rtpSocket = new DatagramSocket(port);
				// UDP socket for sending QoS RTCP packets
				rtcpSocket = new DatagramSocket();
				// set TimeOut value of the socket to 5msec.
				rtpSocket.setSoTimeout(timeout);
			}
			catch (SocketException se)
			{
				System.out.println("Socket exception: "+se);
				System.exit(0);
			}
		}

		@Override
		public void actionPerformed(ActionEvent e)
		{
			// Construct a DatagramPacket to receive data from the UDP socket
			rcvUdpPacket = new DatagramPacket(rcvBuffer, rcvBuffer.length);

			try
			{
				//receive the DP from the socket, save time for stats
				rtpSocket.receive(rcvUdpPacket);

				double curTime = System.currentTimeMillis();
				statTotalPlayTime += curTime - statStartTime;
				statStartTime = curTime;

				//create an RTPpacket object from the DP
				RtpPacket rtpPacket = new RtpPacket(rcvUdpPacket.getData(), rcvUdpPacket.getLength());
				int sequenceNumber = rtpPacket.getSequenceNumber();

				//this is the highest seq num received

				//print important header fields of the RTP packet received:
				System.out.println("Got RTP packet with SeqNum # " + sequenceNumber
								   + " TimeStamp " + rtpPacket.getTimestamp() + " ms, of type "
								   + rtpPacket.getPayloadType());

				//print header bitstream:
				rtpPacket.printHeader();

				//get the payload bitstream from the RTPpacket object
				int payloadLength = rtpPacket.getPayloadLength();
				byte [] payload = new byte[payloadLength];
				rtpPacket.getpayload(payload);

				//compute stats and update the label in GUI
				statExpectedRtpCounter++;
				if (sequenceNumber > statHighestSequenceNumber)
				{
					statHighestSequenceNumber = sequenceNumber;
				}
				if (statExpectedRtpCounter != sequenceNumber)
				{
					statLostPackets++;
				}
				statDataRate = statTotalPlayTime == 0 ? 0 : (statTotalBytes / (statTotalPlayTime / 1000.0));
				statFractionLost = (float)statLostPackets / statHighestSequenceNumber;
				statTotalBytes += payloadLength;

				// update statistics
				updateStats(statTotalBytes, statFractionLost, statDataRate);

				//display the image
				//get an Image object from the payload bytestream
				frameSynchronizer.addFrame(toolkit.createImage(payload, 0, payloadLength), sequenceNumber);
				Image fxImage = frameConverter.convertImage(frameSynchronizer.nextFrame());
				imageView.setImage(fxImage);
			}
			catch (InterruptedIOException iioe)
			{
				System.out.println("Nothing to read");
			}
			catch (IOException ioe)
			{
				System.out.println("Exception caught: "+ioe);
			}
		}
	}

	/**
	 * Updates statistics data labels.
	 * @param bytesReceived
	 * @param fractionLost
	 * @param dataRate
	 */
	public void updateStats(int bytesReceived, float fractionLost, double dataRate)
	{
		DecimalFormat formatter = new DecimalFormat("###,###.##");
		this.bytesReceived.setText(String.valueOf(bytesReceived));
		this.packetsLost.setText(formatter.format(fractionLost));
		this.dataRate.setText(formatter.format(dataRate));
	}

	/** Is called by the main application to give a reference back to itself.
	 *  @param app - main application reference */
	public void setApplication(MainApp app)
	{
		this.application = app;
	}

	public InetAddress getServerIp()
	{
		return serverIp;
	}

	public void setServerIp(InetAddress serverIp)
	{
		this.serverIp = serverIp;
	}
}