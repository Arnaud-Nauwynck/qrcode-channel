package fr.an.qrcode.channel.impl;

import java.util.HashMap;
import java.util.Map;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

public class QREncodeSetting {

    private BarcodeFormat qrCodeFormat =
    		BarcodeFormat.QR_CODE;

    private int qrVersion =
//    		6;
// 			7;
//    		8;
    		9;
//    		10;
//    		12;
//    		15;
// 			20;
//    		30;
//			40;

    private ErrorCorrectionLevel errorCorrectionLevel =
//    		ErrorCorrectionLevel.Q; // ~ 25%
    		ErrorCorrectionLevel.H; // ~ 30%

    private Map<EncodeHintType, Object> qrHints;

    private int qrCodeW = 17 + 4*qrVersion; // cf com.google.zxing.qrcode.decoder.Version.getDimensionForVersion()
    private int qrCodeH = qrCodeW;

    // FEC-like redundancy: when enabled, QRCodesEncoderChannel.nextFragmentToSend cycles through this sequence
    // of group sizes, XORing that many still-pending fragments together instead of always sending one plain
    // fragment -- so the decoder can recover a fragment from a combo once all-but-one of its members are known.
    private boolean comboRedundancyEnabled = false;
    private int[] comboGroupSizes = new int[] { 1, 2, 3 };


    public QREncodeSetting() {
		qrHints = new HashMap<>();
        if (errorCorrectionLevel != null) {
            qrHints.put(EncodeHintType.ERROR_CORRECTION, errorCorrectionLevel);
        }
        qrHints.put(EncodeHintType.QR_VERSION, qrVersion);
        // payload is wrapped as ISO-8859-1 text to carry arbitrary bytes (incl. XOR combo data) losslessly through ZXing's String API
        qrHints.put(EncodeHintType.CHARACTER_SET, "ISO-8859-1");
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

	public boolean isComboRedundancyEnabled() {
		return comboRedundancyEnabled;
	}

	public void setComboRedundancyEnabled(boolean p) {
		this.comboRedundancyEnabled = p;
	}

	public int[] getComboGroupSizes() {
		return comboGroupSizes;
	}

	public void setComboGroupSizes(int[] p) {
		this.comboGroupSizes = p;
	}

}
