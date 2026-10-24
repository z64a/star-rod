package util.ui;

import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.function.Consumer;
import java.util.function.Predicate;

import javax.swing.AbstractAction;
import javax.swing.JTextField;

import app.SwingUtils;
import util.Logger;

public class NameTextField extends JTextField
{
	private String value;
	private final Consumer<String> listener;
	private final Predicate<String> isValid;
	private final String invalidMessage;

	public NameTextField(Consumer<String> listener)
	{
		this(listener, s -> true, null);
	}

	/** A field that reverts names isValid rejects, logging invalidMessage, instead of passing them to listener. */
	public NameTextField(Consumer<String> listener, Predicate<String> isValid, String invalidMessage)
	{
		this.listener = listener;
		this.isValid = isValid;
		this.invalidMessage = invalidMessage;

		setMargin(SwingUtils.TEXTBOX_INSETS);

		addActionListener(new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				commit();
			}
		});

		addFocusListener(new FocusListener() {
			@Override
			public void focusGained(FocusEvent e)
			{}

			@Override
			public void focusLost(FocusEvent e)
			{
				if (!e.isTemporary())
					commit();
				else
					setValue(value);
			}
		});
	}

	private void commit()
	{
		String s = getText();
		if (!s.isEmpty() && !s.equals(value)) {
			if (isValid.test(s)) {
				value = s;
				listener.accept(s);
			}
			else {
				Toolkit.getDefaultToolkit().beep();
				Logger.logWarning(invalidMessage);
			}
		}
		setValue(value);
	}

	public void setValue(String s)
	{
		value = s;
		setText(s);
	}
}
