package fr.an.qrcode.channel.impl.decode;

/** a received "combo" packet: byte-wise XOR of the `code` consecutive plain fragments ending at `id` */
public class ComboPacket {

	public final int id;
	public final int code;
	public final int len;
	public final byte[] data;

	public ComboPacket(int id, int code, int len, byte[] data) {
		this.id = id;
		this.code = code;
		this.len = len;
		this.data = data;
	}

	public int rangeFrom() {
		return id - code + 1;
	}

	public int rangeTo() {
		return id;
	}

}
