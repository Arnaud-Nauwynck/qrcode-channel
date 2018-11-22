package fr.an.qrcode.channel.impl.decode.filter;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.Binarizer;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.DecodeHintType;
import com.google.zxing.FormatException;
import com.google.zxing.LuminanceSource;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.ResultPoint;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.DecoderResult;
import com.google.zxing.common.DetectorResult;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.decoder.Decoder;
import com.google.zxing.qrcode.decoder.QRCodeDecoderMetaData;
import com.google.zxing.qrcode.detector.Detector;

import fr.an.qrcode.channel.impl.decode.filter.QRDecodeRollingStats.Bucket;
import fr.an.qrcode.channel.impl.decode.input.ImageStreamCallback;
import fr.an.qrcode.channel.impl.util.DimInt2D;

/**
 * <PRE>          -------                     -------                   -------    
 *               |       |                   |       |                 |       |   
 *  --RGBImage-->|       |-->Binary Bitmap-->|       |-->QR Detected-->|       |-->QR Decoded
 *               |       |                   |       |                 |       |   
 *                -------                     -------                   -------    
 * </PRE>
 *
 */
public class ZXingQRStreamFromImageStreamCallback extends ImageStreamCallback {

	private static final Logger log = LoggerFactory.getLogger(ZXingQRStreamFromImageStreamCallback.ImageSampling.class);
	
	private QRStreamCallback delegateQRStream;
	
	private int samplingLen;
	
	private double maxPixelImageVarianceForFlush = 0.1;

	
	Mat grayImg, grayImgThreshold, imgErode, imgMorpho, imgBinarize;
	Mat kernel33 = Mat.ones(3, 3, CvType.CV_32F);
	
	private Map<DecodeHintType, Object> qrHints;
	private Decoder decoder;
	
	

	private static class ImageSampling {
		BufferedImage image;
		long nanosSnapshotTime;
		long nanosSnapshot;
		
		BinaryBitmap bitmap; // computed from image + binarizer..
		
		public ImageSampling(BufferedImage image) {
			this.image = image;
		}
		
	}
	
	private ImageSampling[] prevSamplings;
	private int firstIndexModulo = 0;
	private int lastIndexModulo = 0;
	
	private BufferedImage currentResultImage;
	private BitMatrix previousBitMatrix;
	

    private Result currQrResult; 
    private long currNanosDecode;
    private DetectorResult currDetectorResult;
    private long currNanosDetect;
    private BinaryBitmap currBitmap;
    private long currNanosBinarize;
    private BufferedImage currImage;
    private long currNanosSnapshotTime;
    private long currNanosSnapshot;
	
    private QRDecodeRollingStats rollingStats = new QRDecodeRollingStats();
    
    private Executor executor1 = new ThreadPoolExecutor(1, 1, // 1 thread
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>(10)); // max 10 waiting work items

    private Executor executor2 = new ThreadPoolExecutor(1, 1, // 1 thread
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>(10)); // max 10 waiting work items

    private Executor executor3 = new ThreadPoolExecutor(1, 1, // 1 thread
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>(10)); // max 10 waiting work items

    
    
	
	// --------------------------------------------------------------------------------------------
	
	public ZXingQRStreamFromImageStreamCallback(
			QRStreamCallback delegate, 
			int samplingLen,
			Map<DecodeHintType, Object> qrHints) {
		this.delegateQRStream = delegate;
		this.samplingLen = samplingLen;
		this.prevSamplings = new ImageSampling[samplingLen];
		this.qrHints = qrHints;
		this.decoder = new Decoder();
	}

	// --------------------------------------------------------------------------------------------

	public QRDecodeRollingStats getRollingStats() {
		return rollingStats;
	}

	@Override
	public void onStart(DimInt2D dim) {
		Size s = new Size(dim.w, dim.h);
		// cvCreateImage(size, opencv_core.IPL_DEPTH_8U, 1);
		grayImg = new Mat(s, CvType.CV_8UC1);  // CV_32SC1
		grayImgThreshold = new Mat(s, CvType.CV_8UC1);
		imgErode = new Mat(s, CvType.CV_8UC1);
		imgMorpho = new Mat(s, CvType.CV_8UC1);
		imgBinarize = new Mat(s, CvType.CV_8UC1);
		
		for(int i = 0; i < samplingLen; i++) {
//			CvSive cvSive = new CvSive(w, h);
//			IplImage img = cvCreateImage(cvSize, CvType.IPL_DEPTH_8U, 3);
			prevSamplings[i] = new ImageSampling(null);
		}
	
		rollingStats.clearStats();
		rollingStats.startBucket();
		
		delegateQRStream.onStart(dim);
	}

	@Override
	public void onEnd() {
		// may flush last buffered image(s)
		delegateQRStream.onEnd();
	}

	@Override
	public void onImage(BufferedImage img, long nanosSnapshotTime, long nanosSnapshot) {
	    Bucket bucketStats = rollingStats.checkRoll();
		ImageSampling sampling = prevSamplings[lastIndexModulo];
		sampling.nanosSnapshot = nanosSnapshot;
		sampling.nanosSnapshotTime = nanosSnapshotTime;
		sampling.image = img;
		
		lastIndexModulo = (lastIndexModulo+1) % samplingLen;
		
		// TODO sub-sampling average consecutive images.. if time diff
		// currentResultImage;
		
		long nanosBeforeBinarize = System.nanoTime();
		
//		cvtColor(opencv_core.cvarrToMat(img), grayImg, COLOR_BGR2GRAY);
//		// opencv_imgproc.threshold(grayImg, grayImgThreshold, 16, 240, type);
//		opencv_imgproc.erode(grayImg, imgErode, kernel33);
//		opencv_imgproc.dilate(imgErode, imgMorpho, kernel33);
//		
//		opencv_imgproc.threshold(imgMorpho, imgBinarize, 128, 256, THRESH_BINARY);
//		// opencv_imgproc.pyrDown(src, dst);

		
		
	    BitMatrix binaryMatrix;
	    try {
	    	LuminanceSource source = new BufferedImageLuminanceSource(img);
	    	Binarizer binarizer = new HybridBinarizer(source);
	    	binaryMatrix = binarizer.getBlackMatrix();
	    	
//	    	int w = imgBinarize.arrayWidth(), h = imgBinarize.arrayHeight();
//	    	binaryMatrix = new BitMatrix(w, h);
//	    	ByteBuffer asByteBuffer = imgBinarize.asByteBuffer();
//	    	int cap = asByteBuffer.capacity();
//	    	UByteIndexer sI = imgBinarize.createIndexer();
//	    	log.info(w + "x" + h + "=" + (w*h) + " capacity:" + cap + "limit:" + asByteBuffer.limit());
//	    	for(int y = 0, i=0; y < h; y++) {
//	    		for(int x = 0; x < w; i++,x++) {
//	    			int v2 = sI.get(x, y);
//	    			int v = asByteBuffer.get();
//	    			if (v != v2) 
//	    				throw new IllegalStateException();
//	    			if (v != 0) {
//	    				binaryMatrix.set(x, y);	    				
//	    			}
//	    		}
//	    	}
	    	
	    	// TODO .. rewrite..
//	        int[][] blackPoints = calculateBlackPoints(luminances, subWidth, subHeight, width, height);
//	        BitMatrix newMatrix = new BitMatrix(width, height);
//	        calculateThresholdForBlock(luminances, subWidth, subHeight, width, height, blackPoints, newMatrix);
	    } catch(Exception ex) {
	    	log.warn("failed?", ex);
	    	bucketStats.incrCountQRPacketNotFound(); // should not occur?
	    	return;
	    }
	    
	    // sampling.binaryMatrix = binaryMatrix;
	    
	    boolean dropImage = false;
    	if (previousBitMatrix != null && previousBitMatrix.equals(binaryMatrix)) {
    		dropImage = true;
    		log.info("drop same binarized image");
    	}

    	previousBitMatrix = binaryMatrix;
    	long nanosBinarize = System.nanoTime() - nanosBeforeBinarize;
		
	    
	    if (dropImage) {
			bucketStats.incrCountImageDropped();
			
	    	//delegateQRStream.onQRCaptured(new QRCapturedEvent(null, 0, img, nanosSnapshot, nanosSnapshotTime));
	    } else {
	    	// delegateQRStream.onImage(image, sampling.bitmap, nanosTime, nanos);
//        	qrResult = qrCodeReader.decode(bitmap, qrHints);

	    	Result qrResult;

	    	DetectorResult detectorResult;
	    	BitMatrix qrbits;
	    	long nanosDetect;
	    	long nanosBeforeDetect = System.nanoTime();
	        try {
	        	Detector detector = new Detector(binaryMatrix);
				detectorResult = detector.detect(qrHints);
				qrbits = detectorResult.getBits();
				
//	            FinderPatternFinder finder = new FinderPatternFinder(binaryMatrix, null);
//	            FinderPatternInfo info = finder.find(qrHints);
//	            detectorResult = processFinderPatternInfo(info);
//				qrbits = detectorResult.getBits();
	            
				
	        	nanosDetect = System.nanoTime() - nanosBeforeDetect;
	        	
	            this.currDetectorResult = detectorResult;
	        } catch(FormatException | NotFoundException ex) {
	        	nanosDetect = System.nanoTime() - nanosBeforeDetect;
	        	this.currDetectorResult = null;
	        	if (ex instanceof FormatException) {
	        		bucketStats.incrCountQRPacketFormatException();
	        	} else {
	        		bucketStats.incrCountQRPacketNotFound();
	        	}
		    	return;
	        }
	        
	        
	        long nanosBeforeDecode = System.nanoTime();
	        long nanosDecode;
	        DecoderResult decoderResult;
	        try {
				decoderResult = decoder.decode(qrbits, qrHints);
	            // If the code was mirrored: swap the bottom-left and the top-right points.
	            ResultPoint[] points = detectorResult.getPoints(); // bottomLeft, topLeft, topRight, alignmentPattern?
	            if (decoderResult.getOther() instanceof QRCodeDecoderMetaData) {
	              ((QRCodeDecoderMetaData) decoderResult.getOther()).applyMirroredCorrection(detectorResult.getPoints());
	            }
	        } catch(ChecksumException | FormatException ex) {
	            nanosDecode = System.nanoTime() - nanosBeforeDecode;
	            this.currQrResult = null;
	        	if (ex instanceof ChecksumException) {
	        		// currDecodeMsg = "\n<<<<<<<< FAILED to decode QRCode: ChecksumException >>>>>>>>>>>>\n";
	        		bucketStats.incrCountQRPacketChecksumException();
	        	} else {
	        		// currDecodeMsg = "\n<<<<<<<< FAILED to decode QRCode: FormatException >>>>>>>>>>>>\n";
	        		bucketStats.incrCountQRPacketChecksumException();
	        	}
		    	return;
	        }

            nanosDecode = System.nanoTime() - nanosBeforeDecode;
            bucketStats.incrCountQRPacketRecognized();

            ResultPoint[] pts = detectorResult.getPoints();
            
            qrResult = new Result(decoderResult.getText(), decoderResult.getRawBytes(), detectorResult.getPoints(), BarcodeFormat.QR_CODE);
            this.currQrResult = qrResult;
            
            // TODO
//            delegateQRStream.onQRCaptured(new QRCapturedEvent(
//            		qrResult, nanosDecode,
//            		detectorResult, nanosDetect,
//	    			binaryMatrix, nanosBinarize, 
//	    			img, nanosSnapshotTime, nanosSnapshot));
	    }
	}

	public Result getCurrQrResult() {
		return currQrResult;
	}

	public long getCurrNanosDecode() {
		return currNanosDecode;
	}

	public DetectorResult getCurrDetectorResult() {
		return currDetectorResult;
	}

	public long getCurrNanosDetect() {
		return currNanosDetect;
	}

	public long getCurrNanosBinarize() {
		return currNanosBinarize;
	}

	public long getCurrNanosSnapshot() {
		return currNanosSnapshot;
	}

	
}
