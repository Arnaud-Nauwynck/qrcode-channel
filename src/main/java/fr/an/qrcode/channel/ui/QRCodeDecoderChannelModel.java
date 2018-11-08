package fr.an.qrcode.channel.ui;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeListener;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.swing.SwingUtilities;
import javax.swing.event.SwingPropertyChangeSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamException;
import com.github.sarxos.webcam.ds.openimaj.OpenImajDriver;
import com.google.zxing.DecodeHintType;

import fr.an.qrcode.channel.impl.QRCodeUtils;
import fr.an.qrcode.channel.impl.decode.DecoderChannelEvent;
import fr.an.qrcode.channel.impl.decode.DecoderChannelListener;
import fr.an.qrcode.channel.impl.decode.DesktopScreenshotImageProvider;
import fr.an.qrcode.channel.impl.decode.ImageProvider;
import fr.an.qrcode.channel.impl.decode.QRCodesDecoderChannel;
import fr.an.qrcode.channel.impl.decode.QRCodesDecoderChannel.SnapshotFragmentResult;
import fr.an.qrcode.channel.impl.decode.WebcamImageProvider;

/**
 * model associated to QRCodeDecoderChannelView<BR/>
 * 
 * delegate all to underlying QRCodesDecoderChannel
 * wrap event callbacks with SwingUtilities.invokeLater
 * handle creation / reset of QRCodesDecoderChannel
 */
public class QRCodeDecoderChannelModel {
	
	private static final Logger LOG = LoggerFactory.getLogger(QRCodeDecoderChannelModel.class);
	
    private SwingPropertyChangeSupport pcs = new SwingPropertyChangeSupport(this);
    private DecoderChannelListener uiEventListener;
    
    public static enum ImageProviderMode { DesktopScreenshot, WebCam };
    ImageProviderMode imageProviderMode = ImageProviderMode.DesktopScreenshot;
    DesktopScreenshotImageProvider desktopImageProvider = new DesktopScreenshotImageProvider();
    WebcamImageProvider webcamImageProvider; // = new WebcamImageProvider();
        
    private Map<DecodeHintType, Object> qrDecoderHints = QRCodeUtils.createDefaultDecoderHints();
    
    private QRCodesDecoderChannel decoderChannel;
    
    private String fullText = "";
    private BufferedImage currentScreenshotImg;
    private SnapshotFragmentResult currentSnapshotResult;
    
    // ------------------------------------------------------------------------

    public QRCodeDecoderChannelModel() {
    	reset();
    }

    // ------------------------------------------------------------------------

    public void reset() {
    	if (this.decoderChannel != null) {
    		LOG.info("reset");
    		this.decoderChannel.stopListenSnapshots();
    	}
    	this.decoderChannel = new QRCodesDecoderChannel(qrDecoderHints, createImageProvider(), e -> onDecoderChannelEvent(e));
    	this.currentScreenshotImg = null;
    	setCurrentSnapshotResult(null);
    	setFullText("");
    }
    
    public void setUiEventListener(DecoderChannelListener uiEventListener) {
    	this.uiEventListener = uiEventListener; 
    }
    
    protected ImageProvider createImageProvider() {
    	switch(imageProviderMode) {
    	case DesktopScreenshot: return desktopImageProvider;
    	case WebCam:return createWebcamImageProvider();
    	default: throw new IllegalStateException();
    	}
    }

    protected ImageProvider createWebcamImageProvider() {
		if (webcamImageProvider == null) {
			Webcam.setDriver(new OpenImajDriver());
			
			Webcam webcam;
			try {
				webcam = Webcam.getDefault(10, TimeUnit.SECONDS);
			} catch (WebcamException | TimeoutException ex) {
				throw new IllegalStateException("Failed to detect webcam", ex);
			}
			if (webcam == null) {
				throw new IllegalStateException("No detected webcam");
			}

			Webcam.getDiscoveryService().stop(); // avoid useless re-discovery loop ???

			webcamImageProvider = new WebcamImageProvider();

			webcamImageProvider.init(webcam);
		}
		return webcamImageProvider;
	}

	public void setImageProviderMode(ImageProviderMode mode) {
		if (mode != this.imageProviderMode) { 	
			this.imageProviderMode = mode;
			reset();
		}
	}

	
    protected void onDecoderChannelEvent(DecoderChannelEvent event) {
    	SwingUtilities.invokeLater(() -> {
    		// startTime;
    		// timeMillis;
    		this.currentScreenshotImg = event.img;
    		this.currentSnapshotResult = event.snapshotResult;
    		this.fullText = event.readyText;
    		
    		uiEventListener.onEvent(event);
    	});
    }
    
    public void addPropertyChangeListener(PropertyChangeListener listener) {
		pcs.addPropertyChangeListener(listener);
	}

	public void removePropertyChangeListener(PropertyChangeListener listener) {
		pcs.removePropertyChangeListener(listener);
	}

    public int getChannelSequenceNumber() {
        return this.decoderChannel.getChannelSequenceNumber();
    }

    public String getFullText() {
        return fullText;
    }

    public void setFullText(String p) {
    	String prev = fullText; 
        this.fullText = p;
        pcs.firePropertyChange("fullText", prev, p);
    }

    public SnapshotFragmentResult getCurrentSnapshotResult() {
		return currentSnapshotResult;
	}

	public void setCurrentSnapshotResult(SnapshotFragmentResult p) {
		SnapshotFragmentResult prev = currentSnapshotResult; 
		this.currentSnapshotResult = p;
        pcs.firePropertyChange("currentSnapshotResult", prev, p);
	}

	public void setCurrentScreenshotImg(BufferedImage p) {
		BufferedImage prev = currentScreenshotImg; 
		this.currentScreenshotImg = p;
        pcs.firePropertyChange("currentScreenshotImg", prev, p);
	}

	public BufferedImage getCurrentScreenshotImg() {
        return currentScreenshotImg;
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


}
