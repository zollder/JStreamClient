package org.client.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.text.DecimalFormat;
import java.util.Random;
import java.util.StringTokenizer;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;

import org.client.model.RtcpPacket;
import org.client.model.RtpPacket;
import org.client.service.FrameSynchronizer;

/**--------------------------------------------------------------------------------------------------------
 * Client implementation.
 * --------------------------------------------------------------------------------------------------------*/
public class Client
{
	/**--------------------------------------------------------------------------------------------
	 * GUI.
	 * --------------------------------------------------------------------------------------------*/
	JFrame frame = new JFrame("Client");
	JButton setupButton = new JButton("Setup");
	JButton playButton = new JButton("Play");
	JButton pauseButton = new JButton("Pause");
	JButton tearButton = new JButton("Close");
	JButton describeButton = new JButton("Session");
	JPanel mainPanel = new JPanel();
	JPanel buttonPanel = new JPanel();
	JLabel statLabel1 = new JLabel();
	JLabel statLabel2 = new JLabel();
	JLabel statLabel3 = new JLabel();
	JLabel iconLabel = new JLabel();
	ImageIcon icon;

	/**--------------------------------------------------------------------------------------------
	 * RTP variables
	 * --------------------------------------------------------------------------------------------*/
	DatagramPacket rcvUdpPacket;		// UDP packet received from the server
	DatagramSocket rtpSocket;			// UDP packets send/receive socket
	static int RTP_RCV_PORT = 25000;	// client RTP port (receive)

	Timer timer;						// timer used to receive data from the UDP socket
	byte[] rcvBuffer;					// buffer used to store data received from the server

	/**--------------------------------------------------------------------------------------------
	 * RTSP variables
	 * --------------------------------------------------------------------------------------------*/
	private Socket rtspSocket;				// RTSP messages socket (send/receive)
	private InetAddress serverIp;
	static int currentState;		// RTSP states: INIT or READY or PLAYING

	static String videoFileName = "movie.Mjpeg";	// video file name
	int rtspSequenceNumber = 0;		// RTSP message sequence number (within the session)
	int rtspId = 0;					// RTSP session ID (given by the RTSP Server)

	//input and output stream filters
	static BufferedReader rtspBufferedReader;
	static BufferedWriter rtspBufferedWriter;

	// RTSP states
	final static int INIT = 0;
	final static int READY = 1;
	final static int PLAYING = 2;

	final static String CRLF = "\r\n";
	final static String DES_FNAME = "session_info.txt";

	/**--------------------------------------------------------------------------------------------
	 * RTCP variables
	 * --------------------------------------------------------------------------------------------*/
	DatagramSocket rtcpSocket;			// UDP socket for sending RTCP packets
	RtcpSender rtcpSender;
	static int RTCP_RCV_PORT = 19001;	// port where the client will receive the RTP packets
	static int RTCP_PERIOD = 400;		// How often to send RTCP packets

	/**--------------------------------------------------------------------------------------------
	 * Video constants
	 * --------------------------------------------------------------------------------------------*/
	static int MJPEG_TYPE = 26;			// RTP payload type for MJPEG video

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

	FrameSynchronizer frameSynchronizer;

	/**--------------------------------------------------------------------------------------------
	 * Constructor
	 * --------------------------------------------------------------------------------------------*/
	public Client()
	{
		// Buttons
		buttonPanel.setLayout(new GridLayout(1,0));
		buttonPanel.add(setupButton);
		buttonPanel.add(playButton);
		buttonPanel.add(pauseButton);
		buttonPanel.add(tearButton);
		buttonPanel.add(describeButton);
		setupButton.addActionListener(new setupButtonListener());
		playButton.addActionListener(new playButtonListener());
		pauseButton.addActionListener(new pauseButtonListener());
		tearButton.addActionListener(new tearButtonListener());
		describeButton.addActionListener(new describeButtonListener());

		// Statistics
		statLabel1.setText("Total Bytes Received: 0");
		statLabel2.setText("Packets Lost: 0");
		statLabel3.setText("Data Rate (bytes/sec): 0");

		// Image display label
		iconLabel.setIcon(null);

		// frame layout
		mainPanel.setLayout(null);
		mainPanel.add(iconLabel);
		mainPanel.add(buttonPanel);
		mainPanel.add(statLabel1);
		mainPanel.add(statLabel2);
		mainPanel.add(statLabel3);
		iconLabel.setBounds(0,0,380,280);
		buttonPanel.setBounds(0,280,380,50);
		statLabel1.setBounds(0,330,380,20);
		statLabel2.setBounds(0,350,380,20);
		statLabel3.setBounds(0,370,380,20);

		frame.getContentPane().add(mainPanel, BorderLayout.CENTER);
		frame.setSize(new Dimension(380,420));
		frame.setVisible(true);

		timer = new Timer(20, new timerListener());
		timer.setInitialDelay(0);
		timer.setCoalesce(true);

		rtcpSender = new RtcpSender(400);
		rcvBuffer = new byte[15000];
		frameSynchronizer = new FrameSynchronizer(100);
	}

	//------------------------------------
	//Handler for buttons
	//------------------------------------

	//Handler for Setup button
	//-----------------------
	class setupButtonListener implements ActionListener
	{
		@Override
		public void actionPerformed(ActionEvent e)
		{
			System.out.println("Setup Button pressed !");
			if (currentState == INIT)
			{
				//Init non-blocking RTPsocket that will be used to receive data
				try
				{
					//construct a new DatagramSocket to receive RTP packets from the server, on port RTP_RCV_PORT
					rtpSocket = new DatagramSocket(RTP_RCV_PORT);
					//UDP socket for sending QoS RTCP packets
					rtcpSocket = new DatagramSocket();
					//set TimeOut value of the socket to 5msec.
					rtpSocket.setSoTimeout(5);
				}
				catch (SocketException se)
				{
					System.out.println("Socket exception: "+se);
					System.exit(0);
				}

				//init RTSP sequence number
				rtspSequenceNumber = 1;

				//Send SETUP message to the server
				sendRtspRequest("SETUP");

				//Wait for the response
				if (parseServerResponse() != 200)
					System.out.println("Invalid Server Response");
				else
				{
					//change RTSP state and print new state
					currentState = READY;
					System.out.println("New RTSP state: READY");
				}
			}
			//else if state != INIT then do nothing
		}
	}

	/**----------------------------------------------------------------------------------------------
	 * Play button handler.
	 * ----------------------------------------------------------------------------------------------*/
	class playButtonListener implements ActionListener
	{
		@Override
		public void actionPerformed(ActionEvent e)
		{
			System.out.println("Play Button pressed!");

			// Start to save the time in stats
			statStartTime = System.currentTimeMillis();

			if (currentState == READY)
			{
				rtspSequenceNumber++;

				//Send PLAY message to the server
				sendRtspRequest("PLAY");

				if (parseServerResponse() != 200)
					System.out.println("Invalid Server Response");
				else
				{
					//change RTSP state and print out new state
					currentState = PLAYING;
					System.out.println("New RTSP state: PLAYING");

					//start the timer
					timer.start();
					rtcpSender.startSend();
				}
			}
			//else if state != READY then do nothing
		}
	}

	/**----------------------------------------------------------------------------------------------
	 * Pause button handler.
	 * ----------------------------------------------------------------------------------------------*/
	class pauseButtonListener implements ActionListener
	{
		@Override
		public void actionPerformed(ActionEvent e)
		{
			System.out.println("Pause Button pressed!");

			if (currentState == PLAYING)
			{
				//increase RTSP sequence number
				rtspSequenceNumber++;

				//Send PAUSE message to the server
				sendRtspRequest("PAUSE");

				//Wait for the response
				if (parseServerResponse() != 200)
					System.out.println("Invalid Server Response");
				else
				{
					//change RTSP state and print out new state
					currentState = READY;
					System.out.println("New RTSP state: READY");

					//stop the timer
					timer.stop();
					rtcpSender.stopSend();
				}
			}
			//else if state != PLAYING then do nothing
		}
	}

	/**----------------------------------------------------------------------------------------------
	 * Teardown button handler.
	 * ----------------------------------------------------------------------------------------------*/
	class tearButtonListener implements ActionListener
	{
		@Override
		public void actionPerformed(ActionEvent e)
		{
			System.out.println("Teardown Button pressed !");

			//increase RTSP sequence number
			rtspSequenceNumber++;

			//Send TEARDOWN message to the server
			sendRtspRequest("TEARDOWN");

			//Wait for the response
			if (parseServerResponse() != 200)
				System.out.println("Invalid Server Response");
			else
			{
				//change RTSP state and print out new state
				currentState = INIT;
				System.out.println("New RTSP state: INIT");

				//stop the timer
				timer.stop();
				rtcpSender.stopSend();

				//exit
				System.exit(0);
			}
		}
	}

	//
	/**----------------------------------------------------------------------------------------------
	 * Returns data stream info.
	 * ----------------------------------------------------------------------------------------------*/
	class describeButtonListener implements ActionListener
	{
		@Override
		public void actionPerformed(ActionEvent e)
		{
			System.out.println("Sending DESCRIBE request");

			//increase RTSP sequence number
			rtspSequenceNumber++;

			//Send DESCRIBE message to the server
			sendRtspRequest("DESCRIBE");

			// Wait for the response
			if (parseServerResponse() != 200)
				System.out.println("Invalid Server Response");
			else
				System.out.println("Received response for DESCRIBE");
		}
	}

	/**----------------------------------------------------------------------------------------------
	 * Handler for timer.
	 * ----------------------------------------------------------------------------------------------*/
	class timerListener implements ActionListener
	{
		@Override
		public void actionPerformed(ActionEvent e)
		{
			//Construct a DatagramPacket to receive data from the UDP socket
			rcvUdpPacket = new DatagramPacket(rcvBuffer, rcvBuffer.length);

			try
			{
				//receive the DP from the socket, save time for stats
				rtpSocket.receive(rcvUdpPacket);

				double curTime = System.currentTimeMillis();
				statTotalPlayTime += curTime - statStartTime;
				statStartTime = curTime;

				//create an RTPpacket object from the DP
				RtpPacket rtp_packet = new RtpPacket(rcvUdpPacket.getData(), rcvUdpPacket.getLength());
				int seqNb = rtp_packet.getsequencenumber();

				//this is the highest seq num received

				//print important header fields of the RTP packet received:
				System.out.println("Got RTP packet with SeqNum # " + seqNb
								   + " TimeStamp " + rtp_packet.gettimestamp() + " ms, of type "
								   + rtp_packet.getpayloadtype());

				//print header bitstream:
				rtp_packet.printheader();

				//get the payload bitstream from the RTPpacket object
				int payload_length = rtp_packet.getpayload_length();
				byte [] payload = new byte[payload_length];
				rtp_packet.getpayload(payload);

				//compute stats and update the label in GUI
				statExpectedRtpCounter++;
				if (seqNb > statHighestSequenceNumber)
				{
					statHighestSequenceNumber = seqNb;
				}
				if (statExpectedRtpCounter != seqNb)
				{
					statLostPackets++;
				}
				statDataRate = statTotalPlayTime == 0 ? 0 : (statTotalBytes / (statTotalPlayTime / 1000.0));
				statFractionLost = (float)statLostPackets / statHighestSequenceNumber;
				statTotalBytes += payload_length;
				updateStatsLabel();

				//get an Image object from the payload bitstream
				Toolkit toolkit = Toolkit.getDefaultToolkit();
				frameSynchronizer.addFrame(toolkit.createImage(payload, 0, payload_length), seqNb);

				//display the image as an ImageIcon object
				icon = new ImageIcon(frameSynchronizer.nextFrame());
				iconLabel.setIcon(icon);
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

	/**----------------------------------------------------------------------------------------------
	 * Send RTCP control packets for QoS feedback.
	 * ----------------------------------------------------------------------------------------------*/
	class RtcpSender implements ActionListener
	{
		private Timer rtcpTimer;
		int interval;

		// Stats variables
		private int numPktsExpected;	// Number of RTP packets expected since the last RTCP packet
		private int numPktsLost;		// Number of RTP packets lost since the last RTCP packet
		private int lastHighSeqNb;	  // The last highest Seq number received
		private int lastCumLost;		// The last cumulative packets lost
		private float lastFractionLost; // The last fraction lost

		Random randomGenerator;		 // For testing only

		public RtcpSender(int interval)
		{
			this.interval = interval;
			rtcpTimer = new Timer(interval, this);
			rtcpTimer.setInitialDelay(0);
			rtcpTimer.setCoalesce(true);
			randomGenerator = new Random();
		}

		public void run()
		{
			System.out.println("RtcpSender Thread Running");
		}

		@Override
		public void actionPerformed(ActionEvent e)
		{
			// Calculate the stats for this period
			numPktsExpected = statHighestSequenceNumber - lastHighSeqNb;
			numPktsLost = statLostPackets - lastCumLost;
			lastFractionLost = numPktsExpected == 0 ? 0f : (float)numPktsLost / numPktsExpected;
			lastHighSeqNb = statHighestSequenceNumber;
			lastCumLost = statLostPackets;

			// To test lost feedback on lost packets
			// lastFractionLost = randomGenerator.nextInt(10)/10.0f;

			RtcpPacket rtcpPacket = new RtcpPacket(lastFractionLost, statLostPackets, statHighestSequenceNumber);
			int packetLength = rtcpPacket.getlength();
			byte[] packetBits = new byte[packetLength];
			rtcpPacket.getpacket(packetBits);

			try
			{
				DatagramPacket datagram = new DatagramPacket(packetBits, packetLength, serverIp, RTCP_RCV_PORT);
				rtcpSocket.send(datagram);
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

		/**----------------------------------------------------------------------------------------------
		 * Start sending RTCP packets.
		 * ----------------------------------------------------------------------------------------------*/
		public void startSend()
		{
			rtcpTimer.start();
		}

		/**----------------------------------------------------------------------------------------------
		 * Stop sending RTCP packets.
		 * ----------------------------------------------------------------------------------------------*/
		public void stopSend()
		{
			rtcpTimer.stop();
		}
	}

	/**----------------------------------------------------------------------------------------------
	 * Parse Server Response.
	 * ----------------------------------------------------------------------------------------------*/
	private int parseServerResponse()
	{
		int replyCode = 0;
		try
		{
			//parse status line and extract the reply_code:
			String StatusLine = rtspBufferedReader.readLine();
			System.out.println("RTSP Client - Received from Server:");
			System.out.println(StatusLine);

			StringTokenizer tokens = new StringTokenizer(StatusLine);
			tokens.nextToken(); //skip over the RTSP version
			replyCode = Integer.parseInt(tokens.nextToken());

			// if reply code is OK get and print the 2 other lines
			if (replyCode == 200)
			{
				String sequenceNumberLine = rtspBufferedReader.readLine();
				System.out.println(sequenceNumberLine);

				String sessionLine = rtspBufferedReader.readLine();
				System.out.println(sessionLine);

				tokens = new StringTokenizer(sessionLine);
				String temp = tokens.nextToken();
				// if state == INIT gets the Session Id from the SessionLine
				if ((currentState == INIT) && (temp.compareTo("Session:") == 0))
				{
					rtspId = Integer.parseInt(tokens.nextToken());
				}
				else if (temp.compareTo("Content-Base:") == 0)
				{
					// Get the DESCRIBE lines
					String newLine;
					for (int i = 0; i < 6; i++)
					{
						newLine = rtspBufferedReader.readLine();
						System.out.println(newLine);
					}
				}
			}
		}
		catch(Exception ex)
		{
			System.out.println("Exception caught: "+ex);
			System.exit(0);
		}
		return(replyCode);
	}

	/**----------------------------------------------------------------------------------------------
	 * Updates stats label.
	 * ----------------------------------------------------------------------------------------------*/
	private void updateStatsLabel()
	{
		DecimalFormat formatter = new DecimalFormat("###,###.##");
		statLabel1.setText("Total Bytes Received: " + statTotalBytes);
		statLabel2.setText("Packet Lost Rate: " + formatter.format(statFractionLost));
		statLabel3.setText("Data Rate: " + formatter.format(statDataRate) + " bytes/s");
	}

	/**----------------------------------------------------------------------------------------------
	 * Send RTSP Request.
	 * ----------------------------------------------------------------------------------------------*/
	private void sendRtspRequest(String requestType)
	{
		// Use the RTSPBufferedWriter to write to the RTSP socket
		try
		{
			//write the request line:
			rtspBufferedWriter.write(requestType + " " + videoFileName + " RTSP/1.0" + CRLF);

			//write the CSeq line:
			rtspBufferedWriter.write("CSeq: " + rtspSequenceNumber + CRLF);

			switch (requestType)
			{
				case "SETUP":	// send RTP packets RTP_RCV_PORT to server
					rtspBufferedWriter.write("Transport: RTP/UDP; client_port= " + RTP_RCV_PORT + CRLF);
				case "DESCRIBE":
					rtspBufferedWriter.write("Accept: application/sdp" + CRLF);
				default:
					//otherwise, write the Session line from the RTSPid field
					rtspBufferedWriter.write("Session: " + rtspId + CRLF);
			}
			rtspBufferedWriter.flush();
		}
		catch(Exception ex)
		{
			System.out.println("Exception caught: "+ex);
			System.exit(0);
		}
	}

	//------------------------------------------------------------------------------------------------------
	public InetAddress getServerIp()
	{
		return serverIp;
	}

	public void setServerIp(InetAddress serverIp)
	{
		this.serverIp = serverIp;
	}

	public Socket getRtspSocket()
	{
		return rtspSocket;
	}

	public void setRtspSocket(Socket rtspSocket)
	{
		this.rtspSocket = rtspSocket;
	}

	public static BufferedReader getRtspBufferedReader()
	{
		return rtspBufferedReader;
	}

	public static void setRtspBufferedReader(BufferedReader rtspBufferedReader)
	{
		Client.rtspBufferedReader = rtspBufferedReader;
	}

	public static BufferedWriter getRtspBufferedWriter()
	{
		return rtspBufferedWriter;
	}

	public static void setRtspBufferedWriter(BufferedWriter rtspBufferedWriter)
	{
		Client.rtspBufferedWriter = rtspBufferedWriter;
	}

	public static int getCurrentState()
	{
		return currentState;
	}

	public static void setCurrentState(int currentState)
	{
		Client.currentState = currentState;
	}
}
//end of Class Client