package fr.an.qrcode.channel.impl;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.CRC32;

import org.apache.commons.codec.binary.Base64;

public class QRCodecChannelUtils {


	public static long crc32(String data) {
		CRC32 crc = new CRC32();
		crc.update(data.getBytes());
		long crc32 = crc.getValue();
		return crc32;
	}

	public static String sha256(String data) {
		MessageDigest md;
		try {
			md = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException();
		}
		md.update(data.getBytes());
		byte[] digest = md.digest();
		return new String(Base64.encodeBase64(digest));
	}

}
