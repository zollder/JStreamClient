package org.client.ui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.DatagramPacket;
import java.util.Random;

import javax.swing.Timer;

import org.client.model.RtcpPacket;

/**----------------------------------------------------------------------------------------------
 * RtcpSender implementation.
 * Is responsible for sending RTCP control packets for QoS feedback.
 * ----------------------------------------------------------------------------------------------*/
public class RtcpService implements ActionListener
{
	StreamClientController client;
	private Timer rtcpTimer;
	int interval = 400;

	// Stats variables
	private int numPktsExpected;	// Number of RTP packets expected since the last RTCP packet
	private int numPktsLost;		// Number of RTP packets lost since the last RTCP packet
	private int lastHighSeqNb;	  // The last highest Seq number received
	private int lastCumLost;		// The last cumulative packets lost
	private float lastFractionLost; // The last fraction lost

	Random randomGenerator;		 // For testing only

	static int RTCP_RCV_PORT = 19001;	// port where the client will receive the RTP packets

	public RtcpService(StreamClientController client)
	{
		this.client = client;
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
		numPktsExpected = client.statHighestSequenceNumber - lastHighSeqNb;
		numPktsLost = client.statLostPackets - lastCumLost;
		lastFractionLost = numPktsExpected == 0 ? 0f : (float)numPktsLost / numPktsExpected;
		lastHighSeqNb = client.statHighestSequenceNumber;
		lastCumLost = client.statLostPackets;

		// To test lost feedback on lost packets
		// lastFractionLost = randomGenerator.nextInt(10)/10.0f;

		RtcpPacket rtcpPacket = new RtcpPacket(lastFractionLost, client.statLostPackets, client.statHighestSequenceNumber);
		int packetLength = rtcpPacket.getlength();
		byte[] packetBits = new byte[packetLength];
		rtcpPacket.getpacket(packetBits);

		try
		{
			DatagramPacket datagram = new DatagramPacket(packetBits, packetLength, client.getServerIp(), RTCP_RCV_PORT);
			client.rtcpSocket.send(datagram);
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
