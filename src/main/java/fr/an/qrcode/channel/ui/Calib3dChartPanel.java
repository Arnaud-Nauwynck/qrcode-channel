package fr.an.qrcode.channel.ui;

import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;

import javax.swing.JComponent;
import javax.swing.JPanel;

public class Calib3dChartPanel {

	private JPanel comp;
	private Canvas canvas;
	
	// private int defaultChessboardSize = 500;
	private int rows = 8;
	private int borderW = 25;
	
	public Calib3dChartPanel() {
		this.comp = new JPanel(new BorderLayout());
		this.canvas = new Canvas() {
			@Override
			public void paint(Graphics g) {
				drawChessboard(g);
			}
		};
		comp.add(canvas, BorderLayout.CENTER);
	}

	public JComponent getComp() {
		return comp;
	}
	
	protected void drawChessboard(Graphics g) {
		Dimension size = canvas.getSize();
		g.setColor(Color.white);
		g.fillRect(0, 0, size.width, size.height);
		
		g.setColor(Color.black);
		int chessboardSize = Math.min(size.width, size.height) - 2*borderW;
		int cellW = chessboardSize / rows;
		for (int row = 0; row < rows; row++) {
			int y = borderW + row * cellW;
			for (int col = (row%2); col < rows; col+=2) {
				int x = borderW + col * cellW;
				g.fillRect(x, y, cellW, cellW);
			}
		}
	}
	
}
