package fr.an.qrcode.channel.impl.decode.filter;

public class QRDecodeRollingStats {
    
    private static long STAT_BUCKET_DURATION_MILLIS = 1000;
    private static int STAT_COUNT = 10;

    public static class Bucket {
    	long fromTime;
    	long millis; // STAT_BUCKET_DURATION_MILLIS
	    private int countImageDropped;
	    private int countQRRecognized;
	    private int countQRNotFound;
	    private int countQRFormatException;
	    private int countQRChecksumException;
	    private int countQRRecognizedDuplicate;
	    private int countQRPacketProtocolError;
		
	    
	    public int totalOkCount() {
	    	return countImageDropped + countQRRecognized + countQRRecognizedDuplicate;
	    }
	    public int totalErrCount() {
	    	return countQRNotFound + countQRFormatException + countQRChecksumException + countQRPacketProtocolError;
	    }
	    
	    public void clearStat() {
	    	fromTime = 0;
	    	millis = 0;
		    countImageDropped = 0;
		    countQRRecognized = 0;
		    countQRNotFound = 0;
		    countQRFormatException = 0;
		    countQRChecksumException = 0;
		    countQRRecognizedDuplicate = 0;
		    countQRPacketProtocolError = 0;
		}
	    private void add(Bucket src) {
	    	if (fromTime == 0) {
	    		fromTime = src.fromTime;
	    	} else if (src.fromTime != 0) {
	    		fromTime = Math.min(fromTime, src.fromTime);
	    	}
	    	this.millis += src.millis;
	    	countImageDropped += src.countImageDropped;
	    	countQRRecognized += src.countQRRecognized;
	    	countQRNotFound += src.countQRNotFound;
	    	countQRFormatException += src.countQRFormatException;
	    	countQRChecksumException += src.countQRChecksumException;
	    	countQRRecognizedDuplicate += src.countQRRecognizedDuplicate;
	    	countQRPacketProtocolError += src.countQRPacketProtocolError;
	    }
	    
		public void incrCountImageDropped() {
			countImageDropped++;
		}
		public void incrCountQRPacketRecognized() {
			countQRRecognized++;
		}
	    public void incrCountQRPacketNotFound() {
	    	countQRNotFound++;
	    }
	    public void incrCountQRPacketFormatException() {
	    	countQRFormatException++;
	    }
	    public void incrCountQRPacketChecksumException() {
	    	countQRChecksumException++;
	    }
	    public void incrCountQRPacketRecognizedDuplicate() {
	    	countQRRecognizedDuplicate++;
	    }
	    public void incrCountQRPacketProtocolError() {
	    	countQRPacketProtocolError++;
	    }

    }

    private Bucket currStat;
    private Bucket[] rollingStats = new Bucket[STAT_COUNT];
    private int currRollingStatIndex;
    
    // --------------------------------------------------------------------------------------------
    
    public QRDecodeRollingStats() {
	    for(int i = 0; i < STAT_COUNT; i++) {
	    	this.rollingStats[i] = new Bucket();
	    }
	    this.currRollingStatIndex = 0;
	    this.currStat = rollingStats[currRollingStatIndex];
    }
    
    // --------------------------------------------------------------------------------------------
    
	public void clearStats() {
		for(int i = 0; i < STAT_COUNT; i++) {
			rollingStats[i].clearStat();
		}
		currRollingStatIndex = 0;
	    currStat = rollingStats[currRollingStatIndex];
	}

	public void startBucket() {
		currStat.fromTime = System.currentTimeMillis();
	}

    public Bucket checkRoll() {
    	long millis = System.currentTimeMillis() - rollingStats[currRollingStatIndex].fromTime;
    	if (millis > STAT_BUCKET_DURATION_MILLIS) {
    		rollingStats[currRollingStatIndex].millis = millis;
	    	currRollingStatIndex = nextRollingStatIndex(currRollingStatIndex+1);
		    currStat = rollingStats[currRollingStatIndex];
		    currStat.clearStat();
		    currStat.fromTime = System.currentTimeMillis();
    	}
    	return currStat;
    }

    private int nextRollingStatIndex(int i) {
    	return (i+1) % STAT_COUNT;
    }


	public String getRecognitionStatsText() {
		Bucket sum = new Bucket();
		sum.fromTime = System.currentTimeMillis();
		for(int i = nextRollingStatIndex(currRollingStatIndex); i != currRollingStatIndex; i = nextRollingStatIndex(i)) {
		    sum.add(rollingStats[i]);
		}
		StringBuilder sb = new StringBuilder();
		int totalOk = sum.totalOkCount();
	    int totalErrCount = sum.totalErrCount();
		int total = totalOk + totalErrCount;
		double ratio = 1.0 / total;
		sb.append("(" + (sum.millis / 1000) + " s)");
		sb.append("ok:" + ratioText(ratio*sum.countQRRecognized));
		sb.append(" dup:" + ratioText(ratio*sum.countQRRecognizedDuplicate));
		sb.append(" dropped: " + ratioText(ratio*sum.countImageDropped));
		sb.append(" / err qr NotFound:" + ratioText(ratio*sum.countQRNotFound));
		sb.append(" Format:" + ratioText(ratio*sum.countQRFormatException));
		sb.append(" Checksum:" + ratioText(ratio*sum.countQRChecksumException));
		sb.append(" Protocol:" + ratioText(ratio*sum.countQRPacketProtocolError));

		return sb.toString();
	}

	private static String ratioText(double value) {
		return String.format("%2.1f", 100.0*value);
	}


}
