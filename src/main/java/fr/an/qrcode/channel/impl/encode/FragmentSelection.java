package fr.an.qrcode.channel.impl.encode;

import java.util.List;

/**
 * the result of {@link NextFragmentIdsChoiceStrategy#chooseNextIds(List)}: which code type (plain/xor2/xor3) to
 * send next, and the base position in pendingList it starts from -- the rest of the group is the following
 * {@code code.groupSize - 1} consecutive (wrapping) positions, i.e. relative offsets 0, +1, +2 from baseIndex.
 */
public class FragmentSelection {

	public final ComboCode code;
	public final int baseIndex;

	public FragmentSelection(ComboCode code, int baseIndex) {
		this.code = code;
		this.baseIndex = baseIndex;
	}

	/** resolves this selection's group of relative positions (baseIndex, baseIndex+1, ...) into ascending-sorted fragment ids */
	public int[] toIds(List<Integer> pendingList) {
		int n = pendingList.size();
		int groupSize = Math.min(code.groupSize, n);
		int[] ids = new int[groupSize];
		for (int i = 0; i < groupSize; i++) {
			ids[i] = pendingList.get((baseIndex + i) % n);
		}
		java.util.Arrays.sort(ids);
		return ids;
	}

}
