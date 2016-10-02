package fr.an.qrcode.channel.ui.utils;

import java.awt.Dimension;

import javax.swing.JFrame;

public class TransparentFrameScreenArea extends JFrame {

    /** */
    private static final long serialVersionUID = 1L;
    
    // ------------------------------------------------------------------------

    public TransparentFrameScreenArea() {
        super.setUndecorated(true);
        super.setOpacity(0.7f);
        
        ComponentResizer cr = new ComponentResizer();
        cr.registerComponent(this);
        cr.setSnapSize(new Dimension(10, 10));
        // cr.setMaximumSize(new Dimension());
        cr.setMinimumSize(new Dimension(10, 10));
    }

    // ------------------------------------------------------------------------

}
