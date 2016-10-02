package fr.an.qrcode.channel;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import fr.an.qrcode.channel.impl.encode.QREncodeSetting;
import fr.an.qrcode.channel.ui.QRCodeEncoderChannelModel;
import fr.an.qrcode.channel.ui.QRCodeEncoderChannelView;

public class QRCodeChannelEncoderApp {


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
            	QRCodeEncoderChannelModel model = new QRCodeEncoderChannelModel(new QREncodeSetting());
                QRCodeEncoderChannelView view = new QRCodeEncoderChannelView(model);
                
                JFrame frame = new JFrame();
                frame.getContentPane().add(view.getJComponent());
                
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.pack();
                frame.setVisible(true);
            }

        });
    }

}
