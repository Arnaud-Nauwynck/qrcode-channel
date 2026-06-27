package fr.an.qrcode.channel.impl.decode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.IntFunction;

import fr.an.qrcode.channel.impl.util.ByteArrayXorUtils;

/**
 * buffers combo (XOR) packets that aren't immediately resolvable, and attempts to recover a missing
 * plain fragment's bytes whenever new information arrives (a new plain fragment, or a new combo).
 *
 * a combo covering a set of ids can recover at most ONE missing fragment via XOR; if 2+ ids in its set
 * are unknown it stays buffered until more information arrives, and is never resolvable by this single
 * combo alone (gracefully ignored, never throws).
 */
public class ComboPacketCache {

	private List<ComboPacket> combos = new ArrayList<>();
	private LinkedHashSet<String> seenKeys = new LinkedHashSet<>();

	/** returns false if this exact id set was already buffered (duplicate) */
	public boolean insertCombo(ComboPacket combo, IntFunction<byte[]> knownFragmentLookup, FragmentRecoveredCallback onRecovered) {
		if (!seenKeys.add(combo.key())) {
			return false; // duplicate
		}
		combos.add(combo);
		tryRecoverFromCombo(combo, knownFragmentLookup, onRecovered);
		return true;
	}

	/** called whenever a new plain fragment becomes known, to unlock any buffered combo it completes; cascades to a fixed point */
	public void onPlainFragmentArrived(int fragId, IntFunction<byte[]> knownFragmentLookup, FragmentRecoveredCallback onRecovered) {
		boolean recoveredAny = true;
		while (recoveredAny) {
			recoveredAny = false;
			for (ComboPacket combo : new ArrayList<>(combos)) {
				if (containsId(combo, fragId)) {
					if (tryRecoverFromCombo(combo, knownFragmentLookup, onRecovered)) {
						recoveredAny = true;
					}
				}
			}
		}
	}

	private static boolean containsId(ComboPacket combo, int fragId) {
		for (int id : combo.ids) {
			if (id == fragId) {
				return true;
			}
		}
		return false;
	}

	/** attempts XOR-recovery of a combo's single missing fragment; returns true if a fragment was recovered */
	private boolean tryRecoverFromCombo(ComboPacket combo, IntFunction<byte[]> knownFragmentLookup, FragmentRecoveredCallback onRecovered) {
		int missingId = -1;
		int missingCount = 0;
		List<byte[]> knownBytes = new ArrayList<>();
		for (int fragId : combo.ids) {
			byte[] bytes = knownFragmentLookup.apply(fragId);
			if (bytes == null) {
				missingCount++;
				missingId = fragId;
				if (missingCount > 1) {
					return false; // under-determined, cannot recover -- leave buffered
				}
			} else {
				knownBytes.add(bytes);
			}
		}
		if (missingCount == 0) {
			return false; // nothing to recover, combo is now redundant (will be evicted by cleanupConsumed)
		}
		// missingCount == 1
		knownBytes.add(combo.data);
		byte[] recoveredPadded = ByteArrayXorUtils.xorWithPadding(knownBytes, combo.len);
		onRecovered.accept(missingId, recoveredPadded);
		return true;
	}

	/** evicts combos whose every id is already fully consumed into the decoder's ready output (id < nextSequenceNumber) */
	public void cleanupConsumed(int nextSequenceNumber) {
		combos.removeIf(combo -> allIdsBelow(combo, nextSequenceNumber));
	}

	private static boolean allIdsBelow(ComboPacket combo, int nextSequenceNumber) {
		for (int id : combo.ids) {
			if (id >= nextSequenceNumber) {
				return false;
			}
		}
		return true;
	}

	public int size() {
		return combos.size();
	}

	public interface FragmentRecoveredCallback {
		void accept(int fragSeqNumber, byte[] recoveredBytes);
	}

}
