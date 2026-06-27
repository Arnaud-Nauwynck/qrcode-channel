package fr.an.qrcode.channel.impl.util;

import java.util.List;

public class ByteArrayXorUtils {

	public static int maxLength(List<byte[]> arrays) {
		int max = 0;
		for (byte[] arr : arrays) {
			max = Math.max(max, arr.length);
		}
		return max;
	}

	public static byte[] xorWithPadding(List<byte[]> arrays, int targetLen) {
		byte[] res = new byte[targetLen];
		for (byte[] arr : arrays) {
			int len = Math.min(arr.length, targetLen);
			for (int i = 0; i < len; i++) {
				res[i] ^= arr[i];
			}
		}
		return res;
	}

	public static byte[] xorWithPadding(byte[] a, byte[] b, int targetLen) {
		byte[] res = new byte[targetLen];
		int lenA = Math.min(a.length, targetLen);
		for (int i = 0; i < lenA; i++) {
			res[i] ^= a[i];
		}
		int lenB = Math.min(b.length, targetLen);
		for (int i = 0; i < lenB; i++) {
			res[i] ^= b[i];
		}
		return res;
	}

}
