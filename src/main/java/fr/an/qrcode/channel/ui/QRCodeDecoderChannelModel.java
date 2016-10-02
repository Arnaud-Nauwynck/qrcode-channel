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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.CRC32;

import javax.swing.SwingUtilities;
import javax.swing.event.SwingPropertyChangeSupport;

import fr.an.qrcode.channel.impl.decode.QRCodesDecoderChannel;
import fr.an.qrcode.channel.impl.decode.QRCodesDecoderChannel.SnapshotFragmentResult;
import fr.an.qrcode.channel.ui.utils.DesktopScreenSnaphotProvider;

/**
 * model associated to QRCodeDecoderChannelView<BR/>
 * 
 * take screenshot of rectangular record area, decode QRCode, concatenate text result
 */
public class QRCodeDecoderChannelModel {
    
    private SwingPropertyChangeSupport pcs = new SwingPropertyChangeSupport(this);

    private DesktopScreenSnaphotProvider screenSnaphostProvider = new DesktopScreenSnaphotProvider(false, true);

    private Rectangle recordArea = new Rectangle(40,167,730,720); // 30,147,750,740); //40, 207,840,780); 
    
    private QRCodesDecoderChannel decoderChannel;
    
    private String fullText = "";
    
    private BufferedImage currentScreenshotImg;
    private long currentScreenshotImgCrc32;
    private SnapshotFragmentResult currentSnapshotResult;
    
    private AtomicBoolean stopListenSnapshotsRequested = new AtomicBoolean(true);
    private AtomicBoolean listenSnapshotsRunning = new AtomicBoolean(false);
	private long sleepMillis = 5;

	private ExecutorService snapshotExecutor = Executors.newSingleThreadExecutor();
	
    // ------------------------------------------------------------------------

    public QRCodeDecoderChannelModel() {
    	reset();
    }

    // ------------------------------------------------------------------------

    public void reset() {
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
    
    public SnapshotFragmentResult doCaptureAndHandleSnapshot() {
		long startTime = System.currentTimeMillis();

		currentScreenshotImg = screenSnaphostProvider.captureScreen(recordArea);
        
        long imgCrc32 = imgCrc32(currentScreenshotImg);
        if (currentScreenshotImgCrc32 == imgCrc32) {
        	return null; // exact same screenshot ..ignore
        }
        		
        SnapshotFragmentResult snapshotResult = decoderChannel.handleSnapshot(currentScreenshotImg);

        long timeMillis = System.currentTimeMillis() - startTime;
        currentScreenshotImgCrc32 = imgCrc32;
        
        String readyText = decoderChannel.getReadyText();
        
        snapshotResult.millis = timeMillis;
        
        SwingUtilities.invokeLater(() -> {
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

    public Rectangle getRecordArea() {
        return recordArea;
    }

    public void setRecordArea(Rectangle recordArea) {
        this.recordArea = recordArea;
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

	public BufferedImage getCurrentScreenshotImg() {
        return currentScreenshotImg;
    }

	public String getAheadFragsInfo() {
        return decoderChannel.getAheadFragsInfo();
	}
    
}
