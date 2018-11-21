package fr.an.qrcode.channel.impl.decode.input;

import java.awt.image.BufferedImage;

import fr.an.qrcode.channel.impl.decode.filter.QRDecodeRollingStats;
import fr.an.qrcode.channel.impl.util.DimInt2D;

public abstract class ImageStreamCallback {

	public abstract void onImage(BufferedImage image, long nanosTime, long nanos);
	
	public void onStart(DimInt2D dim) {}
	public void onEnd() {}
	
	public abstract QRDecodeRollingStats getRollingStats();
}
