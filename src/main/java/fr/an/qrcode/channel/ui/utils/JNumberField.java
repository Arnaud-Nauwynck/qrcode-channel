package fr.an.qrcode.channel.ui.utils;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.function.IntConsumer;

import javax.swing.JTextField;

/** small JTextField for editing an int value, invoking an IntConsumer when Enter is pressed (ignored if not a valid int) */
public class JNumberField extends JTextField {

	private static final long serialVersionUID = 1L;

	public JNumberField(int columns) {
		super(columns);
	}

	public JNumberField(int initialValue, int columns) {
		super(columns);
		setValue(initialValue);
	}

	public int getValue() {
		return Integer.parseInt(getText().trim());
	}

	public void setValue(int value) {
		setText(Integer.toString(value));
	}

	/** registers a listener invoked with the parsed int value when Enter is pressed in this field */
	public void onEnterCommit(IntConsumer onCommit) {
		addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent event) {
				if (event.getKeyCode() == KeyEvent.VK_ENTER) {
					try {
						onCommit.accept(getValue());
					} catch (NumberFormatException ex) {
						// ignored: leave previous value in model
					}
				}
			}
		});
	}

}
