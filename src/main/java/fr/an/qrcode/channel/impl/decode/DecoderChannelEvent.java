package fr.an.qrcode.channel.impl.decode;

import java.awt.image.BufferedImage;

import fr.an.qrcode.channel.impl.decode.QRCodesDecoderChannel.SnapshotFragmentResult;

public class DecoderChannelEvent {

	public final long startTime;
	public final long timeMillis;
	public final BufferedImage img;
	public final SnapshotFragmentResult snapshotResult;
	public final String readyText;
	
	public DecoderChannelEvent(long startTime, long timeMillis, BufferedImage img,
			SnapshotFragmentResult snapshotResult, String readyText) {
		super();
		this.startTime = startTime;
		this.timeMillis = timeMillis;
		this.img = img;
		this.snapshotResult = snapshotResult;
		this.readyText = readyText;
	}
	
}