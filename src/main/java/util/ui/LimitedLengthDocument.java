package util.ui;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;

public class LimitedLengthDocument extends PlainDocument
{
	private int characterLimit;

	public LimitedLengthDocument(int limit)
	{
		super();
		this.characterLimit = limit;
	}

	@Override
	public void insertString(int offset, String s, AttributeSet attr) throws BadLocationException
	{
		if (s == null)
			return;

		if ((getLength() + s.length()) <= characterLimit) {
			super.insertString(offset, s, attr);
		}
	}
}
