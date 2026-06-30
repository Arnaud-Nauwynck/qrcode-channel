package fr.an.qrcode.channel.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.List;

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
import fr.an.qrcode.channel.impl.decode.QRCodesDecoderChannel.FragmentState;
import fr.an.qrcode.channel.impl.decode.QRResult;
import fr.an.qrcode.channel.impl.decode.filter.QRCapturedEvent;
import fr.an.qrcode.channel.impl.util.PtInt2D;
import fr.an.qrcode.channel.ui.QRCodeDecoderChannelModel.ImageProviderMode;
import fr.an.qrcode.channel.ui.utils.ImageCanvas;
import fr.an.qrcode.channel.ui.utils.SquaresStripPanel;
import fr.an.qrcode.channel.ui.utils.TransparentFrameScreenArea;

/**
 * a "QRCode(s) Decoder Channel to Text" view
 *
 * cf QRCodeDecoderChannelModel
 *    QRCodesDecoderChannel
 */
public class QRCodeDecoderChannelView {

	private static Logger LOG = LoggerFactory.getLogger(QRCodeDecoderChannelView.class);
	
    private QRCodeDecoderChannelModel model;

    private DecoderChannelListener uiEventListener = e -> onDecoderChannelEvent(e);
    private PropertyChangeListener propChangeListener = (evt) -> onModelPropChangeEvent(evt);

    private JPanel mainComponent;
    private JTabbedPane tabbedPane;

    private JPanel recorderTabPanel;
    private JToolBar recorderToolbar;
    private JTextField recordAreaField;
    private ButtonGroup sourceButtonGroup;
    private JRadioButton sourceScreenshotRadioButton;
    private JRadioButton sourceOpenCVRadioButton;
    private JRadioButton sourceWebcamRadioButton;
    private javax.swing.JCheckBox rgbSplitModeCheckBox;

    // for webcam recording
    private JPanel webcamParamsPanel;
    private javax.swing.JComboBox<com.github.sarxos.webcam.Webcam> webcamComboBox;

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
    private ImageCanvas qrCodeRedCanvas;
    private ImageCanvas qrCodeGreenCanvas;
    private ImageCanvas qrCodeBlueCanvas;
    private JTextArea qrCodeRedHistoryArea;
    private JTextArea qrCodeGreenHistoryArea;
    private JTextArea qrCodeBlueHistoryArea;

    private JPanel metadataTabPanel;
    private JTextArea metadataTextArea;


    private JPanel infoPanel;
    private JLabel currentDecodeMessageLabel;
    private JLabel currentDecodeTimeMillisLabel;
    private JLabel currentChannelSequenceNumberLabel;
    private JTextArea currentAheadFragsInfoArea;
    private JTextArea recognitionStatsText;

    private JLabel saveFileMessageLabel;

    private SquaresStripPanel<FragmentState> fragmentsStatusPanel;
    private SquaresStripPanel<FragmentState> repairFragmentsStatusPanel;

    private Calib3dView calib3dView;

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
            sourceOpenCVRadioButton = new JRadioButton("opencv");
            recorderToolbar.add(sourceOpenCVRadioButton);
            sourceWebcamRadioButton = new JRadioButton("webcam");
            recorderToolbar.add(sourceWebcamRadioButton);
            sourceScreenshotRadioButton.setSelected(model.getImageProviderMode() == ImageProviderMode.DesktopScreenshot);
            sourceOpenCVRadioButton.setSelected(model.getImageProviderMode() == ImageProviderMode.OpenCV);
            sourceWebcamRadioButton.setSelected(model.getImageProviderMode() == ImageProviderMode.WebCam);

            sourceScreenshotRadioButton.addActionListener(e -> model.setImageProviderMode(ImageProviderMode.DesktopScreenshot));
            sourceOpenCVRadioButton.addActionListener(e -> model.setImageProviderMode(ImageProviderMode.OpenCV));
            sourceWebcamRadioButton.addActionListener(e -> model.setImageProviderMode(ImageProviderMode.WebCam));

            sourceButtonGroup = new ButtonGroup();
            sourceButtonGroup.add(sourceScreenshotRadioButton);
            sourceButtonGroup.add(sourceOpenCVRadioButton);
            sourceButtonGroup.add(sourceWebcamRadioButton);
            {
            	webcamParamsPanel = new JPanel();
            	recorderToolbar.add(webcamParamsPanel);

            	java.util.List<com.github.sarxos.webcam.Webcam> webcams = model.getWebcams();
            	webcamComboBox = new javax.swing.JComboBox<>(webcams.toArray(new com.github.sarxos.webcam.Webcam[0]));
            	webcamComboBox.setRenderer(new javax.swing.DefaultListCellRenderer() {
            		@Override
            		public Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index,
            				boolean isSelected, boolean cellHasFocus) {
            			Component res = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            			if (value instanceof com.github.sarxos.webcam.Webcam) {
            				setText(((com.github.sarxos.webcam.Webcam) value).getName());
            			}
            			return res;
            		}
            	});
            	com.github.sarxos.webcam.Webcam selected = model.getSelectedWebcam();
            	if (selected == null && !webcams.isEmpty()) {
            		selected = fr.an.qrcode.channel.impl.decode.input.WebcamImageProvider.chooseDefaultWebcam(webcams);
            	}
            	webcamComboBox.setSelectedItem(selected);
            	webcamComboBox.addActionListener(e ->
            			model.setSelectedWebcam((com.github.sarxos.webcam.Webcam) webcamComboBox.getSelectedItem()));
            	webcamParamsPanel.add(webcamComboBox);
            }
            {
            	screenshotParamsPanel = new JPanel();
            	recorderToolbar.add(screenshotParamsPanel);
            }

            revealRecordAreaButton = new JButton("reveal area");
            revealRecordAreaButton.addActionListener(e -> onRevealRecordAreaAction());
            recorderToolbar.add(revealRecordAreaButton);

            rgbSplitModeCheckBox = new javax.swing.JCheckBox("3 QRCodes (RGB split)");
            rgbSplitModeCheckBox.setSelected(model.isRgbSplitMode());
            rgbSplitModeCheckBox.addActionListener(e -> {
                model.setRgbSplitMode(rgbSplitModeCheckBox.isSelected());
                updateDetailImageLayout();
            });
            recorderToolbar.add(rgbSplitModeCheckBox);

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
            qrCodeImageCanvas.setPreferredSize(new Dimension(250, 250));

            qrCodeRedCanvas = new ImageCanvas();
            qrCodeRedCanvas.setPreferredSize(new Dimension(250, 250));
            qrCodeGreenCanvas = new ImageCanvas();
            qrCodeGreenCanvas.setPreferredSize(new Dimension(250, 250));
            qrCodeBlueCanvas = new ImageCanvas();
            qrCodeBlueCanvas.setPreferredSize(new Dimension(250, 250));

            qrCodeRedHistoryArea = makeChannelHistoryArea();
            qrCodeGreenHistoryArea = makeChannelHistoryArea();
            qrCodeBlueHistoryArea = makeChannelHistoryArea();

            updateDetailImageLayout();
        }

        { // metadataTabPanel
            metadataTabPanel = new JPanel(new BorderLayout());

            metadataTextArea = new JTextArea();
            metadataTextArea.setEditable(false);
            metadataTextArea.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
            JScrollPane metadataScrollPane = new JScrollPane(metadataTextArea);
            metadataTabPanel.add(metadataScrollPane, BorderLayout.CENTER);
        }

        calib3dView = new Calib3dView(model.getCalib3dImageProvider());

        tabbedPane.add("output", recorderTabPanel);
        tabbedPane.add("img", detailImageTabPanel);
        tabbedPane.add("metadata", metadataTabPanel);
        tabbedPane.add("calib3d", calib3dView.getComp());

        infoPanel = new JPanel(new GridLayout(8, 1));
        {
	        currentDecodeMessageLabel = new JLabel();
	        infoPanel.add(currentDecodeMessageLabel);

	        currentDecodeTimeMillisLabel = new JLabel();
	        infoPanel.add(currentDecodeTimeMillisLabel);

	        currentChannelSequenceNumberLabel = new JLabel();
	        infoPanel.add(currentChannelSequenceNumberLabel);

	        currentAheadFragsInfoArea = new JTextArea(1, 50);
	        infoPanel.add(currentAheadFragsInfoArea);

	        recognitionStatsText = new JTextArea(1, 50);
	        infoPanel.add(recognitionStatsText);

	        fragmentsStatusPanel = new SquaresStripPanel<>(5, 1, QRCodeDecoderChannelView::colorForFragmentState);
	        JScrollPane fragmentsStatusScrollPane = new JScrollPane(fragmentsStatusPanel);
	        fragmentsStatusScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
	        fragmentsStatusScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
	        infoPanel.add(fragmentsStatusScrollPane);

	        repairFragmentsStatusPanel = new SquaresStripPanel<>(5, 1, QRCodeDecoderChannelView::colorForFragmentState);
	        JScrollPane repairFragmentsStatusScrollPane = new JScrollPane(repairFragmentsStatusPanel);
	        repairFragmentsStatusScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
	        repairFragmentsStatusScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
	        infoPanel.add(repairFragmentsStatusScrollPane);

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
	        drawCurrCanvas();
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

        currentDecodeMessageLabel.setText(model.getCurrDecodeMsg());
//        currentDecodeTimeMillisLabel.setText(currentSnapshotResult != null?
//        		TimeUnit.NANOSECONDS.toMillis(currentSnapshotResult.nanosCompute) + " ms" : "");

        String fullText = model.getFullText();
        outputTextArea.setText(fullText);

        currentChannelSequenceNumberLabel.setText("next seq number:" + model.getNextSequenceNumber());
        currentAheadFragsInfoArea.setText(model.getAheadFragsInfo());

        recognitionStatsText.setText(model.getRecognitionStatsText());

        fragmentsStatusPanel.refresh(model.getFragmentStates());
        repairFragmentsStatusPanel.refresh(model.getRepairFragmentStates());

        metadataTextArea.setText(model.getMetadataInfoText());

        drawCurrCanvas();

        Graphics g = qrCodeImageCanvas.getGraphics();

        QRCapturedEvent qrEvent = model.getCurrQRCapturedEvent();
        if (qrEvent != null) {
        	for(QRResult qrResult : qrEvent.qrResults) {
        		List<PtInt2D> pts = qrResult.resultPoints;
        		if (pts != null) {
        			for(PtInt2D pt : pts) {
        				int x = pt.x, y = pt.y;
        				g.drawOval(x-4, y-4, 8, 8);
        			}
        		}
        	}
        }
//        ImageStreamCallback qrStream = model.getDecoderChannel().getQRStreamFromImageStream();
//        DetectorResult detectorRes = qrStream.getCurrDetectorResult();
//        if (detectorRes != null) {
//        	ResultPoint[] points = detectorRes.getPoints();
//        	for(int i = 0; i < points.length; i++) {
//        		int x = (int) points[i].getX();
//				int y = (int) points[i].getY();
//				g.drawOval(x-4, y-4, 8, 8);
//        	}
//        }

        qrCodeImageCanvas.repaint();

        // saveFileMessageLabel.setText();
    }

    private static JTextArea makeChannelHistoryArea() {
        JTextArea area = new JTextArea(8, 20);
        area.setEditable(false);
        area.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 10));
        return area;
    }

    private static JPanel makeChannelPanel(String label, ImageCanvas canvas, JTextArea historyArea) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JLabel(label, JLabel.CENTER), BorderLayout.NORTH);
        panel.add(canvas, BorderLayout.CENTER);
        panel.add(new JScrollPane(historyArea), BorderLayout.SOUTH);
        return panel;
    }

    private void updateDetailImageLayout() {
        boolean rgb = model.isRgbSplitMode();
        detailImageTabPanel.removeAll();
        if (rgb) {
            JPanel rgbPanel = new JPanel(new GridLayout(1, 3));
            rgbPanel.add(makeChannelPanel("Red",   qrCodeRedCanvas,   qrCodeRedHistoryArea));
            rgbPanel.add(makeChannelPanel("Green", qrCodeGreenCanvas, qrCodeGreenHistoryArea));
            rgbPanel.add(makeChannelPanel("Blue",  qrCodeBlueCanvas,  qrCodeBlueHistoryArea));
            detailImageTabPanel.add(rgbPanel, BorderLayout.CENTER);
        } else {
            detailImageTabPanel.add(qrCodeImageCanvas, BorderLayout.CENTER);
        }
        detailImageTabPanel.revalidate();
        detailImageTabPanel.repaint();
    }

    protected void drawCurrCanvas() {
    	BufferedImage image = model.getCurrentScreenshotImg();
    	if (model.isRgbSplitMode()) {
    	    qrCodeRedCanvas.setImage(extractChannel(image, 0));
    	    qrCodeGreenCanvas.setImage(extractChannel(image, 1));
    	    qrCodeBlueCanvas.setImage(extractChannel(image, 2));
    	    qrCodeRedCanvas.repaint();
    	    qrCodeGreenCanvas.repaint();
    	    qrCodeBlueCanvas.repaint();
    	    updateChannelHistoryAreas();
    	} else {
    	    qrCodeImageCanvas.setImage(image);
    	    qrCodeImageCanvas.repaint();
    	}
    }

    private void updateChannelHistoryAreas() {
        java.util.List<fr.an.qrcode.channel.impl.decode.filter.QRCapturedEvent> history = model.getQRCapturedEventHistory();
        qrCodeRedHistoryArea.setText(buildChannelHistory(history, 0));
        qrCodeGreenHistoryArea.setText(buildChannelHistory(history, 1));
        qrCodeBlueHistoryArea.setText(buildChannelHistory(history, 2));
    }

    /** builds a multi-line history string for one channel index across recent events, newest first */
    private static String buildChannelHistory(
            java.util.List<fr.an.qrcode.channel.impl.decode.filter.QRCapturedEvent> history, int channelIndex) {
        StringBuilder sb = new StringBuilder();
        for (fr.an.qrcode.channel.impl.decode.filter.QRCapturedEvent event : history) {
            fr.an.qrcode.channel.impl.decode.QRResult result =
                    (event.qrResults != null && channelIndex < event.qrResults.size())
                    ? event.qrResults.get(channelIndex) : null;
            if (result != null && result.text != null) {
                // show only the header line (first line) to keep the display compact
                String text = result.text;
                int nl = text.indexOf('\n');
                sb.append(nl >= 0 ? text.substring(0, nl) : text);
            } else {
                sb.append("-");
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /** extracts one color channel (0=R, 1=G, 2=B) from a color image and returns it as a grayscale BufferedImage */
    private static BufferedImage extractChannel(BufferedImage src, int channel) {
        if (src == null) return null;
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        int shift = (2 - channel) * 8; // R=16, G=8, B=0
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int v = (src.getRGB(x, y) >> shift) & 0xFF;
                dst.getRaster().setSample(x, y, 0, v);
            }
        }
        return dst;
    }

    /** white/pale=initial (never seen), green=received (known, not yet sequential), grey=acknowledged (consumed) */
    private static Color colorForFragmentState(FragmentState state) {
    	switch (state) {
    		case ACKNOWLEDGED: return Color.LIGHT_GRAY;
    		case RECEIVED: return Color.GREEN;
    		default: return Color.WHITE;
    	}
    }
}
