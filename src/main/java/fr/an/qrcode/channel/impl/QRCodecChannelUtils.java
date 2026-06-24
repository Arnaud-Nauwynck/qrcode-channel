package fr.an.qrcode.channel.impl;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;


public class QRCodecChannelUtils {


	public static long crc32(byte[] data) {
		CRC32 crc = new CRC32();
		crc.update(data);
		return crc.getValue();
	}

	public static long crc32(String data) {
		return crc32(data.getBytes(StandardCharsets.UTF_8));
	}

}
