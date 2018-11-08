package fr.an.qrcode.channel.impl.decode.filter;

import java.awt.image.BufferedImage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.an.qrcode.channel.impl.decode.input.ImageProvider;

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
    		// TODO ... take ~5 consecutive snapshots, then average sub-sampling
    		doCaptureImage();	
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
    	imageStreamCallback.onStart();
    	try {
	    	for(;;) {
		    	if (stopListenSnapshotsRequested.get()) {
		    		break;
		    	}
		    	long nanosBefore = System.nanoTime();
		    	
		    	doCaptureImage();
		    	
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
    		log.error("Failed");
    	}
		listenSnapshotsRunning.set(false);
    	imageStreamCallback.onEnd();
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
		
		imageStreamCallback.onImage(img, nanosBefore, nanos);
    }

	public ImageProvider getImageProvider() {
		return imageProvider;
	}
	
}
