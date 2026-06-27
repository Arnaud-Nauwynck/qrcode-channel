package fr.an.qrcode.channel.impl.encode;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
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
import fr.an.qrcode.channel.impl.util.ByteArrayXorUtils;

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

	private static final Pattern HEADER_PATTERN = Pattern.compile("([0-9 ]+) ([0-9]+) ([0-9]+) ([0-9]+)");

	@Test
	public void testHeaderFormat_plainFragment() {
		QRCodesEncoderChannel encoder = new QRCodesEncoderChannel(new QREncodeSetting());
		encoder.appendFragmentsFor(randomASCII(50));

		Map<Integer, FragmentImg> imgs = encoder.getFragmentImgs();
		FragmentImg frag1 = imgs.get(1);
		Matcher m = HEADER_PATTERN.matcher(frag1.owner.getHeader().trim());
		assertTrue(m.matches());
		assertEquals("1", m.group(1));
		assertEquals(1, Integer.parseInt(m.group(2)));
		assertEquals(frag1.owner.getData().length, Integer.parseInt(m.group(3)));
		assertEquals(QRCodecChannelUtils.crc32(frag1.owner.getData()), Long.parseLong(m.group(4)));
	}

	@Test
	public void testNextFragmentToSend_plainOnlyWhenRedundancyDisabled() {
		QREncodeSetting settings = new QREncodeSetting();
		QRCodesEncoderChannel encoder = new QRCodesEncoderChannel(settings);
		encoder.appendFragmentsFor(randomASCII(200));

		int totalPlainCount = encoder.getFragmentImgs().size();
		for (int i = 0; i < totalPlainCount; i++) {
			QRCodeEncodedFragment frag = encoder.nextFragmentToSend();
			assertTrue("redundancy disabled -> every send must be plain (code=1)", frag.isPlain());
		}
	}

	@Test
	public void testNextFragmentToSend_cyclesComboGroupSizes() {
		QREncodeSetting settings = new QREncodeSetting();
		settings.setComboRedundancyEnabled(true);
		settings.setComboGroupSizes(new int[] { 1, 2, 3 });
		QRCodesEncoderChannel encoder = new QRCodesEncoderChannel(settings);
		encoder.appendFragmentsFor(randomASCII(2000));

		int[] codesSeen = new int[4]; // index 1..3
		for (int i = 0; i < 30; i++) {
			QRCodeEncodedFragment frag = encoder.nextFragmentToSend();
			assertTrue(frag.getCode() >= 1 && frag.getCode() <= 3);
			codesSeen[frag.getCode()]++;

			int[] ids = frag.getIds();
			for (int j = 1; j < ids.length; j++) {
				assertTrue("ids must be ascending and distinct", ids[j] > ids[j - 1]);
			}

			byte[] dataB = fragmentDataFor(encoder, ids);
			assertArrayEquals(dataB, frag.getData());
		}
		assertTrue("expected to see plain (code=1) sends", codesSeen[1] > 0);
		assertTrue("expected to see 2-way combos", codesSeen[2] > 0);
		assertTrue("expected to see 3-way combos", codesSeen[3] > 0);
	}

	@Test
	public void testNextFragmentToSend_comboFrequencySchedule() {
		QREncodeSetting settings = new QREncodeSetting();
		settings.setComboFrequencyEnabled(true);
		settings.setXor2Frequency(2);
		settings.setXor3Frequency(7);
		QRCodesEncoderChannel encoder = new QRCodesEncoderChannel(settings);
		encoder.appendFragmentsFor(randomASCII(2000)); // -> 10 plain fragments

		assertEquals(10, encoder.getFragmentImgs().size());

		// xor2 due every 2 sends (own cursor, advancing by group size: (1,2), then (3,4), ...);
		// xor3 due every 7 sends (own cursor, advancing by group size: (1,2,3), then (4,5,6), ...)
		String[] expected = {
				"single(1)",
				"single(2)",
				"xor2(1,2)",
				"single(3)",
				"single(4)",
				"xor2(3,4)",
				"xor3(1,2,3)",
				"single(5)",
				"single(6)",
				"xor2(5,6)",
		};

		for (String expectedLabel : expected) {
			QRCodeEncodedFragment frag = encoder.nextFragmentToSend();
			assertEquals(expectedLabel, labelFor(frag));
		}
	}

	private static String labelFor(QRCodeEncodedFragment frag) {
		int[] ids = frag.getIds();
		String idsCsv = java.util.stream.IntStream.of(ids).mapToObj(Integer::toString).collect(java.util.stream.Collectors.joining(","));
		switch (frag.getCode()) {
			case 1: return "single(" + idsCsv + ")";
			case 2: return "xor2(" + idsCsv + ")";
			case 3: return "xor3(" + idsCsv + ")";
			default: return "code" + frag.getCode() + "(" + idsCsv + ")";
		}
	}

	private byte[] fragmentDataFor(QRCodesEncoderChannel encoder, int[] ids) {
		Map<Integer, FragmentImg> plainImgs = encoder.getFragmentImgs();
		java.util.List<byte[]> sourceBytes = new java.util.ArrayList<>();
		for (int id : ids) {
			sourceBytes.add(plainImgs.get(id).owner.getData());
		}
		int expectedLen = ByteArrayXorUtils.maxLength(sourceBytes);
		return ByteArrayXorUtils.xorWithPadding(sourceBytes, expectedLen);
	}

	@Test
	public void testAcknowledge_removesFragmentFromPending() {
		QREncodeSetting settings = new QREncodeSetting();
		QRCodesEncoderChannel encoder = new QRCodesEncoderChannel(settings);
		encoder.appendFragmentsFor(randomASCII(200));

		int totalPlainCount = encoder.getFragmentImgs().size();
		assertFalse(encoder.isFullyAcknowledged());

		encoder.acknowledgeUpTo(totalPlainCount + 1);

		assertTrue(encoder.isFullyAcknowledged());
		assertNull("fully acknowledged -> nothing left to send", encoder.nextFragmentToSend());
	}

	@Test
	public void testAcknowledge_skipsAcknowledgedFragments() {
		QREncodeSetting settings = new QREncodeSetting();
		QRCodesEncoderChannel encoder = new QRCodesEncoderChannel(settings);
		encoder.appendFragmentsFor(randomASCII(2000));

		int totalPlainCount = encoder.getFragmentImgs().size();
		assertTrue(totalPlainCount > 3);

		encoder.acknowledge(1);
		encoder.acknowledge(2);

		for (int i = 0; i < totalPlainCount * 2; i++) {
			QRCodeEncodedFragment frag = encoder.nextFragmentToSend();
			assertNotNull(frag);
			for (int id : frag.getIds()) {
				assertTrue("acknowledged ids must never be resent", id != 1 && id != 2);
			}
		}
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
