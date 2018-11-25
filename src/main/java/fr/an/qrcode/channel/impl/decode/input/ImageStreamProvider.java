package fr.an.qrcode.channel.impl.decode.input;

import java.awt.image.BufferedImage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImageStreamProvider {

	private static final Logger log = LoggerFactory.getLogger(ImageStreamProvider.class);

	private ImageStreamCallback imageStreamCallback;
	
	private ImageProvider imageProvider;

	private ExecutorService snapshotExecutor = Executors.newSingleThreadExecutor();

    private AtomicBoolean stopListenSnapshotsRequested = new AtomicBoolean(true);
    private AtomicBoolean listenSnapshotsRunning = new AtomicBoolean(false);
	private long sleepMillis = 2;

	// private BufferedImage lastImage;
	
	
	public ImageStreamProvider(ImageProvider imageProvider, ImageStreamCallback imageStreamCallback) {
		this.imageProvider = imageProvider;
		this.imageStreamCallback = imageStreamCallback;
	}
	
    public void takeSnapshot() {
    	snapshotExecutor.submit(() -> {
    		imageProvider.open();
    		try {
	    		// TODO ... take ~5 consecutive snapshots, then average sub-sampling
	    		doCaptureImage();	
    		} finally {
    			imageProvider.close();
    		}
    	});
    }

	public void startListenSnapshots() {
    	if (listenSnapshotsRunning.get()) {
    		return;
    	}
    	stopListenSnapshotsRequested.set(false);
    	snapshotExecutor.submit(() -> takeSnapshotsLoop());
    }
    
    private void takeSnapshotsLoop() {
    	listenSnapshotsRunning.set(true);
    	try {
    		imageProvider.open();
    		
    		imageStreamCallback.onStart(imageProvider.getSize());
    		
	    	for(;;) {
		    	if (stopListenSnapshotsRequested.get()) {
		    		break;
		    	}
		    	long nanosBefore = System.nanoTime();
		    	
		    	try {
		    		doCaptureImage();
		    	} catch(Exception ex) {
		    		log.warn("failed to capture image ", ex);
		    		// Failed to capture image??
			    	try {
						Thread.sleep(10);
					} catch (InterruptedException e) {
					}
			    	imageProvider.close();
			    	imageProvider.open();
			    	continue;
		    	}
		    	
	    		long nanosAfter = System.nanoTime();
	    		long millis = TimeUnit.NANOSECONDS.toMillis(nanosAfter - nanosBefore); 
	    		long actualSleepMillis = sleepMillis - millis;
	    		if (actualSleepMillis > 0) { 
			    	try {
						Thread.sleep(actualSleepMillis);
					} catch (InterruptedException e) {
					}
	    		}
	    	}
    	} catch(Exception ex) {
    		log.error("Failed .. stopping loop");
    	} finally {
    		imageProvider.close();
    		listenSnapshotsRunning.set(false);
    		imageStreamCallback.onEnd();
    	}
    }
    
    public void stopListenSnapshots() {
    	if (! listenSnapshotsRunning.get()) {
    		return;
    	}
    	stopListenSnapshotsRequested.set(true);
    }
    
    public void doCaptureImage() {
    	long nanosBefore = System.nanoTime();
    	
		BufferedImage img = imageProvider.captureImage();
		
		long nanos = System.nanoTime() - nanosBefore;
		if (img == null) {
			// exact same image as previously ..ignored, do nothing!
			return;
		}

		// this.lastImage = img;
		try {
			imageStreamCallback.onImage(img, nanosBefore, nanos);
		} catch(Exception ex) {
			log.error("Failed to handle captured img", ex);
		}
    }

	public ImageProvider getImageProvider() {
		return imageProvider;
	}
	
}
