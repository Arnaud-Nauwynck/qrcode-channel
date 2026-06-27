package fr.an.qrcode.channel.impl.encode;

import java.util.List;

import fr.an.qrcode.channel.impl.QREncodeSetting;

/**
 * decides which {@link ComboCode} (plain/xor2/xor3) and base pendingList position to send next, given the
 * current list of still-pending (non-acknowledged) fragment ids. stateful: keeps the cursors/counters needed
 * to walk pendingIds across successive calls, cf. {@link QRCodesEncoderChannel#nextFragmentToSend()}.
 */
public class NextFragmentIdsChoiceStrategy {

	private final QREncodeSetting qrEncodeSettings;

	/** round-robin cursor over pendingIds, cycling group sizes from QREncodeSetting.comboGroupSizes */
	private int sendCursor = 0;
	private int comboGroupSizeIndex = 0;

	/** countdown of remaining steps before a xor2 (resp. xor3) combo is due: decremented each call whose chosen
	 *  type is NOT this one, reset to QREncodeSetting.xor2Frequency/xor3Frequency each call whose chosen type IS this one */
	private int remainNextStepForXor2;
	private int remainNextStepForXor3;
	private boolean comboFrequencyCountersInited = false;

	/** each combo type (plain/xor2/xor3) walks its own position over pendingIds, advanced only when that type is chosen */
	private int plainCursor = 0;
	private int xor2Cursor = 0;
	private int xor3Cursor = 0;

	// ------------------------------------------------------------------------

	public NextFragmentIdsChoiceStrategy(QREncodeSetting qrEncodeSettings) {
		this.qrEncodeSettings = qrEncodeSettings;
	}

	// ------------------------------------------------------------------------

	/**
	 * picks the next code type (plain/xor2/xor3) and base pendingList position to send from, given the
	 * current (non-empty) pendingList; cf. {@link FragmentSelection#toIds(List)} to resolve it to actual ids.
	 */
	public FragmentSelection chooseNextIds(List<Integer> pendingList) {
		int n = pendingList.size();
		if (sendCursor >= n) {
			sendCursor = 0;
		}

		ComboCode code;
		int cursor;
		if (qrEncodeSettings.isComboFrequencyEnabled()) {
			code = nextFrequencyCode();
			cursor = (code == ComboCode.XOR3 ? xor3Cursor : code == ComboCode.XOR2 ? xor2Cursor : plainCursor) % n;
		} else if (qrEncodeSettings.isComboRedundancyEnabled()) {
			int[] groupSizes = qrEncodeSettings.getComboGroupSizes();
			int groupSize = 1;
			if (groupSizes.length > 0) {
				if (comboGroupSizeIndex >= groupSizes.length) {
					comboGroupSizeIndex = 0;
				}
				groupSize = Math.max(1, groupSizes[comboGroupSizeIndex]);
				comboGroupSizeIndex++;
			}
			code = ComboCode.forGroupSize(Math.min(groupSize, 3));
			cursor = sendCursor;
		} else {
			code = ComboCode.PLAIN;
			cursor = sendCursor;
		}

		if (qrEncodeSettings.isComboFrequencyEnabled()) {
			int groupSize = Math.min(code.groupSize, n);
			if (code == ComboCode.XOR3) {
				xor3Cursor = (xor3Cursor + groupSize) % n;
			} else if (code == ComboCode.XOR2) {
				xor2Cursor = (xor2Cursor + groupSize) % n;
			} else {
				plainCursor = (plainCursor + groupSize) % n;
			}
		} else {
			sendCursor = (sendCursor + 1) % n;
		}

		return new FragmentSelection(code, cursor);
	}

	/**
	 * frequency-based combo schedule using countdown counters: remainNextStepForXor2/Xor3 count down towards 0
	 * on every call whose chosen type is NOT theirs; once a counter reaches 0, that combo type is due. If both are
	 * due on the same call, xor3 takes priority. Whichever type is actually chosen has its counter reset to its
	 * configured frequency; the other due-but-not-chosen counter is left at/below 0 so it stays due (fires next call).
	 */
	private ComboCode nextFrequencyCode() {
		if (!comboFrequencyCountersInited) {
			remainNextStepForXor2 = qrEncodeSettings.getXor2Frequency();
			remainNextStepForXor3 = qrEncodeSettings.getXor3Frequency();
			comboFrequencyCountersInited = true;
		}

		boolean xor3Due = qrEncodeSettings.getXor3Frequency() > 0 && remainNextStepForXor3 <= 0;
		boolean xor2Due = qrEncodeSettings.getXor2Frequency() > 0 && remainNextStepForXor2 <= 0;

		ComboCode code;
		if (xor3Due) {
			code = ComboCode.XOR3;
			remainNextStepForXor3 = qrEncodeSettings.getXor3Frequency();
		} else if (xor2Due) {
			code = ComboCode.XOR2;
			remainNextStepForXor2 = qrEncodeSettings.getXor2Frequency();
		} else {
			code = ComboCode.PLAIN;
		}

		if (code != ComboCode.XOR3) {
			remainNextStepForXor3--;
		}
		if (code != ComboCode.XOR2) {
			remainNextStepForXor2--;
		}
		return code;
	}

}
