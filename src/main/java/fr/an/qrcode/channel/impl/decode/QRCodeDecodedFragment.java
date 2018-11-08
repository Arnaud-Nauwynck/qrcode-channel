package fr.an.qrcode.channel.impl.decode;

public class QRCodeDecodedFragment {

	private QRCodesDecoderChannel owner;
	private final int fragmentNumber;
	
    private final String fragmentHeaderText;
    private final String data;

    public QRCodeDecodedFragment(QRCodesDecoderChannel owner, int fragmentNumber,
			String fragmentHeaderText, String data) {
		this.owner = owner;
		this.fragmentNumber = fragmentNumber;
		this.fragmentHeaderText = fragmentHeaderText;
		this.data = data;
	}

	public QRCodesDecoderChannel getOwner() {
		return owner;
	}

	public void setOwner(QRCodesDecoderChannel owner) {
		this.owner = owner;
	}

	public int getFragmentNumber() {
		return fragmentNumber;
	}

	public String getFragmentHeaderText() {
		return fragmentHeaderText;
	}

	public String getData() {
		return data;
	}
    
}
