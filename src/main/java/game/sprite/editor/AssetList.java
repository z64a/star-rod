package game.sprite.editor;

import javax.swing.JList;

import util.EnableCounter;

public class AssetList<T> extends JList<T>
{
	public EnableCounter ignoreChanges = new EnableCounter();
}
