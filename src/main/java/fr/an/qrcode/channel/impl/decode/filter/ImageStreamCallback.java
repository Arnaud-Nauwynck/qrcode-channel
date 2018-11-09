package fr.an.qrcode.channel.impl.decode.filter;

import java.awt.Dimension;
import java.awt.image.BufferedImage;

public abstract class ImageStreamCallback {

	public abstract void onImage(BufferedImage image, long nanosTime, long nanos);
	
	public void onStart(Dimension dim) {}
	public void onEnd() {}
}
