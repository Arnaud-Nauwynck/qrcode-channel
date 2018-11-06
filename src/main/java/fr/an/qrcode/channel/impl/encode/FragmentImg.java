package fr.an.qrcode.channel.impl.encode;

import java.awt.image.BufferedImage;

/**
 * wrapper of QRCodeEncodedFragment, to hold the computed BufferedImage reference (not with WeakReference)
 */
public class FragmentImg {
	public final QRCodeEncodedFragment owner;
	public BufferedImage img;
	
	public FragmentImg(QRCodeEncodedFragment owner, BufferedImage img) {
		this.owner = owner;
		this.img = img;
	}

	public int getFragmentNumber() {
		return owner.getFragmentNumber();
	}

	public boolean isAcknowledge() {
		return owner.isAcknowledge();
	}

	public void acknowledge() {
		this.owner.acknowledge();
		this.img = null;
	}


	
	
}