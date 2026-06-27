package fr.an.qrcode.channel.ui.utils;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import javax.swing.JComponent;

/**
 * generic horizontal strip of small aligned squares, one per item, colored by a caller-supplied function.
 */
public class SquaresStripPanel<T> extends JComponent {

	private final int square;
	private final int gap;
	private final Function<T, Color> colorFn;

	private List<T> items = new ArrayList<>();

	public SquaresStripPanel(int square, int gap, Function<T, Color> colorFn) {
		this.square = square;
		this.gap = gap;
		this.colorFn = colorFn;
	}

	public void refresh(List<T> newItems) {
		this.items = newItems;
		int width = items.size() * (square + gap) + gap;
		setPreferredSize(new Dimension(width, square + 2 * gap));
		revalidate();
		repaint();
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		int x = gap;
		for (T item : items) {
			g.setColor(colorFn.apply(item));
			g.fillRect(x, gap, square, square);
			g.setColor(Color.DARK_GRAY);
			g.drawRect(x, gap, square, square);
			x += square + gap;
		}
	}
}
