package fr.an.qrcode.channel.impl.encode;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.Version;

import fr.an.qrcode.channel.impl.QRCodeUtils;
import fr.an.qrcode.channel.impl.QRCodecChannelUtils;

public class QRCodesEncoderChannel {

	private static int ESTIM_HEADER_LEN = 50;
	
	private QREncodeSetting qrEncodeSettings;
	private boolean encodeSha256Fragments = true;
	
	private int fragmentSequenceNumber = 0; // (sequence number generator)

	private Map<String,QRCodeEncodedFragment> fragments = new LinkedHashMap<>();
	
	// ------------------------------------------------------------------------

	public QRCodesEncoderChannel(QREncodeSetting encodeSetting) {
		this.qrEncodeSettings = encodeSetting;
	}
	
	// ------------------------------------------------------------------------
	
	public int getFragmentSequenceNumber() {
		return fragmentSequenceNumber;
	}
	
	public void appendFragmentsFor(String textContent) {
		Version qrVersion = Version.getVersionForNumber(qrEncodeSettings.getQrVersion());
		int maxBits = QRCodeUtils.qrCodeBitsCapacity(qrVersion, qrEncodeSettings.getErrorCorrectionLevel());
		int maxChars = maxBits/8 - ESTIM_HEADER_LEN;
		// split text in "raw" text fragment
		List<String> splitTexts = new ArrayList<>();
		String remainText = textContent;
		while(! remainText.isEmpty()) {
			int remainTextLength = remainText.length();
			int len = Math.min(maxChars, remainTextLength);
			String split = remainText.substring(0, len);
			splitTexts.add(split);
			if (len == remainTextLength) {
				break;
			}
			remainText= remainText.substring(len, remainTextLength);
		}
		// build Fragment from split text
		for(String splitText: splitTexts) {
			int fragSeqNumber = fragmentSequenceNumber++;
			String fragId = Integer.toString(fragSeqNumber);
			long crc32 = QRCodecChannelUtils.crc32(splitText);

			String header = fragId + " " + crc32;
			if (encodeSha256Fragments) {
				String checkSha256 = QRCodecChannelUtils.sha256(splitText);
				header += " SHA=" + checkSha256; 
	    	}
	    	
			header += "\n";
			QRCodeEncodedFragment frag = new QRCodeEncodedFragment(this, fragSeqNumber, fragId, header, splitText);
			fragments.put(fragId, frag);
		}
	}

	public BufferedImage encodeAndRender(String text) {
		QRCodeWriter qrCodeWriter = new QRCodeWriter();
	    BitMatrix bitMatrix;
		try {
			bitMatrix = qrCodeWriter.encode(text, qrEncodeSettings.getQrCodeFormat(), 
					qrEncodeSettings.getQrCodeW(), qrEncodeSettings.getQrCodeH(), 
					qrEncodeSettings.getQrHints());
		} catch (WriterException ex) {
			throw new RuntimeException("failed to encode qrcode for text", ex);
		}
	    BufferedImage image = MatrixToImageWriter.toBufferedImage(bitMatrix);
	    return image;
	}
 
	public List<FragmentImg> getFragmentImgs() {
		List<FragmentImg> res = new ArrayList<>();
		for(QRCodeEncodedFragment frag : fragments.values()) {
			res.add(frag.getFragmentImg());
		}
		return res;
	}
	
	public BufferedImage getFragmentImg(String id) {
		QRCodeEncodedFragment frag = fragments.get(id);
		if (frag == null) {
			return null;
		}
		return frag.getImg();
	}
	
}
