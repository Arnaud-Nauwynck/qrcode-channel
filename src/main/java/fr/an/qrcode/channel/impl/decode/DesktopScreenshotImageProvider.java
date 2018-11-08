package fr.an.qrcode.channel.impl.decode;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.awt.image.WritableRaster;
import java.util.zip.CRC32;

import fr.an.qrcode.channel.ui.utils.DesktopScreenSnaphotProvider;

public class DesktopScreenshotImageProvider extends ImageProvider { 
	
	private DesktopScreenSnaphotProvider screenSnaphostProvider = new DesktopScreenSnaphotProvider(false, true);

    private long currentScreenshotImgCrc32;

	@Override
	public BufferedImage captureImage() {
		BufferedImage tmpres = screenSnaphostProvider.captureScreen(recordArea);
		if (tmpres == null) {
			throw new IllegalStateException();
		}
		
        long imgCrc32 = imgCrc32(tmpres);
        if (currentScreenshotImgCrc32 == imgCrc32) {
        	return null; // exact same screenshot ..ignore
        }
        this.currentScreenshotImgCrc32 = imgCrc32;

		return tmpres;
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

}
