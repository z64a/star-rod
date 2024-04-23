package game.map.editor.ui;

import javax.swing.JLabel;
import javax.swing.SwingConstants;

import app.SwingUtils;

public class LabelWithTip extends JLabel
{
	private String labelText;
	private String tipText;

	public LabelWithTip(String text, String tipText)
	{
		this(SwingConstants.LEADING, text, -1, tipText);
	}

	public LabelWithTip(int alignment, String text, String tipText)
	{
		this(alignment, text, -1, tipText);
	}

	public LabelWithTip(String text, float size, String tipText)
	{
		this(SwingConstants.LEADING, text, size, tipText);
	}

	public LabelWithTip(int alignment, String text, float size, String tipText)
	{
		super(text, alignment);

		this.labelText = text;
		this.tipText = tipText;

		if (size >= 0)
			SwingUtils.setFontSize(this, size);

		setText(text);
		setToolTipText(tipText);
	}

	@Override
	public void setText(String text)
	{
		labelText = text;
		boolean hasTip = (tipText != null && !tipText.isEmpty());
		super.setText(labelText + (hasTip ? " *" : ""));
	}

	public void updateTip(String tip)
	{
		tipText = tip;
		setToolTipText(tipText);
		setText(labelText);
	}
}
