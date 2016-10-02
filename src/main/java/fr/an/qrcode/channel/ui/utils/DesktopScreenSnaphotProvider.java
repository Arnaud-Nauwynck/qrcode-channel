package fr.an.qrcode.channel.ui.utils;

import java.awt.AWTException;
import java.awt.Graphics2D;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.net.URL;

import javax.imageio.ImageIO;

public class DesktopScreenSnaphotProvider {

    private static Robot robot;
    static {
        try {
            robot = new Robot();
        } catch (AWTException ex) {
            ex.printStackTrace();
            throw new RuntimeException("FATAL error", ex);
        }
    }
    
    private boolean useCursor;

    private BufferedImage mouseCursor;

    public DesktopScreenSnaphotProvider(boolean useCursor, boolean useWhiteCursor) {
        this.useCursor = useCursor;
        if (useCursor) {
            try {
                String mouseCursorFile;
                if (useWhiteCursor)
                    mouseCursorFile = "white_cursor.png";
                else
                    mouseCursorFile = "black_cursor.png";

                URL cursorURL = getClass().getClassLoader().getResource("mouse_cursors/" + mouseCursorFile);

                mouseCursor = ImageIO.read(cursorURL);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public Rectangle initialiseScreenCapture() {
        return new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
    }

    public BufferedImage captureScreen(Rectangle recordArea) {
        BufferedImage image = robot.createScreenCapture(recordArea);
        return image;
    }

    public Point captureMouseLocation() {
        return MouseInfo.getPointerInfo().getLocation();
    }

    public void paintMouseInScreenCapture(BufferedImage image, Point mousePosition) {
        if (useCursor) {
            Graphics2D grfx = image.createGraphics();
            grfx.drawImage(mouseCursor, mousePosition.x - 8, mousePosition.y - 5, null);
            grfx.dispose();
        }
    }

}
