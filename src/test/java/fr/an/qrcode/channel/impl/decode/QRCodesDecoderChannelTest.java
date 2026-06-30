package fr.an.qrcode.channel.impl.decode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.google.zxing.DecodeHintType;

import fr.an.qrcode.channel.impl.QREncodeSetting;
import fr.an.qrcode.channel.impl.decode.input.ImageProvider;
import fr.an.qrcode.channel.impl.encode.QRCodeEncodedFragment;
import fr.an.qrcode.channel.impl.encode.QRCodesEncoderChannel;
import fr.an.qrcode.channel.impl.util.DimInt2D;

public class QRCodesDecoderChannelTest {

	private QRCodesDecoderChannel sut;

	@Before
	public void setUp() {
		Map<DecodeHintType, Object> qrHints = new HashMap<>();
		sut = new QRCodesDecoderChannel(qrHints, new NoopImageProvider(), e -> {});
	}

	private String packetText(QRCodeEncodedFragment frag) {
		return new String(frag.getPayloadBytes(), StandardCharsets.ISO_8859_1);
	}

	@Test
	public void testCrc32Mismatch_corruptedFragment_dropped() {
		byte[] data = "somedata".getBytes(StandardCharsets.UTF_8);
		String badHeader = "1 " + data.length + " 999999\n";
		sut.handleFragmentHeaderAndData(badHeader + new String(data, StandardCharsets.ISO_8859_1));

		assertEquals("", sut.getReadyText());
	}

	@Test
	public void testProtocolError_malformedHeader_dropped() {
		sut.handleFragmentHeaderAndData("not a header\nsomedata");

		assertEquals("", sut.getReadyText());
		assertEquals("", sut.getAheadFragsInfo());
	}

	@Test
	public void testFragmentsBeforeParams_areDropped() {
		// fragment 1 arrives before the FEC-params preamble (fragment 0) -- decoder has no FECParameters yet
		byte[] data = "somedata".getBytes(StandardCharsets.UTF_8);
		long crc32 = fr.an.qrcode.channel.impl.QRCodecChannelUtils.crc32(data);
		String header = "1 " + data.length + " " + crc32 + "\n";
		sut.handleFragmentHeaderAndData(header + new String(data, StandardCharsets.ISO_8859_1));

		assertEquals("", sut.getReadyText());
		assertEquals(0, sut.getNextSequenceNumber());
	}

	@Test
	public void testFullEncodeDecode_allFragmentsInOrder_decodesOriginalText() {
		String originalText = buildRepeatingText(2000);

		QREncodeSetting settings = new QREncodeSetting();
		QRCodesEncoderChannel encoder = new QRCodesEncoderChannel(settings);
		encoder.appendFragmentsFor(originalText);
		sut.setSymbolSizeHint(settings.getSymbolSize());

		int totalFragments = encoder.getFragmentImgs().size();
		for (int i = 0; i < totalFragments; i++) {
			QRCodeEncodedFragment frag = encoder.nextFragmentToSend();
			sut.handleFragmentHeaderAndData(packetText(frag));
		}

		assertEquals(originalText, sut.getReadyText());
	}

	@Test
	public void testFullEncodeDecode_missingSourceSymbols_recoveredFromRepairSymbols() {
		String originalText = buildRepeatingText(3000);

		QREncodeSetting settings = new QREncodeSetting();
		settings.setNumRepairSymbols(20);
		QRCodesEncoderChannel encoder = new QRCodesEncoderChannel(settings);
		encoder.appendFragmentsFor(originalText);
		sut.setSymbolSizeHint(settings.getSymbolSize());

		int totalFragments = encoder.getFragmentImgs().size();

		// send the params fragment + all source symbols except every 3rd one, then enough repair symbols
		// to make up for what's missing -- RaptorQ should still fully reconstruct the source block
		int sent = 0;
		for (int i = 0; i < totalFragments; i++) {
			QRCodeEncodedFragment frag = encoder.nextFragmentToSend();
			boolean dropThisOne = frag.getFragmentNumber() > 0 && (frag.getFragmentNumber() % 3 == 0);
			if (dropThisOne) {
				continue;
			}
			sut.handleFragmentHeaderAndData(packetText(frag));
			sent++;
		}
		// feed extra repair symbols until decoded (or budget exhausted)
		int maxExtra = settings.getNumRepairSymbols() + 5;
		for (int i = 0; i < maxExtra && !sut.getReadyText().equals(originalText); i++) {
			QRCodeEncodedFragment frag = encoder.nextFragmentToSend();
			if (frag == null) {
				break;
			}
			sut.handleFragmentHeaderAndData(packetText(frag));
		}

		assertEquals(originalText, sut.getReadyText());
		assertTrue("expect at least a few fragments to have been usefully consumed", sent > 0);
	}

	@Test
	public void testGetFragmentStates_reflectsReceivedSourceSymbols() {
		String originalText = buildRepeatingText(1000);

		QREncodeSetting settings = new QREncodeSetting();
		QRCodesEncoderChannel encoder = new QRCodesEncoderChannel(settings);
		encoder.appendFragmentsFor(originalText);
		sut.setSymbolSizeHint(settings.getSymbolSize());

		QRCodeEncodedFragment paramsFrag = encoder.nextFragmentToSend();
		sut.handleFragmentHeaderAndData(packetText(paramsFrag));

		assertTrue(sut.getFragmentStates().stream().allMatch(s -> s == QRCodesDecoderChannel.FragmentState.INITIAL));

		QRCodeEncodedFragment sourceFrag = encoder.nextFragmentToSend();
		sut.handleFragmentHeaderAndData(packetText(sourceFrag));

		long receivedCount = sut.getFragmentStates().stream()
				.filter(s -> s == QRCodesDecoderChannel.FragmentState.RECEIVED)
				.count();
		assertEquals(1, receivedCount);
	}

	private String buildRepeatingText(int len) {
		StringBuilder sb = new StringBuilder(len);
		String unit = "The quick brown fox jumps over the lazy dog. ";
		while (sb.length() < len) {
			sb.append(unit);
		}
		return sb.substring(0, len);
	}

	private static class NoopImageProvider extends ImageProvider {
		@Override public DimInt2D getSize() { return new DimInt2D(1, 1); }
		@Override public void open() { }
		@Override public void close() { }
		@Override public BufferedImage captureImage() { return null; }
		@Override public void parseRecordParamsText(String recordParamsText) { }
	}

}
