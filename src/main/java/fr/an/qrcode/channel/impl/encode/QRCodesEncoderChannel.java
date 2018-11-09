package fr.an.qrcode.channel.impl.encode;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.Version;

import fr.an.qrcode.channel.impl.QRCodeUtils;
import fr.an.qrcode.channel.impl.QRCodecChannelUtils;
import fr.an.qrcode.channel.impl.QREncodeSetting;

public class QRCodesEncoderChannel {

	private static final Logger LOG = LoggerFactory.getLogger(QRCodesEncoderChannel.class);
	
	private static int ESTIM_HEADER_LEN = 22;
	
	private QREncodeSetting qrEncodeSettings;
	
	private int fragmentSequenceNumber = 0; // (sequence number generator)

	private Map<Integer,QRCodeEncodedFragment> fragments = new LinkedHashMap<>();
	
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
		int bytesCapacity = QRCodeUtils.qrCodeBytesCapacity(qrVersion, qrEncodeSettings.getErrorCorrectionLevel());
		int maxChars = bytesCapacity - ESTIM_HEADER_LEN;
		
		maxChars = Math.max(10, maxChars);
		LOG.info("splitting " + maxChars  + " (" + (maxChars + ESTIM_HEADER_LEN) + ") chars");
		
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
			long crc32 = QRCodecChannelUtils.crc32(splitText);

			String header = fragSeqNumber + " " + crc32;
	    	
			header += "\n";
			QRCodeEncodedFragment frag = new QRCodeEncodedFragment(this, fragSeqNumber, header, splitText);
			fragments.put(fragSeqNumber, frag);
		}
		LOG.info("splitting " + fragments.size() + " frags");
	}

	public BufferedImage encodeAndRender(String text) {
		QRCodeWriter qrCodeWriter = new QRCodeWriter();
	    BitMatrix bitMatrix;
		try {
			int width = qrEncodeSettings.getQrCodeW();
			int height = qrEncodeSettings.getQrCodeH();
			BarcodeFormat qrCodeFormat = qrEncodeSettings.getQrCodeFormat();
			Map<EncodeHintType, Object> qrHints = qrEncodeSettings.getQrHints();
			
			bitMatrix = qrCodeWriter.encode(text, qrCodeFormat, width, height, qrHints);
			
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
	
	public BufferedImage getFragmentImg(int num) {
		QRCodeEncodedFragment frag = fragments.get(num);
		if (frag == null) {
			return null;
		}
		return frag.getImg();
	}
	
}
