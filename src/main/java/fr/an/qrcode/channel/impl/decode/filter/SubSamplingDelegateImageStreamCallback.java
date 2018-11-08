package fr.an.qrcode.channel.impl.decode.filter;

import java.awt.image.BufferedImage;

public class SubSamplingDelegateImageStreamCallback extends ImageStreamCallback {

	private ImageStreamCallback delegate;
	
	private int samplingLen;
	
	private double maxAvgPixelImageDistanceForFlush = 0.1;
	private int maxImageDistanceForFlush;
	
	private ImageSampling[] prevSamplings;
	private int firstIndexModulo = 0;
	private int lastIndexModulo = 0;
	
	private BufferedImage currentResultImage;
	
	private static class ImageSampling {
		BufferedImage image;
		long nanosTime;
		long nanos;
		
		public ImageSampling(BufferedImage image, long nanosTime, long nanos) {
			this.image = image;
			this.nanosTime = nanosTime;
			this.nanos = nanos;
		}
		
	}
	
	// --------------------------------------------------------------------------------------------
	
	public SubSamplingDelegateImageStreamCallback(ImageStreamCallback delegate, int samplingLen) {
		this.delegate = delegate;
		this.samplingLen = samplingLen;
		this.prevSamplings = new ImageSampling[samplingLen];
	}

	// --------------------------------------------------------------------------------------------

	@Override
	public void onStart() {
		delegate.onStart();
	}

	@Override
	public void onEnd() {
		// may flush last buffered image(s)
		delegate.onEnd();
	}


	@Override
	public void onImage(BufferedImage image, long nanosTime, long nanos) {
		prevSamplings[lastIndexModulo] = new ImageSampling(image, nanosTime, nanos);  
		lastIndexModulo = (lastIndexModulo+1) % samplingLen;
		
		// TODO sub-sampling average consecutive images.. if time diff
		currentResultImage = new BufferedImage(image.getWidth(), image.getHeight(), image.getType());
		// TODO
		
		
		delegate.onImage(image, nanosTime, nanos);
		
	}

	
}
