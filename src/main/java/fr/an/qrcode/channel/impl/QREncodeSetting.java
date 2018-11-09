package fr.an.qrcode.channel.impl;

import java.util.HashMap;
import java.util.Map;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.pdf417.encoder.Dimensions;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

public class QREncodeSetting {

    private BarcodeFormat qrCodeFormat = BarcodeFormat.QR_CODE;
    private int qrVersion = 
    		6; 
// 			7;
//    		8;
// 			20;
//			40;
    
    private ErrorCorrectionLevel errorCorrectionLevel = ErrorCorrectionLevel.H;

    private Map<EncodeHintType, Object> qrHints;
    
    int minRows = 20;
    int maxRows = 50;
    private int qrCodeW = maxRows;
    private int qrCodeH = maxRows;
    
    private com.google.zxing.pdf417.encoder.Dimensions pdf417Dims;
    
    public QREncodeSetting() {
		qrHints = new HashMap<>();
        if (errorCorrectionLevel != null) {
            qrHints.put(EncodeHintType.ERROR_CORRECTION, errorCorrectionLevel);
        }        
        qrHints.put(EncodeHintType.QR_VERSION, qrVersion);
        
        pdf417Dims = new Dimensions(minRows, maxRows, minRows, maxRows);
        qrHints.put(EncodeHintType.PDF417_DIMENSIONS, pdf417Dims);
   
	}

	public BarcodeFormat getQrCodeFormat() {
		return qrCodeFormat;
	}

	public int getQrVersion() {
		return qrVersion;
	}

	public ErrorCorrectionLevel getErrorCorrectionLevel() {
		return errorCorrectionLevel;
	}

	public Map<EncodeHintType, Object> getQrHints() {
		return qrHints;
	}

	public int getQrCodeW() {
		return qrCodeW;
	}

	public int getQrCodeH() {
		return qrCodeH;
	}

    
}
