package fr.an.qrcode.channel.impl;

import nu.pattern.OpenCV;

/** ensures the OpenCV native library is loaded exactly once, regardless of which class touches org.opencv.* first */
public class QROpenCvNativeLoader {

	private static boolean loaded = false;

	public static synchronized void ensureLoaded() {
		if (!loaded) {
			OpenCV.loadLocally();
			loaded = true;
		}
	}

}
