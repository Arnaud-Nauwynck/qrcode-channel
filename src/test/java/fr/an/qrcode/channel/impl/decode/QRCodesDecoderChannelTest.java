package fr.an.qrcode.channel.impl.decode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.Before;
import org.junit.Test;

import com.google.zxing.DecodeHintType;

import fr.an.qrcode.channel.impl.QRCodecChannelUtils;
import fr.an.qrcode.channel.impl.util.ByteArrayXorUtils;
import fr.an.qrcode.channel.impl.util.DimInt2D;
import fr.an.qrcode.channel.impl.decode.input.ImageProvider;

public class QRCodesDecoderChannelTest {

	private QRCodesDecoderChannel sut;

	@Before
	public void setUp() {
		Map<DecodeHintType, Object> qrHints = new HashMap<>();
		sut = new QRCodesDecoderChannel(qrHints, new NoopImageProvider(), e -> {});
	}

	private String header(int id, int code, byte[] data) {
		long crc32 = QRCodecChannelUtils.crc32(data);
		return id + " " + code + " " + data.length + " " + crc32 + "\n";
	}

	private String plainPacket(int id, byte[] data) {
		return header(id, 1, data) + new String(data, StandardCharsets.ISO_8859_1);
	}

	private String comboPacket(int id, int code, byte[] xorData) {
		return header(id, code, xorData) + new String(xorData, StandardCharsets.ISO_8859_1);
	}

	private byte[] randomBytes(Random rand, int len) {
		byte[] res = new byte[len];
		rand.nextBytes(res);
		return res;
	}

	@Test
	public void testPlainFragmentsInOrder() {
		byte[] d1 = "hello ".getBytes(StandardCharsets.UTF_8);
		byte[] d2 = "world".getBytes(StandardCharsets.UTF_8);

		sut.handleFragmentHeaderAndData(plainPacket(1, d1));
		sut.handleFragmentHeaderAndData(plainPacket(2, d2));

		assertEquals("hello world", sut.getReadyText());
		assertEquals(3, sut.getNextSequenceNumber());
	}

	@Test
	public void testPlainFragmentsOutOfOrder_usesAheadFragments() {
		byte[] d1 = "AAA".getBytes(StandardCharsets.UTF_8);
		byte[] d2 = "BBB".getBytes(StandardCharsets.UTF_8);
		byte[] d3 = "CCC".getBytes(StandardCharsets.UTF_8);

		sut.handleFragmentHeaderAndData(plainPacket(3, d3));
		sut.handleFragmentHeaderAndData(plainPacket(2, d2));
		assertEquals("", sut.getReadyText());

		sut.handleFragmentHeaderAndData(plainPacket(1, d1));

		assertEquals("AAABBBCCC", sut.getReadyText());
		assertEquals(4, sut.getNextSequenceNumber());
	}

	@Test
	public void testComboRecovery_singleMissingFragment_code2() {
		Random rand = new Random(7);
		byte[] d1 = randomBytes(rand, 10);
		byte[] d2 = randomBytes(rand, 10);
		byte[] d3 = randomBytes(rand, 10);

		int maxLen = ByteArrayXorUtils.maxLength(java.util.Arrays.asList(d1, d2));
		byte[] combo12 = ByteArrayXorUtils.xorWithPadding(d1, d2, maxLen);

		sut.handleFragmentHeaderAndData(plainPacket(1, d1));
		// fragment 2 lost in transmission
		sut.handleFragmentHeaderAndData(comboPacket(2, 2, combo12));
		sut.handleFragmentHeaderAndData(plainPacket(3, d3));

		byte[] expected = concat(d1, d2, d3);
		assertEquals(new String(expected, StandardCharsets.UTF_8), sut.getReadyText());
		assertEquals(4, sut.getNextSequenceNumber());
	}

	@Test
	public void testComboRecovery_variousCodeValues() {
		for (int code = 2; code <= 8; code++) {
			for (int dropPos = 0; dropPos < code; dropPos++) {
				setUp();
				Random rand = new Random(100 + code * 10 + dropPos);
				byte[][] frags = new byte[code][];
				for (int i = 0; i < code; i++) {
					frags[i] = randomBytes(rand, 8);
				}
				int maxLen = ByteArrayXorUtils.maxLength(java.util.Arrays.asList(frags));
				byte[] combo = ByteArrayXorUtils.xorWithPadding(java.util.Arrays.asList(frags), maxLen);

				for (int i = 0; i < code; i++) {
					if (i != dropPos) {
						sut.handleFragmentHeaderAndData(plainPacket(i + 1, frags[i]));
					}
				}
				sut.handleFragmentHeaderAndData(comboPacket(code, code, combo));

				byte[] expected = concat(frags);
				assertEquals("code=" + code + " dropPos=" + dropPos,
						new String(expected, StandardCharsets.UTF_8), sut.getReadyText());
			}
		}
	}

	@Test
	public void testComboRecovery_multipleMissing_failsGracefully() {
		Random rand = new Random(55);
		byte[] d1 = randomBytes(rand, 6);
		byte[] d2 = randomBytes(rand, 6);
		byte[] d3 = randomBytes(rand, 6);

		int maxLen = ByteArrayXorUtils.maxLength(java.util.Arrays.asList(d1, d2, d3));
		byte[] combo = ByteArrayXorUtils.xorWithPadding(java.util.Arrays.asList(d1, d2, d3), maxLen);

		// fragments 1 and 2 both lost, only the combo arrives
		sut.handleFragmentHeaderAndData(comboPacket(3, 3, combo));

		assertEquals("", sut.getReadyText());
		assertEquals(1, sut.getNextSequenceNumber());
	}

	@Test
	public void testCrc32Mismatch_corruptedCombo_dropped() {
		byte[] data = "somedata".getBytes(StandardCharsets.UTF_8);
		String badHeader = "5 2 " + data.length + " 999999\n";
		sut.handleFragmentHeaderAndData(badHeader + new String(data, StandardCharsets.ISO_8859_1));

		assertEquals("", sut.getReadyText());
		assertEquals(1, sut.getNextSequenceNumber());
	}

	@Test
	public void testComboCache_cleanupAfterConsumption() {
		byte[] d1 = "xx".getBytes(StandardCharsets.UTF_8);
		byte[] d2 = "yy".getBytes(StandardCharsets.UTF_8);
		int maxLen = ByteArrayXorUtils.maxLength(java.util.Arrays.asList(d1, d2));
		byte[] combo = ByteArrayXorUtils.xorWithPadding(d1, d2, maxLen);

		// combo never actually needed: both plain fragments arrive normally
		sut.handleFragmentHeaderAndData(comboPacket(2, 2, combo));
		sut.handleFragmentHeaderAndData(plainPacket(1, d1));
		sut.handleFragmentHeaderAndData(plainPacket(2, d2));

		assertEquals("xxyy", sut.getReadyText());
		assertFalse("combo cache should not grow unbounded once its range is consumed", sut.getComboCacheSize() > 0);
	}

	private byte[] concat(byte[]... arrays) {
		int total = 0;
		for (byte[] a : arrays) total += a.length;
		byte[] res = new byte[total];
		int pos = 0;
		for (byte[] a : arrays) {
			System.arraycopy(a, 0, res, pos, a.length);
			pos += a.length;
		}
		return res;
	}

	private static class NoopImageProvider extends ImageProvider {
		@Override public DimInt2D getSize() { return new DimInt2D(1, 1); }
		@Override public void open() { }
		@Override public void close() { }
		@Override public BufferedImage captureImage() { return null; }
		@Override public void parseRecordParamsText(String recordParamsText) { }
	}

}
