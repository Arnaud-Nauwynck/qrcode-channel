package fr.an.qrcode.channel.impl.decode.filter;

import java.awt.Dimension;
import java.awt.image.BufferedImage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.NotFoundException;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;

/**
 * <PRE>          -------
 *               |       |
 *  --RGBImage-->|       |-->Binary Bitmap-->
 *               |       |
 *                -------
 * </PRE>
 *
 */
public class SubSamplingDelegateImageStreamCallback extends ImageStreamCallback {

	private static final Logger log = LoggerFactory.getLogger(SubSamplingDelegateImageStreamCallback.ImageSampling.class);
	
	private BinaryImageStreamCallback delegate;
	
	private int samplingLen;
	
	private double maxPixelImageVarianceForFlush = 0.1;
	
	private ImageSampling[] prevSamplings;
	private int firstIndexModulo = 0;
	private int lastIndexModulo = 0;
	
	private BufferedImage currentResultImage;
	private BitMatrix previousBitMatrix;
	
	private static class ImageSampling {
		BufferedImage image;
		long nanosTime;
		long nanos;
		
		BinaryBitmap bitmap; // computed from image + binarizer..
		
		public ImageSampling(BufferedImage image) {
			this.image = image;
		}
		
	}
	
	// --------------------------------------------------------------------------------------------
	
	public SubSamplingDelegateImageStreamCallback(BinaryImageStreamCallback delegate, int samplingLen) {
		this.delegate = delegate;
		this.samplingLen = samplingLen;
		this.prevSamplings = new ImageSampling[samplingLen];
	}

	// --------------------------------------------------------------------------------------------

	@Override
	public void onStart(Dimension dim) {
		for(int i = 0; i < samplingLen; i++) {
			BufferedImage img = new BufferedImage((int)dim.getWidth(), (int)dim.getHeight(), BufferedImage.TYPE_INT_RGB);
			prevSamplings[i] = new ImageSampling(img);
		}
				
		delegate.onStart(dim);
	}

	@Override
	public void onEnd() {
		// may flush last buffered image(s)
		delegate.onEnd();
	}


	@Override
	public void onImage(BufferedImage image, long nanosTime, long nanos) {
		ImageSampling sampling = prevSamplings[lastIndexModulo];
		sampling.nanos = nanos;
		sampling.nanosTime = nanosTime;
		sampling.image.setData(image.getData());
		
		lastIndexModulo = (lastIndexModulo+1) % samplingLen;
		
		// TODO sub-sampling average consecutive images.. if time diff
		// currentResultImage;
		
	    LuminanceSource source = new BufferedImageLuminanceSource(image);
	    sampling.bitmap = new BinaryBitmap(new HybridBinarizer(source));

	    boolean dropImage = false;
//	    try {
//	    	BitMatrix m = sampling.bitmap.getBlackMatrix();
//	    	if (previousBitMatrix.equals(m)) {
//	    		dropImage = true;
//	    		log.info("drop same binarized image");
//	    	}
//	    	previousBitMatrix = m;
//	    } catch(NotFoundException ex) {
//	    	// ignore ex?
//	    	dropImage = true; //?? 
//	    }
	    
	    if (dropImage) {
	    	delegate.onDropImage(image, nanosTime, nanos);
	    } else {
	    	delegate.onImage(image, sampling.bitmap, nanosTime, nanos);
	    }
	}

	
}
