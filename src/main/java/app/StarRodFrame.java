package app;

import javax.swing.JFrame;

public class StarRodFrame extends JFrame
{
	public StarRodFrame()
	{
		super();

		reloadIcon();
	}

	public StarRodFrame(String title)
	{
		super(title);

		reloadIcon();
	}

	public void reloadIcon()
	{
		setIconImage(Environment.getDefaultIconImage());
	}
}
