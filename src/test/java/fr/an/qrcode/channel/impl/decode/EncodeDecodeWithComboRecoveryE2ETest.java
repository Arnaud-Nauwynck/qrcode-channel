package fr.an.qrcode.channel.impl.decode;

import static org.junit.Assert.assertEquals;

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
 * full pipeline smoke test: real QR images rendered by the encoder, decoded back via ZXing, with some
 * source symbols intentionally dropped to exercise RaptorQ's actual repair-symbol recovery path.
 */
public class EncodeDecodeWithComboRecoveryE2ETest {

	@Test
	public void testFullPipeline_recoversDroppedSourceSymbolsViaRepairSymbols() throws Exception {
		String originalText = buildRepeatingText(3000);

		QREncodeSetting encodeSettings = new QREncodeSetting();
		encodeSettings.setNumRepairSymbols(30);

		QRCodesEncoderChannel encoder = new QRCodesEncoderChannel(encodeSettings);
		encoder.appendFragmentsFor(originalText);

		Map<DecodeHintType, Object> decodeHints = new HashMap<>();
		decodeHints.put(DecodeHintType.CHARACTER_SET, "ISO-8859-1");
		QRCodeReader reader = new QRCodeReader();

		QRCodesDecoderChannel decoder = new QRCodesDecoderChannel(decodeHints, new NoopImageProvider(), e -> {});
		decoder.setSymbolSizeHint(encodeSettings.getSymbolSize());

		int totalFragments = encoder.getFragmentImgs().size();

		// drop every 4th source symbol (never the params preamble, fragment 0) to simulate QR codes
		// the camera failed to capture; repair symbols sent afterwards should make up for the loss
		int maxSends = totalFragments + encodeSettings.getNumRepairSymbols();
		for (int i = 0; i < maxSends && !decoder.getReadyText().equals(originalText); i++) {
			QRCodeEncodedFragment frag = encoder.nextFragmentToSend();
			if (frag == null) {
				break;
			}

			boolean dropThisOne = frag.getFragmentNumber() > 0 && (frag.getFragmentNumber() % 4 == 0) && i < totalFragments;
			if (dropThisOne) {
				continue; // simulate this one QR code never being captured by the camera
			}

			String headerAndData = decodeImageToText(reader, frag.getImg(), decodeHints);
			decoder.handleFragmentHeaderAndData(headerAndData);
		}

		assertEquals(originalText, decoder.getReadyText());
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
