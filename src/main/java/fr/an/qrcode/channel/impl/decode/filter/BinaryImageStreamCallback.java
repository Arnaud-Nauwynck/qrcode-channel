package fr.an.qrcode.channel.impl.decode.filter;

import java.awt.Dimension;
import java.awt.image.BufferedImage;

import com.google.zxing.BinaryBitmap;

public abstract class BinaryImageStreamCallback {

	public abstract void onImage(
			BufferedImage image,
			BinaryBitmap bitmap, long nanosTime, long nanos);

	public abstract void onDropImage(
			BufferedImage image, long nanosTime, long nanos);

	public void onStart(Dimension dim) {}
	public void onEnd() {}
    
}
