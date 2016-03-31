/* ----------------------------------------------------------
   Server
   usage: java Server [RTSP listening port]
   ---------------------------------------------------------- */
package org.client;

import java.io.IOException;

import javafx.application.Application;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import org.client.ui.StreamClientController;

public class MainApp extends Application
{
	private Stage primaryStage;
	private BorderPane rootLayout;

	/**---------------------------------------------------------------------------------------
	 * Main executor.
	 * ---------------------------------------------------------------------------------------*/
	public static void main(String[] args) throws Exception
	{
		launch(args);
	}

	/**---------------------------------------------------------------------------------------
	 * Starts application.
	 * Initializes UI components and opens primary stage.
	 * ---------------------------------------------------------------------------------------*/
	@Override
	public void start(Stage stage) throws Exception
	{
		primaryStage = stage;
		primaryStage.setTitle("Stream Client");

		// stop application when its main window is closed
        primaryStage.setOnCloseRequest(new EventHandler<WindowEvent>() {
			@Override
			public void handle(WindowEvent event) { System.exit(0); }
        });

        // init and show UI components
		initRootLayout();
		showStreamClient();

/*		//Create a Client object
		Client client = new Client();

		String host = "localhost";
		client.serverIp = InetAddress.getByName(host);

		//Establish a TCP connection with the server to exchange RTSP messages
		client.rtspSocket = new Socket(client.serverIp, client.RTSP_SERVER_PORT);

		// Establish a UDP connection with the server to exchange RTCP control packets
		// Set input and output stream filters and initial state.
		Client.rtspBufferedReader = new BufferedReader(new InputStreamReader(client.rtspSocket.getInputStream()));
		Client.rtspBufferedWriter = new BufferedWriter(new OutputStreamWriter(client.rtspSocket.getOutputStream()));
		Client.currentState = Client.INIT;

		CommunicationService service = new CommunicationService(new Server());

        Button setupButton = new Button("Setup");
        Button playButton = new Button("Play");
        Button pauseButton = new Button("Pause");
        Button closeButton = new Button("Close");
        Button sessionButton = new Button("Session");

        // stop application when "close" button is clicked
        closeButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                System.out.println("Stopping stream server");
                service.cancel();
                stop(primaryStage);
            }
        });

        service.start();*/
	}

	/**---------------------------------------------------------------------------------------
	 * Initializes root layout - a wrapper around the main StreamClient scene.
	 * ---------------------------------------------------------------------------------------*/
	private void initRootLayout()
	{
		try
		{
			// Load root layout.
			FXMLLoader loader = new FXMLLoader();
			loader.setLocation(getClass().getResource("/org/client/ui/RootLayout.fxml"));
			rootLayout = (BorderPane) loader.load();

			// Show scene containing root layout.
			Scene scene = new Scene(rootLayout);
			primaryStage.setScene(scene);
			primaryStage.show();
		}
		catch (IOException exception) { exception.printStackTrace(); }
	}

	/**---------------------------------------------------------------------------------------
	 * Configures and shows StreamClient's layout inside root layout.
	 * ---------------------------------------------------------------------------------------*/
	private void showStreamClient()
	{
		try
		{
			// Load client's layout from fxml file.
			FXMLLoader loader = new FXMLLoader();
			loader.setLocation(getClass().getResource("/org/client/ui/StreamClientLayout.fxml"));
			AnchorPane streamClient = (AnchorPane) loader.load();

			// set client's layout in the center of root
			rootLayout.setCenter(streamClient);

			StreamClientController clientController = loader.getController();
			clientController.setApplication(this);
		}
		catch (IOException exception) { exception.printStackTrace(); }
	}

	/**---------------------------------------------------------------------------------------
	 * Stops underlying services before closing the primary application stage.
	 * ---------------------------------------------------------------------------------------*/
	@Override
	public void stop()
	{
		primaryStage.close();
    	System.exit(0);
	}

	/**---------------------------------------------------------------------------------------
	 * Returns primary stage.
	 * ---------------------------------------------------------------------------------------*/
	public Stage getPrimaryStage()
	{
		return primaryStage;
	}
}