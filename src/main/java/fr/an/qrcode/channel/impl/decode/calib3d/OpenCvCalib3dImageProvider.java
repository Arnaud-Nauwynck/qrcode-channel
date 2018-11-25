package fr.an.qrcode.channel.impl.decode.calib3d;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.an.qrcode.channel.impl.QROpenCvUtils;
import fr.an.qrcode.channel.impl.decode.input.ImageProvider;
import fr.an.qrcode.channel.impl.util.DimInt2D;
import fr.an.qrcode.channel.impl.util.PtInt2D;

/**
 * 
 */
public class OpenCvCalib3dImageProvider extends ImageProvider {
	
	private static final Logger log = LoggerFactory.getLogger(OpenCvCalib3dImageProvider.class);

	private ImageProvider delegate;
	private OpenCvCalib3d calib3d;
	
	private boolean freezeCalibImage = false;
	
	BufferedImage currImg;
	BufferedImage currDisplayImg;
	BufferedImage currUndistortImg;

	Mat currImgMat;
	Mat currImgGrayMat;
	Mat currDisplayImgMat;
	Mat currUndistortImgMat;
	PtInt2D[] currCorners = null;
	
	private Calib3dListener listener;
	
	private String calib3dfileName = "calib3d-default.txt";
	
	// --------------------------------------------------------------------------------------------
	
	public OpenCvCalib3dImageProvider(ImageProvider delegate, OpenCvCalib3d calib) {
		this.delegate = delegate;
		this.calib3d = calib;	}

	// --------------------------------------------------------------------------------------------
	
	public void setListener(Calib3dListener listener) {
		this.listener = listener;
	}

	protected void fireOnImage() {
		if (listener != null) {
			listener.onImage(currUndistortImg, currImg, currCorners);
		}
	}
	
	public void setFreeCalib3dImage(boolean p) {
		this.freezeCalibImage = p;
	}
	
	public boolean isFreeCalib3dImage() {
		return this.freezeCalibImage;
	}
	
	public boolean toogleFreezeCalib3dImage() {
		this.freezeCalibImage = !this.freezeCalibImage;
		return this.freezeCalibImage;
	}
	
	public OpenCvCalib3d getCalib3d() {
		return calib3d;
	}

	public void reset() {
		calib3d.clearCorners();
		fireOnImage();
	}

	public void calibrate() {
		calib3d.calibrate();
		processImage();
	}

	public void processImage() {
		if (currImg == null) {
			return;
		}
		QROpenCvUtils.imgINT_RGB_toMat(currImg, currImgMat);
		Imgproc.cvtColor(currImgMat, currImgGrayMat, Imgproc.COLOR_RGB2GRAY);
		
		calib3d.processFrame(currImgGrayMat, currDisplayImgMat);
		
		QROpenCvUtils.mat8UC3_to_img3BYTE_BGR(currDisplayImgMat, currDisplayImg);
		
		fireOnImage();
	}
	
	public void load() {
		calib3d.loadConfParams(calib3dfileName);
		fireOnImage();
	}

	public void save() {
		calib3d.saveConfParams(calib3dfileName);
	}
	
	// implements ImageStreamProvider
	// --------------------------------------------------------------------------------------------
	
	@Override
	public DimInt2D getSize() {
		return delegate.getSize();
	}

	@Override
	public void open() {
		calib3d.loadConfParams(calib3dfileName);

		DimInt2D size = delegate.getSize();
		int rows = size.h, cols = size.w;
		currImgMat = new Mat(rows, cols, CvType.CV_8UC3);
		currImgGrayMat = new Mat(rows, cols, CvType.CV_8UC1);
		currUndistortImgMat = new Mat(rows, cols, CvType.CV_8UC3);
		currDisplayImgMat  = new Mat(rows, cols, CvType.CV_8UC3);
		
		currDisplayImg = new BufferedImage(size.w, size.h, BufferedImage.TYPE_3BYTE_BGR);
		currUndistortImg = new BufferedImage(size.w, size.h, BufferedImage.TYPE_3BYTE_BGR);
		
		delegate.open();
		if (listener != null) {
			listener.onStart(size);
		}
	}

	@Override
	public void close() {
		if (listener != null) {
			listener.onEnd();
		}
		delegate.close();
	}
		
	@Override
	public BufferedImage captureImage() {
		BufferedImage img = delegate.captureImage();
		
//		Calib3d.findCheckerboardCorners();
		
		if (!freezeCalibImage) {
			currImg = img;
			if (calib3d.isCalibrated()) {
				// perform camera distortion correction on images stream capture
				QROpenCvUtils.img3BYTE_BGR_toMat8UC3(currImg, currImgMat);

				calib3d.undistort(currImgMat, currUndistortImgMat);
				
				QROpenCvUtils.mat8UC3_to_img3BYTE_BGR(currUndistortImgMat, currUndistortImg);
			} else {
				// simple copy
//				QROpenCvUtils.img3BYTE_BGR_toMat8UC3(currImg, currImgMat);
//				QROpenCvUtils.mat8UC3_to_img3BYTE_BGR(currImgMat, currUndistortImg);
				fireOnImage();
				return img;
			}
			
			fireOnImage();
		} else {
			// frozen image
		}
		
		return currUndistortImg;		
	}


	@Override
	public void parseRecordParamsText(String recordParamsText) {
		delegate.parseRecordParamsText(recordParamsText);
	}
	
	@Override
	public Rectangle getRecordArea() {
		return delegate.getRecordArea();
	}

	@Override
	public void setRecordArea(Rectangle r) {
		delegate.setRecordArea(r);
	}


}
