package fr.an.qrcode.channel.impl;

import org.junit.Assert;
import org.junit.Test;

public class QRCodecChannelUtilsTest {

	@Test
	public void testSha256() {
		String res = QRCodecChannelUtils.sha256("test");
		// ?? n4bQgYhMfWWaL+qgxVrQFaO/TxsrC4Is0V1sFbDwCgg=
		Assert.assertNotNull(res);
	}
}
