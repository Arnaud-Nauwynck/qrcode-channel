package fr.an.qrcode.channel.impl.decode.input;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamDiscoveryService;
import com.github.sarxos.webcam.WebcamException;

import fr.an.qrcode.channel.impl.util.DimInt2D;


public class WebcamImageProvider extends ImageProvider {

	private static final Logger log = LoggerFactory.getLogger(WebcamImageProvider.class);

	private Webcam webcam;
	
	// --------------------------------------------------------------------------------------------
	
	public WebcamImageProvider(Webcam webcam) {
		this.webcam = webcam;
		log.info("create WebcamImageProvider with webcam:" + webcam);
	}

	static {
		// ?? Webcam.setDriver(new OpenImajDriver());
		// Webcam.setDriver(new JavaCvDriver());
	}

	public static WebcamImageProvider createDefault() {
		Webcam webcam;
		try {
			webcam = Webcam.getDefault(10, TimeUnit.SECONDS);
		} catch (WebcamException | TimeoutException ex) {
			throw new IllegalStateException("Failed to detect webcam", ex);
		}
		if (webcam == null) {
			throw new IllegalStateException("No detected webcam");
		}

		Dimension[] viewSizes = webcam.getViewSizes();
		Dimension bestViewSize = viewSizes[0];
		for(Dimension viewSize : viewSizes) {
			if (viewSize.getHeight()*viewSize.getWidth() > bestViewSize.getHeight()*bestViewSize.getWidth()) {
				bestViewSize = viewSize;
			}
		}
		Dimension currViewSize = webcam.getViewSize();
		if (currViewSize == null || ! currViewSize.equals(bestViewSize)) {
			log.info("changing viewSize:" + currViewSize + " -> " + bestViewSize);
			webcam.setViewSize(bestViewSize);
		}
		
		WebcamDiscoveryService discoveryService = Webcam.getDiscoveryService();
		if (discoveryService.isRunning()) {
			discoveryService.stop(); // avoid useless re-discovery loop ???
		}

		return new WebcamImageProvider(webcam);
	}

	// --------------------------------------------------------------------------------------------
	
	@Override
	public void open() {
		log.info("webcam.open..");
		try {
			webcam.open();
		} catch(RuntimeException ex) {
			log.error("Failed webcam.open");
			throw ex;
		}
		
		BufferedImage checkImg = captureImage();
		log.info("check capture image:" + checkImg.getWidth() + "x" + checkImg.getHeight());
	}

	@Override
	public void close() {
		log.info("webcam.close..");
		try {
			webcam.close();
		} catch(RuntimeException ex) {
			log.error("Failed webcam.close");
			throw ex;
		}
	}

	@Override
	public BufferedImage captureImage() {
		BufferedImage img = webcam.getImage();
		return img;
	}

	@Override
	public void parseRecordParamsText(String recordParamsText) {
		// TODO
	}

	@Override
	public DimInt2D getSize() {
		Dimension s = webcam.getViewSize();
		return new DimInt2D((int)s.getWidth(), (int)s.getHeight());
	}


}
