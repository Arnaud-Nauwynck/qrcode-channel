package fr.an.qrcode.channel.impl.decode;

import fr.an.qrcode.channel.impl.decode.filter.QRCapturedEvent;

public class DecoderChannelEvent {

	public final String currDecodeMsg;
	public final String readyText;

	public final QRCapturedEvent qrEvent;
	
	public DecoderChannelEvent(String currDecodeMsg,  
			String readyText,
			QRCapturedEvent qrEvent) {
		super();
		this.currDecodeMsg = currDecodeMsg;
		this.readyText = readyText;
		this.qrEvent = qrEvent;
	}

	
}