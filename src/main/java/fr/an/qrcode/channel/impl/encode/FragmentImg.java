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

	/** true for the FEC-params preamble and RaptorQ source symbols; false for repair (redundant) symbols */
	public boolean isSource() {
		return owner.isParamsFragment() || owner.getPacket().symbolType() == net.fec.openrq.SymbolType.SOURCE;
	}

	public boolean isRepair() {
		return !isSource();
	}

	public boolean isAcknowledge() {
		return owner.isAcknowledge();
	}

	public void acknowledge() {
		this.owner.acknowledge();
		this.img = null;
	}

	/** marks this fragment as (re-)displayed, incrementing its sent counter */
	public void incrSentCount() {
		owner.incrSentCount();
	}

	public int getSentCount() {
		return owner.getSentCount();
	}


	
	
}