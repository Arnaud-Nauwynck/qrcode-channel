package fr.an.qrcode.channel.ui;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeListener;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.SwingUtilities;
import javax.swing.event.SwingPropertyChangeSupport;

import fr.an.qrcode.channel.impl.decode.QRCodesDecoderChannel.FragmentState;
import lombok.Getter;

import com.google.zxing.DecodeHintType;

import fr.an.qrcode.channel.impl.QRCodeUtils;
import fr.an.qrcode.channel.impl.decode.DecoderChannelEvent;
import fr.an.qrcode.channel.impl.decode.DecoderChannelListener;
import fr.an.qrcode.channel.impl.decode.QRCodesDecoderChannel;
import fr.an.qrcode.channel.impl.decode.calib3d.OpenCvCalib3d;
import fr.an.qrcode.channel.impl.decode.calib3d.OpenCvCalib3dImageProvider;
import fr.an.qrcode.channel.impl.decode.filter.QRCapturedEvent;
import fr.an.qrcode.channel.impl.decode.input.AvgFilterImageProvider;
import fr.an.qrcode.channel.impl.decode.input.DesktopScreenshotImageProvider;
import fr.an.qrcode.channel.impl.decode.input.ImageProvider;
import fr.an.qrcode.channel.impl.decode.input.WebcamImageProvider;
import fr.an.qrcode.channel.impl.util.DimInt2D;
import lombok.extern.slf4j.Slf4j;

/**
 * model associated to QRCodeDecoderChannelView<BR/>
 *
 * delegate all to underlying QRCodesDecoderChannel
 * wrap event callbacks with SwingUtilities.invokeLater
 * handle creation / reset of QRCodesDecoderChannel
 */
@Slf4j
public class QRCodeDecoderChannelModel {

    private SwingPropertyChangeSupport pcs = new SwingPropertyChangeSupport(this);
    private DecoderChannelListener uiEventListener;

    public static enum ImageProviderMode { DesktopScreenshot, OpenCV, WebCam };
    ImageProviderMode imageProviderMode =
//    		ImageProviderMode.OpenCV;
    		ImageProviderMode.WebCam;
//    		ImageProviderMode.DesktopScreenshot;
    DesktopScreenshotImageProvider desktopImageProvider = new DesktopScreenshotImageProvider();
    // OpenCVImageProvider openCVImageProvider; // = new openCVImageProvider();
    WebcamImageProvider webcamImageProvider; // = new WebcamImageProvider();
    private List<com.github.sarxos.webcam.Webcam> webcams;
    private com.github.sarxos.webcam.Webcam selectedWebcam;

    private String calib3dConfParamsName = "default-calib3d.data";
    OpenCvCalib3dImageProvider calib3dImageProvider;
	@Getter
    OpenCvCalib3d calib3d;

    private Map<DecodeHintType, Object> qrDecoderHints = QRCodeUtils.createDefaultDecoderHints();

    private boolean rgbSplitMode = false;

    private QRCodesDecoderChannel decoderChannel;

	@Getter
    private String fullText = "";
	// @Getter
    private BufferedImage currentScreenshotImg;

	public BufferedImage getCurrentScreenshotImg() {
		return currentScreenshotImg;
	}

	private String currDecodeMsg;
	@Getter
    private String recognitionStatsText;
    @Getter
	private QRCapturedEvent currQRCapturedEvent;
	private static final int QR_HISTORY_SIZE = 8;
	private final Deque<QRCapturedEvent> qrCapturedEventHistory = new ArrayDeque<>();

	// lombok @Getter above isn't generating these under the current Java/lombok toolchain -- explicit fallbacks
	public String getFullText() {
		return fullText;
	}
	public String getRecognitionStatsText() {
		return recognitionStatsText;
	}
	public QRCapturedEvent getCurrQRCapturedEvent() {
		return currQRCapturedEvent;
	}

	/** returns the N most recent captured events, newest first (includes the current one) */
	public List<QRCapturedEvent> getQRCapturedEventHistory() {
		return new ArrayList<>(qrCapturedEventHistory);
	}

	private AtomicBoolean pendingRefresh = new AtomicBoolean();

    // ------------------------------------------------------------------------

    public QRCodeDecoderChannelModel() {
    	reset();
    }

    // ------------------------------------------------------------------------

    public void reset() {
    	if (this.decoderChannel != null) {
    		// log.info("reset");
    		this.decoderChannel.stopListenSnapshots();
    	}
    	this.decoderChannel = new QRCodesDecoderChannel(qrDecoderHints,
    			getImageProvider(), e -> onDecoderChannelEvent(e), rgbSplitMode);
    	this.currentScreenshotImg = null;
    	setFullText("");
    }

    public boolean isRgbSplitMode() {
    	return rgbSplitMode;
    }

    public void setRgbSplitMode(boolean rgbSplitMode) {
    	if (rgbSplitMode != this.rgbSplitMode) {
    		this.rgbSplitMode = rgbSplitMode;
    		reset();
    	}
    }

    public void setUiEventListener(DecoderChannelListener uiEventListener) {
    	this.uiEventListener = uiEventListener;
    }

    protected ImageProvider getImageProvider() {
    	switch(imageProviderMode) {
    	case DesktopScreenshot: return desktopImageProvider;
    	case OpenCV: {
    		// TODO
//    		if (openCVImageProvider == null) {
//    			openCVImageProvider = OpenCVImageProvider.createDefault();
//    		}
//    		return new AvgFilterImageProvider(openCVImageProvider);
    	}
    	case WebCam: {
    		if (webcamImageProvider == null) {
    			if (webcams == null) {
    				webcams = WebcamImageProvider.listWebcams();
    			}
    			if (selectedWebcam == null) {
    				selectedWebcam = WebcamImageProvider.chooseDefaultWebcam(webcams);
    			}
    			webcamImageProvider = WebcamImageProvider.create(selectedWebcam);
    		}
    		DimInt2D size = webcamImageProvider.getSize();
    		this.calib3d = new OpenCvCalib3d(size.w, size.h);
    		this.calib3dImageProvider = new OpenCvCalib3dImageProvider(webcamImageProvider, calib3d);
			return new AvgFilterImageProvider(calib3dImageProvider);
    	}
    	default: throw new IllegalStateException();
    	}
    }

    public ImageProviderMode getImageProviderMode() {
    	return imageProviderMode;
    }

	public void setImageProviderMode(ImageProviderMode mode) {
		if (mode != this.imageProviderMode) {
			this.imageProviderMode = mode;
			reset();
		}
	}

	public List<com.github.sarxos.webcam.Webcam> getWebcams() {
		if (webcams == null) {
			webcams = WebcamImageProvider.listWebcams();
		}
		return webcams;
	}

	public com.github.sarxos.webcam.Webcam getSelectedWebcam() {
		return selectedWebcam;
	}

	public void setSelectedWebcam(com.github.sarxos.webcam.Webcam webcam) {
		if (webcam != this.selectedWebcam) {
			this.selectedWebcam = webcam;
			this.webcamImageProvider = null;
			if (imageProviderMode == ImageProviderMode.WebCam) {
				reset();
			}
		}
	}

	
    protected void onDecoderChannelEvent(DecoderChannelEvent event) {
    	if (! pendingRefresh.get()) {
    		pendingRefresh.set(true);
    		//? pendingRefresh.compareAndSet(false, true);
    		this.currQRCapturedEvent = event.qrEvent;
    		if (event.qrEvent != null) {
    			qrCapturedEventHistory.addFirst(event.qrEvent);
    			while (qrCapturedEventHistory.size() > QR_HISTORY_SIZE) {
    				qrCapturedEventHistory.removeLast();
    			}
    		}
    		
    		SwingUtilities.invokeLater(() -> {
    			try {
    				// this.nextSequenceNumber = this.decoderChannel.getNextSequenceNumber();
    				// startTime;
		    		// timeMillis;
    				// setCurrentScreenshotImg(event.qrEvent.image);
		    		this.currentScreenshotImg = event.qrEvent.image;
		    		this.fullText = event.readyText;
		    		
		    		this.recognitionStatsText = decoderChannel.getRecognitionStatsText();
		    		this.currDecodeMsg = event.currDecodeMsg;
		    		
		    		uiEventListener.onEvent(event);
    			} catch(Exception ex) {
    				// log.error("Failed", ex);
					System.err.println("Failed");
					ex.printStackTrace(System.err);
    			}
	    		pendingRefresh.set(false);
	    	});
    	}
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
		pcs.addPropertyChangeListener(listener);
	}

	public void removePropertyChangeListener(PropertyChangeListener listener) {
		pcs.removePropertyChangeListener(listener);
	}

    public int getNextSequenceNumber() {
        return // nextSequenceNumber
        		this.decoderChannel.getNextSequenceNumber();
    }

    public void setFullText(String p) {
    	String prev = fullText;
        this.fullText = p;
        pcs.firePropertyChange("fullText", prev, p);
    }

	public void setCurrentScreenshotImg(BufferedImage p) {
		BufferedImage prev = currentScreenshotImg;
		this.currentScreenshotImg = p;
        pcs.firePropertyChange("currentScreenshotImg", prev, p);
	}



	// delegate to calib3d
	// --------------------------------------------------------------------------------------------

	public void calib3dSaveConfParams() {
		if (this.calib3d.isCalibrated()) {
			calib3d.saveConfParams(calib3dConfParamsName);
		}
	}

	public void calib3dLoadConfParams() {
		calib3d.loadConfParams(calib3dConfParamsName);
	}

	public void calib3dReset() {
		this.calib3d.clearCorners();
	}

	public void calib3dProcessFrame(BufferedImage img) {
//		// calib3d.processFrame(grayFrame, displayRgbaFrame);
//		Mat mat = QROpenCvUtils.toMat(img);
//		calib3d.findPattern(mat);
	}

	public OpenCvCalib3dImageProvider getCalib3dImageProvider() {
		return calib3dImageProvider;
	}
	public void calib3dCalibrate() {
		calib3d.calibrate();
	}
	
	
	// delegate to decoderChannel
	// --------------------------------------------------------------------------------------------

	public QRCodesDecoderChannel getDecoderChannel() {
		return decoderChannel;
	}
	
	public void takeSnapshot() {
		decoderChannel.takeSnapshot();
	}
	
	public void startListenSnapshots() {
		decoderChannel.startListenSnapshots();
	}

	public void stopListenSnapshots() {
		decoderChannel.stopListenSnapshots();
	}
	
	public String getAheadFragsInfo() {
        return decoderChannel.getAheadFragsInfo();
	}

	public void parseRecordParamsText(String recordParamsText) {
		decoderChannel.parseRecordParamsText(recordParamsText);
	}

	public Rectangle getRecordArea() {
		return decoderChannel.getRecordArea();
	}

	public void setRecordArea(Rectangle r) {
		decoderChannel.setRecordArea(r);
	}

	public String getCurrDecodeMsg() {
		return currDecodeMsg;
	}

	public List<FragmentState> getFragmentStates() {
		return decoderChannel.getFragmentStates();
	}

	public List<FragmentState> getRepairFragmentStates() {
		return decoderChannel.getRepairFragmentStates();
	}

	public String getMetadataInfoText() {
		StringBuilder sb = new StringBuilder();
		sb.append("imageProviderMode: ").append(imageProviderMode).append("\n");
		if (imageProviderMode == ImageProviderMode.WebCam && selectedWebcam != null) {
			sb.append("selectedWebcam: ").append(selectedWebcam.getName()).append("\n");
		}
		Rectangle recordArea = decoderChannel.getRecordArea();
		sb.append("recordArea: ").append(recordArea != null ? recordArea : "<none>").append("\n");
		sb.append("nextSequenceNumber: ").append(getNextSequenceNumber()).append("\n");
		sb.append("aheadFragsInfo: ").append(getAheadFragsInfo()).append("\n");
		sb.append("currDecodeMsg: ").append(currDecodeMsg).append("\n");
		sb.append("fullText.length: ").append(fullText != null ? fullText.length() : 0).append("\n");
		sb.append("\n");
		sb.append(decoderChannel.getMetadataInfoText());
		sb.append("\n");
		sb.append("recognitionStats: ").append(recognitionStatsText).append("\n");
		return sb.toString();
	}

}
