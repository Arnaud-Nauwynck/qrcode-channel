package fr.an.qrcode.channel.impl.encode;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
import fr.an.qrcode.channel.impl.QREncodeSetting;
import fr.an.qrcode.channel.impl.util.ByteArrayXorUtils;

public class QRCodesEncoderChannel {

	private static final Logger LOG = LoggerFactory.getLogger(QRCodesEncoderChannel.class);

	// header shape: "<id1> [<id2>] [<id3>] <code> <len> <crc32>\n" -- worst case ~ "9999999 9999998 9999997 3 99999 4294967295\n"
	private static int ESTIM_HEADER_LEN = 45;

	private QREncodeSetting qrEncodeSettings;

	/** next sequence number generator, to write */
	private int nextSeqNumber = 1;

	private Map<Integer,QRCodeEncodedFragment> fragments = new LinkedHashMap<>();

	/** ids of plain fragments not yet acknowledged by the decoder; drives what nextFragmentToSend() emits */
	private LinkedHashSet<Integer> pendingIds = new LinkedHashSet<>();

	/** round-robin cursor over pendingIds, cycling group sizes from QREncodeSetting.comboGroupSizes */
	private int sendCursor = 0;
	private int comboGroupSizeIndex = 0;

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
			pendingIds.add(fragSeqNumber);
		}
		LOG.info("splitting " + fragments.size() + " frags");
	}

	private void buildAndStorePlainFragment(int fragSeqNumber, byte[] data) {
		QRCodeEncodedFragment frag = new QRCodeEncodedFragment(this, new int[] { fragSeqNumber }, data);
		fragments.put(fragSeqNumber, frag);
	}

	/**
	 * computes the next fragment to transmit: a round-robin walk over still-pending (non-acknowledged) fragment
	 * ids, cycling through QREncodeSetting.comboGroupSizes -- e.g. sizes {1,2,3} alternates between sending one
	 * pending fragment plain, XOR-ing it with the next pending one, then with the next two pending ones, so the
	 * decoder can recover a fragment from a combo once all-but-one of its members become known by any means.
	 * returns null once every fragment has been acknowledged.
	 */
	public QRCodeEncodedFragment nextFragmentToSend() {
		if (pendingIds.isEmpty()) {
			return null;
		}
		List<Integer> pendingList = new ArrayList<>(pendingIds);
		int n = pendingList.size();
		if (sendCursor >= n) {
			sendCursor = 0;
		}

		int groupSize = 1;
		if (qrEncodeSettings.isComboRedundancyEnabled()) {
			int[] groupSizes = qrEncodeSettings.getComboGroupSizes();
			if (groupSizes.length > 0) {
				if (comboGroupSizeIndex >= groupSizes.length) {
					comboGroupSizeIndex = 0;
				}
				groupSize = Math.max(1, groupSizes[comboGroupSizeIndex]);
				comboGroupSizeIndex++;
			}
		}
		groupSize = Math.min(groupSize, n);

		int[] ids = new int[groupSize];
		for (int i = 0; i < groupSize; i++) {
			ids[i] = pendingList.get((sendCursor + i) % n);
		}
		java.util.Arrays.sort(ids);
		sendCursor = (sendCursor + 1) % n;

		for (int id : ids) {
			QRCodeEncodedFragment plainFrag = fragments.get(id);
			if (plainFrag != null) {
				plainFrag.incrSentCount(ids.length);
			}
		}

		return buildFragmentFor(ids);
	}

	private QRCodeEncodedFragment buildFragmentFor(int[] ids) {
		if (ids.length == 1) {
			return fragments.get(ids[0]);
		}
		List<byte[]> sourceBytes = new ArrayList<>();
		for (int id : ids) {
			sourceBytes.add(fragments.get(id).getData());
		}
		int len = ByteArrayXorUtils.maxLength(sourceBytes);
		byte[] data = ByteArrayXorUtils.xorWithPadding(sourceBytes, len);
		return new QRCodeEncodedFragment(this, ids, data);
	}

	/** marks a plain fragment id as acknowledged by the decoder; it is no longer scheduled by nextFragmentToSend() */
	public void acknowledge(int fragSeqNumber) {
		pendingIds.remove(fragSeqNumber);
		QRCodeEncodedFragment frag = fragments.get(fragSeqNumber);
		if (frag != null) {
			frag.acknowledge();
		}
	}

	/** marks every fragment id strictly below seqNumber as acknowledged */
	public void acknowledgeUpTo(int seqNumber) {
		for (int id : new ArrayList<>(pendingIds)) {
			if (id < seqNumber) {
				acknowledge(id);
			}
		}
	}

	public boolean isFullyAcknowledged() {
		return pendingIds.isEmpty();
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

	public BufferedImage getFragmentImg(int num) {
		QRCodeEncodedFragment frag = fragments.get(num);
		if (frag == null) {
			return null;
		}
		return frag.getImg();
	}

}
