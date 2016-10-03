package fr.an.qrcode.channel.impl.encode;

import java.util.HashMap;
import java.util.Map;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

public class QREncodeSetting {

    private BarcodeFormat qrCodeFormat = BarcodeFormat.QR_CODE;
    private int qrVersion = 40;
    private ErrorCorrectionLevel errorCorrectionLevel = ErrorCorrectionLevel.H;

    private Map<EncodeHintType, Object> qrHints;
    
    private int qrCodeW = 400;
    private int qrCodeH = 400;
    
    public QREncodeSetting() {
		qrHints = new HashMap<>();
        if (errorCorrectionLevel != null) {
            qrHints.put(EncodeHintType.ERROR_CORRECTION, errorCorrectionLevel);
        }        
        qrHints.put(EncodeHintType.QR_VERSION, qrVersion);
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
