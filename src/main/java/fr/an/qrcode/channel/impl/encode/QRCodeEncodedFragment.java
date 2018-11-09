package fr.an.qrcode.channel.impl.encode;

import java.awt.image.BufferedImage;
import java.lang.ref.WeakReference;

public class QRCodeEncodedFragment {
	
	private QRCodesEncoderChannel owner;
	private int fragmentNumber;
	
    private final String header;
    private final String data;
    
    private WeakReference<BufferedImage> imgRef;
	// ???
    private boolean acknowledge;

    public QRCodeEncodedFragment(QRCodesEncoderChannel owner, int fragmentNumber, 
    		String header, String data) {
        this.owner = owner;
        this.fragmentNumber = fragmentNumber;
        this.header = header;
    	this.data = data;
    }
    
    public int getFragmentNumber() {
		return fragmentNumber;
	}

	public String getHeader() {
        return header;
    }

	public String getData() {
        return data;
    }
    
    public BufferedImage getImg() {
    	BufferedImage img = (imgRef != null)? imgRef.get() : null;
    	if (img == null) {
    		String text = header + data;
    		img = owner.encodeAndRender(text);
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