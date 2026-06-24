package fr.an.qrcode.channel.impl.encode;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
		byte[] payload = new byte[256];
		for (int i = 0; i < 256; i++) {
			payload[i] = (byte) i;
		}

		BufferedImage img = sut.encodeAndRender(payload);

		LuminanceSource source = new BufferedImageLuminanceSource(img);
		BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
		Map<DecodeHintType, Object> decodeHints = new HashMap<>();
		decodeHints.put(DecodeHintType.CHARACTER_SET, "ISO-8859-1");

		QRCodeReader reader = new QRCodeReader();
		com.google.zxing.Result result = reader.decode(bitmap, decodeHints);
		byte[] decodedPayload = result.getText().getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);

		assertArrayEquals(payload, decodedPayload);
	}

	private static final Pattern HEADER_PATTERN = Pattern.compile("([0-9]+) ([0-9]+) ([0-9]+) ([0-9]+)");

	@Test
	public void testHeaderFormat_plainFragment() {
		QRCodesEncoderChannel encoder = new QRCodesEncoderChannel(new QREncodeSetting());
		encoder.appendFragmentsFor(randomASCII(50));

		Map<Integer, FragmentImg> imgs = encoder.getFragmentImgs();
		FragmentImg frag1 = imgs.get(1);
		Matcher m = HEADER_PATTERN.matcher(frag1.owner.getHeader());
		assertTrue(m.matches());
		assertEquals(1, Integer.parseInt(m.group(1)));
		assertEquals(1, Integer.parseInt(m.group(2)));
		assertEquals(frag1.owner.getData().length, Integer.parseInt(m.group(3)));
		assertEquals(QRCodecChannelUtils.crc32(frag1.owner.getData()), Long.parseLong(m.group(4)));
	}

	@Test
	public void testComboScheduling_disabledByDefault() {
		QREncodeSetting settings = new QREncodeSetting();
		QRCodesEncoderChannel encoder = new QRCodesEncoderChannel(settings);
		encoder.appendFragmentsFor(randomASCII(2000));

		assertTrue(encoder.getComboFragments().isEmpty());
	}

	@Test
	public void testComboScheduling_enabled_basicCode2() {
		QREncodeSetting settings = new QREncodeSetting();
		settings.setComboRedundancyEnabled(true);
		settings.setComboCodes(new int[] { 2 });
		settings.setComboEmitEveryNFragments(2);
		QRCodesEncoderChannel encoder = new QRCodesEncoderChannel(settings);
		encoder.appendFragmentsFor(randomASCII(2000));

		assertFalse(encoder.getComboFragments().isEmpty());
		Map<Integer, FragmentImg> plainImgs = encoder.getFragmentImgs();
		for (QRCodeEncodedFragment combo : encoder.getComboFragments()) {
			assertEquals(2, combo.getCode());
			byte[] dataA = plainImgs.get(combo.getFragmentNumber() - 1).owner.getData();
			byte[] dataB = plainImgs.get(combo.getFragmentNumber()).owner.getData();
			int expectedLen = ByteArrayXorUtils.maxLength(java.util.Arrays.asList(dataA, dataB));
			byte[] expectedXor = ByteArrayXorUtils.xorWithPadding(dataA, dataB, expectedLen);
			assertArrayEquals(expectedXor, combo.getData());
		}
	}

	@Test
	public void testComboScheduling_skipsWhenNotEnoughHistory() {
		QREncodeSetting settings = new QREncodeSetting();
		settings.setComboRedundancyEnabled(true);
		settings.setComboCodes(new int[] { 3 });
		settings.setComboEmitEveryNFragments(1);
		QRCodesEncoderChannel encoder = new QRCodesEncoderChannel(settings);
		encoder.appendFragmentsFor(randomASCII(2000));

		for (QRCodeEncodedFragment combo : encoder.getComboFragments()) {
			assertTrue("code=3 combo must never anchor below id 3", combo.getFragmentNumber() >= 3);
		}
	}

	@Test
	public void testComboScheduling_skipsBeyondMessageEnd() {
		QREncodeSetting settings = new QREncodeSetting();
		settings.setComboRedundancyEnabled(true);
		settings.setComboCodes(new int[] { 2, 3, 4, 5, 6, 7, 8 });
		settings.setComboEmitEveryNFragments(1);
		QRCodesEncoderChannel encoder = new QRCodesEncoderChannel(settings);
		encoder.appendFragmentsFor(randomASCII(50)); // short message -> only 3 plain fragments

		int totalPlainCount = encoder.getFragmentImgs().size();
		assertTrue(totalPlainCount < 8);
		for (QRCodeEncodedFragment combo : encoder.getComboFragments()) {
			assertTrue("combo must never reference ids beyond the message", combo.getFragmentNumber() <= totalPlainCount);
			assertTrue("combo must never require fragments below id 1", combo.getFragmentNumber() - combo.getCode() + 1 >= 1);
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
