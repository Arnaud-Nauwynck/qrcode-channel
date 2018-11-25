package fr.an.qrcode.channel.impl.decode.calib3d;

import java.awt.image.BufferedImage;

import fr.an.qrcode.channel.impl.util.DimInt2D;
import fr.an.qrcode.channel.impl.util.PtInt2D;

public abstract class Calib3dListener {

	public abstract void onImage(
			BufferedImage undistortImage, 
			BufferedImage image, 
			PtInt2D[] corners
			);
	
	public void onStart(DimInt2D dim) {}
	public void onEnd() {}

}
