package fr.an.qrcode.channel.impl.decode.input;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

public abstract class ImageProvider {

    protected Rectangle recordArea = new Rectangle(40,217,740,720);

	public abstract BufferedImage captureImage();

	public abstract void parseRecordParamsText(String recordParamsText);
	
	public Rectangle getRecordArea() {
		return this.recordArea;
	}

	public void setRecordArea(Rectangle r) {
		this.recordArea = r;
	}

	public Dimension getSize() {
		return recordArea.getSize();
	}
	
}