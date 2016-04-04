package org.client.ui;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.text.DecimalFormat;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

import org.client.MainApp;

public class StreamClientController
{
	private static final int RTSP_SERVER_PORT = 13569;
	private static final String SERVER_HOST = "localhost";

	private MainApp application;

	private Client client;

	@FXML private Label bytesReceived;
	@FXML private Label packetsLost;
	@FXML private Label dataRate;

	/**
	 * Constructor, is called before #initialize()
	 */
	public StreamClientController() {}

   /** Initializes the controller class. This method is automatically called after the fxml file has been loaded.
    *  @throws IOException */
	@FXML
	private void initialize()
	{
		// Create a Client object
		client = new Client();
		this.updateStat(0, 0, 0);
	}

	/** Is called by the main application to give a reference back to itself.
	 *  @param app - main application reference */
	public void setApplication(MainApp app)
	{
		this.application = app;
	}

	/** Handles "setup" button operation.
	 *  Performs RTSP setup routine. */
	@FXML
	private void setup()
	{
		System.out.println("Setup Button pressed !");
		if (Client.currentState == Client.INIT)
		{
			//Init non-blocking RTPsocket that will be used to receive data
			try
			{
				//construct a new DatagramSocket to receive RTP packets from the server, on port RTP_RCV_PORT
				client.rtpSocket = new DatagramSocket(Client.RTP_RCV_PORT);
				//UDP socket for sending QoS RTCP packets
				client.rtcpSocket = new DatagramSocket();
				//set TimeOut value of the socket to 5msec.
				client.rtpSocket.setSoTimeout(5);
			}
			catch (SocketException se)
			{
				System.out.println("Socket exception: "+se);
				System.exit(0);
			}

			//init RTSP sequence number
			client.rtspSequenceNumber = 1;

			//Send SETUP message to the server
			client.sendRtspRequest("SETUP");

			//Wait for the response
			if (client.parseServerResponse() != 200)
				System.out.println("Invalid Server Response");
			else
			{
				//change RTSP state and print new state
				Client.currentState = Client.READY;
				System.out.println("New RTSP state: READY");
			}
		}
		//else if state != INIT then do nothing
	}

	/** Handles "play" button operation.
	 *  Initiates or resumes data transfer b/w server and client. */
	@FXML
	private void play()
	{
		System.out.println("Play Button pressed!");

		// Start to save the time in stats
		client.statStartTime = System.currentTimeMillis();

		if (Client.currentState == Client.READY)
		{
			client.rtspSequenceNumber++;

			//Send PLAY message to the server
			client.sendRtspRequest("PLAY");

			if (client.parseServerResponse() != 200)
				System.out.println("Invalid Server Response");
			else
			{
				//change RTSP state and print out new state
				Client.currentState = Client.PLAYING;
				System.out.println("New RTSP state: PLAYING");

				//start the timer
				client.timer.start();
				client.rtcpSender.startSend();
			}
		}
		//else if state != READY then do nothing
	}

	/** Handles "pause" button operation.
	 *  Suspends data transfer b/w server and client. */
	@FXML
	private void pause()
	{
		System.out.println("Pause Button pressed!");

		if (Client.currentState == Client.PLAYING)
		{
			//increase RTSP sequence number
			client.rtspSequenceNumber++;

			//Send PAUSE message to the server
			client.sendRtspRequest("PAUSE");

			//Wait for the response
			if (client.parseServerResponse() != 200)
				System.out.println("Invalid Server Response");
			else
			{
				//change RTSP state and print out new state
				Client.currentState = Client.READY;
				System.out.println("New RTSP state: READY");

				//stop the timer
				client.timer.stop();
				client.rtcpSender.stopSend();
			}
		}
		//else if state != PLAYING then do nothing
	}

	/** Handles "session" button operation.
	 *  Retrieves and shows session info. */
	@FXML
	private void session()
	{
		System.out.println("Sending DESCRIBE request");

		//increase RTSP sequence number
		client.rtspSequenceNumber++;

		//Send DESCRIBE message to the server
		client.sendRtspRequest("DESCRIBE");

		// Wait for the response
		if (client.parseServerResponse() != 200)
			System.out.println("Invalid Server Response");
		else
			System.out.println("Received response for DESCRIBE");
	}

	/** Handles close operation. */
	@FXML
	private void close()
	{
		System.out.println("Close Button pressed !");

		//increase RTSP sequence number
		client.rtspSequenceNumber++;

		//Send TEARDOWN message to the server
		client.sendRtspRequest("TEARDOWN");

		//Wait for the response
		if (client.parseServerResponse() != 200)
			System.out.println("Invalid Server Response");
		else
		{
			//change RTSP state and print out new state
			Client.currentState = Client.INIT;
			System.out.println("New RTSP state: INIT");

			//stop the timer
			client.timer.stop();
			client.rtcpSender.stopSend();

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
		client.setServerIp(InetAddress.getByName(SERVER_HOST));

		// Establish a TCP connection with the server to exchange RTSP messages (blocking)
		client.setRtspSocket(new Socket(client.getServerIp(), RTSP_SERVER_PORT));

		// Establish a UDP connection with the server to exchange RTCP control packets
		// Set input and output stream filters and initial state.
		Client.rtspBufferedReader = new BufferedReader(new InputStreamReader(client.getRtspSocket().getInputStream()));
		Client.rtspBufferedWriter = new BufferedWriter(new OutputStreamWriter(client.getRtspSocket().getOutputStream()));
		Client.currentState = Client.INIT;
	}

	/**
	 * Updates statistics data labels.
	 * @param bytesReceived
	 * @param fractionLost
	 * @param dataRate
	 */
	public void updateStat(int bytesReceived, float fractionLost, double dataRate)
	{
		DecimalFormat formatter = new DecimalFormat("###,###.##");
		this.bytesReceived.setText(String.valueOf(bytesReceived));
		this.packetsLost.setText(formatter.format(fractionLost));
		this.dataRate.setText(formatter.format(dataRate));
	}
}