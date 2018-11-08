package fr.an.qrcode.channel.impl.decode;

@FunctionalInterface
public interface DecoderChannelListener {

	public void onEvent(DecoderChannelEvent event);
	
}
