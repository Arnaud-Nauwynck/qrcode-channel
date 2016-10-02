package fr.an.qrcode.channel.impl.encode;

import java.awt.image.BufferedImage;

public class FragmentImg {
	public final int fragmentNumber;
	public final String fragmentId;
	// public final String fragmentHeader;
	public BufferedImage img;
	public boolean acknowledge = false;
	
	public FragmentImg(int fragmentNumber, String fragmentId, BufferedImage img) {
		this.fragmentNumber = fragmentNumber;
		this.fragmentId = fragmentId;
		this.img = img;
	}

	public boolean isAcknowledge() {
		return acknowledge;
	}

	public void acknowledge() {
		this.acknowledge = true;
		this.img = null;
	}
	
	
}