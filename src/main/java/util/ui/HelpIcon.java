package util.ui;

import javax.swing.JLabel;

public class HelpIcon extends JLabel
{
	public HelpIcon(String msg)
	{
		super(ThemedIcon.INFO_16);

		setToolTipText(msg);
	}
}
