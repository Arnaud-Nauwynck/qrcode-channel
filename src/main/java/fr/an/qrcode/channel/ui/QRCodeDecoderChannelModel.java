package fr.an.qrcode.channel.ui;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeListener;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.SwingUtilities;
import javax.swing.event.SwingPropertyChangeSupport;

import org.opencv.core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.zxing.DecodeHintType;

import fr.an.qrcode.channel.impl.QRCodeUtils;
import fr.an.qrcode.channel.impl.QROpenCvUtils;
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

/**
 * model associated to QRCodeDecoderChannelView<BR/>
 * 
 * delegate all to underlying QRCodesDecoderChannel
 * wrap event callbacks with SwingUtilities.invokeLater
 * handle creation / reset of QRCodesDecoderChannel
 */
public class QRCodeDecoderChannelModel {
	
	private static final Logger log = LoggerFactory.getLogger(QRCodeDecoderChannelModel.class);
	
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

    private String calib3dConfParamsName = "default-calib3d.data";
    OpenCvCalib3dImageProvider calib3dImageProvider;
    OpenCvCalib3d calib3d;
    
    private Map<DecodeHintType, Object> qrDecoderHints = QRCodeUtils.createDefaultDecoderHints();
    
    private QRCodesDecoderChannel decoderChannel;

    private String fullText = "";
    private BufferedImage currentScreenshotImg;
    private String currDecodeMsg;    
    private String recognitionStatsText;
    private QRCapturedEvent currQRCapturedEvent;
    
	private AtomicBoolean pendingRefresh = new AtomicBoolean();

    // ------------------------------------------------------------------------

    public QRCodeDecoderChannelModel() {
    	reset();
    }

    // ------------------------------------------------------------------------

    public void reset() {
    	if (this.decoderChannel != null) {
    		log.info("reset");
    		this.decoderChannel.stopListenSnapshots();
    	}
    	this.decoderChannel = new QRCodesDecoderChannel(qrDecoderHints, 
    			getImageProvider(), e -> onDecoderChannelEvent(e));
    	this.currentScreenshotImg = null;
    	setFullText("");
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
    			webcamImageProvider = WebcamImageProvider.createDefault();
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

	
    protected void onDecoderChannelEvent(DecoderChannelEvent event) {
    	if (! pendingRefresh.get()) {
    		pendingRefresh.set(true);
    		//? pendingRefresh.compareAndSet(false, true);
    		this.currQRCapturedEvent = event.qrEvent;
    		
    		SwingUtilities.invokeLater(() -> {
    			try {
    				// this.nextSequenceNumber = this.decoderChannel.getNextSequenceNumber();
    				// startTime;
		    		// timeMillis;
		    		this.currentScreenshotImg = event.qrEvent.image;
		    		this.fullText = event.readyText;
		    		
		    		this.recognitionStatsText = decoderChannel.getRecognitionStatsText();
		    		this.currDecodeMsg = event.currDecodeMsg;
		    		
		    		uiEventListener.onEvent(event);
    			} catch(Exception ex) {
    				log.error("Failed", ex);
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

    public String getFullText() {
        return fullText;
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

	public BufferedImage getCurrentScreenshotImg() {
        return currentScreenshotImg;
    }

	public String getRecognitionStatsText() {
		return recognitionStatsText;
	}

	public QRCapturedEvent getCurrQRCapturedEvent() {
		return currQRCapturedEvent;
	}

	// delegate to calib3d 
	// --------------------------------------------------------------------------------------------

	public OpenCvCalib3d getCalib3d() {
		return calib3d;
	}

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

}
