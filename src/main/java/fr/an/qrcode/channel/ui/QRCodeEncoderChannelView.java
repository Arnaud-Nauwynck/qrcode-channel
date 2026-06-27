package fr.an.qrcode.channel.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToolBar;

import org.apache.commons.io.FileUtils;

import fr.an.qrcode.channel.impl.QREncodeSetting;
import fr.an.qrcode.channel.impl.encode.FragmentImg;
import fr.an.qrcode.channel.impl.encode.QRCodeEncodedFragment;
import fr.an.qrcode.channel.ui.QRCodeEncoderChannelModel.DisplayMode;
import fr.an.qrcode.channel.ui.utils.ImageCanvas;
import fr.an.qrcode.channel.ui.utils.SquaresStripPanel;

/**
 * a "Text to QRCode(s)" player view (with main application)
 *
 */
public class QRCodeEncoderChannelView {

    private QRCodeEncoderChannelModel model;

    private PropertyChangeListener listener = (evt) -> onModelPropChangeEvent(evt);

    private JTabbedPane tabbedPane;

    private JPanel inputTabPanel;
    private JToolBar inputToolbar;
    private JTextField inputFilenameField;
//    private JTextField imageSizeField;
    private JButton computeQRCodeButton;
    private JScrollPane inputTextScrollPane;
    private JTextArea inputTextArea;

    private JPanel playerTabPanel;
    private JToolBar playerToolbar;
    private JCheckBox rgbSplitModeCheckBox;
    private JButton prevQRCodeButton;
    private JTextField qrCodeNumberField;
    private JLabel channelSeqNumberLabel;
    private JButton nextQRCodeButton;

    private JTextField millisBetweenImageField;
    private JButton playQRCodeButton;
    private JButton stopQRCodeButton;

    private JLabel acknowledgeInfoLabel;
    private JTextField acknowledgeSeqNumberField;
    private JTextField acknowledgeAddField;

    private ImageCanvas qrCodeImageCanvas;

    private JPanel qrDetailPanel;
    private JToolBar qrDetailHeaderToolbar;
    private JCheckBox qrDetailShowAllChannelsCheckBox;
    private JToolBar qrDetailSingleLineToolbar;
    private JTextField qrDetailHeaderText;
    private JTextField qrDetailDataText;
    private JPanel qrDetailAllChannelsGridPanel;
    private JLabel[] qrDetailAllChannelsHeaderLabels;
    private JLabel[] qrDetailAllChannelsDataLabels;
    // private JTextField qrDetailDuplexMetadataText;

    private SquaresStripPanel<FragmentImg> fragmentsStatusPanel;

    private Calib3dChartPanel calib3dChartPanel;

    // ------------------------------------------------------------------------

    public QRCodeEncoderChannelView(QRCodeEncoderChannelModel model) {
    	this.model = model;
        initUI();
        model.addPropertyChangeListener(listener);
    }

    // ------------------------------------------------------------------------

	private void onModelPropChangeEvent(PropertyChangeEvent evt) {
		if (evt.getPropertyName().equals("text")) {
			inputTextArea.setText(model.getText());
			model2view();
		} else {
			model2view();
		}
	}

	public JComponent getJComponent() {
		return tabbedPane;
	}

    private void initUI() {
        tabbedPane = new JTabbedPane();

        { // inputTabPanel
            inputTabPanel = new JPanel(new BorderLayout());

            inputToolbar = new JToolBar();
            inputTabPanel.add(inputToolbar, BorderLayout.NORTH);

            inputFilenameField = new JTextField();
            inputFilenameField.addKeyListener(new KeyAdapter() {
            	public void keyPressed(KeyEvent event) {
            		if (event.getKeyCode() == KeyEvent.VK_ENTER) {
            			onInputFilenameEntered();
            		}
            	}
            });
            inputToolbar.add(inputFilenameField);

//            imageSizeField = new JTextField();
//            inputToolbar.add(imageSizeField);

            computeQRCodeButton = new JButton("compute QRCode(s)");
            inputToolbar.add(computeQRCodeButton);
            computeQRCodeButton.addActionListener(e -> onComputeQRCodesAction());

            inputTextArea = new JTextArea();
            inputTextScrollPane = new JScrollPane(inputTextArea);
            inputTabPanel.add(inputTextScrollPane, BorderLayout.CENTER);
        }

        { // playerTabPanel
            playerTabPanel = new JPanel(new BorderLayout());

            playerToolbar = new JToolBar();
            playerTabPanel.add(playerToolbar, BorderLayout.NORTH);

            rgbSplitModeCheckBox = new JCheckBox("3 QRCodes (RGB split)");
            rgbSplitModeCheckBox.setSelected(model.getDisplayMode() == DisplayMode.RGB_SPLIT);
            rgbSplitModeCheckBox.addActionListener(e -> {
            	model.setDisplayMode(rgbSplitModeCheckBox.isSelected() ? DisplayMode.RGB_SPLIT : DisplayMode.BLACK_WHITE);
            	model2view();
            });
            playerToolbar.add(rgbSplitModeCheckBox);

            JLabel numLabel = new JLabel("num: ");
            playerToolbar.add(numLabel);

            qrCodeNumberField = new JTextField(3);
            Dimension prefSize = qrCodeNumberField.getPreferredSize();
            prefSize.setSize(3*20, prefSize.getHeight());
            qrCodeNumberField.setPreferredSize(prefSize);
            qrCodeNumberField.addKeyListener(new KeyAdapter() {
            	public void keyPressed(KeyEvent event) {
            		if (event.getKeyCode() == KeyEvent.VK_ENTER) {
            			int n = Integer.parseInt(qrCodeNumberField.getText());
            			model.onDisplayFrag(n);
            		}
            	}
            });
            playerToolbar.add(qrCodeNumberField);

            channelSeqNumberLabel = new JLabel();
            playerToolbar.add(channelSeqNumberLabel);

            prevQRCodeButton = new JButton("<");
            playerToolbar.add(prevQRCodeButton);
            prevQRCodeButton.addActionListener(e -> model.onDisplayPrevFrag());

            nextQRCodeButton = new JButton(">");
            nextQRCodeButton.addActionListener(e -> model.onDisplayNextFrag());
            playerToolbar.add(nextQRCodeButton);

            JLabel millisBetweenImageLabel = new JLabel(" ms/img: ");
            playerToolbar.add(millisBetweenImageLabel);

            millisBetweenImageField = new JTextField(3);
            // millisBetweenImageField.setPreferredSize(new Dimension(50, 30));
            playerToolbar.add(millisBetweenImageField);
            millisBetweenImageField.addKeyListener(new KeyAdapter() {
            	public void keyPressed(KeyEvent event) {
            		if (event.getKeyCode() == KeyEvent.VK_ENTER) {
            			long millis = Long.parseLong(millisBetweenImageField.getText());
            			model.setMillisBetweenImg(millis);
            		}
            	}
            });

            playQRCodeButton = new JButton("Start");
            playQRCodeButton.addActionListener(e -> model.startDisplayLoop());
            playerToolbar.add(playQRCodeButton);

            //TODO
//            JButton rewindToAckButton = new JButton("Rewind");
//            rewindToAckButton.addActionListener(e -> model.rewindToAck());
//            playerToolbar.add(rewindToAckButton);

            stopQRCodeButton = new JButton("Stop");
            stopQRCodeButton.addActionListener(e -> model.stopDisplayLoop());
            playerToolbar.add(stopQRCodeButton);


            acknowledgeInfoLabel = new JLabel(" ack:");
            playerToolbar.add(acknowledgeInfoLabel);

            acknowledgeSeqNumberField = new JTextField(3);
            acknowledgeSeqNumberField.addKeyListener(new KeyAdapter() {
            	public void keyPressed(KeyEvent event) {
            		if (event.getKeyCode() == KeyEvent.VK_ENTER) {
            			int seq = Integer.parseInt(acknowledgeSeqNumberField.getText());
            			model.setAckSeqNumber(seq);
            		}
            	}
            });

            playerToolbar.add(createAckButton(5));
            playerToolbar.add(createAckButton(10));
            playerToolbar.add(createAckButton(20));
            playerToolbar.add(createAckButton(50));


            acknowledgeAddField = new JTextField();
            acknowledgeAddField.addKeyListener(new KeyAdapter() {
            	public void keyPressed(KeyEvent event) {
            		if (event.getKeyCode() == KeyEvent.VK_ENTER) {
            			String text = acknowledgeAddField.getText();
            			model.addAcknowledge(text);
            		}
            	}
            });
            playerToolbar.add(acknowledgeAddField);


            qrCodeImageCanvas = new ImageCanvas();
            qrCodeImageCanvas.setPreferredSize(new Dimension(250, 250));
            JPanel qrCodeImageHolderPanel = new JPanel();
            qrCodeImageHolderPanel.add(qrCodeImageCanvas);
            playerTabPanel.add(qrCodeImageHolderPanel, BorderLayout.CENTER);


            qrDetailPanel = new JPanel(new BorderLayout());

            qrDetailHeaderToolbar = new JToolBar();
            qrDetailHeaderToolbar.add(new JLabel("data:"));

            qrDetailShowAllChannelsCheckBox = new JCheckBox("show all 3");
            qrDetailShowAllChannelsCheckBox.addActionListener(e -> model2view());
            qrDetailHeaderToolbar.add(qrDetailShowAllChannelsCheckBox);

            qrDetailPanel.add(qrDetailHeaderToolbar, BorderLayout.NORTH);

            qrDetailSingleLineToolbar = new JToolBar();
            qrDetailHeaderText = new JTextField();
            qrDetailSingleLineToolbar.add(qrDetailHeaderText);
            qrDetailDataText = new JTextField();
            qrDetailSingleLineToolbar.add(qrDetailDataText);
//            qrDetailDuplexMetadataText = new JTextField();
//            qrDetailSingleLineToolbar.add(qrDetailDuplexMetadataText);

            int maxChannels = 3;
            qrDetailAllChannelsGridPanel = new JPanel(new GridLayout(maxChannels, 2));
            qrDetailAllChannelsHeaderLabels = new JLabel[maxChannels];
            qrDetailAllChannelsDataLabels = new JLabel[maxChannels];
            for (int i = 0; i < maxChannels; i++) {
            	qrDetailAllChannelsHeaderLabels[i] = new JLabel();
            	qrDetailAllChannelsDataLabels[i] = new JLabel();
            	qrDetailAllChannelsGridPanel.add(qrDetailAllChannelsHeaderLabels[i]);
            	qrDetailAllChannelsGridPanel.add(qrDetailAllChannelsDataLabels[i]);
            }

            qrDetailPanel.add(qrDetailSingleLineToolbar, BorderLayout.CENTER);

            fragmentsStatusPanel = new SquaresStripPanel<>(5, 1, QRCodeEncoderChannelView::colorForEncodedFragment);
            JScrollPane fragmentsStatusScrollPane = new JScrollPane(fragmentsStatusPanel);
            fragmentsStatusScrollPane.setPreferredSize(new Dimension(0, 30));
            fragmentsStatusScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            fragmentsStatusScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);

            JPanel southPanel = new JPanel(new BorderLayout());
            southPanel.add(qrDetailPanel, BorderLayout.NORTH);
            southPanel.add(fragmentsStatusScrollPane, BorderLayout.SOUTH);
            playerTabPanel.add(southPanel, BorderLayout.SOUTH);
        }

        calib3dChartPanel = new Calib3dChartPanel();

        tabbedPane.add("input", inputTabPanel);
        tabbedPane.add("player", playerTabPanel);
        tabbedPane.add("calib3d-chart", calib3dChartPanel.getComp());

        model2view();
    }

    protected JButton createAckButton(int count) {
    	JButton button = new JButton("" + count);
    	button.addActionListener(e -> model.incrAckSeqNumber(count));
    	return button;
    }

    public void onInputFilenameEntered() {
    	String inputFilename = inputFilenameField.getText();
    	if (inputFilename == null || inputFilename.isEmpty()) {
    		inputFilename = "input.txt";
    		inputFilenameField.setText(inputFilename);
    	}
    	File file = new File(inputFilename);
    	if (!file.exists()) {
    		return;
    	}
    	if (!file.canRead()) {
    		return;
    	}
    	String inputTextContent;
		try {
			inputTextContent = FileUtils.readFileToString(file);
		} catch (IOException e) {
			return;
		}
    	inputTextArea.setText(inputTextContent);
    	// inputTextArea.setEditable(false);
    	onComputeQRCodesAction();
    }

    private void onComputeQRCodesAction() {
//        String[] qrCodeDimText = imageSizeField.getText().split(",");
//        int w = Integer.parseInt(qrCodeDimText[0]);
//        int h = Integer.parseInt(qrCodeDimText[1]);
    	
    	String inputTextContent = inputTextArea.getText();

        model.computeQRCodes(inputTextContent);

        // model.setCurrentQRCodeFragmentIndex(0);

        tabbedPane.setSelectedIndex(1);
        model2view();
    }


    private void model2view() {
        // imageSizeField.setText(model.getQrCodeW() + "," + model.getQrCodeH());

    	List<FragmentImg> group = model.getCurrentDisplayGroup();
    	FragmentImg fragImg = group.isEmpty()? null : group.get(0);

        String fragId = fragImg != null? "" + fragImg.getFragmentNumber() : "";
        qrCodeNumberField.setText(fragId);

        int fragmentsSeqNumber = model.getChannelNextSequenceNumber();
        channelSeqNumberLabel.setText("/"+ fragmentsSeqNumber);

        String acknowledgeInfo = model.getAcknowledgeInfo();
        acknowledgeInfoLabel.setText(acknowledgeInfo);

        BufferedImage img = model.getCurrentFragmentImg();
        qrCodeImageCanvas.setImage(img);

        qrDetailShowAllChannelsCheckBox.setEnabled(group.size() > 1);
        updateDetailTexts(group);

        fragmentsStatusPanel.refresh(model.getFragmentImgsList());
    }

    private void updateDetailTexts(List<FragmentImg> group) {
    	boolean showAll = qrDetailShowAllChannelsCheckBox.isSelected() && group.size() > 1;

    	JComponent currentCenter = showAll ? qrDetailAllChannelsGridPanel : qrDetailSingleLineToolbar;
    	if (qrDetailPanel.getComponentCount() < 2 || qrDetailPanel.getComponent(1) != currentCenter) {
    		qrDetailPanel.removeAll();
    		qrDetailPanel.add(qrDetailHeaderToolbar, BorderLayout.NORTH);
    		qrDetailPanel.add(currentCenter, BorderLayout.CENTER);
    		qrDetailPanel.revalidate();
    		qrDetailPanel.repaint();
    	}

    	if (showAll) {
    		int maxChannels = qrDetailAllChannelsHeaderLabels.length;
    		for (int i = 0; i < maxChannels; i++) {
    			if (i < group.size()) {
    				QRCodeEncodedFragment frag = group.get(i).owner;
    				String headerOneLine = frag.getHeader().replace("\n", "");
    				qrDetailAllChannelsHeaderLabels[i].setText(headerOneLine.length() + " " + headerOneLine);
    				qrDetailAllChannelsDataLabels[i].setText(frag.getData().length + " "
    						+ new String(frag.getData(), java.nio.charset.StandardCharsets.ISO_8859_1));
    			} else {
    				qrDetailAllChannelsHeaderLabels[i].setText("");
    				qrDetailAllChannelsDataLabels[i].setText("");
    			}
    		}
    	} else {
    		FragmentImg fragImg = group.isEmpty() ? null : group.get(0);
    		QRCodeEncodedFragment frag = fragImg != null ? fragImg.owner : null;
    		qrDetailHeaderText.setText(frag != null ? frag.getHeader().length() + " " + frag.getHeader() : "");
    		qrDetailDataText.setText(frag != null
    				? frag.getData().length + " " + new String(frag.getData(), java.nio.charset.StandardCharsets.ISO_8859_1) : "");
    	}
    }

    /** grey if acknowledged, else colored from pale to deep orange/red by sent-count intensity */
    private static Color colorForEncodedFragment(FragmentImg frag) {
    	if (frag.isAcknowledge()) {
    		return Color.LIGHT_GRAY;
    	}
    	int sentCount = frag.getSentPlainCount() + frag.getSentXor2Count() + frag.getSentXor3Count();
    	float intensity = 1f - 1f / (1f + sentCount);
    	int green = Math.round(255 * (1f - intensity));
    	return new Color(255, green, 0);
    }

}
