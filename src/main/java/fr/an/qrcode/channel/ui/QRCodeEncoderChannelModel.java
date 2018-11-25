package fr.an.qrcode.channel.ui;

import java.awt.image.BufferedImage;
import java.beans.PropertyChangeListener;
import java.util.Iterator;
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
import fr.an.qrcode.channel.impl.encode.QRCodesEncoderChannel;

/**
 * model associated to QRCodeEncoderChannelView<BR/>
 */
public class QRCodeEncoderChannelModel {
	
	private static final Logger LOG = LoggerFactory.getLogger(QRCodeEncoderChannelModel.class);
	
    private SwingPropertyChangeSupport pcs = new SwingPropertyChangeSupport(this);
    
	private QREncodeSetting encodeSetting;
    
	private QRCodesEncoderChannel encoderChannel;
    
    private String text;
    
    private FragmentImg currentDisplayFragment;
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

    	FragmentImg frag0 = !fragmentImgs.isEmpty()? fragmentImgs.get(0) : null; 
    	setCurrentDisplayFragment(frag0);
    	
    	pcs.firePropertyChange("text", null, text);
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
    	List<FragmentImg> fragmentImgs = encoderChannel.getNextFragmentImgs();
    	
    	// first 3.. to autocalibrate webcam brightness..
    	int firstPreview = 15;
    	int index = 0;
    	for (Iterator<FragmentImg> iterator = fragmentImgs.iterator(); iterator.hasNext();) {
			FragmentImg fragImg = iterator.next();
			if (fragImg.isAcknowledge()) {
				continue;
			}
			index++;
			if (index >= firstPreview) {
				break;
			}
			
    		try {
	    		SwingUtilities.invokeAndWait(() -> setCurrentDisplayFragment(fragImg));
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
    	
    	for(FragmentImg fragImg : fragmentImgs) {
			if (fragImg.isAcknowledge()) {
				continue;
			}
			try {
				SwingUtilities.invokeAndWait(() -> setCurrentDisplayFragment(fragImg));
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
    
    public void onDisplayNextFrag() {
    	if (fragmentImgs == null) {
    		// should click "compute" before!
    	}
    	if (fragmentImgs != null && currDisplayIndex+1 < fragmentImgs.size()) {
    		while(currDisplayIndex+1 < fragmentImgs.size()) {
    			FragmentImg fragImg = fragmentImgs.get(++currDisplayIndex);
	    		if (fragImg.isAcknowledge()) {
					continue;
				}
    			setCurrentDisplayFragment(fragImg);
	    		break;
    		}
    	}
    }

	public void rewindToAck() {
		// TODO
	}


	
	public void onDisplayFrag(int n) {
		this.currDisplayIndex = Math.max(0, Math.min(n, fragmentImgs.size()));
		setCurrentDisplayFragment(fragmentImgs.get(currDisplayIndex));
	}

    public void onDisplayPrevFrag() {
    	if (fragmentImgs != null && currDisplayIndex-1 >= 0) {
			while(currDisplayIndex > 0) {
				FragmentImg fragImg = fragmentImgs.get(--currDisplayIndex);
	    		if (fragImg == null || fragImg.isAcknowledge()) {
					continue;
				}
	    		setCurrentDisplayFragment(fragImg);
	    		break;
			}
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
	
	public FragmentImg getCurrentDisplayFragment() {
		return currentDisplayFragment;
	}

	public void setCurrentDisplayFragment(FragmentImg p) {
		FragmentImg old = currentDisplayFragment; 
		this.currentDisplayFragment = p;
		if (p != null) {
			currDisplayIndex = p.getFragmentNumber();
		}
		pcs.firePropertyChange("currentDisplayFragment", old, currentDisplayFragment);
	}

	public int getCurrentDisplayFragmentNumber() {
		return currentDisplayFragment != null? currentDisplayFragment.getFragmentNumber() : 0;
	}

	public BufferedImage getCurrentFragmentImg() {
		return currentDisplayFragment != null? currentDisplayFragment.img : null;
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
