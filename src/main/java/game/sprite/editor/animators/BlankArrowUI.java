package game.sprite.editor.animators;

import javax.swing.JButton;
import javax.swing.plaf.basic.BasicComboBoxUI;

public class BlankArrowUI extends BasicComboBoxUI
{
	@Override
	protected JButton createArrowButton()
	{
		return new JButton() {
			@Override
			public int getWidth()
			{ return 0; }
		};
	}
}
