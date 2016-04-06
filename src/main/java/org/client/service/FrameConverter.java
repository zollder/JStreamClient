package org.client.service;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;


/**--------------------------------------------------------------------------------------------------------
 * Frame converter implementation.
 * --------------------------------------------------------------------------------------------------------*/
public class FrameConverter
{
	public Image convertImage(java.awt.Image awtImage) throws IOException
	{
		BufferedImage bImg ;
		if (awtImage instanceof BufferedImage) {
		    bImg = (BufferedImage) awtImage ;
		} else {
		    bImg = new BufferedImage(awtImage.getWidth(null), awtImage.getHeight(null), BufferedImage.TYPE_INT_ARGB);
		    Graphics2D graphics = bImg.createGraphics();
		    graphics.drawImage(awtImage, 0, 0, null);
		    graphics.dispose();
		}
		Image fxImage = SwingFXUtils.toFXImage(bImg, null);
		return fxImage;
	}
}