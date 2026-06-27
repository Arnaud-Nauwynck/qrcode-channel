package fr.an.qrcode.channel.impl.decode;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.junit.Before;
import org.junit.Test;

import com.google.zxing.DecodeHintType;

import fr.an.qrcode.channel.impl.QRCodecChannelUtils;
import fr.an.qrcode.channel.impl.decode.input.ImageProvider;
import fr.an.qrcode.channel.impl.util.ByteArrayXorUtils;
import fr.an.qrcode.channel.impl.util.DimInt2D;

public class QRCodesDecoderChannelTest {

	private QRCodesDecoderChannel sut;

	@Before
	public void setUp() {
		Map<DecodeHintType, Object> qrHints = new HashMap<>();
		sut = new QRCodesDecoderChannel(qrHints, new NoopImageProvider(), e -> {});
	}

	/** header shape: "<id1> [<id2>] [<id3>] <code> <len> <crc32>\n" -- code is the count of leading (ascending) ids */
	private String header(byte[] data, int... ids) {
		long crc32 = QRCodecChannelUtils.crc32(data);
		StringBuilder sb = new StringBuilder();
		for (int id : ids) {
			sb.append(id).append(' ');
		}
		sb.append(ids.length).append(' ').append(data.length).append(' ').append(crc32).append('\n');
		return sb.toString();
	}

	private String packet(byte[] data, int... ids) {
		return header(data, ids) + new String(data, StandardCharsets.ISO_8859_1);
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

		sut.handleFragmentHeaderAndData(packet(d1, 1));
		sut.handleFragmentHeaderAndData(packet(d2, 2));

		assertEquals("hello world", sut.getReadyText());
		assertEquals(3, sut.getNextSequenceNumber());
	}

	@Test
	public void testPlainFragmentsOutOfOrder_usesAheadFragments() {
		byte[] d1 = "AAA".getBytes(StandardCharsets.UTF_8);
		byte[] d2 = "BBB".getBytes(StandardCharsets.UTF_8);
		byte[] d3 = "CCC".getBytes(StandardCharsets.UTF_8);

		sut.handleFragmentHeaderAndData(packet(d3, 3));
		sut.handleFragmentHeaderAndData(packet(d2, 2));
		assertEquals("", sut.getReadyText());

		sut.handleFragmentHeaderAndData(packet(d1, 1));

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

		sut.handleFragmentHeaderAndData(packet(d1, 1));
		// fragment 2 lost in transmission
		sut.handleFragmentHeaderAndData(packet(combo12, 1, 2));
		sut.handleFragmentHeaderAndData(packet(d3, 3));

		byte[] expected = concat(d1, d2, d3);
		assertArrayEquals(expected, sut.getReadyBytes());
		assertEquals(4, sut.getNextSequenceNumber());
	}

	@Test
	public void testComboRecovery_variousCodeValues() {
		for (int code = 2; code <= 3; code++) {
			for (int dropPos = 0; dropPos < code; dropPos++) {
				setUp();
				Random rand = new Random(100 + code * 10 + dropPos);
				byte[][] frags = new byte[code][];
				int[] ids = new int[code];
				for (int i = 0; i < code; i++) {
					frags[i] = randomBytes(rand, 8);
					ids[i] = i + 1;
				}
				int maxLen = ByteArrayXorUtils.maxLength(java.util.Arrays.asList(frags));
				byte[] combo = ByteArrayXorUtils.xorWithPadding(java.util.Arrays.asList(frags), maxLen);

				for (int i = 0; i < code; i++) {
					if (i != dropPos) {
						sut.handleFragmentHeaderAndData(packet(frags[i], ids[i]));
					}
				}
				sut.handleFragmentHeaderAndData(packet(combo, ids));

				byte[] expected = concat(frags);
				assertArrayEquals("code=" + code + " dropPos=" + dropPos,
						expected, sut.getReadyBytes());
			}
		}
	}

	@Test
	public void testComboRecovery_nonConsecutiveIds() {
		Random rand = new Random(42);
		byte[] d1 = randomBytes(rand, 8);
		byte[] d2 = randomBytes(rand, 8);
		byte[] d3 = randomBytes(rand, 8);
		byte[] d4 = randomBytes(rand, 8);

		int maxLen = ByteArrayXorUtils.maxLength(java.util.Arrays.asList(d1, d3));
		byte[] combo13 = ByteArrayXorUtils.xorWithPadding(d1, d3, maxLen);

		sut.handleFragmentHeaderAndData(packet(d1, 1));
		sut.handleFragmentHeaderAndData(packet(d2, 2));
		// fragment 3 lost in transmission -- recovered via the non-consecutive combo {1,3}
		sut.handleFragmentHeaderAndData(packet(combo13, 1, 3));
		sut.handleFragmentHeaderAndData(packet(d4, 4));

		byte[] expected = concat(d1, d2, d3, d4);
		assertArrayEquals(expected, sut.getReadyBytes());
		assertEquals(5, sut.getNextSequenceNumber());
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
		sut.handleFragmentHeaderAndData(packet(combo, 1, 2, 3));

		assertEquals("", sut.getReadyText());
		assertEquals(1, sut.getNextSequenceNumber());
	}

	@Test
	public void testCrc32Mismatch_corruptedCombo_dropped() {
		byte[] data = "somedata".getBytes(StandardCharsets.UTF_8);
		String badHeader = "5 6 2 " + data.length + " 999999\n";
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
		sut.handleFragmentHeaderAndData(packet(combo, 1, 2));
		sut.handleFragmentHeaderAndData(packet(d1, 1));
		sut.handleFragmentHeaderAndData(packet(d2, 2));

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
