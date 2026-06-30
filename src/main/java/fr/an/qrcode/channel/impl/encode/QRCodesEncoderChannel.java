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

import net.fec.openrq.EncodingPacket;
import net.fec.openrq.OpenRQ;
import net.fec.openrq.encoder.DataEncoder;
import net.fec.openrq.encoder.SourceBlockEncoder;
import net.fec.openrq.parameters.FECParameters;

import fr.an.qrcode.channel.impl.QREncodeSetting;

/**
 * splits a text payload into a RaptorQ (RFC 6330, OpenRQ) source block, and emits it as a stream of QR
 * fragments: a leading FEC-params preamble fragment (id 0, carrying dataLength so the decoder can derive
 * identical FECParameters), then all source symbols once, then repair symbols cycled indefinitely for
 * redundancy -- the decoder can reconstruct the whole payload from any sufficiently large subset of
 * source+repair symbols, received in any order.
 */
public class QRCodesEncoderChannel {

	private static final Logger LOG = LoggerFactory.getLogger(QRCodesEncoderChannel.class);

	private QREncodeSetting qrEncodeSettings;

	private FECParameters fecParams;
	private DataEncoder dataEncoder;
	private SourceBlockEncoder sourceBlockEncoder;

	/** fragmentNumber 0 = FEC-params preamble; 1..K = source symbols (ESI 0..K-1); K+1.. = repair symbols */
	private Map<Integer,QRCodeEncodedFragment> fragments = new LinkedHashMap<>();

	/** next fragmentNumber to allocate when generating a new repair symbol on demand */
	private int nextRepairFragmentNumber;
	private int nextRepairEsi;

	/** round-robin cursor over fragments.keySet(), driving nextFragmentToSend() */
	private List<Integer> sendOrder = new ArrayList<>();
	private int sendCursor = 0;

	// ------------------------------------------------------------------------

	public QRCodesEncoderChannel(QREncodeSetting encodeSetting) {
		this.qrEncodeSettings = encodeSetting;
	}

	// ------------------------------------------------------------------------

	public int getNextSequenceNumber() {
		return fragments.size() + 1;
	}

	public void appendFragmentsFor(String textContent) {
		byte[] allBytes = textContent.getBytes(java.nio.charset.StandardCharsets.UTF_8);

		int symbolSize = qrEncodeSettings.getSymbolSize();
		fecParams = FECParameters.newParameters(allBytes.length, symbolSize, 1);
		dataEncoder = OpenRQ.newEncoder(allBytes, fecParams);
		sourceBlockEncoder = dataEncoder.sourceBlock(0);

		int paramsFragNumber = 0;
		buildAndStoreParamsFragment(paramsFragNumber, allBytes.length, symbolSize);
		sendOrder.add(paramsFragNumber);

		int fragNumber = 1;
		for (EncodingPacket sourcePacket : sourceBlockEncoder.sourcePacketsIterable()) {
			buildAndStorePacketFragment(fragNumber, sourcePacket);
			sendOrder.add(fragNumber);
			fragNumber++;
		}

		nextRepairFragmentNumber = fragNumber;
		nextRepairEsi = sourceBlockEncoder.numberOfSourceSymbols();

		LOG.info("splitting " + sourceBlockEncoder.numberOfSourceSymbols() + " source symbol(s) of " + symbolSize + " bytes");
	}

	private void buildAndStoreParamsFragment(int fragNumber, int dataLength, int symbolSize) {
		byte[] data = (dataLength + " " + symbolSize).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
		QRCodeEncodedFragment frag = new QRCodeEncodedFragment(this, fragNumber, null, data);
		fragments.put(fragNumber, frag);
	}

	private void buildAndStorePacketFragment(int fragNumber, EncodingPacket packet) {
		QRCodeEncodedFragment frag = new QRCodeEncodedFragment(this, fragNumber, packet, packet.asArray());
		fragments.put(fragNumber, frag);
	}

	/**
	 * computes the next fragment to transmit: round-robins through the FEC-params preamble and all source
	 * symbols, generating additional repair symbols on demand (up to QREncodeSetting.numRepairSymbols) once
	 * every source symbol has been sent at least once; returns null only before appendFragmentsFor is called.
	 */
	public QRCodeEncodedFragment nextFragmentToSend() {
		if (sendOrder.isEmpty()) {
			return null;
		}
		if (sendCursor >= sendOrder.size()) {
			growWithRepairSymbolIfBudgetAllows();
			if (sendCursor >= sendOrder.size()) {
				// budget exhausted, no new repair symbol was appended -- wrap back to the start
				sendCursor = 0;
			}
		}
		int fragNumber = sendOrder.get(sendCursor);
		sendCursor++;

		QRCodeEncodedFragment frag = fragments.get(fragNumber);
		frag.incrSentCount();
		return frag;
	}

	private void growWithRepairSymbolIfBudgetAllows() {
		if (nextRepairFragmentNumber - sourceBlockEncoder.numberOfSourceSymbols() - 1 >= qrEncodeSettings.getNumRepairSymbols()) {
			return; // already generated the configured number of repair symbols; keep cycling existing ones
		}
		EncodingPacket repairPacket = sourceBlockEncoder.repairPacket(nextRepairEsi);
		buildAndStorePacketFragment(nextRepairFragmentNumber, repairPacket);
		sendOrder.add(nextRepairFragmentNumber);
		nextRepairFragmentNumber++;
		nextRepairEsi++;
	}

	/** marks a fragment id as acknowledged by the decoder; it stays in fragments but display can skip it if desired */
	public void acknowledge(int fragmentNumber) {
		QRCodeEncodedFragment frag = fragments.get(fragmentNumber);
		if (frag != null) {
			frag.acknowledge();
		}
	}

	/** marks every fragment id strictly below fragmentNumber as acknowledged */
	public void acknowledgeUpTo(int fragmentNumber) {
		for (Map.Entry<Integer,QRCodeEncodedFragment> e : fragments.entrySet()) {
			if (e.getKey() < fragmentNumber) {
				e.getValue().acknowledge();
			}
		}
	}

	public boolean isFullyAcknowledged() {
		for (QRCodeEncodedFragment frag : fragments.values()) {
			if (!frag.isAcknowledge()) {
				return false;
			}
		}
		return !fragments.isEmpty();
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
