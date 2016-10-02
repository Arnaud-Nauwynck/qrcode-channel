package fr.an.qrcode.channel.ui.utils;

import java.awt.Graphics;
import java.awt.Image;

import javax.swing.JComponent;

public class ImageCanvas extends JComponent {

    /** */
    private static final long serialVersionUID = 1L;
    
    private Image image;

    // ------------------------------------------------------------------------

    public ImageCanvas() {
    }

    // ------------------------------------------------------------------------

    public void setImage(Image image) {
        this.image = image;
        repaint();
    }

    public void paint(Graphics g) {
        int w = this.getWidth();
        int h = this.getHeight();
        if (image != null) {
            g.drawImage(image, 0, 0, w, h, this);
        } else {
            g.fillRect(0,  0, w,  h);
        }
    }
}