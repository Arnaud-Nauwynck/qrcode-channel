package fr.an.qrcode.channel.impl.decode;

import java.util.List;

import fr.an.qrcode.channel.impl.util.PtInt2D;

public class QRResult {

	public final String text;
	
	public final byte[] rawBytes;
	
	public final List<PtInt2D> resultPoints;

	// QRBitsMatrix
	
	public QRResult(String text, byte[] rawBytes, List<PtInt2D> resultPoints) {
		this.text = text;
		this.rawBytes = rawBytes;
		this.resultPoints = resultPoints;
	}
	  
	
}
