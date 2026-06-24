package fr.an.qrcode.channel.impl.decode;

import static org.junit.Assert.assertEquals;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
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
import fr.an.qrcode.channel.impl.encode.FragmentImg;
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
		encodeSettings.setComboCodes(new int[] { 2 });
		encodeSettings.setComboEmitEveryNFragments(1); // redundancy for every anchor, to guarantee coverage of the dropped one

		QRCodesEncoderChannel encoder = new QRCodesEncoderChannel(encodeSettings);
		encoder.appendFragmentsFor(originalText);

		List<FragmentImg> allFragmentImgs = encoder.getNextFragmentImgs();

		int totalPlainCount = encoder.getFragmentImgs().size();
		int droppedFragmentNumber = totalPlainCount / 2;

		Map<DecodeHintType, Object> decodeHints = new HashMap<>();
		decodeHints.put(DecodeHintType.CHARACTER_SET, "ISO-8859-1");
		QRCodeReader reader = new QRCodeReader();

		QRCodesDecoderChannel decoder = new QRCodesDecoderChannel(decodeHints, new NoopImageProvider(), e -> {});

		for (FragmentImg fragImg : allFragmentImgs) {
			boolean isPlainDroppedFragment = fragImg.owner.getCode() == 1 && fragImg.getFragmentNumber() == droppedFragmentNumber;
			if (isPlainDroppedFragment) {
				continue; // simulate this one QR code never being captured by the camera
			}

			String headerAndData = decodeImageToText(reader, fragImg.img, decodeHints);
			decoder.handleFragmentHeaderAndData(headerAndData);
		}

		assertEquals(originalText, decoder.getReadyText());
		assertEquals(totalPlainCount + 1, decoder.getNextSequenceNumber());
	}

	private String decodeImageToText(QRCodeReader reader, BufferedImage img, Map<DecodeHintType, Object> hints)
			throws NotFoundException, ChecksumException, FormatException {
		LuminanceSource source = new BufferedImageLuminanceSource(img);
		BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
		Result result = reader.decode(bitmap, hints);
		return result.getText();
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
