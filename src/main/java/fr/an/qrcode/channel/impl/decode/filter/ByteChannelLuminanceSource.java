package fr.an.qrcode.channel.impl.decode.filter;

import com.google.zxing.LuminanceSource;

/** wraps a single 8-bit channel plane (e.g. one of R/G/B, row-major, no padding) as a ZXing LuminanceSource, with no luminance mixing */
public class ByteChannelLuminanceSource extends LuminanceSource {

	private final byte[] channelData;

	public ByteChannelLuminanceSource(byte[] channelData, int width, int height) {
		super(width, height);
		this.channelData = channelData;
	}

	@Override
	public byte[] getRow(int y, byte[] row) {
		int width = getWidth();
		if (row == null || row.length < width) {
			row = new byte[width];
		}
		System.arraycopy(channelData, y * width, row, 0, width);
		return row;
	}

	@Override
	public byte[] getMatrix() {
		return channelData;
	}

}
