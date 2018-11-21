package fr.an.qrcode.channel;

import java.awt.Rectangle;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import fr.an.qrcode.channel.ui.QRCodeDecoderChannelModel;
import fr.an.qrcode.channel.ui.QRCodeDecoderChannelView;

public class QRCodeChannelDecoderApp {

    /**
     * @param args
     */
    public static void main(String[] args) {
        try {
            doMain(args);
        }  catch(Exception ex) {
            System.err.println("Failed");
            ex.printStackTrace(System.err);
        }
    }
    
    public static void doMain(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
            	QRCodeDecoderChannelModel model = new QRCodeDecoderChannelModel();
                QRCodeDecoderChannelView view = new QRCodeDecoderChannelView(model);
                
                JFrame frame = new JFrame();
                frame.getContentPane().add(view.getJComponent());
                
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.pack();
                frame.setVisible(true);
                
                frame.setBounds(new Rectangle(0, 50, 920, 1000));
            }
        });
    }
}
