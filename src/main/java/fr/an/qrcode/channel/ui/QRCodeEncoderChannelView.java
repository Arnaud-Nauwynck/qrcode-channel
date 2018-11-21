package fr.an.qrcode.channel.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;

import javax.swing.JButton;
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
import fr.an.qrcode.channel.ui.utils.ImageCanvas;

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
    private JButton prevQRCodeButton;
    private JTextField qrCodeNumberField;
    private JLabel channelSeqNumberLabel;
    private JButton nextQRCodeButton;
    private JButton playQRCodeButton;
    private JButton stopQRCodeButton;
    private JLabel acknowledgeInfoLabel;
    private JTextField acknowledgeAddField;
    
    private ImageCanvas qrCodeImageCanvas;

    private JToolBar qrDetailPanel;
    private JTextField qrDetailHeaderText;
    private JTextField qrDetailDataText;
    // private JTextField qrDetailDuplexMetadataText;
    
    
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
                        
            playQRCodeButton = new JButton("Start");
            playQRCodeButton.addActionListener(e -> model.startDisplayLoop());
            playerToolbar.add(playQRCodeButton);

            stopQRCodeButton = new JButton("Stop");
            stopQRCodeButton.addActionListener(e -> model.stopDisplayLoop());
            playerToolbar.add(stopQRCodeButton);

            acknowledgeInfoLabel = new JLabel();
            playerToolbar.add(acknowledgeInfoLabel);
            
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
            int zoom = 2;
            QREncodeSetting qrSettings = model.getEncodeSetting();
            // qrCodeImageCanvas.setPreferredSize(new Dimension(zoom*qrSettings.getQrCodeW(), zoom*qrSettings.getQrCodeH()));
            playerTabPanel.add(qrCodeImageCanvas, BorderLayout.CENTER);


            qrDetailPanel = new JToolBar();
            qrDetailPanel.add(new JLabel("data:"));
            qrDetailHeaderText = new JTextField();
            qrDetailPanel.add(qrDetailHeaderText);
            qrDetailDataText = new JTextField();
            qrDetailPanel.add(qrDetailDataText);
//            qrDetailDuplexMetadataText = new JTextField();
//            qrDetailPanel.add(qrDetailDuplexMetadataText);
            
            playerTabPanel.add(qrDetailPanel, BorderLayout.SOUTH);
        }

        tabbedPane.add("input", inputTabPanel);
        tabbedPane.add("player", playerTabPanel);        

        model2view();
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
    	
    	FragmentImg fragImg = model.getCurrentDisplayFragment();
        QRCodeEncodedFragment frag = fragImg != null? fragImg.owner : null;
        
        String fragId = fragImg != null? "" + fragImg.getFragmentNumber() : "";
        qrCodeNumberField.setText(fragId);
        
        int fragmentsSeqNumber = model.getChannelSequenceNumber();
        channelSeqNumberLabel.setText("/"+ fragmentsSeqNumber);

        String acknowledgeInfo = model.getAcknowledgeInfo();
        acknowledgeInfoLabel.setText(acknowledgeInfo);
        
        BufferedImage img = fragImg != null? fragImg.img : null;
        qrCodeImageCanvas.setImage(img);
        
        qrCodeImageCanvas.repaint();
        
        qrDetailHeaderText.setText(frag != null? "" + frag.getHeader().length() + " " + frag.getHeader(): "");
        qrDetailDataText.setText(frag != null? "" + frag.getData().length() + " " + frag.getData(): "");
    }

}
