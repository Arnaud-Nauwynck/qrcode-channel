package fr.an.qrcode.channel.impl.encode;

import java.awt.image.BufferedImage;
import java.lang.ref.WeakReference;

public class QRCodeEncodedFragment {
	
	private QRCodesEncoderChannel owner;
	private int fragmentNumber;
	private String fragmentId;
	
    private final String fragmentHeaderText;
    private final String data;
    
    private WeakReference<BufferedImage> imgRef;
	private boolean acknowledge;

    public QRCodeEncodedFragment(QRCodesEncoderChannel owner, int fragmentNumber, String fragmentId, String fragmentHeaderText, String data) {
        this.owner = owner;
        this.fragmentNumber = fragmentNumber;
        this.fragmentId = fragmentId;
        this.fragmentHeaderText = fragmentHeaderText;
    	this.data = data;
    }
    
    public int getFragmentNumber() {
		return fragmentNumber;
	}

    public String getFragmentId() {
    	return fragmentId;
    }
    
	public String getData() {
        return data;
    }
    
    public BufferedImage getImg() {
    	BufferedImage img = (imgRef != null)? imgRef.get() : null;
    	if (img == null) {
    		String text = fragmentHeaderText + data;
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