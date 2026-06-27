package fr.an.qrcode.channel.impl.encode;

import java.awt.image.BufferedImage;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import fr.an.qrcode.channel.impl.QRCodecChannelUtils;

public class QRCodeEncodedFragment {

	private QRCodesEncoderChannel owner;

	/** ascending ids of the plain fragment(s) XORed together; a single entry means a plain (non-combo) fragment */
	private final int[] ids;

    private final byte[] data;
    private final long crc32;

    private WeakReference<BufferedImage> imgRef;
    private boolean acknowledge;

    /** counts how many times this id was displayed alone, in a 2-way xor combo, or in a 3-way xor combo */
    private int sentPlainCount;
    private int sentXor2Count;
    private int sentXor3Count;

    public QRCodeEncodedFragment(QRCodesEncoderChannel owner, int[] ids, byte[] data) {
        this.owner = owner;
        this.ids = ids;
    	this.data = data;
    	this.crc32 = QRCodecChannelUtils.crc32(data);
    }

    public int[] getIds() {
    	return ids;
    }

    public boolean isPlain() {
    	return ids.length == 1;
    }

    /** lowest id in the group; used for display/navigation position */
    public int getFragmentNumber() {
		return ids[0];
	}

	/** number of fragment ids XORed together (1 = plain fragment) */
	public int getCode() {
		return ids.length;
	}

	public long getCrc32() {
		return crc32;
	}

	public String getHeader() {
    	StringBuilder sb = new StringBuilder();
    	for (int id : ids) {
    		sb.append(id).append(' ');
    	}
    	sb.append(ids.length).append(' ').append(data.length).append(' ').append(crc32).append('\n');
        return sb.toString();
    }

	public byte[] getData() {
        return data;
    }

    public BufferedImage getImg() {
    	BufferedImage img = (imgRef != null)? imgRef.get() : null;
    	if (img == null) {
    		img = owner.encodeAndRender(getPayloadBytes());
    		imgRef = new WeakReference<>(img);
    	}
        return img;
    }

    /** the full QR payload (header + data) as transmitted, ready for QR rendering */
    public byte[] getPayloadBytes() {
    	byte[] headerBytes = getHeader().getBytes(StandardCharsets.US_ASCII);
    	byte[] payload = Arrays.copyOf(headerBytes, headerBytes.length + data.length);
    	System.arraycopy(data, 0, payload, headerBytes.length, data.length);
    	return payload;
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

	/** records that this id was just sent as part of a group of the given size (1=plain, 2=xor2, 3=xor3) */
	public void incrSentCount(int groupSize) {
		if (groupSize <= 1) {
			sentPlainCount++;
		} else if (groupSize == 2) {
			sentXor2Count++;
		} else {
			sentXor3Count++;
		}
	}

	public int getSentPlainCount() {
		return sentPlainCount;
	}
	public int getSentXor2Count() {
		return sentXor2Count;
	}
	public int getSentXor3Count() {
		return sentXor3Count;
	}

}
