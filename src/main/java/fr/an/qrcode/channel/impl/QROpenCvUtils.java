package fr.an.qrcode.channel.impl;

import java.awt.image.BufferedImage;

import org.bytedeco.javacpp.opencv_core.IplImage;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameConverter;

public class QROpenCvUtils {
	
	private static final OpenCVFrameConverter<IplImage> frameToIplImageConverter = new OpenCVFrameConverter.ToIplImage();
	private static final Java2DFrameConverter java2dFrameConverter = new Java2DFrameConverter();

	public static BufferedImage toBufferedImage(IplImage src) {
		Frame frame = frameToIplImageConverter.convert(src);
		return java2dFrameConverter.convert(frame);
	}

	public static BufferedImage toBufferedImage(Frame src) {
		return java2dFrameConverter.convert(src);
	}

	public static IplImage toIplImage(BufferedImage src) {
		Frame frame = java2dFrameConverter.convert(src);
		return frameToIplImageConverter.convert(frame);
	}
	
	public static IplImage toIplImage(Frame src) {
		return frameToIplImageConverter.convert(src);
	}
	
	public static Frame toFrame(IplImage src) {
		return frameToIplImageConverter.convert(src);
	}
}
