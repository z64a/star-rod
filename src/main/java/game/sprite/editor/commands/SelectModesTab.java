package game.sprite.editor.commands;

import javax.swing.JTabbedPane;

import common.commands.AbstractCommand;
import game.sprite.editor.SpriteEditor;

public class SelectModesTab extends AbstractCommand
{
	private final SpriteEditor editor;
	private final JTabbedPane tabs;
	private final int prevIndex;
	private final int nextIndex;

	public SelectModesTab(JTabbedPane tabs, int index)
	{
		super("Change Editor Tab");
		this.tabs = tabs;
		this.nextIndex = index;

		editor = SpriteEditor.instance();
		this.prevIndex = editor.getModesTab();
	}

	@Override
	public boolean shouldExec()
	{
		return prevIndex != nextIndex;
	}

	@Override
	public boolean modifiesData()
	{
		return false;
	}

	@Override
	public void exec()
	{
		super.exec();

		// force tab selection to update, but suppress generating a new command
		editor.suppressSelectionEvents = true;
		tabs.setSelectedIndex(nextIndex);
		editor.suppressSelectionEvents = false;

		SpriteEditor editor = SpriteEditor.instance();
		editor.setModesTab(nextIndex);
	}

	@Override
	public void undo()
	{
		super.undo();

		// force tab selection to update, but suppress generating a new command
		editor.suppressSelectionEvents = true;
		tabs.setSelectedIndex(prevIndex);
		editor.suppressSelectionEvents = false;

		SpriteEditor editor = SpriteEditor.instance();
		editor.setModesTab(prevIndex);
	}
}
