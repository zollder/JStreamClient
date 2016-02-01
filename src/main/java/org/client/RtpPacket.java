package org.client;

import java.util.Arrays;

public class RtpPacket
{
	// RTP header size (12 bytes)
	static int HEADER_SIZE = 12;

	// RTP header fields
	public int version = 2;				// (2 bits) protocol version (version = 2)
	public int padding = 0;				// (1 bit) if set, the packet contains additional padding octets
	public int extension = 0;			// (1 bit) if set, the fixed header is followed by exactly one packet extension
	public int csrcCount = 0;			// (4 bits) number of CSRC identifiers that follow the fixed header
	public int marker = 0;				// (1 bit) defined by profile, identifies frame boundaries
	public int payloadType;				// (7 bits) identifies the format of RTP payload
	public int sequenceNumber;			// (16 bits) increments by 1 for each RTP packet sent
	public int timeStamp;				// (32 bits) reflects sampling instant of the 1st octet of RTP data packet
	public int ssrc = 1337;				// (32 bits) identifies synchronization source (server identifier)

	// RTP header Bitstream
	public byte[] header;

	// RTP payload size
	public int payloadSize;

	// RTP payload Bitstream
	public byte[] payload;

	//------------------------------------------------------------------------------
	// Constructor of an RtpPacket object from header fields and payload bitstream.
	//------------------------------------------------------------------------------
	public RtpPacket(int pType, int frameNum, int time, byte[] data, int dataLength)
	{
		// initialize changing header fields
		this.payloadType = pType;
		this.sequenceNumber = frameNum;
		this.timeStamp = time;

		// initialize header bistream:
		header = new byte[HEADER_SIZE];

		// fill RTP packet header (to set n-th bit use: var | 1 << 7-n)
		// payloadType gets promoted to int (padding with 1s, & 0x000000FF clears them out)
		header[0] = (byte)((version << 6) | (padding << 5) | (extension << 4) | csrcCount);
		header[1] = (byte)((marker << 7) | (payloadType & 0x000000FF));
		header[2] = (byte)(sequenceNumber >> 8);		// retrieves upper 8 bits by shifting right by 8 bits
		header[3] = (byte)(sequenceNumber & 0xFF);		// same as & 0x000000FF, retrieves lower 8 bits, clears out others
		header[4] = (byte)(timeStamp >> 24);
		header[5] = (byte)(timeStamp >> 16);
		header[6] = (byte)(timeStamp >> 8);
		header[7] = (byte)(timeStamp & 0xFF);
		header[8] = (byte)(ssrc >> 24);
		header[9] = (byte)(ssrc >> 16);
		header[10] = (byte)(ssrc >> 8);
		header[11] = (byte)(ssrc & 0xFF);

		// fill RTP packet data section
		payloadSize = dataLength;
		payload = new byte[dataLength];
		payload = Arrays.copyOf(data, payloadSize);
	}

	//------------------------------------------------------------------------------
	// RtpPacket constructor from the packet bistream.
	//------------------------------------------------------------------------------
	public RtpPacket(byte[] packet, int packetSize)
	{
		ssrc = 0;

		// check if total packet size is lower than the header size
		if (packetSize >= HEADER_SIZE)
		{
			// retrieve header bytes
			header = new byte[HEADER_SIZE];
			for (int i = 0; i < HEADER_SIZE; i++) {
				header[i] = packet[i];
			}

			// retrieve payload bytes
			payloadSize = packetSize - HEADER_SIZE;
			payload = new byte[payloadSize];
			for (int i = HEADER_SIZE; i < packetSize; i++)
				payload[i-HEADER_SIZE] = packet[i];

			// interpret non-constant header fields
			version = (header[0] & 0xFF) >>> 6;
			payloadType = header[1] & 0x7F;
			sequenceNumber = (header[3] & 0xFF)
					+ ((header[2] & 0xFF) << 8);
			timeStamp = (header[7] & 0xFF)
					+ ((header[6] & 0xFF) << 8)
					+ ((header[5] & 0xFF) << 16)
					+ ((header[4] & 0xFF) << 24);
		}
	}

	//--------------------------
	//getpayload: return the payload bistream of the RTPpacket and its size
	//--------------------------
	public int getpayload(byte[] data) {

		for (int i = 0; i < payloadSize; i++)
			data[i] = payload[i];

		return(payloadSize);
	}

	//--------------------------
	//getpayload_length: return the length of the payload
	//--------------------------
	public int getpayload_length() {
		return(payloadSize);
	}

	//--------------------------
	//getlength: return the total length of the RTP packet
	//--------------------------
	public int getlength() {
		return(payloadSize + HEADER_SIZE);
	}

	//--------------------------
	//getpacket: returns the packet bitstream and its length
	//--------------------------
	public int getpacket(byte[] packet)
	{
		//construct the packet = header + payload
		for (int i=0; i < HEADER_SIZE; i++)
			packet[i] = header[i];
		for (int i=0; i < payloadSize; i++)
			packet[i+HEADER_SIZE] = payload[i];

		//return total size of the packet
		return(payloadSize + HEADER_SIZE);
	}

	//--------------------------
	//gettimestamp
	//--------------------------

	public int gettimestamp() {
		return(timeStamp);
	}

	//--------------------------
	//getsequencenumber
	//--------------------------
	public int getsequencenumber() {
		return(sequenceNumber);
	}

	//--------------------------
	//getpayloadtype
	//--------------------------
	public int getpayloadtype() {
		return(payloadType);
	}

	//--------------------------
	//print headers without the SSRC
	//--------------------------
	public void printheader()
	{
		System.out.print("[RTP-Header] ");
		System.out.println("Version: " + version
						   + ", Padding: " + padding
						   + ", Extension: " + extension
						   + ", CC: " + csrcCount
						   + ", Marker: " + marker
						   + ", PayloadType: " + payloadType
						   + ", SequenceNumber: " + sequenceNumber
						   + ", TimeStamp: " + timeStamp);

	}
}