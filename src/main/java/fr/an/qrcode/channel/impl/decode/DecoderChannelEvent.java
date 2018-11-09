package fr.an.qrcode.channel.impl.decode;

import java.awt.image.BufferedImage;

import com.google.zxing.BinaryBitmap;

import fr.an.qrcode.channel.impl.decode.QRCodesDecoderChannel.QRPacketResult;

public class DecoderChannelEvent {

	public final BufferedImage img;
	public final long captureNanosTime;
	public final long captureNanos;
	
	public final BinaryBitmap bitmap;

	public final QRPacketResult snapshotResult;
	public final long computeNanosTime;
	public final long computeNanos;

	public final String readyText;

	public DecoderChannelEvent(
			BufferedImage img, long captureNanosTime, long captureNanos,
			BinaryBitmap bitmap,
			QRPacketResult snapshotResult, long computeNanosTime, long computeNanos, 
			String readyText) {
		this.img = img;
		this.bitmap = bitmap;
		this.captureNanosTime = captureNanosTime;
		this.captureNanos = captureNanos;
		this.snapshotResult = snapshotResult;
		this.computeNanosTime = computeNanosTime;
		this.computeNanos = computeNanos;
		this.readyText = readyText;
	}
	
}