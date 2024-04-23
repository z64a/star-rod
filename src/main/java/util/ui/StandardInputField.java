package util.ui;

import java.util.function.Consumer;

import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import app.SwingUtils;

public class StandardInputField extends JTextField
{
	public StandardInputField(Consumer<String> callback)
	{
		setMargin(SwingUtils.TEXTBOX_INSETS);
		SwingUtils.addBorderPadding(this);

		getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				textChanged(e);
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				textChanged(e);
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				textChanged(e);
			}

			public void textChanged(DocumentEvent e)
			{
				callback.accept(StandardInputField.this.getText());
			}
		});
	}
}
