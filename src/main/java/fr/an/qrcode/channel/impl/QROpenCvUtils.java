package fr.an.qrcode.channel.impl;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

import org.opencv.core.CvType;
import org.opencv.core.Mat;


public class QROpenCvUtils {
	
	public static Mat toMat(BufferedImage image) {
		Mat mat = new Mat(image.getHeight(), image.getWidth(), CvType.CV_8UC3);
		toMat(image, mat);
	    return mat;
	}

	public static void toMat(BufferedImage image, Mat dest) {
		byte[] data = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
	    dest.put(0, 0, data);
	}
	
	public static BufferedImage toBufferedImage(Mat src, int imageType) {
		final int w = src.cols(), h = src.rows();
		BufferedImage dest = new BufferedImage(w, h, imageType);
		toBufferedImage(src, dest);
	    return dest;
	}

	public static void toBufferedImage(Mat src, BufferedImage dest) {
		final int w = src.cols(), h = src.rows();
		byte[] data = new byte[h * w * (int)src.elemSize()];
	    src.get(0, 0, data);
	    dest.getRaster().setDataElements(0, 0, w, h, data);
	}
    
}
