package fr.an.qrcode.channel.ui;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.awt.image.WritableRaster;
import java.beans.PropertyChangeListener;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.CRC32;

import javax.swing.SwingUtilities;
import javax.swing.event.SwingPropertyChangeSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamException;
import com.github.sarxos.webcam.ds.openimaj.OpenImajDriver;

import fr.an.qrcode.channel.impl.decode.QRCodesDecoderChannel;
import fr.an.qrcode.channel.impl.decode.QRCodesDecoderChannel.SnapshotFragmentResult;
import fr.an.qrcode.channel.ui.utils.DesktopScreenSnaphotProvider;

/**
 * model associated to QRCodeDecoderChannelView<BR/>
 * 
 * take screenshot of rectangular record area, decode QRCode, concatenate text result
 */
public class QRCodeDecoderChannelModel {

	
	private static final Logger LOG = LoggerFactory.getLogger(QRCodeDecoderChannelModel.class);
	
    private SwingPropertyChangeSupport pcs = new SwingPropertyChangeSupport(this);

    public static abstract class ImageProvider {

	    protected Rectangle recordArea = new Rectangle(40,217,740,720);

    	public abstract BufferedImage captureImage();

		public abstract void parseRecordParamsText(String recordParamsText);
		
		public Rectangle getRecordArea() {
			return this.recordArea;
		}

		public void setRecordArea(Rectangle r) {
			this.recordArea = r;
		}

    }
    
    DesktopScreenshotImageProvider desktopImageProvider = new DesktopScreenshotImageProvider();
    WebcamImageProvider webcamImageProvider; // = new WebcamImageProvider();
    
    ImageProvider imageProvider = desktopImageProvider;
    
    public static class DesktopScreenshotImageProvider extends ImageProvider { 
    	private DesktopScreenSnaphotProvider screenSnaphostProvider = new DesktopScreenSnaphotProvider(false, true);

		@Override
		public BufferedImage captureImage() {
			return screenSnaphostProvider.captureScreen(recordArea);
		}

		@Override
		public void parseRecordParamsText(String recordParamsText) {
			String[] coordTexts = recordParamsText.split(",");
	        int x = Integer.parseInt(coordTexts[0]);
	        int y = Integer.parseInt(coordTexts[1]);
	        int w = Integer.parseInt(coordTexts[2]);
	        int h = Integer.parseInt(coordTexts[3]);
	        recordArea = new Rectangle(x, y, w, h);
		}
		
    }


    public static class WebcamImageProvider extends ImageProvider { 
    	
    	private Webcam webcam;

	    public WebcamImageProvider() {
	    }
	    
	    public void init(Webcam webcam) {
	    	if (webcam == null) {
		    	webcam = Webcam.getDefault();
	    	}
	    	this.webcam = webcam;
//	    	webcam.open();
//	    	webcam.close();
	    }
	    
		@Override
		public BufferedImage captureImage() {
			if (! webcam.isOpen()) {
				webcam.open();
			}
			return webcam.getImage();
		}

		@Override
		public void parseRecordParamsText(String recordParamsText) {
			// TODO
		}
    }
    
    
    private QRCodesDecoderChannel decoderChannel;
    
    private String fullText = "";
    
    private BufferedImage currentScreenshotImg;
    private long currentScreenshotImgCrc32;
    private SnapshotFragmentResult currentSnapshotResult;
    
    private AtomicBoolean stopListenSnapshotsRequested = new AtomicBoolean(true);
    private AtomicBoolean listenSnapshotsRunning = new AtomicBoolean(false);
	private long sleepMillis = 2;

	private ExecutorService snapshotExecutor = Executors.newSingleThreadExecutor();
	
    // ------------------------------------------------------------------------

    public QRCodeDecoderChannelModel() {
    	reset();
    }

    // ------------------------------------------------------------------------

    public void reset() {
    	stopListenSnapshots();
    	this.decoderChannel = new QRCodesDecoderChannel();
    	this.currentScreenshotImg = null;
    	this.currentScreenshotImgCrc32 = 0;
    	setCurrentSnapshotResult(null);
    	setFullText("");
    	stopListenSnapshotsRequested.set(true);
    }
    
    public void takeSnapshot() {
    	snapshotExecutor.submit(() -> doCaptureAndHandleSnapshot());
    }

	public void setSourceWebcam() {
		if (webcamImageProvider == null) {
			Webcam.setDriver(new OpenImajDriver());
			
			Webcam webcam;
			try {
				webcam = Webcam.getDefault(10, TimeUnit.SECONDS);
			} catch (WebcamException | TimeoutException ex) {
				LOG.error("Failed to detec webcam", ex);
				return;
			}

			Webcam.getDiscoveryService().stop(); // avoid re-discovery loop ???

			webcamImageProvider = new WebcamImageProvider();

			webcamImageProvider.init(webcam);
		}
		this.imageProvider = webcamImageProvider;
	}

	public void setSourceScreenshot() {
		this.imageProvider = desktopImageProvider;
	}

	
    public SnapshotFragmentResult doCaptureAndHandleSnapshot() {
		long startTime = System.currentTimeMillis();

		BufferedImage img = imageProvider.captureImage();
		if (img == null) {
			LOG.error("null img");
		}
		
        long imgCrc32 = imgCrc32(img);
        if (currentScreenshotImgCrc32 == imgCrc32) {
        	return null; // exact same screenshot ..ignore
        }
        		
        SnapshotFragmentResult snapshotResult = decoderChannel.handleSnapshot(img);

        long timeMillis = System.currentTimeMillis() - startTime;
        currentScreenshotImgCrc32 = imgCrc32;
        
        String readyText = decoderChannel.getReadyText();
        
        snapshotResult.millis = timeMillis;
        
        SwingUtilities.invokeLater(() -> {
        	setCurrentScreenshotImg(img);
        	setCurrentSnapshotResult(snapshotResult);
	        setFullText(readyText);
        });
        return snapshotResult;
	}  
    

    public static long imgCrc32(BufferedImage img) {
    	CRC32 crc = new CRC32();
        WritableRaster imgRaster = img.getRaster();
        DataBuffer dataBuffer = imgRaster.getDataBuffer();
        if (dataBuffer instanceof DataBufferInt) {
        	DataBufferInt di = (DataBufferInt) dataBuffer;
        	final int[] data = di.getData();
            for(int d : data) {
            	crc.update(d);
            }
        } else if (dataBuffer instanceof DataBufferByte) {
        	DataBufferByte di = (DataBufferByte) dataBuffer;
        	final byte[] data = di.getData();
            for(int d : data) {
            	crc.update(d);
            }
        } else {
        	throw new UnsupportedOperationException("not impl");
        }
		return crc.getValue();
	}

	public void startListenSnapshots() {
    	if (listenSnapshotsRunning.get()) {
    		return;
    	}
    	stopListenSnapshotsRequested.set(false);
    	snapshotExecutor.submit(() -> listenSnapshotLoop());
    }
    
    private void listenSnapshotLoop() {
    	listenSnapshotsRunning.set(true);
    	try {
	    	for(;;) {
		    	if (stopListenSnapshotsRequested.get()) {
		    		break;
		    	}
		    	long timeBefore = System.currentTimeMillis();
		    	
		    	doCaptureAndHandleSnapshot();
		    	
	    		long timeAfter = System.currentTimeMillis();
	    		long actualSleepMillis = (timeBefore + sleepMillis) - timeAfter;
	    		if (actualSleepMillis > 0) { 
			    	try {
						Thread.sleep(actualSleepMillis);
					} catch (InterruptedException e) {
					}
	    		}
	    	}
    	} catch(Exception ex) {
    		LOG.error("Failed");
    	}
		listenSnapshotsRunning.set(false);
    }
    
    public void stopListenSnapshots() {
    	if (! listenSnapshotsRunning.get()) {
    		return;
    	}
    	stopListenSnapshotsRequested.set(true);
    }
    
    
    // ------------------------------------------------------------------------
	
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

	public String getAheadFragsInfo() {
        return decoderChannel.getAheadFragsInfo();
	}

	public void parseRecordParamsText(String recordParamsText) {
		imageProvider.parseRecordParamsText(recordParamsText);
	}

	public Rectangle getRecordArea() {
		return imageProvider.getRecordArea();
	}

	public void setRecordArea(Rectangle r) {
		imageProvider.setRecordArea(r);
	}


}
