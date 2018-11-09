package fr.an.qrcode.channel.impl.encode;

import java.util.List;
import java.util.Random;

import org.junit.Test;

import com.google.common.collect.ImmutableList;

import fr.an.qrcode.channel.impl.QREncodeSetting;

public class QRCodesEncoderChannelTest {

	protected QRCodesEncoderChannel sut = new QRCodesEncoderChannel(new QREncodeSetting());
	Random rand = new Random(1234);
	
	@Test
	public void test1000() {
		String text = randomASCII(1000);
		sut.appendFragmentsFor(text);
		sut.getFragmentImgs();
	}
	
	@Test
	public void test_sizes() {
		List<Integer> sizes = ImmutableList.of(10, 100, 1000, 2000, 5000, 10000, 20000);
		for (int size : sizes) {
			QRCodesEncoderChannel encoder = new QRCodesEncoderChannel(new QREncodeSetting());
			String text = randomASCII(size);
			encoder.appendFragmentsFor(text);
			encoder.getFragmentImgs();
		}
	}
	
	protected String randomASCII(int len) {
		StringBuilder res = new StringBuilder(len);
		for(int i = 0; i < len; i++) {
			char ch = (char) rand.nextInt(256);
			res.append(ch);			
		}
		return res.toString();
	}
}
