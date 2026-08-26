package util.ui;

import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.JTextField;

import app.SwingUtils;

public class DoubleTextField extends JTextField
{
	private double value;

	public DoubleTextField(Consumer<Double> listener)
	{
		setMargin(SwingUtils.TEXTBOX_INSETS);

		addActionListener(new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				try {
					double d = Double.parseDouble(getText());
					if (d != value) {
						value = d;
						listener.accept(d);
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
						double d = Double.parseDouble(getText());
						if (d != value) {
							value = d;
							listener.accept(d);
						}
					}
					catch (NumberFormatException nfe) {}

					setValue(value);
				}
			}
		});
	}

	public void setValue(double f)
	{
		value = f;
		setText(f + "");
	}
}
