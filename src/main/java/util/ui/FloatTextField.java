package util.ui;

import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.JTextField;

import app.SwingUtils;

public class FloatTextField extends JTextField
{
	private float value;

	public FloatTextField(Consumer<Float> listener)
	{
		setMargin(SwingUtils.TEXTBOX_INSETS);

		addActionListener(new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				try {
					float f = Float.parseFloat(getText());
					if (f != value) {
						value = f;
						listener.accept(f);
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
						float f = Float.parseFloat(getText());
						if (f != value) {
							value = f;
							listener.accept(f);
						}
					}
					catch (NumberFormatException nfe) {}

					setValue(value);
				}
			}
		});
	}

	public void setValue(float f)
	{
		value = f;
		setText(f + "");
	}
}
