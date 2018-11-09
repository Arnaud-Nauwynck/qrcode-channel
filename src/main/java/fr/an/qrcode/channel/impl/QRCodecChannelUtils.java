package fr.an.qrcode.channel.impl;

import java.util.zip.CRC32;


public class QRCodecChannelUtils {


	public static long crc32(String data) {
		CRC32 crc = new CRC32();
		crc.update(data.getBytes());
		long crc32 = crc.getValue();
		return crc32;
	}

}
