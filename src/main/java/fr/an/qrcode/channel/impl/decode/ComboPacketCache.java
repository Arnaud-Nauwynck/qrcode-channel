package fr.an.qrcode.channel.impl.decode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

import fr.an.qrcode.channel.impl.util.ByteArrayXorUtils;

/**
 * buffers combo (XOR) packets that aren't immediately resolvable, and attempts to recover a missing
 * plain fragment's bytes whenever new information arrives (a new plain fragment, or a new combo).
 *
 * a combo covering [id-code+1 .. id] can recover at most ONE missing fragment via XOR; if 2+ fragments
 * in its range are unknown it stays buffered until more information arrives, and is never resolvable
 * by this single combo alone (gracefully ignored, never throws).
 */
public class ComboPacketCache {

	/** keyed by anchor id; several combos (different code) can share the same anchor id */
	private Map<Integer, List<ComboPacket>> combosByAnchorId = new LinkedHashMap<>();

	/** returns false if this exact (id,code) combo was already buffered (duplicate) */
	public boolean insertCombo(ComboPacket combo, IntFunction<byte[]> knownFragmentLookup, FragmentRecoveredCallback onRecovered) {
		List<ComboPacket> existing = combosByAnchorId.computeIfAbsent(combo.id, k -> new ArrayList<>());
		for (ComboPacket c : existing) {
			if (c.code == combo.code) {
				return false; // duplicate
			}
		}
		existing.add(combo);
		tryRecoverFromCombo(combo, knownFragmentLookup, onRecovered);
		return true;
	}

	/** called whenever a new plain fragment becomes known, to unlock any buffered combo it completes; cascades to a fixed point */
	public void onPlainFragmentArrived(int fragId, IntFunction<byte[]> knownFragmentLookup, FragmentRecoveredCallback onRecovered) {
		boolean recoveredAny = true;
		while (recoveredAny) {
			recoveredAny = false;
			for (List<ComboPacket> combos : combosByAnchorId.values()) {
				for (ComboPacket combo : new ArrayList<>(combos)) {
					if (fragId >= combo.rangeFrom() && fragId <= combo.rangeTo()) {
						if (tryRecoverFromCombo(combo, knownFragmentLookup, onRecovered)) {
							recoveredAny = true;
						}
					}
				}
			}
		}
	}

	/** attempts XOR-recovery of a combo's single missing fragment; returns true if a fragment was recovered */
	private boolean tryRecoverFromCombo(ComboPacket combo, IntFunction<byte[]> knownFragmentLookup, FragmentRecoveredCallback onRecovered) {
		int missingId = -1;
		int missingCount = 0;
		List<byte[]> knownBytes = new ArrayList<>();
		for (int fragId = combo.rangeFrom(); fragId <= combo.rangeTo(); fragId++) {
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

	/** evicts combos whose whole range is already fully consumed into the decoder's ready output (id < nextSequenceNumber) */
	public void cleanupConsumed(int nextSequenceNumber) {
		combosByAnchorId.entrySet().removeIf(e -> e.getKey() < nextSequenceNumber);
	}

	public int size() {
		int count = 0;
		for (List<ComboPacket> combos : combosByAnchorId.values()) {
			count += combos.size();
		}
		return count;
	}

	public interface FragmentRecoveredCallback {
		void accept(int fragSeqNumber, byte[] recoveredBytes);
	}

}
