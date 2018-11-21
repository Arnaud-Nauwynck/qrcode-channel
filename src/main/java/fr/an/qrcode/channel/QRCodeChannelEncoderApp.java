package fr.an.qrcode.channel;

import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.apache.commons.io.FileUtils;

import fr.an.qrcode.channel.impl.QREncodeSetting;
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
                
                String content;
                try {
                	content = FileUtils.readFileToString(new File("pom.xml"));
				} catch (IOException e) {
					content = "ERROR";
				}
                model.computeQRCodes(content);
                
                JFrame frame = new JFrame();
                frame.getContentPane().add(view.getJComponent());
                
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.pack();
                frame.setVisible(true);
                
                frame.setBounds(new Rectangle(1000, 50, 1000, 1000));
            }

        });
    }

}
