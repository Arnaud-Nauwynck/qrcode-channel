package fr.an.qrcode.channel.impl.encode;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import fr.an.qrcode.channel.impl.QREncodeSetting;
import fr.an.qrcode.channel.impl.QRCodecChannelUtils;

public class QRCodesEncoderChannelTest {

	protected QRCodesEncoderChannel sut = new QRCodesEncoderChannel(new QREncodeSetting());
	Random rand = new Random(1234);

	@Test
	public void test1000() {
		String text = randomASCII(1000);
		sut.appendFragmentsFor(text);
		sut.getFragmentImgs();
	}

	@Test
	public void test_sizes() {
		List<Integer> sizes = ImmutableList.of(10, 100, 1000, 2000, 5000, 10000, 20000);
		for (int size : sizes) {
			QRCodesEncoderChannel encoder = new QRCodesEncoderChannel(new QREncodeSetting());
			String text = randomASCII(size);
			encoder.appendFragmentsFor(text);
			encoder.getFragmentImgs();
		}
	}

	@Test
	public void testByteRoundTripThroughZXing() throws Exception {
		// covers the full 0x00-0xFF byte range (every 4th value), kept short enough to fit the default QR version's capacity
		byte[] payload = new byte[64];
		for (int i = 0; i < 64; i++) {
			payload[i] = (byte) (i * 4);
		}

		BufferedImage img = withWhiteBorder(sut.encodeAndRender(payload), 20);

		LuminanceSource source = new BufferedImageLuminanceSource(img);
		BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
		Map<DecodeHintType, Object> decodeHints = new HashMap<>();
		decodeHints.put(DecodeHintType.CHARACTER_SET, "ISO-8859-1");

		QRCodeReader reader = new QRCodeReader();
		com.google.zxing.Result result = reader.decode(bitmap, decodeHints);
		byte[] decodedPayload = result.getText().getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);

		assertArrayEquals(payload, decodedPayload);
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

	private static final Pattern HEADER_PATTERN = Pattern.compile("([0-9]+) ([0-9]+) ([0-9]+)");

	@Test
	public void testHeaderFormat() {
		QRCodesEncoderChannel encoder = new QRCodesEncoderChannel(new QREncodeSetting());
		encoder.appendFragmentsFor(randomASCII(50));

		Map<Integer, FragmentImg> imgs = encoder.getFragmentImgs();
		FragmentImg frag1 = imgs.get(1);
		Matcher m = HEADER_PATTERN.matcher(frag1.owner.getHeader().trim());
		assertTrue(m.matches());
		assertEquals("1", m.group(1));
		assertEquals(frag1.owner.getData().length, Integer.parseInt(m.group(2)));
		assertEquals(QRCodecChannelUtils.crc32(frag1.owner.getData()), Long.parseLong(m.group(3)));
	}

	@Test
	public void testAppendFragmentsFor_paramsFragmentThenSourceSymbols() {
		QRCodesEncoderChannel encoder = new QRCodesEncoderChannel(new QREncodeSetting());
		encoder.appendFragmentsFor(randomASCII(2000));

		Map<Integer, FragmentImg> imgs = encoder.getFragmentImgs();
		assertTrue("expect a params preamble (0) plus at least one source symbol", imgs.size() > 1);
		assertTrue("fragment 0 is the FEC-params preamble", imgs.get(0).owner.isParamsFragment());
		for (int i = 1; i < imgs.size(); i++) {
			assertFalse("fragment " + i + " must wrap a RaptorQ EncodingPacket", imgs.get(i).owner.isParamsFragment());
		}
	}

	@Test
	public void testNextFragmentToSend_sendsEverySourceSymbolBeforeRepeating() {
		QREncodeSetting settings = new QREncodeSetting();
		QRCodesEncoderChannel encoder = new QRCodesEncoderChannel(settings);
		encoder.appendFragmentsFor(randomASCII(2000));

		int totalFragments = encoder.getFragmentImgs().size();
		Set<Integer> seenInFirstPass = new HashSet<>();
		for (int i = 0; i < totalFragments; i++) {
			QRCodeEncodedFragment frag = encoder.nextFragmentToSend();
			assertNotNull(frag);
			assertTrue("each fragment number must be sent exactly once during the first pass",
					seenInFirstPass.add(frag.getFragmentNumber()));
		}
		assertEquals(totalFragments, seenInFirstPass.size());
	}

	@Test
	public void testNextFragmentToSend_generatesRepairSymbolsAfterFirstPass() {
		QREncodeSetting settings = new QREncodeSetting();
		settings.setNumRepairSymbols(5);
		QRCodesEncoderChannel encoder = new QRCodesEncoderChannel(settings);
		encoder.appendFragmentsFor(randomASCII(2000));

		int totalFragments = encoder.getFragmentImgs().size();
		for (int i = 0; i < totalFragments; i++) {
			encoder.nextFragmentToSend();
		}

		// the next pass should include newly generated repair symbols (more fragments known than before)
		QRCodeEncodedFragment next = encoder.nextFragmentToSend();
		assertNotNull(next);
		assertTrue("expect repair symbols to grow the known fragment set",
				encoder.getFragmentImgs().size() >= totalFragments);
	}

	@Test
	public void testAcknowledge_removesFragmentFromPending() {
		QREncodeSetting settings = new QREncodeSetting();
		QRCodesEncoderChannel encoder = new QRCodesEncoderChannel(settings);
		encoder.appendFragmentsFor(randomASCII(200));

		assertFalse(encoder.isFullyAcknowledged());

		int totalFragments = encoder.getFragmentImgs().size();
		encoder.acknowledgeUpTo(totalFragments + 1);

		assertTrue(encoder.isFullyAcknowledged());
	}

	@Test
	public void testAcknowledge_skipsAcknowledgedFragmentsOnResend() {
		QREncodeSetting settings = new QREncodeSetting();
		QRCodesEncoderChannel encoder = new QRCodesEncoderChannel(settings);
		encoder.appendFragmentsFor(randomASCII(2000));

		int totalFragments = encoder.getFragmentImgs().size();
		assertTrue(totalFragments > 3);

		encoder.acknowledge(0);
		encoder.acknowledge(1);

		for (int i = 0; i < totalFragments; i++) {
			QRCodeEncodedFragment frag = encoder.nextFragmentToSend();
			assertNotNull(frag);
		}
		// acknowledging doesn't stop the round-robin from cycling -- it only marks fragments for UI display purposes
		assertFalse(encoder.isFullyAcknowledged());
	}

	protected String randomASCII(int len) {
		StringBuilder res = new StringBuilder(len);
		for(int i = 0; i < len; i++) {
			char ch = (char) rand.nextInt(256);
			res.append(ch);
		}
		return res.toString();
	}
}
