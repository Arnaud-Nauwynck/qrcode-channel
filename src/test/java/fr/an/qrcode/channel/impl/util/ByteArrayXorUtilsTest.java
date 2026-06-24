package fr.an.qrcode.channel.impl.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.Test;

public class ByteArrayXorUtilsTest {

	@Test
	public void testXor_equalLengthArrays() {
		byte[] a = { 0x01, 0x02 };
		byte[] b = { 0x03, 0x04 };
		byte[] res = ByteArrayXorUtils.xorWithPadding(a, b, 2);
		assertArrayEquals(new byte[] { 0x02, 0x06 }, res);
	}

	@Test
	public void testXor_selfCancel() {
		byte[] a = { 0x11, 0x22, 0x33 };
		byte[] res = ByteArrayXorUtils.xorWithPadding(a, a, 3);
		assertArrayEquals(new byte[] { 0, 0, 0 }, res);
	}

	@Test
	public void testXor_paddingShorterArray() {
		byte[] a = { 0x01, 0x02, 0x03 };
		byte[] b = { 0x0F };
		int targetLen = ByteArrayXorUtils.maxLength(Arrays.asList(a, b));
		assertEquals(3, targetLen);
		byte[] res = ByteArrayXorUtils.xorWithPadding(Arrays.asList(a, b), targetLen);
		assertArrayEquals(new byte[] { 0x0E, 0x02, 0x03 }, res);
	}

	@Test
	public void testXorRecover_singleMissing() {
		Random rand = new Random(42);
		byte[] data1 = randomBytes(rand, 12);
		byte[] data2 = randomBytes(rand, 9);
		byte[] data3 = randomBytes(rand, 12);

		int maxLen = ByteArrayXorUtils.maxLength(Arrays.asList(data1, data2, data3));
		byte[] combo = ByteArrayXorUtils.xorWithPadding(Arrays.asList(data1, data2, data3), maxLen);

		byte[] recoveredPadded = ByteArrayXorUtils.xorWithPadding(Arrays.asList(combo, data1, data3), maxLen);
		byte[] recovered = Arrays.copyOf(recoveredPadded, data2.length);

		assertArrayEquals(data2, recovered);
	}

	private byte[] randomBytes(Random rand, int len) {
		byte[] res = new byte[len];
		rand.nextBytes(res);
		return res;
	}

}
