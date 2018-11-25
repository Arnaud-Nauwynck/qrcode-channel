package fr.an.qrcode.channel.ui.utils;

import java.awt.Graphics;
import java.awt.Image;
import java.awt.Toolkit;

import javax.swing.JComponent;

public class ImageCanvas extends JComponent {

	/** */
	private static final long serialVersionUID = 1L;

	private Image image;

	// ------------------------------------------------------------------------

	public ImageCanvas() {
		setOpaque(true);
	}

	// ------------------------------------------------------------------------

	public void setImage(Image image) {
		this.image = image;
		int w = this.getWidth(), h = this.getHeight();
		if (!isVisible()) {
			return;
		}

		paintImmediately(0, 0, w, h);
		Toolkit.getDefaultToolkit().sync();
	}

	@Override
	public void paint(Graphics g) {
		int w = this.getWidth(), h = this.getHeight();
		int min = Math.min(w, h); 
		if (image != null) {
			g.drawImage(image, 0, 0, min, min, this);
		} else {
			g.fillRect(0, 0, w, h);
		}
	}
}