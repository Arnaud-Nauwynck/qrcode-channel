package fr.an.qrcode.channel.impl.decode;

/** a received "combo" packet: byte-wise XOR of the plain fragments whose ids are listed in `ids` (ascending) */
public class ComboPacket {

	public final int[] ids;
	public final int len;
	public final byte[] data;

	public ComboPacket(int[] ids, int len, byte[] data) {
		this.ids = ids;
		this.len = len;
		this.data = data;
	}

	/** a stable key identifying this exact combo (its id set), to detect duplicates */
	public String key() {
		StringBuilder sb = new StringBuilder();
		for (int id : ids) {
			sb.append(id).append(',');
		}
		return sb.toString();
	}

}
