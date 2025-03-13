package util.ui;

import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.JTextField;

import app.SwingUtils;

public class IntTextField extends JTextField
{
	private int value;

	public IntTextField(Consumer<Integer> listener)
	{
		setMargin(SwingUtils.TEXTBOX_INSETS);

		addActionListener(new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				try {
					int v = Integer.decode(getText());
					if (v != value) {
						value = v;
						listener.accept(v);
					}
				}
				catch (NumberFormatException nfe) {}

				setValue(value);
			}
		});

		addFocusListener(new FocusListener() {
			@Override
			public void focusGained(FocusEvent e)
			{}

			@Override
			public void focusLost(FocusEvent e)
			{
				if (!e.isTemporary()) {
					try {
						int v = Integer.decode(getText());
						if (v != value) {
							value = v;
							listener.accept(v);
						}
					}
					catch (NumberFormatException nfe) {}

					setValue(value);
				}
			}
		});
	}

	public void setValue(int v)
	{
		value = v;
		setText(v + "");
	}

	public int getValue()
	{
		return value;
	}
}
