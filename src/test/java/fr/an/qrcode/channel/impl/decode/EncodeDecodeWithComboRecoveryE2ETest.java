package fr.an.qrcode.channel.impl.decode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.DecodeHintType;
import com.google.zxing.FormatException;
import com.google.zxing.LuminanceSource;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import fr.an.qrcode.channel.impl.QREncodeSetting;
import fr.an.qrcode.channel.impl.decode.input.ImageProvider;
import fr.an.qrcode.channel.impl.encode.QRCodeEncodedFragment;
import fr.an.qrcode.channel.impl.encode.QRCodesEncoderChannel;
import fr.an.qrcode.channel.impl.util.DimInt2D;

/**
 * full pipeline smoke test: real QR images rendered by the encoder, decoded back via ZXing,
 * with one plain fragment intentionally dropped to exercise the actual combo XOR recovery path.
 */
public class EncodeDecodeWithComboRecoveryE2ETest {

	@Test
	public void testFullPipeline_recoversDroppedFragmentViaCombo() throws Exception {
		String originalText = buildRepeatingText(3000);

		QREncodeSetting encodeSettings = new QREncodeSetting();
		encodeSettings.setComboRedundancyEnabled(true);
		encodeSettings.setComboGroupSizes(new int[] { 1, 2 }); // alternate plain sends (to bootstrap the decoder) with 2-way combos

		QRCodesEncoderChannel encoder = new QRCodesEncoderChannel(encodeSettings);
		encoder.appendFragmentsFor(originalText);

		int totalPlainCount = encoder.getFragmentImgs().size();
		int droppedFragmentNumber = totalPlainCount / 2;

		Map<DecodeHintType, Object> decodeHints = new HashMap<>();
		decodeHints.put(DecodeHintType.CHARACTER_SET, "ISO-8859-1");
		QRCodeReader reader = new QRCodeReader();

		QRCodesDecoderChannel decoder = new QRCodesDecoderChannel(decodeHints, new NoopImageProvider(), e -> {});

		// every pending plain fragment id needs to be covered by at least one combo it's part of; cycling
		// through several full round-robin passes is enough for every id to be sent plain or anchor a combo
		int maxSends = totalPlainCount * 4;
		for (int i = 0; i < maxSends && !encoder.isFullyAcknowledged(); i++) {
			QRCodeEncodedFragment frag = encoder.nextFragmentToSend();
			if (frag == null) {
				break;
			}

			boolean containsDroppedFragment = false;
			for (int id : frag.getIds()) {
				if (id == droppedFragmentNumber) {
					containsDroppedFragment = true;
				}
			}
			boolean isPlainDroppedFragment = frag.isPlain() && containsDroppedFragment;
			if (isPlainDroppedFragment) {
				continue; // simulate this one QR code never being captured by the camera
			}

			String headerAndData = decodeImageToText(reader, frag.getImg(), decodeHints);
			decoder.handleFragmentHeaderAndData(headerAndData);

			// the human-in-the-loop ack channel reports back fragments the decoder has fully reassembled
			encoder.acknowledgeUpTo(decoder.getNextSequenceNumber());
		}

		assertEquals(originalText, decoder.getReadyText());
		assertEquals(totalPlainCount + 1, decoder.getNextSequenceNumber());
		assertTrue(encoder.isFullyAcknowledged());
	}

	private String decodeImageToText(QRCodeReader reader, BufferedImage img, Map<DecodeHintType, Object> hints)
			throws NotFoundException, ChecksumException, FormatException {
		LuminanceSource source = new BufferedImageLuminanceSource(withWhiteBorder(img, 20));
		BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
		Result result = reader.decode(bitmap, hints);
		return result.getText();
	}

	/** real captures (screenshot/webcam) always have a quiet zone around the QR code; a raw render with zero margin can fail ZXing's detector */
	private static BufferedImage withWhiteBorder(BufferedImage src, int border) {
		BufferedImage bordered = new BufferedImage(src.getWidth() + 2 * border, src.getHeight() + 2 * border, BufferedImage.TYPE_INT_RGB);
		java.awt.Graphics2D g = bordered.createGraphics();
		g.setColor(java.awt.Color.WHITE);
		g.fillRect(0, 0, bordered.getWidth(), bordered.getHeight());
		g.drawImage(src, border, border, null);
		g.dispose();
		return bordered;
	}

	private String buildRepeatingText(int len) {
		StringBuilder sb = new StringBuilder(len);
		String unit = "The quick brown fox jumps over the lazy dog. ";
		while (sb.length() < len) {
			sb.append(unit);
		}
		return sb.substring(0, len);
	}

	private static class NoopImageProvider extends ImageProvider {
		@Override public DimInt2D getSize() { return new DimInt2D(1, 1); }
		@Override public void open() { }
		@Override public void close() { }
		@Override public BufferedImage captureImage() { return null; }
		@Override public void parseRecordParamsText(String recordParamsText) { }
	}

}
