package fr.an.qrcode.channel.impl.decode;

import java.awt.Rectangle;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.zxing.DecodeHintType;

import fr.an.qrcode.channel.impl.QRCodecChannelUtils;
import fr.an.qrcode.channel.impl.decode.filter.QRCapturedEvent;
import fr.an.qrcode.channel.impl.decode.filter.QRDecodeRollingStats;
import fr.an.qrcode.channel.impl.decode.filter.QRDecodeRollingStats.Bucket;
import fr.an.qrcode.channel.impl.decode.filter.QRStreamCallback;
import fr.an.qrcode.channel.impl.decode.filter.ZBarQRStreamFromImageStreamCallback;
import fr.an.qrcode.channel.impl.decode.input.ImageProvider;
import fr.an.qrcode.channel.impl.decode.input.ImageStreamCallback;
import fr.an.qrcode.channel.impl.decode.input.ImageStreamProvider;

public class QRCodesDecoderChannel {

	private static final Logger log = LoggerFactory.getLogger(QRCodesDecoderChannel.class);

    private ImageStreamProvider imageStreamProvider;
    
    private ImageStreamCallback qrStreamFromImageStream;
    
    private QRStreamCallback innerQRStreamCallback = new InnerQRStreamCallback();

    private String currDecodeMsg;
	private int nextSequenceNumber = 1;
	private Map<Integer,QRCodeDecodedFragment> aheadFragments = new LinkedHashMap<>();

	private String readyText = "";
	
    // emit 
	private DecoderChannelListener eventListener;
	
	
	// ------------------------------------------------------------------------
    
	public QRCodesDecoderChannel(
			Map<DecodeHintType, Object> qrHints, 
			ImageProvider imageProvider, 
			DecoderChannelListener eventListener) {
		int samplingLen = 3;
		this.eventListener = eventListener;
		this.qrStreamFromImageStream = // new ZXingQRStreamFromImageStreamCallback(innerQRStreamCallback, samplingLen, qrHints);
				new ZBarQRStreamFromImageStreamCallback(innerQRStreamCallback, samplingLen);
		this.imageStreamProvider = new ImageStreamProvider(imageProvider, qrStreamFromImageStream);
		log.info("ctor QRCodesDecoderChannel");
	}

	// ------------------------------------------------------------------------

	public void startListenSnapshots() {
		// qrStreamFromImageStream.onStart();
		imageStreamProvider.startListenSnapshots();
		
	}
	
	public void stopListenSnapshots() {
		imageStreamProvider.stopListenSnapshots();
	}

	public void takeSnapshot() {
		imageStreamProvider.takeSnapshot();
	}


    
	protected class InnerQRStreamCallback extends QRStreamCallback {
		
		@Override
		public void onQRCaptured(QRCapturedEvent event) {
			if (event.qrResults != null) {
				for(QRResult qrResult : event.qrResults) {
					String headerAndData = qrResult.text;
			    	handleFragmentHeaderAndData(headerAndData);
				}
			}
			
	    	fireDecoderChannelEvent(event);
		}

	}
	
	
	
    private static final Pattern fragmentHeaderPattern = Pattern.compile("([0-9\\.]*) ([0-9]+)");
    
    // TODO synchronized ??!!!!!!
    protected synchronized void handleFragmentHeaderAndData(String headerAndData) {
    	// long nanosBefore = System.nanoTime(); 
    	Bucket bucketStats = getRollingStats().checkRoll();
    	int lineSep = headerAndData.indexOf("\n");
    	if (lineSep == -1) {
    		currDecodeMsg = "header line break not found";
			bucketStats.incrCountQRPacketProtocolError();
    		return;
    	}
    	String header = headerAndData.substring(0, lineSep);
    	String data = headerAndData.substring(lineSep+1, headerAndData.length());
    	Matcher headerMatcher = fragmentHeaderPattern.matcher(header);
    	if (! headerMatcher.matches()) {
    		currDecodeMsg = "header not recognised: " + header;
    		bucketStats.incrCountQRPacketProtocolError();
    		return;
    	}
    	String fragId = headerMatcher.group(1);
    	int fragSeqNumber = Integer.parseInt(fragId); // TODO... handle case id!=number : "id.subPart"
    	
    	if (fragSeqNumber < nextSequenceNumber) {
			// drop already seen/handled fragment .. do nothing!
			currDecodeMsg = "dropped past fragment (" + fragId + ")";
			bucketStats.incrCountQRPacketRecognizedDuplicate();
			return;
    	}
    	
    	long crc32 = Long.parseLong(headerMatcher.group(2));
    	long checkCrc32 = QRCodecChannelUtils.crc32(data);
    	if (checkCrc32 != crc32) {
    		currDecodeMsg = "corrupted data: crc32 differs for fragId:" + fragId;
    		bucketStats.incrCountQRPacketChecksumException();
    		return;
    	}
    	
    	// ok, got id + data...
    	
		// check if fragment is next one expected in sequence
		if (nextSequenceNumber == fragSeqNumber) {
			readyText += data;
			currDecodeMsg = "OK recognised fragment (" + fragSeqNumber + ")";
			nextSequenceNumber++;
			// bucketStats.incrCountQRPacketRecognized();
			
			// then check ahead fragments
			int seqNumberBeforeUsedAheadFrags = nextSequenceNumber;
			for(;;) {
				boolean foundNextFrag = false;
				for(Iterator<QRCodeDecodedFragment> nextFragIter = aheadFragments.values().iterator(); nextFragIter.hasNext(); ) {
					QRCodeDecodedFragment frag = nextFragIter.next();
					int nextFragNumber = frag.getFragmentNumber();
					if (nextFragNumber == nextSequenceNumber) {
						readyText += frag.getData();
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
				currDecodeMsg += ", then use ahead frag(s), expecting " + nextSequenceNumber; 
			}
			
		} else {
			// push ahead fragment (if not already pushed)
			if (fragSeqNumber > nextSequenceNumber && aheadFragments.get(fragSeqNumber) == null) {
				QRCodeDecodedFragment frag = new QRCodeDecodedFragment(this, fragSeqNumber, header, data);
				aheadFragments.put(fragSeqNumber, frag);
				currDecodeMsg = "pushed ahead fragment (" + fragSeqNumber + ")";
				// bucketStats.incrCountQRPacketRecognized();
			} else {
				currDecodeMsg = "dropped already pushed ahead fragment (" + fragSeqNumber + ")";
				bucketStats.incrCountQRPacketRecognizedDuplicate();
			}
		}
		
    	// long computeNanos = System.nanoTime() - nanosBefore; 
    }

    protected void fireDecoderChannelEvent(QRCapturedEvent event) {
    	eventListener.onEvent(new DecoderChannelEvent(
    			currDecodeMsg,
    			readyText,
    			event));
    }
    
    // ------------------------------------------------------------------------
    
	public int getNextSequenceNumber() {
		return nextSequenceNumber;
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

	
	public String getRecognitionStatsText() {
		return getRollingStats().getRecognitionStatsText();
	}

	private QRDecodeRollingStats getRollingStats() {
		return qrStreamFromImageStream.getRollingStats();
	}

	public ImageStreamCallback getQRStreamFromImageStream() {
		return qrStreamFromImageStream;
	}
	
}
