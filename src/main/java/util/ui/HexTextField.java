package util.ui;

import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.PlainDocument;

import app.SwingUtils;

public class HexTextField extends JTextField
{
	private int value;
	private int digits;

	private final String displayFormat;

	public HexTextField(Consumer<Integer> listener)
	{
		this(8, false, listener);
	}

	public HexTextField(int digits, Consumer<Integer> listener)
	{
		this(digits, false, listener);
	}

	public HexTextField(int digits, boolean fireChangeUpdates, Consumer<Integer> listener)
	{
		this.digits = digits;
		setMargin(SwingUtils.TEXTBOX_INSETS);

		displayFormat = "%0" + digits + "X";

		if (fireChangeUpdates) {
			getDocument().addDocumentListener(new DocumentListener() {
				@Override
				public void changedUpdate(DocumentEvent e)
				{
					notifyChange();
				}

				@Override
				public void removeUpdate(DocumentEvent e)
				{
					notifyChange();
				}

				@Override
				public void insertUpdate(DocumentEvent e)
				{
					notifyChange();
				}

				public void notifyChange()
				{
					try {
						int v = (int) Long.parseLong(getText(), 16);
						if (v != value) {
							value = v;
							listener.accept(v);
						}
					}
					catch (NumberFormatException nfe) {}
				}
			});
		}

		addActionListener(new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				try {
					int v = (int) Long.parseLong(getText(), 16);
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
						int v = (int) Long.parseLong(getText(), 16);
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
		setText(String.format(displayFormat, v));
	}

	public int getValue()
	{
		try {
			return (int) Long.parseLong(getText(), 16);
		}
		catch (NumberFormatException nfe) {}

		return 0;
	}

	@Override
	protected Document createDefaultModel()
	{
		return new HexTextDocument(this);
	}

	private static class HexTextDocument extends PlainDocument
	{
		private HexTextField tf;

		public HexTextDocument(HexTextField tf)
		{
			this.tf = tf;
		}

		@Override
		public void insertString(int pos, String s, AttributeSet attr) throws BadLocationException
		{
			if (s == null)
				return;

			if ((getLength() + s.length()) <= tf.digits) {
				String oldString = getText(0, getLength());
				String newString = oldString.substring(0, pos) + s + oldString.substring(pos);
				try {
					Long.parseLong(newString, 16);
					super.insertString(pos, s, attr);
				}
				catch (NumberFormatException e) {}
			}
		}
	}
}
