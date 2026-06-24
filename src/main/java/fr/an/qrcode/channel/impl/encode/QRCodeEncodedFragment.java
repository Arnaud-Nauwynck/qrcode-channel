package fr.an.qrcode.channel.impl.encode;

import java.awt.image.BufferedImage;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class QRCodeEncodedFragment {

	private QRCodesEncoderChannel owner;
	private int fragmentNumber;
	private int code;

    private final String header;
    private final byte[] data;

    private WeakReference<BufferedImage> imgRef;
	// ???
    private boolean acknowledge;

    public QRCodeEncodedFragment(QRCodesEncoderChannel owner, int fragmentNumber, int code,
    		String header, byte[] data) {
        this.owner = owner;
        this.fragmentNumber = fragmentNumber;
        this.code = code;
        this.header = header;
    	this.data = data;
    }

    public int getFragmentNumber() {
		return fragmentNumber;
	}

	public int getCode() {
		return code;
	}

	public String getHeader() {
        return header;
    }

	public byte[] getData() {
        return data;
    }

    public BufferedImage getImg() {
    	BufferedImage img = (imgRef != null)? imgRef.get() : null;
    	if (img == null) {
    		byte[] headerBytes = header.getBytes(StandardCharsets.US_ASCII);
    		byte[] payload = Arrays.copyOf(headerBytes, headerBytes.length + data.length);
    		System.arraycopy(data, 0, payload, headerBytes.length, data.length);
    		img = owner.encodeAndRender(payload);
    		imgRef = new WeakReference<>(img);
    	}
        return img;
    }

	public FragmentImg getFragmentImg() {
		BufferedImage img = getImg();
		return new FragmentImg(this, img);
	}

	public void acknowledge() {
		this.acknowledge = true;
	}
	public boolean isAcknowledge() {
		return this.acknowledge;
	}
	
}