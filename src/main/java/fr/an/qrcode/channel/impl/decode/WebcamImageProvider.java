package fr.an.qrcode.channel.impl.decode;

import java.awt.image.BufferedImage;

import com.github.sarxos.webcam.Webcam;

public class WebcamImageProvider extends ImageProvider { 
    	
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