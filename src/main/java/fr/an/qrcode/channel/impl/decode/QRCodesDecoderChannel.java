package fr.an.qrcode.channel.impl.decode;

import java.awt.Rectangle;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.zxing.DecodeHintType;

import net.fec.openrq.ArrayDataDecoder;
import net.fec.openrq.EncodingPacket;
import net.fec.openrq.OpenRQ;
import net.fec.openrq.Parsed;
import net.fec.openrq.decoder.SourceBlockDecoder;
import net.fec.openrq.decoder.SourceBlockState;
import net.fec.openrq.parameters.FECParameters;

import fr.an.qrcode.channel.impl.QRCodecChannelUtils;
import fr.an.qrcode.channel.impl.decode.filter.QRCapturedEvent;
import fr.an.qrcode.channel.impl.decode.filter.QRDecodeRollingStats;
import fr.an.qrcode.channel.impl.decode.filter.QRDecodeRollingStats.Bucket;
import fr.an.qrcode.channel.impl.decode.filter.QRStreamCallback;
import fr.an.qrcode.channel.impl.decode.filter.RgbSplitQRStreamFromImageStreamCallback;
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

    /** dataLength carried by the FEC-params preamble fragment (fragmentNumber 0); null until received */
    private Integer paramsDataLength;
    private FECParameters fecParams;
    private ArrayDataDecoder dataDecoder;
    private SourceBlockDecoder sourceBlockDecoder;

    private boolean dataDecoded = false;
    private byte[] readyBytes = new byte[0];

    // emit
	private DecoderChannelListener eventListener;


	// ------------------------------------------------------------------------

	public QRCodesDecoderChannel(
			Map<DecodeHintType, Object> qrHints,
			ImageProvider imageProvider,
			DecoderChannelListener eventListener) {
		this(qrHints, imageProvider, eventListener, false);
	}

	public QRCodesDecoderChannel(
			Map<DecodeHintType, Object> qrHints,
			ImageProvider imageProvider,
			DecoderChannelListener eventListener,
			boolean rgbSplitMode) {
		int samplingLen = 3;
		this.eventListener = eventListener;
		this.qrStreamFromImageStream = rgbSplitMode
				? new RgbSplitQRStreamFromImageStreamCallback(innerQRStreamCallback, qrHints)
				: new ZXingQRStreamFromImageStreamCallback(innerQRStreamCallback, samplingLen, qrHints);
				// new ZBarQRStreamFromImageStreamCallback(innerQRStreamCallback, samplingLen);
		this.imageStreamProvider = new ImageStreamProvider(imageProvider, qrStreamFromImageStream);
		log.info("ctor QRCodesDecoderChannel rgbSplitMode:" + rgbSplitMode);
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

    	// header shape: "<fragmentNumber> <len> <crc32>"
    	String[] tokens = header.trim().split("\\s+");
    	int fragmentNumber;
    	int len;
    	long crc32;
    	try {
    		if (tokens.length != 3) {
    			currDecodeMsg = "header not recognised: " + header;
    			bucketStats.incrCountQRPacketProtocolError();
    			return;
    		}
    		fragmentNumber = Integer.parseInt(tokens[0]);
    		len = Integer.parseInt(tokens[1]);
    		crc32 = Long.parseLong(tokens[2]);
    	} catch (NumberFormatException ex) {
    		currDecodeMsg = "header not recognised: " + header;
    		bucketStats.incrCountQRPacketProtocolError();
    		return;
    	}

    	if (dataBytes.length != len) {
    		currDecodeMsg = "data length mismatch for fragment:" + fragmentNumber + " expected:" + len + " got:" + dataBytes.length;
    		bucketStats.incrCountQRPacketProtocolError();
    		return;
    	}

    	long checkCrc32 = QRCodecChannelUtils.crc32(dataBytes);
    	if (checkCrc32 != crc32) {
    		currDecodeMsg = "corrupted data: crc32 differs for fragment:" + fragmentNumber;
    		bucketStats.incrCountQRPacketChecksumException();
    		return;
    	}

    	// ok, got fragmentNumber + data...

    	if (fragmentNumber == 0) {
    		handleParamsFragment(dataBytes, bucketStats);
    		return;
    	}

    	if (dataDecoder == null) {
    		currDecodeMsg = "dropped fragment(" + fragmentNumber + "), FEC params not yet received";
    		bucketStats.incrCountQRPacketProtocolError();
    		return;
    	}
    	if (dataDecoded) {
    		currDecodeMsg = "dropped fragment(" + fragmentNumber + "), already fully decoded";
    		bucketStats.incrCountQRPacketRecognizedDuplicate();
    		return;
    	}

    	Parsed<EncodingPacket> parsed = dataDecoder.parsePacket(dataBytes, true);
    	if (!parsed.isValid()) {
    		currDecodeMsg = "unparsable RaptorQ packet for fragment(" + fragmentNumber + "): " + parsed.failureReason();
    		bucketStats.incrCountQRPacketProtocolError();
    		return;
    	}
    	EncodingPacket packet = parsed.value();

    	boolean alreadyKnown = packet.symbolType() == net.fec.openrq.SymbolType.SOURCE
    			? sourceBlockDecoder.containsSourceSymbol(packet.encodingSymbolID())
    			: sourceBlockDecoder.containsRepairSymbol(packet.encodingSymbolID());
    	if (alreadyKnown) {
    		currDecodeMsg = "dropped already known fragment(" + fragmentNumber + ")";
    		bucketStats.incrCountQRPacketRecognizedDuplicate();
    		return;
    	}

    	SourceBlockState state = sourceBlockDecoder.putEncodingPacket(packet);
    	currDecodeMsg = "OK recognised fragment (" + fragmentNumber + "), missing source symbols:" + sourceBlockDecoder.missingSourceSymbols().size();

    	if (state == SourceBlockState.DECODED && !dataDecoded) {
    		dataDecoded = true;
    		readyBytes = dataDecoder.dataArray();
    		currDecodeMsg = "OK fully decoded (" + readyBytes.length + " bytes)";
    	}

    	// long computeNanos = System.nanoTime() - nanosBefore;
    }

    /** receives the FEC-params preamble (fragmentNumber 0, dataLength as ASCII text); builds the RaptorQ decoder once */
    private void handleParamsFragment(byte[] dataBytes, Bucket bucketStats) {
    	int dataLength;
    	try {
    		dataLength = Integer.parseInt(new String(dataBytes, StandardCharsets.US_ASCII).trim());
    	} catch (NumberFormatException ex) {
    		currDecodeMsg = "unparsable FEC params fragment";
    		bucketStats.incrCountQRPacketProtocolError();
    		return;
    	}
    	if (paramsDataLength != null && paramsDataLength == dataLength) {
    		currDecodeMsg = "dropped already known FEC params fragment";
    		bucketStats.incrCountQRPacketRecognizedDuplicate();
    		return;
    	}
    	paramsDataLength = dataLength;
    	fecParams = FECParameters.newParameters(dataLength, symbolSizeHint, 1);
    	dataDecoder = OpenRQ.newDecoderWithZeroOverhead(fecParams);
    	sourceBlockDecoder = dataDecoder.sourceBlock(0);
    	dataDecoded = false;
    	readyBytes = new byte[0];
    	currDecodeMsg = "OK received FEC params (dataLength:" + dataLength + ")";
    }

    /** symbol size used to derive FECParameters from the params fragment's dataLength; must match the encoder's QREncodeSetting.symbolSize */
    private int symbolSizeHint = 1200;

    public void setSymbolSizeHint(int symbolSize) {
    	this.symbolSizeHint = symbolSize;
    }

    protected void fireDecoderChannelEvent(QRCapturedEvent event) {
    	eventListener.onEvent(new DecoderChannelEvent(
    			currDecodeMsg,
    			getReadyText(),
    			event));
    }

    // ------------------------------------------------------------------------

	/** next expected source symbol ESI (0-based) before full decode, or numberOfSourceSymbols once fully decoded; 0 if FEC params not yet received */
	public int getNextSequenceNumber() {
		if (sourceBlockDecoder == null) {
			return 0;
		}
		return sourceBlockDecoder.numberOfSourceSymbols() - sourceBlockDecoder.missingSourceSymbols().size();
	}

	public String getReadyText() {
		return new String(readyBytes, StandardCharsets.UTF_8);
	}

	public byte[] getReadyBytes() {
		return readyBytes;
	}

	public String getAheadFragsInfo() {
		if (sourceBlockDecoder == null) {
			return "";
		}
		int numSource = sourceBlockDecoder.numberOfSourceSymbols();
		int missing = sourceBlockDecoder.missingSourceSymbols().size();
		int repairs = sourceBlockDecoder.availableRepairSymbols().size();
		return (numSource - missing) + "/" + numSource + " source symbols, " + repairs + " repair symbol(s) received"
				+ (dataDecoded ? ", DECODED" : "");
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

	public enum FragmentState { INITIAL, RECEIVED, ACKNOWLEDGED }

	/**
	 * per-source-symbol-ESI display state (ESI 0..numberOfSourceSymbols-1): ACKNOWLEDGED if the whole
	 * source block has been fully decoded, RECEIVED if that source symbol was individually received,
	 * else INITIAL (not yet seen -- may still be recoverable from repair symbols).
	 */
	public List<FragmentState> getFragmentStates() {
		if (sourceBlockDecoder == null) {
			return new ArrayList<>();
		}
		int numSource = sourceBlockDecoder.numberOfSourceSymbols();
		List<FragmentState> states = new ArrayList<>(numSource);
		for (int esi = 0; esi < numSource; esi++) {
			if (dataDecoded) {
				states.add(FragmentState.ACKNOWLEDGED);
			} else if (sourceBlockDecoder.containsSourceSymbol(esi)) {
				states.add(FragmentState.RECEIVED);
			} else {
				states.add(FragmentState.INITIAL);
			}
		}
		return states;
	}

}
