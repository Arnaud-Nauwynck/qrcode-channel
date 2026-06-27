package fr.an.qrcode.channel.impl.decode.filter;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.zxing.Binarizer;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.DecodeHintType;
import com.google.zxing.FormatException;
import com.google.zxing.LuminanceSource;
import com.google.zxing.NotFoundException;
import com.google.zxing.ResultPoint;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.DecoderResult;
import com.google.zxing.common.DetectorResult;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.decoder.Decoder;
import com.google.zxing.qrcode.decoder.QRCodeDecoderMetaData;
import com.google.zxing.qrcode.detector.Detector;

import fr.an.qrcode.channel.impl.QROpenCvNativeLoader;
import fr.an.qrcode.channel.impl.QROpenCvUtils;
import fr.an.qrcode.channel.impl.decode.QRResult;
import fr.an.qrcode.channel.impl.decode.filter.QRDecodeRollingStats.Bucket;
import fr.an.qrcode.channel.impl.decode.input.ImageStreamCallback;
import fr.an.qrcode.channel.impl.util.DimInt2D;
import fr.an.qrcode.channel.impl.util.PtInt2D;

/**
 * splits each captured frame into its R/G/B color planes (via OpenCV) and independently
 * binarizes + detects + decodes a QR code on each plane, so up to 3 QR codes displayed
 * simultaneously in distinct color channels can be captured from a single frame.
 *
 * NOTE: this depends on faithful, low-crosstalk color reproduction between screen and camera
 * (no aggressive auto white balance / chroma subsampling) -- see project notes on the
 * robustness tradeoffs of this approach compared to plain sequential (grayscale) QR display.
 */
public class RgbSplitQRStreamFromImageStreamCallback extends ImageStreamCallback {

	static {
		QROpenCvNativeLoader.ensureLoaded();
	}

	private static final Logger log = LoggerFactory.getLogger(RgbSplitQRStreamFromImageStreamCallback.class);

	private static final String[] CHANNEL_NAMES = { "R", "G", "B" };

	private QRStreamCallback delegateQRStream;

	private Map<DecodeHintType, Object> qrHints;
	private Decoder decoder = new Decoder();

	private QRDecodeRollingStats rollingStats = new QRDecodeRollingStats();

	private Mat rgbMat;
	private List<Mat> channelMats;

	// --------------------------------------------------------------------------------------------

	public RgbSplitQRStreamFromImageStreamCallback(QRStreamCallback delegate, Map<DecodeHintType, Object> qrHints) {
		this.delegateQRStream = delegate;
		this.qrHints = qrHints;
	}

	// --------------------------------------------------------------------------------------------

	@Override
	public QRDecodeRollingStats getRollingStats() {
		return rollingStats;
	}

	@Override
	public void onStart(DimInt2D dim) {
		rgbMat = new Mat(dim.h, dim.w, CvType.CV_8UC3);
		channelMats = new ArrayList<>();
		rollingStats.clearStats();
		rollingStats.startBucket();
		delegateQRStream.onStart(dim);
	}

	@Override
	public void onEnd() {
		delegateQRStream.onEnd();
	}

	@Override
	public void onImage(BufferedImage img, long nanosSnapshotTime, long nanosSnapshot) {
		Bucket bucketStats = rollingStats.checkRoll();

		QROpenCvUtils.imgINT_RGB_toMat(img, rgbMat);
		channelMats.clear();
		Core.split(rgbMat, channelMats);

		List<QRResult> qrResults = new ArrayList<>();
		long nanosDecodeTotal = 0;

		for (int c = 0; c < channelMats.size(); c++) {
			Mat channelMat = channelMats.get(c);
			String channelName = c < CHANNEL_NAMES.length ? CHANNEL_NAMES[c] : ("ch" + c);

			long nanosBeforeChannel = System.nanoTime();
			QRResult qrResult = decodeChannel(channelMat, img.getWidth(), img.getHeight(), channelName, bucketStats);
			nanosDecodeTotal += System.nanoTime() - nanosBeforeChannel;

			if (qrResult != null) {
				qrResults.add(qrResult);
			}
		}

		if (!qrResults.isEmpty()) {
			bucketStats.incrCountQRPacketRecognized();
		}

		QRCapturedEvent qrCapturedEvent = new QRCapturedEvent(qrResults, nanosDecodeTotal, img, nanosSnapshot, nanosSnapshotTime);
		delegateQRStream.onQRCaptured(qrCapturedEvent);
	}

	private QRResult decodeChannel(Mat channelMat, int width, int height, String channelName, Bucket bucketStats) {
		byte[] channelBytes = new byte[width * height];
		channelMat.get(0, 0, channelBytes);

		BitMatrix binaryMatrix;
		try {
			LuminanceSource source = new ByteChannelLuminanceSource(channelBytes, width, height);
			Binarizer binarizer = new HybridBinarizer(source);
			binaryMatrix = binarizer.getBlackMatrix();
		} catch (Exception ex) {
			log.warn("channel " + channelName + ": binarize failed", ex);
			bucketStats.incrCountQRPacketNotFound();
			return null;
		}

		DetectorResult detectorResult;
		BitMatrix qrbits;
		try {
			Detector detector = new Detector(binaryMatrix);
			detectorResult = detector.detect(qrHints);
			qrbits = detectorResult.getBits();
		} catch (FormatException ex) {
			bucketStats.incrCountQRPacketFormatException();
			return null;
		} catch (NotFoundException ex) {
			bucketStats.incrCountQRPacketNotFound();
			return null;
		}

		DecoderResult decoderResult;
		try {
			decoderResult = decoder.decode(qrbits, qrHints);
			if (decoderResult.getOther() instanceof QRCodeDecoderMetaData) {
				((QRCodeDecoderMetaData) decoderResult.getOther()).applyMirroredCorrection(detectorResult.getPoints());
			}
		} catch (ChecksumException | FormatException ex) {
			bucketStats.incrCountQRPacketChecksumException();
			return null;
		}

		ResultPoint[] xingResultPoints = detectorResult.getPoints();
		List<PtInt2D> resultPts = new ArrayList<>();
		if (xingResultPoints != null) {
			for (ResultPoint pt : xingResultPoints) {
				resultPts.add(new PtInt2D((int) pt.getX(), (int) pt.getY()));
			}
		}

		return new QRResult(decoderResult.getText(), null, resultPts);
	}

}
