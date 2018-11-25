package fr.an.qrcode.channel.impl.decode.input;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.an.qrcode.channel.impl.util.DimInt2D;

/**
 * an ImageProvider that delegate to underlying, to do the Average between current image and previous
 *
 */
public class AvgFilterImageProvider extends ImageProvider {
	
	private static final Logger log = LoggerFactory.getLogger(AvgFilterImageProvider.class);

	private ImageProvider delegate;

	private BufferedImage prevImg; 
	private BufferedImage imgAvg; 

	// --------------------------------------------------------------------------------------------
	
	public AvgFilterImageProvider(ImageProvider delegate) {
		this.delegate = delegate;
	}

	// --------------------------------------------------------------------------------------------

	public DimInt2D getSize() {
		return delegate.getSize();
	}

	public void open() {
		delegate.open();
		DimInt2D size = getSize();
		log.info("allocating prev,avg img size:" + size);
//		CvSize size = new CvSize(getSize().w, getSize().h);
//		prevImg = cvCreateImage(size, IPL_DEPTH_8U, 3);
//		imgAvg = cvCreateImage(size, IPL_DEPTH_8U, 3);
	}

	public void close() {
		delegate.close();
	}
		
	public BufferedImage captureImage() {
		BufferedImage currImg = delegate.captureImage();
//		if (prevImg == null) {
//			opencv_core.cvCopy(currImg, prevImg);
//			currImg = delegate.captureImage();
//		}
//		opencv_core.cvAddWeighted(prevImg, 1.0, currImg, 1.0, 0.0, imgAvg);
//		opencv_core.cvCopy(currImg, prevImg);

		// Imgproc.erode(src, dst, kernel);
		
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