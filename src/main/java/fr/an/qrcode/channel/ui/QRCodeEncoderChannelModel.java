package fr.an.qrcode.channel.ui;

import java.awt.image.BufferedImage;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.SwingUtilities;
import javax.swing.event.SwingPropertyChangeSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.an.qrcode.channel.impl.QREncodeSetting;
import fr.an.qrcode.channel.impl.encode.FragmentImg;
import fr.an.qrcode.channel.impl.encode.QRCodeEncodedFragment;
import fr.an.qrcode.channel.impl.encode.QRCodesEncoderChannel;

/**
 * model associated to QRCodeEncoderChannelView<BR/>
 */
public class QRCodeEncoderChannelModel {

	private static final Logger LOG = LoggerFactory.getLogger(QRCodeEncoderChannelModel.class);

	public enum DisplayMode {
		BLACK_WHITE("1 QRCode (black/white)"),
		RGB_SPLIT("3 QRCodes (RGB split)");

		private final String label;

		DisplayMode(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

    private SwingPropertyChangeSupport pcs = new SwingPropertyChangeSupport(this);

	private QREncodeSetting encodeSetting;

	private QRCodesEncoderChannel encoderChannel;

    private String text;

    private DisplayMode displayMode = DisplayMode.BLACK_WHITE;

    /** the fragment(s) currently shown: 1 in BLACK_WHITE mode, up to 3 in RGB_SPLIT mode */
    private List<FragmentImg> currentDisplayGroup = new ArrayList<>();
    private BufferedImage currentDisplayImg;
    protected int currDisplayIndex;

    private ExecutorService displayExecutor = Executors.newSingleThreadExecutor();
    protected AtomicBoolean displayLoopRunning = new AtomicBoolean(false);
    protected AtomicBoolean displayLoopStopRequested = new AtomicBoolean(false);
	protected long millisBetweenImg = 300;

	// computed imgs
	protected Map<Integer,FragmentImg> fragmentImgs;

	private int ackSeqNumber = 0;

    // ------------------------------------------------------------------------

    public QRCodeEncoderChannelModel(QREncodeSetting encodeSetting) {
    	this.encodeSetting = encodeSetting;
    }

    // ------------------------------------------------------------------------

    public void computeQRCodes(String textContent) {
    	this.text = textContent;
    	this.encoderChannel = new QRCodesEncoderChannel(encodeSetting);
    	encoderChannel.appendFragmentsFor(textContent);

    	// compute..
    	this.fragmentImgs = encoderChannel.getFragmentImgs();

    	this.currDisplayIndex = 0;
    	setCurrentDisplayGroupAt(1);

    	pcs.firePropertyChange("text", null, text);
    }

    public DisplayMode getDisplayMode() {
    	return displayMode;
    }

    public void setDisplayMode(DisplayMode mode) {
    	if (mode == this.displayMode) {
    		return;
    	}
    	this.displayMode = mode;
    	if (!currentDisplayGroup.isEmpty()) {
    		setCurrentDisplayGroupAt(currentDisplayGroup.get(0).getFragmentNumber());
    	}
    }

    private int groupSize() {
    	return displayMode == DisplayMode.RGB_SPLIT ? 3 : 1;
    }

    /** builds the display group of `groupSize()` consecutive fragments starting at fragNum (clamped to available fragments), and updates the displayed image */
    private void setCurrentDisplayGroupAt(int fragNum) {
    	if (fragmentImgs == null || fragmentImgs.isEmpty()) {
    		return;
    	}
    	int n = Math.max(1, Math.min(fragNum, fragmentImgs.size()));
    	List<FragmentImg> group = new ArrayList<>();
    	for (int i = n; i < n + groupSize() && i <= fragmentImgs.size(); i++) {
    		FragmentImg frag = fragmentImgs.get(i);
    		if (frag != null) {
    			group.add(frag);
    		}
    	}
    	setCurrentDisplayGroup(group);
    }


    public void startDisplayLoop() {
    	if (displayLoopRunning.get()) {
    		return;
    	}
        displayLoopRunning.set(true);
        displayLoopStopRequested.set(false);
        displayExecutor.submit(() -> runPlayThread());
    }

    public void stopDisplayLoop() {
    	if (! displayLoopRunning.get()) {
    		return;
    	}
        displayLoopStopRequested.set(true);
    }

    protected void runPlayThread() {
    	int groupSize = groupSize();

    	// first few groups.. to autocalibrate webcam brightness..
    	int firstPreviewGroups = 15;
    	int groupCount = 0;
    	for (int fragNum = 1; fragNum <= fragmentImgs.size(); fragNum += groupSize) {
    		if (isGroupFullyAcknowledged(fragNum, groupSize)) {
    			continue;
    		}
			groupCount++;
			if (groupCount > firstPreviewGroups) {
				break;
			}

    		final int n = fragNum;
    		try {
	    		SwingUtilities.invokeAndWait(() -> setCurrentDisplayGroupAt(n));
			} catch (Exception e) {
			}
			try {
				Thread.sleep(millisBetweenImg);
			} catch (InterruptedException e) {
			}
			if (displayLoopStopRequested.get()) {
				displayLoopRunning.set(false);
				return;
			}
		}

    	for (int fragNum = 1; fragNum <= fragmentImgs.size(); fragNum += groupSize) {
    		if (isGroupFullyAcknowledged(fragNum, groupSize)) {
    			continue;
    		}
    		final int n = fragNum;
			try {
				SwingUtilities.invokeAndWait(() -> setCurrentDisplayGroupAt(n));
			} catch (Exception e) {
			}
			try {
				Thread.sleep(millisBetweenImg);
			} catch (InterruptedException e) {
			}
			if (displayLoopStopRequested.get()) {
				break;
			}
		}

    	// finally, cycle through redundancy combo fragments (always shown individually, in black/white)
    	for (QRCodeEncodedFragment combo : encoderChannel.getComboFragments()) {
			try {
				SwingUtilities.invokeAndWait(() -> setCurrentDisplayGroup(Collections.singletonList(combo.getFragmentImg())));
			} catch (Exception e) {
			}
			try {
				Thread.sleep(millisBetweenImg);
			} catch (InterruptedException e) {
			}
			if (displayLoopStopRequested.get()) {
				break;
			}
		}

    	displayLoopRunning.set(false);
    }

    private boolean isGroupFullyAcknowledged(int fragNum, int groupSize) {
    	for (int i = fragNum; i < fragNum + groupSize && i <= fragmentImgs.size(); i++) {
    		FragmentImg frag = fragmentImgs.get(i);
    		if (frag != null && !frag.isAcknowledge()) {
    			return false;
    		}
    	}
    	return true;
    }

    public void onDisplayNextFrag() {
    	if (fragmentImgs == null) {
    		// should click "compute" before!
    		return;
    	}
    	int groupSize = groupSize();
    	int n = currDisplayIndex + groupSize;
    	while (n <= fragmentImgs.size()) {
    		if (!isGroupFullyAcknowledged(n, groupSize)) {
    			setCurrentDisplayGroupAt(n);
    			return;
    		}
    		n += groupSize;
    	}
    }

	public void rewindToAck() {
		// TODO
	}



	public void onDisplayFrag(int n) {
		setCurrentDisplayGroupAt(n);
	}

    public void onDisplayPrevFrag() {
    	if (fragmentImgs == null) {
    		return;
    	}
    	int groupSize = groupSize();
    	int n = currDisplayIndex - groupSize;
    	while (n >= 1) {
    		if (!isGroupFullyAcknowledged(n, groupSize)) {
    			setCurrentDisplayGroupAt(n);
    			return;
    		}
    		n -= groupSize;
    	}
    }

	public void addAcknowledge(String text) {
		String[] tokens = text.split("\\s");
		for(String token : tokens) {
			try {
				int rangeSep = token.indexOf("-");
				if (rangeSep == -1) {
					int n = Integer.parseInt(token);
					addAcknowledgeFrag(n);
				} else {
					String fromText = token.substring(0, rangeSep);
					String toText = token.substring(rangeSep+1, token.length());
					int from = Integer.parseInt(fromText);
					int toIncluded = Integer.parseInt(toText);
					for(int i = from; i < toIncluded; i++) {
						addAcknowledgeFrag(i);
					}
				}
			} catch(NumberFormatException ex) {
				// unrecognized ack text!..ignore
				LOG.error("");
			}
		}
		pcs.firePropertyChange("fragmentImgs", null, fragmentImgs);
	}
	
	public int getAckSeqNumber() {
		return ackSeqNumber;
	}
	
	public void setAckSeqNumber(int num) {
		ackSeqNumber = num;

    	for(FragmentImg frag : fragmentImgs.values()) {
    		if (frag.getFragmentNumber() < num) {
    			frag.acknowledge();
    		}
    	}
		pcs.firePropertyChange("fragmentImgs", null, fragmentImgs);
	}

	public void incrAckSeqNumber(int count) {
		setAckSeqNumber(ackSeqNumber + count);
	}
	
    private void addAcknowledgeFrag(int fragNum) {
    	if (ackSeqNumber+1 == fragNum) {
    		ackSeqNumber++;
    	}
    	
    	FragmentImg frag = fragmentImgs.get(fragNum);
    	if (frag != null) {
			frag.acknowledge();
    	}
	}


	// ------------------------------------------------------------------------

    public void addPropertyChangeListener(PropertyChangeListener listener) {
		pcs.addPropertyChangeListener(listener);
	}

	public void removePropertyChangeListener(PropertyChangeListener listener) {
		pcs.removePropertyChangeListener(listener);
	}
	
	public QREncodeSetting getEncodeSetting() {
		return encodeSetting;
	}

	public QRCodesEncoderChannel getEncoderChannel() {
		return encoderChannel;
	}

	public String getText() {
		return text;
	}

	public int getChannelNextSequenceNumber() {
		return encoderChannel != null? encoderChannel.getNextSequenceNumber() : 0;
	}
	
	/** the first (or only) fragment of the currently displayed group, e.g. for header/data detail display */
	public FragmentImg getCurrentDisplayFragment() {
		return currentDisplayGroup.isEmpty() ? null : currentDisplayGroup.get(0);
	}

	public List<FragmentImg> getCurrentDisplayGroup() {
		return currentDisplayGroup;
	}

	public void setCurrentDisplayGroup(List<FragmentImg> group) {
		List<FragmentImg> old = currentDisplayGroup;
		this.currentDisplayGroup = group;
		if (!group.isEmpty()) {
			currDisplayIndex = group.get(0).getFragmentNumber();
		}
		this.currentDisplayImg = computeDisplayImg(group);
		pcs.firePropertyChange("currentDisplayGroup", old, currentDisplayGroup);
	}

	private BufferedImage computeDisplayImg(List<FragmentImg> group) {
		if (group.isEmpty()) {
			return null;
		}
		if (group.size() == 1 || displayMode != DisplayMode.RGB_SPLIT) {
			return group.get(0).img;
		}
		QRCodeEncodedFragment red = group.get(0).owner;
		QRCodeEncodedFragment green = group.size() > 1 ? group.get(1).owner : null;
		QRCodeEncodedFragment blue = group.size() > 2 ? group.get(2).owner : null;
		return encoderChannel.encodeAndRenderRgbSplit(red, green, blue);
	}

	public int getCurrentDisplayFragmentNumber() {
		FragmentImg first = getCurrentDisplayFragment();
		return first != null? first.getFragmentNumber() : 0;
	}

	public BufferedImage getCurrentFragmentImg() {
		return currentDisplayImg;
	}

	public String getAcknowledgeInfo() {
		StringBuilder sb = new StringBuilder();
        if (fragmentImgs != null) {
        	int countNoAck = 0, minNoAck = Integer.MAX_VALUE, maxNoAck = -1;
        	for(FragmentImg frag : fragmentImgs.values()) {
        		if (! frag.isAcknowledge()) {
        			countNoAck++;
        			minNoAck = Math.min(minNoAck, frag.getFragmentNumber());
        			maxNoAck = Math.max(maxNoAck, frag.getFragmentNumber());
        		}
        	}
        	if (countNoAck == 0) {
        		sb.append("all ack");
        	} else if (countNoAck == 1) {
        		sb.append("frag not ack: " + minNoAck);
        	} else {
        		sb.append(countNoAck + " frags, in " + minNoAck + "-" + maxNoAck);
        	}
        } else {
        	
        }
		return sb.toString();
	}

	public void setMillisBetweenImg(long p) {
		this.millisBetweenImg = p;
	}

}
