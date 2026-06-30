package fr.an.qrcode.channel.impl;

import java.util.HashMap;
import java.util.Map;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.decoder.Version;

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

    // RaptorQ (RFC 6330, OpenRQ) FEC redundancy: QRCodesEncoderChannel.appendFragmentsFor splits the payload
    // into source symbols of this size (one per plain QR fragment), and QRCodesEncoderChannel.nextFragmentToSend
    // sends all source symbols once, then cycles repair symbols indefinitely -- the decoder can reconstruct
    // the whole source block from any sufficiently large subset of source+repair symbols, in any order.
    // defaults to (QR code byte capacity - header overhead), cf computeDefaultSymbolSize().
    private int symbolSize;

    // number of repair symbols generated and cycled through after the source symbols, for redundancy/retransmission
    private int numRepairSymbols = 50;

	// header shape: "<fragmentNumber> <len> <crc32>\n" -- worst case ~ "9999999 99999 4294967295\n"
	private static final int ESTIM_HEADER_LEN = 30;

    public QREncodeSetting() {
		qrHints = new HashMap<>();
        if (errorCorrectionLevel != null) {
            qrHints.put(EncodeHintType.ERROR_CORRECTION, errorCorrectionLevel);
        }
        qrHints.put(EncodeHintType.QR_VERSION, qrVersion);
        // payload is wrapped as ISO-8859-1 text to carry arbitrary bytes (incl. RaptorQ symbol data) losslessly through ZXing's String API
        qrHints.put(EncodeHintType.CHARACTER_SET, "ISO-8859-1");

        this.symbolSize = computeDefaultSymbolSize();
   	}

    private int computeDefaultSymbolSize() {
    	Version version = Version.getVersionForNumber(qrVersion);
    	int bytesCapacity = QRCodeUtils.qrCodeBytesCapacity(version, errorCorrectionLevel);
    	return Math.max(10, bytesCapacity - ESTIM_HEADER_LEN);
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

	public int getSymbolSize() {
		return symbolSize;
	}

	public void setSymbolSize(int p) {
		this.symbolSize = p;
	}

	public int getNumRepairSymbols() {
		return numRepairSymbols;
	}

	public void setNumRepairSymbols(int p) {
		this.numRepairSymbols = p;
	}

}
