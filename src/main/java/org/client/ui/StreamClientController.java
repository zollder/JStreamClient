package org.client.ui;

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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;

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

	private FrameSynchronizer frameSynchronizer;
	private FrameConverter frameConverter;

	private byte[] rcvBuffer;					// buffer used to store data received from the server
	private DatagramPacket rcvUdpPacket;		// UDP packet received from the server
	private DatagramSocket rtpSocket;			// UDP packets send/receive socket

	private ScheduledExecutorService executorService;
	private DataService dataService;

	/**--------------------------------------------------------------------------------------------
	 * Statistics variables
	 * --------------------------------------------------------------------------------------------*/
	double statStartTime;				//Time in milliseconds when start is pressed
	double statDataRate;				//Rate of video data received in bytes/s
	int statTotalBytes;					//Total number of bytes received in a session
	double statTotalPlayTime;			//Time in milliseconds of video playing since beginning
	float statFractionLost;				//Fraction of RTP data packets from sender lost since the prev packet was sent
	int statLostPackets;				//Number of packets lost
	int statExpectedRtpCounter;			//Expected Sequence number of RTP messages within the session
	int statHighestSequenceNumber;		//Highest sequence number received in session

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
		updateStatValues(0, 0, 0);

		rcvBuffer = new byte[15000];

		rtspService = new RtspService();
		rtcpSender = new RtcpService(this);
		frameSynchronizer = new FrameSynchronizer(100);

		executorService = Executors.newScheduledThreadPool(1);
		executorService.scheduleAtFixedRate(new Runnable() { @Override public void run() {} }, 0, 50, TimeUnit.MILLISECONDS);
		dataService = new DataService();
		dataService.setExecutor(executorService);
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
			initializeRtpSocket(RtspService.RTP_RCV_PORT, 5);

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

		// initialize/reset stats time
		dataService.resetStatStartTime();

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

				dataService.restart();
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

				dataService.cancel();
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

			dataService.cancel();
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

	/** Initializes non-blocking RTP socket that will be used to receive data.
	 *  @param port - datagram socket port
	 *  @param timeout - socket timeout interval */
	public void initializeRtpSocket(int port, int timeout)
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

	/**
	 * Implements a service that encapsulates raw data retrieval and processing task.
	 * The following work is performed by the service on each call:
	 * 1. constructs a datagram packet and receives data from UDP socket;
	 * 2. constructs an RTP packet object from the datagram packet created in 1.;
	 * 3. retrieves payload (raw data) from the RTP packet;
	 * 4. builds an Image from raw data and wraps it into the ImageView node, which is finally returned;
	 * 5. calculates and populates statistical data.
	 */
	class DataService extends Service<ImageView>
	{
		public DataService()
		{
		}

		@Override
		protected Task<ImageView> createTask()
		{
			Task<ImageView> task = new Task<ImageView>() {
				@Override
				protected ImageView call() throws Exception
				{
					try
					{
						// Construct a DatagramPacket to receive data from the UDP socket
						rcvUdpPacket = new DatagramPacket(rcvBuffer, rcvBuffer.length);

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
//						updateStatValues(statTotalBytes, statFractionLost, statDataRate);

						//display the image
						//get an Image object from the payload bytestream
//						frameSynchronizer.addFrame(toolkit.createImage(payload, 0, payloadLength), sequenceNumber);
//						Image fxImage = frameConverter.convertImage(frameSynchronizer.nextFrame());
//						imageView.setImage(fxImage);
					}
					catch (InterruptedIOException iioe)
					{
						System.out.println("Nothing to read");
					}
					catch (IOException ioe)
					{
						System.out.println("Exception caught: "+ioe);
					}
					return null;
				}
			};
			return task;
		}

		@Override
		protected void succeeded() {
			DecimalFormat formatter = new DecimalFormat("###,###.##");
			bytesReceived.setText(String.valueOf(statTotalBytes));
			packetsLost.setText(formatter.format(statFractionLost));
			dataRate.setText(formatter.format(statDataRate));
		};
		/** Initializes (resets) statistics start time. */
		public void resetStatStartTime()
		{
			statStartTime = System.currentTimeMillis();
		}
	}

	/**
	 * Updates statistics data properties.
	 * @param bytesReceived
	 * @param fractionLost
	 * @param dataRate
	 */
	public void updateStatValues(int bytesReceived, float fractionLost, double dataRate)
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