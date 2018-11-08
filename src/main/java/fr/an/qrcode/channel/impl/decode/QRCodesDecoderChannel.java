package fr.an.qrcode.channel.impl.decode;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import fr.an.qrcode.channel.impl.QRCodecChannelUtils;
import fr.an.qrcode.channel.impl.decode.filter.ImageStreamCallback;
import fr.an.qrcode.channel.impl.decode.filter.ImageStreamProvider;
import fr.an.qrcode.channel.impl.decode.input.ImageProvider;

public class QRCodesDecoderChannel {

	private static final Logger log = LoggerFactory.getLogger(QRCodesDecoderChannel.class);

	private boolean useSHA = false;
	
	private Map<DecodeHintType, Object> qrHints;
	
	private String readyText = "";
	
	private int nextSequenceNumber = 0;
	
	private Map<Integer,QRCodeDecodedFragment> aheadFragments = new LinkedHashMap<>();
	
    private ImageStreamProvider imageStreamProvider;
    
    private ImageStreamCallback innerImageStreamCallback = new ImageStreamCallback() {
		@Override
		public void onImage(BufferedImage image, long nanosTime, long nanos) {
			try {
				onStream_handleImage(image, nanosTime, nanos);
			} catch(Exception ex) {
				log.warn("Failed to handle image", ex);
			}
		}
    };

    private QRCodeReader qrCodeReader = new QRCodeReader();

	private BufferedImage currentImg;
	
    private QRPacketResult currentSnapshotResult;


    
	// no need to have several listeners
	private DecoderChannelListener eventListener;
	
	
	// ------------------------------------------------------------------------
    
	public QRCodesDecoderChannel(
			Map<DecodeHintType, Object> qrHints, 
			ImageProvider imageProvider, 
			DecoderChannelListener eventListener) {
		this.qrHints = qrHints;
		this.imageStreamProvider = new ImageStreamProvider(imageProvider, innerImageStreamCallback);
		this.eventListener = eventListener;
	}

	// ------------------------------------------------------------------------

	public void startListenSnapshots() {
		imageStreamProvider.startListenSnapshots();
	}
	
	public void stopListenSnapshots() {
		imageStreamProvider.stopListenSnapshots();
	}

	public void takeSnapshot() {
		imageStreamProvider.takeSnapshot();
	}

	
	public static class QRPacketResult {
		public String decodeMsg;
		public int channelSequenceNumber;
		
		public Result qrResult;
		public int fragmentSequenceNumber;
		public String text;
		
		public long nanosCompute;
	}
	
	protected void onStream_handleImage(BufferedImage img, long captureNanosTime, long captureNanos) {
		this.currentImg = img;

		long nanosComputeBefore = System.nanoTime();
		
        QRPacketResult snapshotResult = computeQRPacketResult(img);

        long nanosCompute = System.nanoTime() - nanosComputeBefore;
		        
        snapshotResult.nanosCompute = nanosCompute;
        eventListener.onEvent(new DecoderChannelEvent(img, captureNanosTime, captureNanos,
        		snapshotResult, nanosComputeBefore, nanosCompute,
        		readyText));
	}  
    
	public QRPacketResult computeQRPacketResult(BufferedImage img) {
		QRPacketResult res = new QRPacketResult();
		
        LuminanceSource source = new BufferedImageLuminanceSource(img);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
        
        Result qrResult;
        try {
        	qrResult = qrCodeReader.decode(bitmap, qrHints);
        } catch(NotFoundException ex) {
        	res.decodeMsg = "no qr code found";
            return res;
        } catch(com.google.zxing.ChecksumException ex) {
        	res.decodeMsg = "\n<<<<<<<< FAILED to decode QRCode: ChecksumException >>>>>>>>>>>>\n";
            return res;
        } catch(com.google.zxing.FormatException ex) {
        	res.decodeMsg = "\n<<<<<<<< FAILED to decode QRCode: FormatException >>>>>>>>>>>>\n";
            return res;
        }
        
        if (qrResult == null) {
        	res.decodeMsg = "no result";
        	return res;
        }
        	
    	String headerAndData = qrResult.getText();
    	handleFragmentHeaderAndData(res, headerAndData);
    
    	res.channelSequenceNumber = nextSequenceNumber;
        return res;
	}




	
	
    private static final Pattern fragmentHeaderPattern = Pattern.compile("([0-9\\.]*) ([0-9]+) ([^\n]*)");
    private static final Pattern fragmentHeaderNoSHAPattern = Pattern.compile("([0-9\\.]*) ([0-9]+)");
    
    protected void handleFragmentHeaderAndData(QRPacketResult res, String headerAndData) {
    	int lineSep = headerAndData.indexOf("\n");
    	if (lineSep == -1) {
    		res.decodeMsg = "header line break not found";
    		return;
    	}
    	String header = headerAndData.substring(0, lineSep);
    	String data = headerAndData.substring(lineSep+1, headerAndData.length());
    	Matcher headerMatcher = ((useSHA)? fragmentHeaderPattern : fragmentHeaderNoSHAPattern).matcher(header);
    	if (! headerMatcher.matches()) {
    		res.decodeMsg = "header not recognised: " + header;
    		return;
    	}
    	String fragId = headerMatcher.group(1);
    	int fragSeqNumber = Integer.parseInt(fragId); // TODO... handle case id!=number : "id.subPart"
    	
    	if (fragSeqNumber < nextSequenceNumber) {
			// drop already seen/handled fragment .. do nothing!
			res.decodeMsg = "dropped past fragment (" + fragId + ")";
			return;
    	}
    	
    	long crc32 = Long.parseLong(headerMatcher.group(2));
    	long checkCrc32 = QRCodecChannelUtils.crc32(data);
    	if (checkCrc32 != crc32) {
    		res.decodeMsg = "corrupted data: crc32 differs for fragId:" + fragId;
    		return;
    	}
    	
    	if (useSHA) {
    		String headerArgs = headerMatcher.group(3);
    		if (headerArgs != null && headerArgs.startsWith("SHA=")) {
	    		int endShaIdx = headerArgs.indexOf(" ");
	    		if (endShaIdx == -1) endShaIdx = headerArgs.length(); 
	    		String sha256 = headerArgs.substring(4, endShaIdx);
		    	String checkSha256 = QRCodecChannelUtils.sha256(data);
		    	if (! checkSha256.equals(sha256)) {
		    		res.decodeMsg = "corrupted data: SHA-256 differs for fragId:" + fragId;
		    		return;
		    	}
    		}
    	}
    	
    	// ok, got id + data...
		res.fragmentSequenceNumber = fragSeqNumber;
		res.text = data;
    	
		// check if fragment is next one expected in sequence
		if (nextSequenceNumber == fragSeqNumber) {
			readyText += data;
			res.decodeMsg = "OK recognised fragment (" + fragSeqNumber + ")";
			nextSequenceNumber++;
			
			// then check ahead fragments
			int seqNumberBeforeUsedAheadFrags = nextSequenceNumber;
			for(;;) {
				boolean foundNextFrag = false;
				for(Iterator<QRCodeDecodedFragment> nextFragIter = aheadFragments.values().iterator(); nextFragIter.hasNext(); ) {
					QRCodeDecodedFragment nextFrag = nextFragIter.next();
					int nextFragNumber = nextFrag.getFragmentNumber();
					if (nextFragNumber == nextSequenceNumber) {
						readyText += nextFrag.getData();
						nextSequenceNumber++;
						
						foundNextFrag = true;
						nextFragIter.remove();
					}
				}
				if (!foundNextFrag) {
					break;
				}
			}
			if (nextSequenceNumber > seqNumberBeforeUsedAheadFrags) {
				res.decodeMsg += ", then use ahead frag(s), expecting " + nextSequenceNumber; 
			}
			
		} else {
			// push ahead fragment (if not already pushed)
			if (aheadFragments.get(fragSeqNumber) == null) {
				QRCodeDecodedFragment frag = new QRCodeDecodedFragment(this, fragSeqNumber, header, data);
				aheadFragments.put(fragSeqNumber, frag);
				res.decodeMsg = "pushed ahead fragment (" + fragSeqNumber + ")";
			} else {
				res.decodeMsg = "dropped already pushed ahead fragment (" + fragSeqNumber + ")";
			}
		}
    }

    // ------------------------------------------------------------------------
    
	public int getChannelSequenceNumber() {
		return nextSequenceNumber;
	}
	
	public BufferedImage getCurrentImg() {
		return currentImg;
	}

    public QRPacketResult getCurrentSnapshotResult() {
    	return currentSnapshotResult;
    }

	public String getReadyText() {
		return readyText;
	}

	public String getAheadFragsInfo() {
		StringBuilder res = new StringBuilder();
		if (!aheadFragments.isEmpty()) {
			res.append("ahead " + aheadFragments.size() + " frag(s):\n");
			for(QRCodeDecodedFragment frag : aheadFragments.values()) {
				res.append(frag.getFragmentNumber());
				res.append(" ");
			}
		}
		return res.toString();
	}
    
	public void parseRecordParamsText(String recordParamsText) {
		imageStreamProvider.getImageProvider().parseRecordParamsText(recordParamsText);
	}

	public Rectangle getRecordArea() {
		return imageStreamProvider.getImageProvider().getRecordArea();
	}

	public void setRecordArea(Rectangle r) {
		imageStreamProvider.getImageProvider().setRecordArea(r);
	}

}
