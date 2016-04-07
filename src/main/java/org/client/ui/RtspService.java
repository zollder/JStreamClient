package org.client.ui;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.util.StringTokenizer;


/**----------------------------------------------------------------------------------------------
 * RtspService implementation
 * Is responsible for sending and parsing RTSP datagrams.
 * ----------------------------------------------------------------------------------------------*/
class RtspService
{
	//input and output stream filters
	static BufferedReader rtspBufferedReader;
	static BufferedWriter rtspBufferedWriter;

	int rtspSequenceNumber = 0;		// RTSP message sequence number (within the session)
	int rtspId = 0;					// RTSP session ID (given by the RTSP Server)

	static int RTP_RCV_PORT = 25000;	// client RTP port (receive)
	static String videoFileName = "movie.Mjpeg";	// video file name
	final static String CRLF = "\r\n";
	final static String DES_FNAME = "session_info.txt";

	public RtspService()
	{}

	/**----------------------------------------------------------------------------------------------
	 * Send RTSP Request.
	 * ----------------------------------------------------------------------------------------------*/
	protected void sendRtspRequest(String requestType)
	{
		// Write to RTSP socket using rtspBufferedWriter
		try
		{
			// write the request line:
			rtspBufferedWriter.write(requestType + " " + videoFileName + " RTSP/1.0" + CRLF);

			// write the CSeq line:
			rtspBufferedWriter.write("CSeq: " + rtspSequenceNumber + CRLF);

			// write server port for RTP packets (RTP_RCV_PORT) to 'Transport:' line in case of "SETUP" request type
			if (requestType == "SETUP") {
				rtspBufferedWriter.write("Transport: RTP/UDP; client_port= " + RTP_RCV_PORT + CRLF);
			}
			else if (requestType == "DESCRIBE") {
				rtspBufferedWriter.write("Accept: application/sdp" + CRLF);
			}
			else {
				//otherwise, write the Session line from the RTSPid field
				rtspBufferedWriter.write("Session: " + rtspId + CRLF);
			}

			rtspBufferedWriter.flush();
		}
		catch(Exception ex) {
			System.out.println("Exception caught: "+ex);
			System.exit(0);
		}
	}

	/**----------------------------------------------------------------------------------------------
	 * Parse Server Response.
	 * ----------------------------------------------------------------------------------------------*/
	public int parseServerResponse(int currentState)
	{
		int replyCode = 0;
		try
		{
			//parse status line and extract the reply_code:
			String statusLine = rtspBufferedReader.readLine();
			System.out.println("RTSP Client - Received from Server:");
			System.out.println(statusLine);

			StringTokenizer tokens = new StringTokenizer(statusLine);
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
				if ((currentState == 0) && (temp.compareTo("Session:") == 0))
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
			System.out.println("Exception caught: " + ex);
			System.exit(0);
		}
		return(replyCode);
	}
}
