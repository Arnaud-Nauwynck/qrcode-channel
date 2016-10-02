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
	        int maxBytes = qrCodeBitsCapacity(version, ecLevel) / 8;
	        System.out.println("QRCode v " + versionNumber + " (dim:" + dimForVersion + "), ecLevel:" + ecLevel + " => maxBytes:" + maxBytes);
	    }
	}
	
	public static int qrCodeBitsCapacity(Version version, ErrorCorrectionLevel ecLevel) {
		// partial copy&paste from com.google.zxing.qrcode.encoder.Encoder.willFit() ... but return max value, instead of boolean if value exceed max
		// In the following comments, we use numbers of Version 7-H.
		// numBytes = 196
		int numBytes = version.getTotalCodewords();
		// getNumECBytes = 130
		Version.ECBlocks ecBlocks = version.getECBlocksForLevel(ecLevel);
		int numEcBytes = ecBlocks.getTotalECCodewords();
		// getNumDataBytes = 196 - 130 = 66
		int numDataBytes = numBytes - numEcBytes;
		return numDataBytes;
	}	

    public static Map<DecodeHintType, Object> createDefaultDecoderHints() {
    	Map<DecodeHintType, Object>  qrHints = new HashMap<>();
        qrHints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        qrHints.put(DecodeHintType.PURE_BARCODE, Boolean.TRUE);
        return qrHints;
    }
}
