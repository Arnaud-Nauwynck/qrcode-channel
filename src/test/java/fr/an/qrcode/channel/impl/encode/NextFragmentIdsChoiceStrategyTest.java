package fr.an.qrcode.channel.impl.encode;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import fr.an.qrcode.channel.impl.QREncodeSetting;

public class NextFragmentIdsChoiceStrategyTest {

	private static List<Integer> pendingIds(int n) {
		List<Integer> res = new ArrayList<>();
		for (int i = 1; i <= n; i++) {
			res.add(i);
		}
		return res;
	}

	@Test
	public void chooseNextIds_plainOnlyByDefault() {
		QREncodeSetting settings = new QREncodeSetting();
		NextFragmentIdsChoiceStrategy sut = new NextFragmentIdsChoiceStrategy(settings);
		List<Integer> pending = pendingIds(10);

		for (int i = 0; i < 10; i++) {
			FragmentSelection selection = sut.chooseNextIds(pending);
			assertEquals(ComboCode.PLAIN, selection.code);
			assertEquals(1, selection.toIds(pending).length);
		}
	}

	@Test
	public void chooseNextIds_cyclesComboGroupSizes() {
		QREncodeSetting settings = new QREncodeSetting();
		settings.setComboRedundancyEnabled(true);
		settings.setComboGroupSizes(new int[] { 1, 2, 3 });
		NextFragmentIdsChoiceStrategy sut = new NextFragmentIdsChoiceStrategy(settings);
		List<Integer> pending = pendingIds(10);

		int[] codesSeen = new int[4];
		for (int i = 0; i < 9; i++) {
			FragmentSelection selection = sut.chooseNextIds(pending);
			codesSeen[selection.toIds(pending).length]++;
		}
		assertEquals(3, codesSeen[1]);
		assertEquals(3, codesSeen[2]);
		assertEquals(3, codesSeen[3]);
	}

	@Test
	public void chooseNextIds_comboFrequencySchedule() {
		QREncodeSetting settings = new QREncodeSetting();
		settings.setComboFrequencyEnabled(true);
		settings.setXor2Frequency(2);
		settings.setXor3Frequency(7);
		NextFragmentIdsChoiceStrategy sut = new NextFragmentIdsChoiceStrategy(settings);
		List<Integer> pending = pendingIds(10);

		int[][] expected = {
				{ 1 },
				{ 2 },
				{ 1, 2 },
				{ 3 },
				{ 4 },
				{ 3, 4 },
				{ 1, 2, 3 },
				{ 5 },
				{ 6 },
				{ 5, 6 },
		};

		for (int[] expectedIds : expected) {
			FragmentSelection selection = sut.chooseNextIds(pending);
			assertArrayEquals(expectedIds, selection.toIds(pending));
		}
	}

	@Test
	public void chooseNextIds_shrinksGroupSizeWhenFewerPendingThanComboSize() {
		QREncodeSetting settings = new QREncodeSetting();
		settings.setComboFrequencyEnabled(true);
		settings.setXor2Frequency(2);
		settings.setXor3Frequency(3);
		NextFragmentIdsChoiceStrategy sut = new NextFragmentIdsChoiceStrategy(settings);
		List<Integer> pending = Arrays.asList(5, 9); // only 2 pending ids left

		FragmentSelection first = sut.chooseNextIds(pending); // plain
		assertEquals(1, first.toIds(pending).length);
		FragmentSelection second = sut.chooseNextIds(pending); // xor2 due, exactly 2 pending -> ok
		assertEquals(2, second.toIds(pending).length);
		FragmentSelection third = sut.chooseNextIds(pending); // xor3 due but only 2 pending -> clamped to 2
		assertTrue(third.toIds(pending).length <= 2);
	}

}
