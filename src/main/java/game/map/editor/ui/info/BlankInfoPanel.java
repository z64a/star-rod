package game.map.editor.ui.info;

import game.map.editor.MapInfoPanel;
import game.map.shape.Model;

public class BlankInfoPanel extends MapInfoPanel<Model>
{
	public BlankInfoPanel()
	{
		super(false);
	}

	@Override
	public void updateFields(Model obj, String tag)
	{}
}
