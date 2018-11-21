package fr.an.qrcode.channel.impl.decode.filter;

import java.awt.image.BufferedImage;
import java.util.List;

import fr.an.qrcode.channel.impl.decode.QRResult;

/**
 * 
 */
public class QRCapturedEvent {

	public final BufferedImage image;
	public final long nanosSnapshot;
	public final long nanosSnapshotTime;

	public final List<QRResult> qrResults;
	public final long nanosDecode;

	public QRCapturedEvent(
			List<QRResult> qrResults, long nanosDecode,
			BufferedImage image, long nanosSnapshot, long nanosSnapshotTime) {
		this.qrResults = qrResults;
		this.nanosDecode = nanosDecode;
		this.image = image;
		this.nanosSnapshot = nanosSnapshot;
		this.nanosSnapshotTime = nanosSnapshotTime;
	}
	
}