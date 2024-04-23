package util.ui;

import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.function.Consumer;

import javax.swing.JTextField;
import javax.swing.SwingConstants;

import app.SwingUtils;

public class StringField extends JTextField
{
	public StringField(Consumer<String> consumer)
	{
		this(SwingConstants.CENTER, consumer);
	}

	public StringField(int alignment, Consumer<String> consumer)
	{
		setFont(getFont().deriveFont(12f));
		setHorizontalAlignment(alignment);
		SwingUtils.addBorderPadding(this);

		// things that commit changes from text field
		addFocusListener(new FocusListener() {
			@Override
			public void focusGained(FocusEvent e)
			{}

			@Override
			public void focusLost(FocusEvent e)
			{
				consumer.accept(getText());
			}
		});

		addActionListener((e) -> {
			consumer.accept(getText());
		});
	}
}
