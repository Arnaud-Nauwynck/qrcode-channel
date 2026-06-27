package fr.an.qrcode.channel.impl.decode.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.google.zxing.DecodeHintType;

import fr.an.qrcode.channel.impl.QREncodeSetting;
import fr.an.qrcode.channel.impl.decode.QRResult;
import fr.an.qrcode.channel.impl.encode.QRCodesEncoderChannel;
import fr.an.qrcode.channel.impl.util.DimInt2D;

/**
 * real-pipeline test for RGB-channel-split decoding: composites 3 independent QR codes into one
 * R/G/B image (as the encoder would), then runs the actual OpenCV channel-split + ZXing per-channel
 * decode used by the decoder, and checks all 3 payloads come back -- exercising the genuine
 * channel-crosstalk-free case (synthetic composite, not a real camera capture).
 */
public class RgbSplitQRStreamFromImageStreamCallbackTest {

	private List<QRCapturedEvent> capturedEvents;
	private RgbSplitQRStreamFromImageStreamCallback sut;

	@Before
	public void setUp() {
		Map<DecodeHintType, Object> qrHints = new HashMap<>();
		qrHints.put(DecodeHintType.CHARACTER_SET, "ISO-8859-1");
		capturedEvents = new ArrayList<>();
		sut = new RgbSplitQRStreamFromImageStreamCallback(new QRStreamCallback() {
			@Override
			public void onQRCaptured(QRCapturedEvent event) {
				capturedEvents.add(event);
			}
		}, qrHints);
	}

	@Test
	public void testDecodesAllThreeChannels() {
		QRCodesEncoderChannel encoder = new QRCodesEncoderChannel(new QREncodeSetting());

		String redPayload = "RED-CHANNEL-PAYLOAD";
		String greenPayload = "GREEN-CHANNEL-PAYLOAD";
		String bluePayload = "BLUE-CHANNEL-PAYLOAD";

		BufferedImage composite = encoder.encodeAndRenderRgbSplit(
				redPayload.getBytes(StandardCharsets.ISO_8859_1),
				greenPayload.getBytes(StandardCharsets.ISO_8859_1),
				bluePayload.getBytes(StandardCharsets.ISO_8859_1));
		composite = withWhiteBorder(composite, 20);

		sut.onStart(new DimInt2D(composite.getWidth(), composite.getHeight()));
		sut.onImage(composite, 0, 0);

		assertEquals(1, capturedEvents.size());
		QRCapturedEvent event = capturedEvents.get(0);

		List<String> decodedTexts = new ArrayList<>();
		for (QRResult r : event.qrResults) {
			decodedTexts.add(r.text);
		}

		assertTrue("expected RED payload decoded, got: " + decodedTexts, decodedTexts.contains(redPayload));
		assertTrue("expected GREEN payload decoded, got: " + decodedTexts, decodedTexts.contains(greenPayload));
		assertTrue("expected BLUE payload decoded, got: " + decodedTexts, decodedTexts.contains(bluePayload));
	}

	private static BufferedImage withWhiteBorder(BufferedImage src, int border) {
		BufferedImage bordered = new BufferedImage(src.getWidth() + 2 * border, src.getHeight() + 2 * border, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = bordered.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, bordered.getWidth(), bordered.getHeight());
		g.drawImage(src, border, border, null);
		g.dispose();
		return bordered;
	}

}
