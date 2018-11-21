package fr.an.qrcode.channel.impl;

public class QRBitsMatrix {

	public final int width;
	public final int height;

	public final boolean[] bits;

	public QRBitsMatrix(int width, int height, boolean[] bits) {
		super();
		this.width = width;
		this.height = height;
		this.bits = bits;
	}

}
