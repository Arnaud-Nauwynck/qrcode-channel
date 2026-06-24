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
import fr.an.qrcode.channel.impl.util.ByteArrayXorUtils;

public class QRCodesEncoderChannel {

	private static final Logger LOG = LoggerFactory.getLogger(QRCodesEncoderChannel.class);

	// header shape: "<id> <code> <len> <crc32>\n" -- worst case ~ "9999999 8 99999 4294967295\n"
	private static int ESTIM_HEADER_LEN = 30;

	private QREncodeSetting qrEncodeSettings;

	/** next sequence number generator, to write */
	private int nextSeqNumber = 1;

	private Map<Integer,QRCodeEncodedFragment> fragments = new LinkedHashMap<>();

	/** redundancy "combo" fragments (code 2..8), keyed separately since their id (anchor) collides with a plain fragment's id */
	private List<QRCodeEncodedFragment> comboFragments = new ArrayList<>();
	
	// ------------------------------------------------------------------------

	public QRCodesEncoderChannel(QREncodeSetting encodeSetting) {
		this.qrEncodeSettings = encodeSetting;
	}
	
	// ------------------------------------------------------------------------
	
	public int getNextSequenceNumber() {
		return nextSeqNumber;
	}
	
	public void appendFragmentsFor(String textContent) {
		Version qrVersion = Version.getVersionForNumber(qrEncodeSettings.getQrVersion());
		int bytesCapacity = QRCodeUtils.qrCodeBytesCapacity(qrVersion, qrEncodeSettings.getErrorCorrectionLevel());
		int maxBytesPerFragment = bytesCapacity - ESTIM_HEADER_LEN;

		maxBytesPerFragment = Math.max(10, maxBytesPerFragment);
		LOG.info("splitting " + maxBytesPerFragment  + " (" + (maxBytesPerFragment + ESTIM_HEADER_LEN) + ") bytes per fragment");

		byte[] allBytes = textContent.getBytes(java.nio.charset.StandardCharsets.UTF_8);

		// split bytes into fixed-size byte[] chunks (byte-level, so multi-byte UTF-8 chars are never split mid-fragment-boundary corruption-prone way: decoder re-joins bytes before final UTF-8 decode)
		List<byte[]> splitChunks = new ArrayList<>();
		int offset = 0;
		while (offset < allBytes.length) {
			int len = Math.min(maxBytesPerFragment, allBytes.length - offset);
			splitChunks.add(java.util.Arrays.copyOfRange(allBytes, offset, offset + len));
			offset += len;
		}
		if (splitChunks.isEmpty() && allBytes.length == 0) {
			// nothing to send
		}

		// build Fragment from split chunk
		for (byte[] chunk : splitChunks) {
			int fragSeqNumber = nextSeqNumber++;
			buildAndStorePlainFragment(fragSeqNumber, chunk);
		}
		LOG.info("splitting " + fragments.size() + " frags");

		scheduleComboFragments();
	}

	private void buildAndStorePlainFragment(int fragSeqNumber, byte[] data) {
		long crc32 = QRCodecChannelUtils.crc32(data);
		String header = fragSeqNumber + " " + 1 + " " + data.length + " " + crc32 + "\n";
		QRCodeEncodedFragment frag = new QRCodeEncodedFragment(this, fragSeqNumber, 1, header, data);
		fragments.put(fragSeqNumber, frag);
	}

	private void scheduleComboFragments() {
		if (!qrEncodeSettings.isComboRedundancyEnabled()) {
			return;
		}
		int totalPlainCount = fragments.size();
		if (totalPlainCount == 0) {
			return;
		}
		int everyN = Math.max(1, qrEncodeSettings.getComboEmitEveryNFragments());
		for (int code : qrEncodeSettings.getComboCodes()) {
			if (code < 2 || code > 8) {
				continue;
			}
			for (int id = code; id <= totalPlainCount; id++) {
				boolean isLastFragment = (id == totalPlainCount);
				if (id % everyN != 0 && !isLastFragment) {
					continue; // skip -- not a scheduled anchor (always force-emit at the very last fragment)
				}
				buildAndStoreComboFragment(id, code);
			}
		}
	}

	private void buildAndStoreComboFragment(int id, int code) {
		List<byte[]> sourceBytes = new ArrayList<>();
		for (int fragId = id - code + 1; fragId <= id; fragId++) {
			sourceBytes.add(fragments.get(fragId).getData());
		}
		int len = ByteArrayXorUtils.maxLength(sourceBytes);
		byte[] data = ByteArrayXorUtils.xorWithPadding(sourceBytes, len);
		long crc32 = QRCodecChannelUtils.crc32(data);
		String header = id + " " + code + " " + len + " " + crc32 + "\n";
		comboFragments.add(new QRCodeEncodedFragment(this, id, code, header, data));
	}

	public BufferedImage encodeAndRender(String text) {
		return encodeAndRender(text.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
	}

	public BufferedImage encodeAndRender(byte[] payloadBytes) {
		BitMatrix bitMatrix = encodeToBitMatrix(payloadBytes);
	    return MatrixToImageWriter.toBufferedImage(bitMatrix);
	}

	private BitMatrix encodeToBitMatrix(byte[] payloadBytes) {
		String latin1Text = new String(payloadBytes, java.nio.charset.StandardCharsets.ISO_8859_1);

		QRCodeWriter qrCodeWriter = new QRCodeWriter();
		try {
			int width = qrEncodeSettings.getQrCodeW();
			int height = qrEncodeSettings.getQrCodeH();
			BarcodeFormat qrCodeFormat = qrEncodeSettings.getQrCodeFormat();
			Map<EncodeHintType, Object> qrHints = qrEncodeSettings.getQrHints();

			return qrCodeWriter.encode(latin1Text, qrCodeFormat, width, height, qrHints);
		} catch (WriterException ex) {
			throw new RuntimeException("failed to encode qrcode for text", ex);
		}
	}

	/**
	 * composites up to 3 fragments' QR codes into one image, each rendered into a separate color
	 * channel (R, G, B) -- so up to 3 QR codes can be displayed/captured simultaneously in one frame.
	 * a null entry leaves that channel fully bright (255), i.e. no QR code drawn in that channel.
	 */
	public BufferedImage encodeAndRenderRgbSplit(QRCodeEncodedFragment redFrag, QRCodeEncodedFragment greenFrag, QRCodeEncodedFragment blueFrag) {
		return encodeAndRenderRgbSplit(
				redFrag != null ? redFrag.getPayloadBytes() : null,
				greenFrag != null ? greenFrag.getPayloadBytes() : null,
				blueFrag != null ? blueFrag.getPayloadBytes() : null);
	}

	public BufferedImage encodeAndRenderRgbSplit(byte[] redPayload, byte[] greenPayload, byte[] bluePayload) {
		BitMatrix redBits = redPayload != null ? encodeToBitMatrix(redPayload) : null;
		BitMatrix greenBits = greenPayload != null ? encodeToBitMatrix(greenPayload) : null;
		BitMatrix blueBits = bluePayload != null ? encodeToBitMatrix(bluePayload) : null;

		BitMatrix anyBits = redBits != null ? redBits : (greenBits != null ? greenBits : blueBits);
		if (anyBits == null) {
			throw new IllegalArgumentException("at least one channel payload must be non-null");
		}
		int width = anyBits.getWidth();
		int height = anyBits.getHeight();

		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int r = channelValue(redBits, x, y);
				int g = channelValue(greenBits, x, y);
				int b = channelValue(blueBits, x, y);
				img.setRGB(x, y, (r << 16) | (g << 8) | b);
			}
		}
		return img;
	}

	private static int channelValue(BitMatrix bits, int x, int y) {
		if (bits == null) {
			return 0xFF; // no QR code in this channel -- leave fully bright
		}
		return bits.get(x, y) ? 0x00 : 0xFF; // black module -> 0, white/background -> 255
	}

	public Map<Integer,FragmentImg> getFragmentImgs() {
		Map<Integer,FragmentImg> res = new LinkedHashMap<>();
		for(QRCodeEncodedFragment frag : fragments.values()) {
			res.put(frag.getFragmentNumber(), frag.getFragmentImg());
		}
		return res;
	}

	public List<FragmentImg> getNextFragmentImgs() {
		List<FragmentImg> res = new ArrayList<>(getFragmentImgs().values());
		for (QRCodeEncodedFragment combo : comboFragments) {
			res.add(combo.getFragmentImg());
		}
		return res;
	}

	public List<QRCodeEncodedFragment> getComboFragments() {
		return comboFragments;
	}
	
	public BufferedImage getFragmentImg(int num) {
		QRCodeEncodedFragment frag = fragments.get(num);
		if (frag == null) {
			return null;
		}
		return frag.getImg();
	}
	
}
