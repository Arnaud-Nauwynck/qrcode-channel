package fr.an.qrcode.channel.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import fr.an.qrcode.channel.impl.QREncodeSetting;
import fr.an.qrcode.channel.impl.encode.FragmentImg;
import fr.an.qrcode.channel.ui.QRCodeEncoderChannelModel.DisplayMode;

public class QRCodeEncoderChannelModelTest {

	private QRCodeEncoderChannelModel model;

	@Before
	public void setUp() {
		model = new QRCodeEncoderChannelModel(new QREncodeSetting());
		model.computeQRCodes(randomAscii(500));
	}

	private String randomAscii(int len) {
		StringBuilder sb = new StringBuilder(len);
		for (int i = 0; i < len; i++) {
			sb.append((char) ('a' + (i % 26)));
		}
		return sb.toString();
	}

	@Test
	public void testBlackWhiteMode_defaultsToSingleFragmentGroups() {
		assertEquals(DisplayMode.BLACK_WHITE, model.getDisplayMode());

		List<FragmentImg> group = model.getCurrentDisplayGroup();
		assertEquals(1, group.size());
		assertEquals(1, group.get(0).getFragmentNumber());

		model.onDisplayNextFrag();
		assertEquals(1, model.getCurrentDisplayGroup().size());
		assertEquals(2, model.getCurrentDisplayGroup().get(0).getFragmentNumber());
	}

	@Test
	public void testRgbSplitMode_groupsByThree() {
		model.setDisplayMode(DisplayMode.RGB_SPLIT);

		List<FragmentImg> group = model.getCurrentDisplayGroup();
		assertEquals(3, group.size());
		assertEquals(1, group.get(0).getFragmentNumber());
		assertEquals(2, group.get(1).getFragmentNumber());
		assertEquals(3, group.get(2).getFragmentNumber());

		model.onDisplayNextFrag();
		group = model.getCurrentDisplayGroup();
		assertEquals(4, group.get(0).getFragmentNumber());
		assertEquals(5, group.get(1).getFragmentNumber());
		assertEquals(6, group.get(2).getFragmentNumber());
	}

	@Test
	public void testRgbSplitMode_producesCompositeImageDistinctFromSingleFragmentImages() {
		model.setDisplayMode(DisplayMode.RGB_SPLIT);

		BufferedImage composite = model.getCurrentFragmentImg();
		assertNotNull(composite);

		List<FragmentImg> group = model.getCurrentDisplayGroup();
		assertTrue(composite != group.get(0).img);
	}

	@Test
	public void testBlackWhiteMode_displayedImageIsThePlainFragmentImage() {
		BufferedImage img = model.getCurrentFragmentImg();
		List<FragmentImg> group = model.getCurrentDisplayGroup();
		assertEquals(group.get(0).img, img);
	}

	@Test
	public void testOnDisplayPrevFrag_inRgbSplitMode_movesBackByThree() {
		model.setDisplayMode(DisplayMode.RGB_SPLIT);
		model.onDisplayNextFrag(); // now at frag 4

		model.onDisplayPrevFrag();
		List<FragmentImg> group = model.getCurrentDisplayGroup();
		assertEquals(1, group.get(0).getFragmentNumber());
	}

	@Test
	public void testSwitchingModePreservesCurrentFragmentAsAnchor() {
		model.onDisplayNextFrag(); // black/white, now at frag 2

		model.setDisplayMode(DisplayMode.RGB_SPLIT);
		List<FragmentImg> group = model.getCurrentDisplayGroup();
		assertEquals(2, group.get(0).getFragmentNumber());
		assertEquals(3, group.size());
	}

}
