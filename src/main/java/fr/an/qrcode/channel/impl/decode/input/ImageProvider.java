package fr.an.qrcode.channel.impl.decode.input;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import fr.an.qrcode.channel.impl.util.DimInt2D;

public abstract class ImageProvider {

    protected Rectangle recordArea = new Rectangle(40,217,740,720);

	public abstract DimInt2D getSize();

	public abstract void open();

	public abstract void close();
		
	public abstract BufferedImage captureImage();

	
	public abstract void parseRecordParamsText(String recordParamsText);
	
	public Rectangle getRecordArea() {
		return this.recordArea;
	}

	public void setRecordArea(Rectangle r) {
		this.recordArea = r;
	}
	
}