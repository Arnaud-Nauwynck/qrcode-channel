package fr.an.qrcode.channel.impl.decode.input;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import org.opencv.core.Core;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.an.qrcode.channel.impl.util.DimInt2D;

/**
 * 
 */
public class OpenCvCalib3dImageProvider extends ImageProvider {
	
	private static final Logger log = LoggerFactory.getLogger(OpenCvCalib3dImageProvider.class);

	private ImageProvider delegate;
	private OpenCvCalib3d calib;
	
	// --------------------------------------------------------------------------------------------
	
	public OpenCvCalib3dImageProvider(ImageProvider delegate, OpenCvCalib3d calib) {
		this.delegate = delegate;
		this.calib = calib;
	}

	// --------------------------------------------------------------------------------------------

	public DimInt2D getSize() {
		return delegate.getSize();
	}

	public void open() {
		delegate.open();
	}

	public void close() {
		delegate.close();
	}
		
	public BufferedImage captureImage() {
		BufferedImage currImg = delegate.captureImage();
		
		// TODO camera distortion correction 
		// calib
		
		// TODO
		return currImg;		
	}


	public void parseRecordParamsText(String recordParamsText) {
		delegate.parseRecordParamsText(recordParamsText);
	}
	
	public Rectangle getRecordArea() {
		return delegate.getRecordArea();
	}

	public void setRecordArea(Rectangle r) {
		delegate.setRecordArea(r);
	}
	
}
