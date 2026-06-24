package fr.an.qrcode.channel.impl;

import static org.junit.Assert.assertEquals;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class QRCodecChannelUtilsTest {

	@Test
	public void testCrc32Bytes_knownValue() {
		long crc = QRCodecChannelUtils.crc32("123456789".getBytes(StandardCharsets.US_ASCII));
		assertEquals(0xCBF43926L, crc);
	}

	@Test
	public void testCrc32String_isUtf8() {
		String text = "café";
		long expected = QRCodecChannelUtils.crc32(text.getBytes(StandardCharsets.UTF_8));
		assertEquals(expected, QRCodecChannelUtils.crc32(text));
	}

	@Test
	public void testCrc32_byteArrayOverload_matchesStringOverloadForAscii() {
		String text = "hello world";
		assertEquals(QRCodecChannelUtils.crc32(text), QRCodecChannelUtils.crc32(text.getBytes(StandardCharsets.UTF_8)));
	}

}
