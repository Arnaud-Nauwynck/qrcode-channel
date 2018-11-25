package fr.an.qrcode.channel.impl.decode.calib3d;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Point;
import org.opencv.core.Point3;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.core.TermCriteria;
import org.opencv.imgproc.Imgproc;
import org.opencv.utils.Converters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.an.qrcode.channel.impl.QROpenCvIOUtils;

/**
 * 
 * https://github.com/opencv/opencv/blob/master/samples/android/camera-calibration/src/org/opencv/samples/cameracalibration/CameraCalibrator.java
 * https://opencv-java-tutorials.readthedocs.io/en/latest/09-camera-calibration.html
 */
public class OpenCvCalib3d {

	static {
		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
	}
	
	private static final Logger log = LoggerFactory.getLogger(OpenCvCalib3d.class);

	private int flagsCorner = Calib3d.CALIB_CB_ADAPTIVE_THRESH
//            | Calib3d.CALIB_CB_FAST_CHECK 
            | Calib3d.CALIB_CB_NORMALIZE_IMAGE;

	private int flagsCalib = Calib3d.CALIB_ZERO_TANGENT_DIST
            | Calib3d.CALIB_FIX_PRINCIPAL_POINT
//            | Calib3d.CALIB_FIX_ASPECT_RATIO
            | Calib3d.CALIB_FIX_K4
            | Calib3d.CALIB_FIX_K5;
    
	private static final Size winSize = new Size(5, 5), zoneSize = new Size(-1, -1);
    private static final TermCriteria criteria = new TermCriteria(TermCriteria.EPS + TermCriteria.MAX_ITER, 40, 0.001);


    private Size mImageSize;

    private final Size mPatternSize = new Size(7, 7); // internals corners of 8x8 chessboard!
    private final int mCornersSize = (int)(mPatternSize.width * mPatternSize.height);
    
    private final MatOfPoint3f referenceChessboardCorners3dMat;
    
    private List<Mat> objectPoints = new ArrayList<>();  // MatOfPoint3f
    private List<Mat> imagePoints = new ArrayList<>(); // Point2f
    
    private MatOfPoint2f currImageChessboardCorners = new MatOfPoint2f();
    private boolean currImageChessboardCornersFound = false;
    
    private boolean mIsCalibrated = false;
    private Mat mCameraMatrix = new Mat();
    private Mat mDistortionCoefficients = new Mat();
    private double errReproj;
    private double mRms;
//    private double mSquareSize = 0.0181;

    public OpenCvCalib3d(int width, int height) {
    	log.info("init OpenCvCalib3d");
        mImageSize = new Size(width, height);
        Mat.eye(3, 3, CvType.CV_64FC1).copyTo(mCameraMatrix);
        mCameraMatrix.put(0, 0, 1.0);
        Mat.zeros(5, 1, CvType.CV_64FC1).copyTo(mDistortionCoefficients);
        
        referenceChessboardCorners3dMat = newReferenceChessboardCorners_VectorPoint3((int)mPatternSize.width, (int)mPatternSize.height);
    }

    protected static MatOfPoint3f newReferenceChessboardCorners_VectorPoint3(int w, int h) {
    	int len = h * w;
    	Point3[] tmp = new Point3[len];
    	double squareSize = 50;
        int idx = 0;
    	for (int i = 0; i < h; ++i) {
            for (int j = 0; j < w; ++j) {
            	tmp[idx++] = new Point3(j * squareSize, i * squareSize, 0.0d);
            }
        }
        return new MatOfPoint3f(tmp);
    }


    public void clearCorners() {
        objectPoints.clear();
        imagePoints.clear();
        
        mIsCalibrated = false;
    }
    
    public void undistort(Mat src, Mat dest) {
	    if (this.mIsCalibrated) {
	    	Calib3d.undistort(src, dest, mCameraMatrix, mDistortionCoefficients);
	    } else {
	    	src.copyTo(dest);
	    }
    }
    
    public void processFrame(Mat grayFrame, Mat displayRgbaFrame) {
    	findAndAddChessboardCorners(grayFrame);
        if (displayRgbaFrame != null) {
        	renderFrame(displayRgbaFrame);
        }
    }

    
    protected void findAndAddChessboardCorners(Mat imgMat) {
    	currImageChessboardCorners = new MatOfPoint2f();
    	currImageChessboardCornersFound = Calib3d.findChessboardCorners(imgMat, mPatternSize, currImageChessboardCorners, flagsCorner);

		if (currImageChessboardCornersFound) {
			log.info("chessboard corners found .. ");
			Imgproc.cornerSubPix(imgMat, currImageChessboardCorners, winSize, zoneSize, criteria);

		    objectPoints.add(referenceChessboardCorners3dMat);
		    imagePoints.add(currImageChessboardCorners);
		} else {
			log.info("chessboard corners not found!");
		}
	}


    
    public void calibrate() {
		if (objectPoints.size() < 10) {
    		log.info("not enough image points .. skip calibrate!");
			return;
    	}
    	
        List<Mat> rvecs = new ArrayList<>();
        List<Mat> tvecs = new ArrayList<>();
        errReproj = Calib3d.calibrateCamera(objectPoints, imagePoints, 
        		mImageSize, mCameraMatrix, mDistortionCoefficients, rvecs, tvecs, flagsCalib);

        mIsCalibrated = Core.checkRange(mCameraMatrix)
                && Core.checkRange(mDistortionCoefficients);

//        Mat reprojectionErrors = new Mat();
//        mRms = computeReprojectionErrors(rvecs, tvecs, reprojectionErrors);
//        log.info(String.format("Average re-projection error: %f", mRms));
//        log.info("Camera matrix: " + mCameraMatrix.dump());
//        log.info("Distortion coefficients: " + mDistortionCoefficients.dump());
    }


//    private double computeReprojectionErrors(
//            List<Mat> rvecs, List<Mat> tvecs, Mat perViewErrors) {
//        MatOfPoint2f cornerProjected = new MatOfPoint2f();
//        double totalError = 0;
//        float viewErrors[] = new float[objectPoints.size()];
//
//        MatOfDouble distortionCoefficients = new MatOfDouble(mDistortionCoefficients);
//        int totalPoints = 0;
//        for (int i = 0; i < objectPoints.size(); i++) {
//            Calib3d.projectPoints(objectPoints.get(i), rvecs.get(i), tvecs.get(i), mCameraMatrix, distortionCoefficients, cornerProjected);
//			double error = Core.norm(imagePoints.get(i), cornerProjected, Core.NORM_L2);
//            int n = objectPoints.get(i).rows();
//            viewErrors[i] = (float) Math.sqrt(error * error / n);
//            totalError  += error * error;
//            totalPoints += n;
//        }
//        perViewErrors.create(objectPoints.size(), 1, CvType.CV_32FC1);
//        perViewErrors.put(0, 0, viewErrors);
//
//        return Math.sqrt(totalError / totalPoints);
//    }

    
  
    
    

    private void renderFrame(Mat displayMat) {
        Calib3d.drawChessboardCorners(displayMat, mPatternSize, currImageChessboardCorners, currImageChessboardCornersFound);

        Imgproc.putText(displayMat, "Captured: " + imagePoints.size(), 
        		new Point(displayMat.cols() / 3 * 2, displayMat.rows() * 0.1),
                Imgproc.FONT_HERSHEY_SIMPLEX, 1.0, new Scalar(255, 255, 0));
    }

    public Mat getCameraMatrix() {
        return mCameraMatrix;
    }

    public Mat getDistortionCoefficients() {
        return mDistortionCoefficients;
    }

    public double getAvgReprojectionError() {
        return mRms;
    }

    public boolean isCalibrated() {
        return mIsCalibrated;
    }

    public double getErrReproj() {
    	return errReproj;
    }

	public void saveConfParams(String fileName) {
		File confFile = new File(fileName);
		if (isCalibrated()) {
			File bkpFile = new File(fileName + ".bkp");
			if (bkpFile.exists()) {
				bkpFile.delete();
			}
			confFile.renameTo(bkpFile);

			Mat camMat = getCameraMatrix();
			Mat distMat = getDistortionCoefficients();

			try (PrintWriter writer = new PrintWriter(new BufferedOutputStream(new FileOutputStream(confFile)))) {
				QROpenCvIOUtils.writeText(camMat, writer);
				QROpenCvIOUtils.writeText(distMat, writer);
			} catch(Exception ex) {
				log.error("Failed to write file " + confFile, ex);
			}
		} else {
			log.error("no calibration to save");
		}
	}

	public void loadConfParams(String fileName) {
		File confFile = new File(fileName);
		if (confFile.exists()) {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(new FileInputStream(confFile))))) {
				mCameraMatrix = QROpenCvIOUtils.readText(reader);
				mDistortionCoefficients = QROpenCvIOUtils.readText(reader);
			} catch(Exception ex) {
				log.error("Failed to write file " + confFile, ex);
			}
			
		}
	}
}
