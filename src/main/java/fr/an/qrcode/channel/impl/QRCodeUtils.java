package fr.an.qrcode.channel.impl;

import java.util.HashMap;
import java.util.Map;

import com.google.zxing.DecodeHintType;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.decoder.Version;

public class QRCodeUtils {

	public static void printQRCodeCapacities(int versionNumber) {
	    Version version = Version.getVersionForNumber(versionNumber);
	    int dimForVersion = version.getDimensionForVersion();
	    for(ErrorCorrectionLevel ecLevel : ErrorCorrectionLevel.values()) {
	        int maxBytes = com.google.zxing.qrcode.encoder.Encoder.maxBitsWillFit(version, ecLevel);
	        System.out.println("QRCode v " + versionNumber + " (dim:" + dimForVersion + "), ecLevel:" + ecLevel + " => maxBytes:" + maxBytes);
	    }
	}
	
	public static int qrCodeBitsCapacity(Version version, ErrorCorrectionLevel errorCorrectionLevel) {
		return com.google.zxing.qrcode.encoder.Encoder.maxBitsWillFit(version, errorCorrectionLevel);
	}
	

    public static Map<DecodeHintType, Object> createDefaultDecoderHints() {
    	Map<DecodeHintType, Object>  qrHints = new HashMap<>();
        qrHints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        qrHints.put(DecodeHintType.PURE_BARCODE, Boolean.TRUE);
        return qrHints;
    }
}
