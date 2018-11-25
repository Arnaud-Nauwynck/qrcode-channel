package fr.an.qrcode.channel.ui;

import java.awt.BorderLayout;
import java.awt.image.BufferedImage;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JToolBar;

import fr.an.qrcode.channel.impl.decode.calib3d.Calib3dListener;
import fr.an.qrcode.channel.impl.decode.calib3d.OpenCvCalib3dImageProvider;
import fr.an.qrcode.channel.impl.util.PtInt2D;
import fr.an.qrcode.channel.ui.utils.ImageCanvas;

public class Calib3dView {

	private JPanel comp;
	
	private OpenCvCalib3dImageProvider model;
	
	private JButton resetFileButton;
	private JButton toggleFreezeImageButton;
	private JButton processImageFileButton;
	
	private JButton saveFileButton;
	private JButton loadFileButton;
	
	private ImageCanvas undistortImageCanvas;
	private ImageCanvas imageCanvas;
	
	private Calib3dListener innerCalib3dListener = new Calib3dListener() {
		@Override
		public void onImage(BufferedImage undistortImage, BufferedImage image, PtInt2D[] corners) {
			onModelImage(undistortImage, image, corners);
		}
	};
	
	public Calib3dView(OpenCvCalib3dImageProvider model) {
		this.model = model;
		comp = new JPanel(new BorderLayout());

		JToolBar toolbar = new JToolBar();
		comp.add(toolbar, BorderLayout.NORTH);

		resetFileButton = new JButton("reset");
		toolbar.add(resetFileButton);
		resetFileButton.addActionListener(evt -> model.reset());

		toggleFreezeImageButton = new JButton("freeze");
		toolbar.add(toggleFreezeImageButton);
		toggleFreezeImageButton.addActionListener(evt -> { 
			boolean t = model.toogleFreezeCalib3dImage(); 
			toggleFreezeImageButton.setText(t? "unfreeze" : "freeze");
		});

		processImageFileButton = new JButton("process");
		toolbar.add(processImageFileButton);
		processImageFileButton.addActionListener(evt -> { model.processImage(); model.calibrate(); });
		
		saveFileButton = new JButton("save");
		toolbar.add(saveFileButton);
		saveFileButton.addActionListener(evt -> model.save());
		
		loadFileButton = new JButton("load");
		toolbar.add(loadFileButton);
		loadFileButton.addActionListener(evt -> model.load());
		
		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
		comp.add(splitPane, BorderLayout.CENTER);
		imageCanvas = new ImageCanvas();
		splitPane.setLeftComponent(imageCanvas);
		undistortImageCanvas = new ImageCanvas();
		splitPane.setRightComponent(undistortImageCanvas);
		
		splitPane.setDividerLocation(0.5); // TODO ??
		splitPane.setResizeWeight(0.5);
		
		if (model != null) {
			model.setListener(innerCalib3dListener);
		}
	}


	private void onModelImage(BufferedImage undistortImage, BufferedImage image, PtInt2D[] corners) {
		undistortImageCanvas.setImage(undistortImage);
		imageCanvas.setImage(image);
		// corners .. TODO draw
		
		
	}
	
	public JPanel getComp() {
		return comp;
	}

	public OpenCvCalib3dImageProvider getModel() {
		return model;
	}

	
}    

