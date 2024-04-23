package util.ui;

import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.JTextField;

import app.SwingUtils;

public class NameTextField extends JTextField
{
	private String value;

	public NameTextField(Consumer<String> listener)
	{
		setMargin(SwingUtils.TEXTBOX_INSETS);

		addActionListener(new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				String s = getText();
				if (!s.isEmpty() && !s.equals(value)) {
					value = s;
					listener.accept(s);
				}
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
					String s = getText();
					if (!s.isEmpty() && !s.equals(value)) {
						value = s;
						listener.accept(s);
					}
				}
				setValue(value);
			}
		});
	}

	public void setValue(String s)
	{
		value = s;
		setText(s);
	}
}
