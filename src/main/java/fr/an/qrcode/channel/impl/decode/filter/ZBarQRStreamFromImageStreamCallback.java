package fr.an.qrcode.channel.impl.decode.filter;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.Result;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.DetectorResult;

import fr.an.qrcode.channel.impl.decode.QRResult;
import fr.an.qrcode.channel.impl.decode.filter.QRDecodeRollingStats.Bucket;
import fr.an.qrcode.channel.impl.decode.input.ImageStreamCallback;
import fr.an.qrcode.channel.impl.util.DimInt2D;
import fr.an.qrcode.channel.impl.util.PtInt2D;
import net.sourceforge.zbar.Image;
import net.sourceforge.zbar.ImageScanner;
import net.sourceforge.zbar.Symbol;
import net.sourceforge.zbar.SymbolSet;

/**
 * <PRE>          -------                     -------                   -------    
 *               |       |                   |       |                 |       |   
 *  --RGBImage-->|       |-->Binary Bitmap-->|       |-->QR Detected-->|       |-->QR Decoded
 *               |       |                   |       |                 |       |   
 *                -------                     -------                   -------    
 * </PRE>
 *
 */
public class ZBarQRStreamFromImageStreamCallback extends ImageStreamCallback {

	private static final Logger log = LoggerFactory.getLogger(ZBarQRStreamFromImageStreamCallback.ImageSampling.class);
	
	private QRStreamCallback delegateQRStream;
	
	private int samplingLen;

	byte[] imgGrayData;
	private Image zbarImg;
	
	int freqPrintln = 100;
	int moduloPrintln = 0;
	int prevQRResultCount;
	int coutPrevRepeat = 0;
	
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

    
    
	
	// --------------------------------------------------------------------------------------------
	
	public ZBarQRStreamFromImageStreamCallback(
			QRStreamCallback delegate, 
			int samplingLen
			) {
		this.delegateQRStream = delegate;
		this.samplingLen = samplingLen;
		this.prevSamplings = new ImageSampling[samplingLen];
	}

	// --------------------------------------------------------------------------------------------

	public QRDecodeRollingStats getRollingStats() {
		return rollingStats;
	}

	@Override
	public void onStart(DimInt2D dim) {
		try {
			zbarImg = new Image(dim.w, dim.h, "Y800");
		} catch(RuntimeException ex) {
			log.error("Failed to create native zbar Image .. check LD_LIBRARY_PATH..", ex);
			throw ex;
		}
		this.imgGrayData = new byte[dim.w * dim.h];
		
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
//		ImageSampling sampling = prevSamplings[lastIndexModulo];
//		sampling.nanosSnapshot = nanosSnapshot;
//		sampling.nanosSnapshotTime = nanosSnapshotTime;
//		sampling.image = img;
		
		lastIndexModulo = (lastIndexModulo+1) % samplingLen;
		
		// TODO sub-sampling average consecutive images.. if time diff
		// currentResultImage;
		
		long nanosBefore = System.nanoTime();
		
		ImageScanner imageScanner = new ImageScanner();
		imageScanner.enableCache(false);
		// imageScanner.setConfig(arg0, arg1, arg2);

		Raster imgRaster = img.getData();
		DataBufferByte imgBuffer = (DataBufferByte) imgRaster.getDataBuffer();
		byte[] imgData = imgBuffer.getData();
		int imgDataLen = imgData.length;
		// convert RGB to Gray..
		final int w = imgRaster.getWidth(), h = imgRaster.getHeight();  
		for(int y = 0, i=0, gi=0; y < h; y++) {
			for(int x = 0; x < w; x++,i+=3,gi++) {
//				byte r = imgData[i], g = imgData[i+1], b = imgData[i+2];  
				int rgb = img.getRGB(x, y);
				int checkr = ((rgb >> 16) & 0xFF), checkg = ((rgb >> 8) & 0xFF), checkb = (rgb & 0xFF);
//				if (checkr != r || checkg != g || checkb != b) {
//					rgb = img.getRGB(x, y);
//					log.info("? rgb..");
//				}
				// TODO -128 instead of 128...
				int r=checkr, g = checkg, b = checkb;
				
				// cf ZXing BufferedImageLuminance
				// .299R + 0.587G + 0.114B (YUV/YIQ for PAL and NTSC), 
				// (306*R) >> 10 is approximately equal to R*0.299, and so on.
				// 0x200 >> 10 is 0.5, it implements rounding.
				int gray = (306 * r + 601 * g + 117 * b + 0x200) >> 10;
				
				// TODO morphologic erode+dilate+downsize
				imgGrayData[gi] = (byte) gray;
			}
		}
		
		zbarImg.setData(imgGrayData);
		
		imageScanner.scanImage(zbarImg);
		
		SymbolSet results = imageScanner.getResults();
		List<QRResult> qrResults = new ArrayList<>();
		for (Iterator<Symbol> iter = results.iterator(); iter.hasNext(); ) {
			Symbol qrSymbol = iter.next();
			int[] bounds = qrSymbol.getBounds();
			List<PtInt2D> pts = new ArrayList<>();
			int ptsLen = bounds.length / 2;
			for(int pti = 0, i = 0; pti < ptsLen; pti++,i+=2) {
				pts.add(new PtInt2D(bounds[i], bounds[i+1]));
			}
			qrResults.add(new QRResult(qrSymbol.getData(), qrSymbol.getDataBytes(), pts));
		}
		int qrResultCount = qrResults.size(); 
		if (qrResultCount != prevQRResultCount) {
			moduloPrintln--;
			if (moduloPrintln <= 0) {
				moduloPrintln = freqPrintln;
				System.out.println();
			}
			if (coutPrevRepeat > 1) {
				System.out.print("(" + coutPrevRepeat + ") ");
			} else {
				System.out.print(" ");
			}
			if (qrResultCount == 1) {
				System.out.print('1');
			} else if (qrResultCount == 0) {
				System.out.print('0');
			} else {
				System.out.print(Integer.toString(qrResultCount));
			}

			coutPrevRepeat = 0;
		}
		prevQRResultCount = qrResultCount;
		coutPrevRepeat++;
		
        long nanosDecode = System.nanoTime() - nanosBefore;
        bucketStats.incrCountQRPacketRecognized();

    	delegateQRStream.onQRCaptured(new QRCapturedEvent(
    			qrResults, nanosDecode,
    			img, nanosSnapshotTime, nanosSnapshot));

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
