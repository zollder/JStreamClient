package org.client.ui;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

import org.client.MainApp;

public class StreamClientController
{
	private static String SERVER_HOST = "localhost";
	private int RTSP_SERVER_PORT = 13569;

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
	}

	/** Is called by the main application to give a reference back to itself.
	 *  @param app - main application reference */
	public void setApplication(MainApp app)
	{
		this.application = app;
	}

	/** Handles close operation. */
	@FXML
	private void close()
	{
		Platform.exit();
	}

	/** Handles "setup" button operation.
	 *  Performs RTSP setup routine. */
	@FXML
	private void setup()
	{
		Platform.exit();
	}

	/** Handles "play" button operation.
	 *  Initiates or resumes data transfer b/w server and client. */
	@FXML
	private void play()
	{
		Platform.exit();
	}

	/** Handles "pause" button operation.
	 *  Suspends data transfer b/w server and client. */
	@FXML
	private void pause()
	{
		Platform.exit();
	}

	/** Handles "session" button operation.
	 *  Retrieves and shows session info. */
	@FXML
	private void session()
	{
		Platform.exit();
	}

	/**
	 * Initializes client.
	 * Establishes communication with the server.
	 * Blocks execution if the server is not available.
	 * @return null
	 * @throws IOException
	 */
	public Task<Client> connect() throws IOException
	{
		// initialize client
		client.setServerIp(InetAddress.getByName(SERVER_HOST));

		// Establish a TCP connection with the server to exchange RTSP messages (blocking)
		client.setRtspSocket(new Socket(client.getServerIp(), RTSP_SERVER_PORT));

		// Establish a UDP connection with the server to exchange RTCP control packets
		// Set input and output stream filters and initial state.
		Client.setRtspBufferedReader(new BufferedReader(new InputStreamReader(client.getRtspSocket().getInputStream())));
		Client.setRtspBufferedWriter(new BufferedWriter(new OutputStreamWriter(client.getRtspSocket().getOutputStream())));
		Client.setCurrentState(0);

		return null;
	}

	public Label getBytesReceived()
	{
		return bytesReceived;
	}

	public void setBytesReceived(Label bytesReceived)
	{
		this.bytesReceived = bytesReceived;
	}
}