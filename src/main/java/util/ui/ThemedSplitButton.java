package util.ui;

import java.awt.Color;

import javax.swing.UIManager;

import com.alexandriasoftware.swing.JSplitButton;

public class ThemedSplitButton extends JSplitButton
{
	private static final long serialVersionUID = 1L;

	public ThemedSplitButton(String text)
	{
		super(text);
		updateArrowColors();
	}

	@Override
	public void updateUI()
	{
		super.updateUI();
		updateArrowColors();
	}

	@Override
	public void setForeground(Color foreground)
	{
		super.setForeground(foreground);
		updateArrowColors();
	}

	private void updateArrowColors()
	{
		Color foreground = getForeground();
		if (foreground != null && !foreground.equals(getArrowColor()))
			setArrowColor(foreground);

		Color disabledForeground = UIManager.getColor("Button.disabledText");
		if (disabledForeground == null)
			disabledForeground = UIManager.getColor("Label.disabledForeground");
		if (disabledForeground != null && !disabledForeground.equals(getDisabledArrowColor())) {
			setDisabledArrowColor(disabledForeground);
			setDisabledImage(null);
		}
	}
}
