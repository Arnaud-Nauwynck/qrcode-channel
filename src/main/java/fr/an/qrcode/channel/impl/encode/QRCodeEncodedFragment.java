package fr.an.qrcode.channel.impl.encode;

import java.awt.image.BufferedImage;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import net.fec.openrq.EncodingPacket;

import fr.an.qrcode.channel.impl.QRCodecChannelUtils;

/** one transmitted QR fragment: either the leading FEC-params preamble, or a RaptorQ source/repair EncodingPacket */
public class QRCodeEncodedFragment {

	private QRCodesEncoderChannel owner;

	/** display/navigation position: 0 for the FEC-params preamble, then 1..K for source symbols, K+1.. for repair symbols */
	private final int fragmentNumber;

	/** null for the FEC-params preamble fragment */
	private final EncodingPacket packet;

    private final byte[] data;
    private final long crc32;

    private WeakReference<BufferedImage> imgRef;
    private boolean acknowledge;

    /** counts how many times this fragment was displayed/sent */
    private int sentCount;

    public QRCodeEncodedFragment(QRCodesEncoderChannel owner, int fragmentNumber, EncodingPacket packet, byte[] data) {
        this.owner = owner;
        this.fragmentNumber = fragmentNumber;
        this.packet = packet;
    	this.data = data;
    	this.crc32 = QRCodecChannelUtils.crc32(data);
    }

    public boolean isParamsFragment() {
    	return packet == null;
    }

    public EncodingPacket getPacket() {
    	return packet;
    }

    /** position in the display/navigation strip; used for UI indexing (not a RaptorQ concept by itself) */
    public int getFragmentNumber() {
		return fragmentNumber;
	}

	public long getCrc32() {
		return crc32;
	}

	public String getHeader() {
    	return fragmentNumber + " " + data.length + " " + crc32 + "\n";
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

	/** records that this fragment was just sent */
	public void incrSentCount() {
		sentCount++;
	}

	public int getSentCount() {
		return sentCount;
	}

}
