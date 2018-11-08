package fr.an.qrcode.channel.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.SwingWorker;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.an.qrcode.channel.impl.decode.DecoderChannelEvent;
import fr.an.qrcode.channel.impl.decode.DecoderChannelListener;
import fr.an.qrcode.channel.impl.decode.QRCodesDecoderChannel.SnapshotFragmentResult;
import fr.an.qrcode.channel.ui.QRCodeDecoderChannelModel.ImageProviderMode;
import fr.an.qrcode.channel.ui.utils.ImageCanvas;
import fr.an.qrcode.channel.ui.utils.TransparentFrameScreenArea;

/**
 * a "QRCode(s) Decoder Channel to Text" view
 * 
 */
public class QRCodeDecoderChannelView {

	private static Logger LOG = LoggerFactory.getLogger(QRCodeDecoderChannelView.class);
	
    private QRCodeDecoderChannelModel model = new QRCodeDecoderChannelModel();
    
    private DecoderChannelListener uiEventListener = e -> onDecoderChannelEvent(e);
    private PropertyChangeListener propChangeListener = (evt) -> onModelPropChangeEvent(evt);

    private JPanel mainComponent;
    private JTabbedPane tabbedPane;

    private JPanel recorderTabPanel;
    private JToolBar recorderToolbar;
    private JTextField recordAreaField;
    private ButtonGroup sourceButtonGroup;
    private JRadioButton sourceScreenshotRadioButton;
    private JRadioButton sourceWebcamRadioButton;

    // for webcam recording
    private JPanel webcamParamsPanel;
    
    // for screenshot recording
    private JPanel screenshotParamsPanel;
    private JButton revealRecordAreaButton;
    
    private JButton resetButton;
    private JButton nextQRCodeButton;
    private JButton loopQRCodeButton;
    private JButton stopLoopQRCodeButton;
    private JButton clearTextButton;
    private JButton updateTextButton;
    private JTextField fileNameField;
    private JButton saveFileButton;

    private JScrollPane outputTextScrollPane;
    private JTextArea outputTextArea;

    private JPanel detailImageTabPanel;
    private ImageCanvas qrCodeImageCanvas;
    
    private JPanel infoPanel;
    private JLabel currentDecodeMessageLabel;
    private JLabel currentDecodeTimeMillisLabel;
    private JLabel currentChannelSequenceNumberLabel;
    private JTextArea currentAheadFragsInfoArea;
    private JLabel saveFileMessageLabel;

    // ------------------------------------------------------------------------

    public QRCodeDecoderChannelView(QRCodeDecoderChannelModel model) {
    	this.model = model;
        initUI();
        if (model != null) {
        	model.addPropertyChangeListener(propChangeListener);
        	model.setUiEventListener(uiEventListener);
        }
    }

    // ------------------------------------------------------------------------
    
	public Component getJComponent() {
		return mainComponent;
	}

    private void initUI() {
    	mainComponent = new JPanel(new BorderLayout());
    	
        tabbedPane = new JTabbedPane();
        mainComponent.add(tabbedPane, BorderLayout.CENTER);

        { // recorderTabPanel
            recorderTabPanel = new JPanel(new BorderLayout());

            recorderToolbar = new JToolBar();
            recorderTabPanel.add(recorderToolbar, BorderLayout.NORTH);
            
            resetButton = new JButton("Reset");
            resetButton.addActionListener(e -> onReset());
            recorderToolbar.add(resetButton);

            nextQRCodeButton = new JButton("Take Snapshot");
            recorderToolbar.add(nextQRCodeButton);
            nextQRCodeButton.addActionListener(e -> model.takeSnapshot());

            loopQRCodeButton = new JButton("Start Listen");
            recorderToolbar.add(loopQRCodeButton);
            loopQRCodeButton.addActionListener(e -> model.startListenSnapshots());
            
            stopLoopQRCodeButton = new JButton("Stop");
            recorderToolbar.add(stopLoopQRCodeButton);
            stopLoopQRCodeButton.addActionListener(e -> model.stopListenSnapshots());
                        
            recordAreaField = new JTextField();
            recorderToolbar.add(recordAreaField);
            
            sourceScreenshotRadioButton = new JRadioButton("screen");
            recorderToolbar.add(sourceScreenshotRadioButton);
            sourceScreenshotRadioButton.setSelected(true);
            sourceScreenshotRadioButton.addActionListener(e -> model.setImageProviderMode(ImageProviderMode.DesktopScreenshot));

            sourceWebcamRadioButton = new JRadioButton("webcam");
            recorderToolbar.add(sourceWebcamRadioButton);
            sourceWebcamRadioButton.addActionListener(e -> model.setImageProviderMode(ImageProviderMode.WebCam));
            
            sourceButtonGroup = new ButtonGroup();
            sourceButtonGroup.add(sourceScreenshotRadioButton);
            sourceButtonGroup.add(sourceWebcamRadioButton);
            {
            	webcamParamsPanel = new JPanel();
            	recorderToolbar.add(webcamParamsPanel);
            }
            {
            	screenshotParamsPanel = new JPanel();
            	recorderToolbar.add(screenshotParamsPanel);
            }

            revealRecordAreaButton = new JButton("reveal area");
            revealRecordAreaButton.addActionListener(e -> onRevealRecordAreaAction());
            recorderToolbar.add(revealRecordAreaButton);
            
            clearTextButton = new JButton("ClearText");
            recorderToolbar.add(clearTextButton);
            clearTextButton.addActionListener(e -> onClearTextAction());

            updateTextButton = new JButton("Update");
            updateTextButton.addActionListener(e -> onSetTextAction());
            recorderToolbar.add(updateTextButton);

            fileNameField = new JTextField();
            recorderToolbar.add(fileNameField);

            saveFileButton = new JButton("Save");
            saveFileButton.addActionListener(e -> onSaveFileAction());
            recorderToolbar.add(saveFileButton);
            
            outputTextArea = new JTextArea();
            outputTextScrollPane = new JScrollPane(outputTextArea);
            recorderTabPanel.add(outputTextScrollPane, BorderLayout.CENTER);
            
        }
        
        { // detailImageTabPanel
            detailImageTabPanel = new JPanel(new BorderLayout());

            qrCodeImageCanvas = new ImageCanvas();
            qrCodeImageCanvas.setPreferredSize(new Dimension(400, 400));
            detailImageTabPanel.add(qrCodeImageCanvas, BorderLayout.CENTER);
        }
        
        tabbedPane.add("recorder", recorderTabPanel);        
        tabbedPane.add("img", detailImageTabPanel);

        infoPanel = new JPanel(new GridLayout(4, 1));
        { 
	        currentDecodeMessageLabel = new JLabel();
	        infoPanel.add(currentDecodeMessageLabel);
	        currentDecodeTimeMillisLabel = new JLabel();
	        infoPanel.add(currentDecodeTimeMillisLabel);	        
	        currentChannelSequenceNumberLabel = new JLabel();
	        infoPanel.add(currentChannelSequenceNumberLabel);
	        currentAheadFragsInfoArea = new JTextArea(1, 50);
	        infoPanel.add(currentAheadFragsInfoArea);
	        saveFileMessageLabel = new JLabel();
	        infoPanel.add(saveFileMessageLabel);
        }
        mainComponent.add(infoPanel, BorderLayout.SOUTH);
        
        model2view();
    }

    public void onReset() {
    	model.reset();
    	onClearTextAction();
    }
    
    private void onSetTextAction() {
        model.setFullText(outputTextArea.getText());
    }

    private void onClearTextAction() {
        model.setFullText("");
        outputTextArea.setText("");
    }
    
    public void onSaveFileAction() {
    	String fileName = fileNameField.getText();
    	if (fileName == null) {
    		fileName = "output.txt";
    		fileNameField.setText(fileName);
    	}
    	final File outputFile = new File(fileName);
    	final String content = model.getFullText();
    	new SwingWorker<String,Void>() {
			@Override
			protected String doInBackground() throws Exception {
				LOG.info("writing file: " + outputFile.getAbsolutePath());
				try {
					FileUtils.write(outputFile, content);
					return "done write file";
				} catch(Exception ex) {
					return "Failed to write file : " + ex.getMessage();
				}
			}
			@Override
			public void done() {
				try {
					String msg = get();
					saveFileMessageLabel.setText(msg);
				} catch (Exception e) {
				}
			}
    	}.execute();
    }
    
    protected TransparentFrameScreenArea recordAreaFrame;
    
    private void onRevealRecordAreaAction() {
        recordArea_viewToModel();
        if (recordAreaFrame == null) {
            recordAreaFrame = new TransparentFrameScreenArea();
            recordAreaFrame.setBounds(model.getRecordArea());
            recordAreaFrame.setVisible(true);
            recordAreaFrame.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    updateModelRecordArea();
                }

                @Override
                public void componentMoved(ComponentEvent e) {
                    updateModelRecordArea();
                }

                protected void updateModelRecordArea() {
                    Rectangle r = recordAreaFrame.getBounds();
                    model.setRecordArea(r);
                    recordArea_modelToView();
                }
            });
        } else {
            model.setRecordArea(recordAreaFrame.getBounds());
            recordAreaFrame.setVisible(false);
            recordAreaFrame.dispose();
            recordAreaFrame = null;
        }
    }

    
    
    private void recordArea_viewToModel() {
        String recordParamsText = recordAreaField.getText();
        model.parseRecordParamsText(recordParamsText);
    }

    private void recordArea_modelToView() {
        Rectangle r = model.getRecordArea();
        String coord = r.x + "," + r.y + "," + (int)r.getWidth() + "," + (int)r.getHeight();
        recordAreaField.setText(coord);
    }

	private void onModelPropChangeEvent(PropertyChangeEvent evt) {
		String prop = evt.getPropertyName();
		if (prop.equals("currentScreenshotImg")) {
			qrCodeImageCanvas.setImage(model.getCurrentScreenshotImg());
	        qrCodeImageCanvas.repaint();
		} else {
			// tochange? update all properties.. not efficient
			model2view(); 
		}
	}

    protected void onDecoderChannelEvent(DecoderChannelEvent event) {
    	model2view();
    }
    
    private void model2view() {
        recordArea_modelToView();
        
        SnapshotFragmentResult currentSnapshotResult = model.getCurrentSnapshotResult();
        currentDecodeMessageLabel.setText(currentSnapshotResult != null? currentSnapshotResult.decodeMsg : "");
        currentDecodeTimeMillisLabel.setText(currentSnapshotResult != null? currentSnapshotResult.millis + " ms" : "");
        
        String fullText = model.getFullText();
        outputTextArea.setText(fullText);
        
        currentChannelSequenceNumberLabel.setText("next seq number:" + (model.getChannelSequenceNumber()+1));
        currentAheadFragsInfoArea.setText(model.getAheadFragsInfo());
        
        qrCodeImageCanvas.setImage(model.getCurrentScreenshotImg());
        qrCodeImageCanvas.repaint();
        
        // saveFileMessageLabel.setText();
    }

}
