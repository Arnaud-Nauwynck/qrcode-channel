package fr.an.qrcode.channel.impl;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

import org.opencv.core.CvType;
import org.opencv.core.Mat;


public class QROpenCvUtils {

	public static Mat img3BYTE_BGR_toMat8UC3(BufferedImage image) {
		Mat mat = new Mat(image.getHeight(), image.getWidth(), CvType.CV_8UC3);
		img3BYTE_BGR_toMat8UC3(image, mat);
	    return mat;
	}

	public static void img3BYTE_BGR_toMat8UC3(BufferedImage image, Mat dest) {
		byte[] data = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
	    dest.put(0, 0, data);
	}

	public static void mat8UC3_to_img3BYTE_BGR(Mat src, BufferedImage dest) {
		byte[] data = ((DataBufferByte) dest.getRaster().getDataBuffer()).getData();
	    src.get(0, 0, data);
	}

	
	public static BufferedImage toBufferedImage(Mat src, int imageType) {
		final int w = src.cols(), h = src.rows();
		BufferedImage dest = new BufferedImage(w, h, imageType);
		toBufferedImage(src, dest);
	    return dest;
	}

	public static void toBufferedImage(Mat src, BufferedImage dest) {
		byte[] data = ((DataBufferByte) dest.getRaster().getDataBuffer()).getData();
	    src.get(0, 0, data);
	}
    
	
	public static Mat imgINT_RGB_toMat(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();  
		Mat out = new Mat(h, w, CvType.CV_8UC3);
		imgINT_RGB_toMat(img, out);
		return out;
     }

	public static void imgINT_RGB_toMat(BufferedImage img, Mat out) {
        int w = img.getWidth(), h = img.getHeight();  
		byte[] data = new byte[w * h * (int)out.elemSize()];
		int[] dataBuff = img.getRGB(0, 0, w, h, null, 0, w);
		final int dataLen = dataBuff.length;
		for(int i = 0; i < dataLen; i++) {
			int rgb = dataBuff[i];
			data[i*3 + 0] = (byte) ((rgb >> 16) & 0xFF);
			data[i*3 + 1] = (byte) ((rgb >> 8) & 0xFF);
			data[i*3 + 2] = (byte) ((rgb >> 0) & 0xFF);
		}
		out.put(0, 0, data);
	}
	
	
}
