package game.sprite.editor.commands;

import javax.swing.JTabbedPane;

import common.commands.AbstractCommand;
import game.sprite.editor.SpriteEditor;

public class SelectSpritesTab extends AbstractCommand
{
	private final SpriteEditor editor;
	private final JTabbedPane tabs;
	private final int prevIndex;
	private final int nextIndex;

	public SelectSpritesTab(JTabbedPane tabs, int index)
	{
		super(index == 0 ? "View NPC Sprites" : "View Player Sprites");
		this.tabs = tabs;
		this.nextIndex = index;

		editor = SpriteEditor.instance();
		this.prevIndex = editor.getSpriteTab();
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
		editor.ignoreSelectionEvents = true;
		tabs.setSelectedIndex(nextIndex);
		editor.ignoreSelectionEvents = false;

		SpriteEditor editor = SpriteEditor.instance();
		editor.setSpritesTab(nextIndex);
	}

	@Override
	public void undo()
	{
		super.undo();

		// force tab selection to update, but suppress generating a new command
		editor.ignoreSelectionEvents = true;
		tabs.setSelectedIndex(prevIndex);
		editor.ignoreSelectionEvents = false;

		SpriteEditor editor = SpriteEditor.instance();
		editor.setSpritesTab(prevIndex);
	}
}
