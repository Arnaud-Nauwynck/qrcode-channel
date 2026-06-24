package fr.an.qrcode.channel.impl.decode;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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
import fr.an.qrcode.channel.impl.decode.filter.ZXingQRStreamFromImageStreamCallback;
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

	private ByteArrayOutputStream readyBytes = new ByteArrayOutputStream();

	private ComboPacketCache comboCache = new ComboPacketCache();

    // emit
	private DecoderChannelListener eventListener;
	
	
	// ------------------------------------------------------------------------

	public QRCodesDecoderChannel(
			Map<DecodeHintType, Object> qrHints,
			ImageProvider imageProvider,
			DecoderChannelListener eventListener) {
		int samplingLen = 3;
		this.eventListener = eventListener;
		this.qrStreamFromImageStream =
				new ZXingQRStreamFromImageStreamCallback(innerQRStreamCallback, samplingLen, qrHints);
				// new ZBarQRStreamFromImageStreamCallback(innerQRStreamCallback, samplingLen);
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
	
	
	
    private static final Pattern fragmentHeaderPattern = Pattern.compile("([0-9]+) ([0-9]+) ([0-9]+) ([0-9]+)");

    // TODO synchronized ??!!!!!!
    protected synchronized void handleFragmentHeaderAndData(String headerAndData) {
    	// long nanosBefore = System.nanoTime();
    	Bucket bucketStats = getRollingStats().checkRoll();

    	byte[] allBytes = headerAndData.getBytes(StandardCharsets.ISO_8859_1);
    	int lineSep = -1;
    	for (int i = 0; i < allBytes.length; i++) {
    		if (allBytes[i] == '\n') {
    			lineSep = i;
    			break;
    		}
    	}
    	if (lineSep == -1) {
    		currDecodeMsg = "header line break not found";
			bucketStats.incrCountQRPacketProtocolError();
    		return;
    	}
    	String header = headerAndData.substring(0, lineSep);
    	byte[] dataBytes = Arrays.copyOfRange(allBytes, lineSep+1, allBytes.length);

    	Matcher headerMatcher = fragmentHeaderPattern.matcher(header);
    	if (! headerMatcher.matches()) {
    		currDecodeMsg = "header not recognised: " + header;
    		bucketStats.incrCountQRPacketProtocolError();
    		return;
    	}
    	int id = Integer.parseInt(headerMatcher.group(1));
    	int code = Integer.parseInt(headerMatcher.group(2));
    	int len = Integer.parseInt(headerMatcher.group(3));
    	long crc32 = Long.parseLong(headerMatcher.group(4));

    	if (dataBytes.length != len) {
    		currDecodeMsg = "data length mismatch for id:" + id + " expected:" + len + " got:" + dataBytes.length;
    		bucketStats.incrCountQRPacketProtocolError();
    		return;
    	}

    	long checkCrc32 = QRCodecChannelUtils.crc32(dataBytes);
    	if (checkCrc32 != crc32) {
    		currDecodeMsg = "corrupted data: crc32 differs for fragId:" + id;
    		bucketStats.incrCountQRPacketChecksumException();
    		return;
    	}

    	// ok, got id + code + data...

    	if (code == 1) {
    		if (id < nextSequenceNumber) {
				// drop already seen/handled fragment .. do nothing!
				currDecodeMsg = "dropped past fragment (" + id + ")";
				bucketStats.incrCountQRPacketRecognizedDuplicate();
				return;
    		}
    		acceptDecodedFragment(id, header, dataBytes);
    		comboCache.onPlainFragmentArrived(id, this::lookupKnownFragmentBytes, this::acceptRecoveredFragment);
    		comboCache.cleanupConsumed(nextSequenceNumber);
    	} else {
    		int rangeFrom = id - code + 1;
    		if (id < nextSequenceNumber) {
    			// whole combo range already consumed -- useless
    			currDecodeMsg = "dropped past combo (id:" + id + " code:" + code + ")";
    			bucketStats.incrCountQRPacketRecognizedDuplicate();
    			return;
    		}
    		ComboPacket combo = new ComboPacket(id, code, len, dataBytes);
    		boolean inserted = comboCache.insertCombo(combo, this::lookupKnownFragmentBytes, this::acceptRecoveredFragment);
    		if (inserted) {
    			currDecodeMsg = "pushed combo (id:" + id + " code:" + code + " range:" + rangeFrom + "-" + id + ")";
    		} else {
    			currDecodeMsg = "dropped already pushed combo (id:" + id + " code:" + code + ")";
    			bucketStats.incrCountQRPacketRecognizedDuplicate();
    		}
    		comboCache.cleanupConsumed(nextSequenceNumber);
    	}

    	// long computeNanos = System.nanoTime() - nanosBefore;
    }

    /** feeds a decoded fragment (plain-received or XOR-recovered) into the existing sequential reassembly logic */
    private void acceptDecodedFragment(int fragSeqNumber, String header, byte[] dataBytes) {
		if (nextSequenceNumber == fragSeqNumber) {
			readyBytes.writeBytes(dataBytes);
			currDecodeMsg = "OK recognised fragment (" + fragSeqNumber + ")";
			nextSequenceNumber++;

			// then check ahead fragments
			int seqNumberBeforeUsedAheadFrags = nextSequenceNumber;
			for(;;) {
				boolean foundNextFrag = false;
				for(Iterator<QRCodeDecodedFragment> nextFragIter = aheadFragments.values().iterator(); nextFragIter.hasNext(); ) {
					QRCodeDecodedFragment frag = nextFragIter.next();
					int nextFragNumber = frag.getFragmentNumber();
					if (nextFragNumber == nextSequenceNumber) {
						readyBytes.writeBytes(frag.getData());
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
				QRCodeDecodedFragment frag = new QRCodeDecodedFragment(this, fragSeqNumber, header, dataBytes);
				aheadFragments.put(fragSeqNumber, frag);
				currDecodeMsg = "pushed ahead fragment (" + fragSeqNumber + ")";
			} else {
				currDecodeMsg = "dropped already pushed ahead fragment (" + fragSeqNumber + ")";
			}
		}
    }

    /** callback used by ComboPacketCache once it XOR-recovers a missing fragment */
    private void acceptRecoveredFragment(int fragSeqNumber, byte[] dataBytes) {
    	String syntheticHeader = fragSeqNumber + " 1 " + dataBytes.length + " " + QRCodecChannelUtils.crc32(dataBytes) + "\n";
    	acceptDecodedFragment(fragSeqNumber, syntheticHeader, dataBytes);
    }

    /** lookup used by ComboPacketCache to fetch bytes of a fragment id already known to the decoder (consumed or buffered ahead) */
    private byte[] lookupKnownFragmentBytes(int fragSeqNumber) {
    	if (fragSeqNumber < nextSequenceNumber) {
    		// already consumed into readyBytes -- not retained individually, so cannot be used for recovery anymore
    		return null;
    	}
    	QRCodeDecodedFragment frag = aheadFragments.get(fragSeqNumber);
    	return frag != null ? frag.getData() : null;
    }

    protected void fireDecoderChannelEvent(QRCapturedEvent event) {
    	eventListener.onEvent(new DecoderChannelEvent(
    			currDecodeMsg,
    			getReadyText(),
    			event));
    }

    // ------------------------------------------------------------------------

	public int getNextSequenceNumber() {
		return nextSequenceNumber;
	}

	public String getReadyText() {
		return readyBytes.toString(StandardCharsets.UTF_8);
	}

	public String getAheadFragsInfo() {
		StringBuilder res = new StringBuilder();
		if (!aheadFragments.isEmpty()) {
			int minAhead = Integer.MAX_VALUE;
			int maxAhead = -1;
			for(QRCodeDecodedFragment frag : aheadFragments.values()) {
				int fragNum = frag.getFragmentNumber();
				minAhead = Math.min(fragNum, minAhead);
				maxAhead = Math.max(fragNum, maxAhead);
			}
			res.append((aheadFragments.size() + nextSequenceNumber-1)
					+ " = " + (nextSequenceNumber-1) + " + " + aheadFragments.size() + " ahead frag(s) in " + minAhead + ".." + maxAhead + " : ");
			int prev = minAhead;
			for(int i = minAhead+1; i <= maxAhead; i++) {
				while(null != aheadFragments.get(i) && i <= maxAhead) {
					i++;
				}
				int last = i-1;
				if (prev != last) {
					res.append(prev + "-" + last + " ");
				} else {
					res.append(prev + " ");
				}

				while(null == aheadFragments.get(i) && i <= maxAhead) {
					i++;
				}
				prev = i;				
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

	public int getComboCacheSize() {
		return comboCache.size();
	}
	
}
