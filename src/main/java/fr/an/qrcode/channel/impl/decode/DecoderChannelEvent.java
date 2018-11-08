package fr.an.qrcode.channel.impl.decode;

import java.awt.image.BufferedImage;

import fr.an.qrcode.channel.impl.decode.QRCodesDecoderChannel.QRPacketResult;

public class DecoderChannelEvent {

	public final BufferedImage img;
	public final long captureNanosTime;
	public final long captureNanos;

	public final QRPacketResult snapshotResult;
	public final long computeNanosTime;
	public final long computeNanos;

	public final String readyText;

	public DecoderChannelEvent(
			BufferedImage img, long captureNanosTime, long captureNanos,
			QRPacketResult snapshotResult, long computeNanosTime, long computeNanos, 
			String readyText) {
		this.img = img;
		this.captureNanosTime = captureNanosTime;
		this.captureNanos = captureNanos;
		this.snapshotResult = snapshotResult;
		this.computeNanosTime = computeNanosTime;
		this.computeNanos = computeNanos;
		this.readyText = readyText;
	}
	
	
	
}