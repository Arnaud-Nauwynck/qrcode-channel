package fr.an.qrcode.channel.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;

import org.opencv.core.Mat;

public class QROpenCvIOUtils {

	public static void writeText(Mat mat, PrintWriter out) {
		int rows = mat.rows(), cols = mat.cols(), type = mat.type();
		out.println(rows + " " + cols + " " + type);
		for(int row = 0; row < rows; row++) {
			for (int col = 0; col < cols; col++) {
				double[] elt = mat.get(row, col);
				for(int i = 0; i < elt.length; i++) {
					out.print(elt[i]);
					if (i + 1 < elt.length) {
						out.print(";");
					}
				}
				out.print(" ");
			}
			out.print("\n");
		}
	}

	public static Mat readText(BufferedReader in) throws IOException {
		String[] line = in.readLine().split(" ");
		int rows = Integer.parseInt(line[0]), cols = Integer.parseInt(line[1]), type = Integer.parseInt(line[2]); 
		Mat res = new Mat(rows, cols, type);
		readText(res, in);
		return res;
	}

	public static void readText(Mat mat, BufferedReader in) throws IOException {
		int rows = mat.rows(), cols = mat.cols();
		for(int row = 0; row < rows; row++) {
			String line = in.readLine();
			String[] cells = line.split(" ");
			for (int col = 0; col < cols; col++) {
				String[] channelElts = cells[col].split(";");
				double[] elts = parseDoubles(channelElts);
				mat.put(row, col, elts);
			}
		}
		
	}

	public static double[] parseDoubles(String[] values) {
		int len = values .length;
		double[] res = new double[len];
		for(int i = 0; i < len; i++) {
			res[i] = Double.parseDouble(values[i]);
		}
		return res;
	}

	public static String[] formatDoubles(double[] values) {
		int len = values .length;
		String[] res = new String[len];
		for(int i = 0; i < len; i++) {
			res[i] = Double.toString(values[i]);
		}
		return res;
	}
	

	public static int[] parseInts(String[] values) {
		int len = values .length;
		int[] res = new int[len];
		for(int i = 0; i < len; i++) {
			res[i] = Integer.parseInt(values[i]);
		}
		return res;
	}

	public static String[] formatInts(int[] values) {
		int len = values .length;
		String[] res = new String[len];
		for(int i = 0; i < len; i++) {
			res[i] = Integer.toString(values[i]);
		}
		return res;
	}

}
