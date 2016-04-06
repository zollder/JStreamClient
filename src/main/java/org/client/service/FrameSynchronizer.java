package org.client.service;

import java.awt.Image;
import java.util.ArrayDeque;

/**--------------------------------------------------------------------------------------------
 * Frame synchronizer implementation. TODO: please refactor
 * --------------------------------------------------------------------------------------------*/
public class FrameSynchronizer
{
	private ArrayDeque<Image> queue;
	private int currentSequenceNumber;
	private Image lastImage;

	public FrameSynchronizer(int bufferSize)
	{
		currentSequenceNumber = 1;
		queue = new ArrayDeque<Image>(bufferSize);
	}

	/**--------------------------------------------------------------------------------------------
	 * Synchronizes frames based on their sequence number.
	 * --------------------------------------------------------------------------------------------*/
	public void addFrame(Image image, int seqNum)
	{
		if (seqNum < currentSequenceNumber)
			queue.add(lastImage);
		else if (seqNum > currentSequenceNumber)
		{
			for (int i = currentSequenceNumber; i < seqNum; i++)
			{
				queue.add(lastImage);
			}
			queue.add(image);
		}
		else
			queue.add(image);
	}

	/**--------------------------------------------------------------------------------------------
	 * Returns next synchronized frame.
	 * --------------------------------------------------------------------------------------------*/
	public Image nextFrame()
	{
		currentSequenceNumber++;
		lastImage = queue.peekLast();
		return queue.remove();
	}
}