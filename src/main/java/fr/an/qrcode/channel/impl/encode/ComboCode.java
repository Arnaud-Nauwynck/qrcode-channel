package fr.an.qrcode.channel.impl.encode;

/** the wire "code" of a fragment: how many consecutive pending fragments it XORs together (1 = plain, no XOR) */
public enum ComboCode {
	PLAIN(1),
	XOR2(2),
	XOR3(3);

	public final int groupSize;

	ComboCode(int groupSize) {
		this.groupSize = groupSize;
	}

	public static ComboCode forGroupSize(int groupSize) {
		for (ComboCode code : values()) {
			if (code.groupSize == groupSize) {
				return code;
			}
		}
		throw new IllegalArgumentException("no ComboCode for groupSize " + groupSize);
	}
}
