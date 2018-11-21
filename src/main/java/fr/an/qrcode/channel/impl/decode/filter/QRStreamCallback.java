package fr.an.qrcode.channel.impl.decode.filter;

import fr.an.qrcode.channel.impl.util.DimInt2D;

public abstract class QRStreamCallback {

	public void onStart(DimInt2D dim) {}
	public void onEnd() {}

	public abstract void onQRCaptured(QRCapturedEvent event);
    
}
