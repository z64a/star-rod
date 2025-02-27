package game.sprite.editor.commands;

import javax.swing.JComboBox;

import common.commands.AbstractCommand;
import game.sprite.Sprite;
import game.sprite.SpritePalette;
import game.sprite.editor.SpriteEditor;

public class SetOverridePalette extends AbstractCommand
{
	private final SpriteEditor editor;
	private final JComboBox<?> box;
	private final Sprite sprite;
	private final SpritePalette prev;
	private final SpritePalette next;

	public SetOverridePalette(JComboBox<?> box, Sprite sprite)
	{
		super("Set Override Palette");
		this.box = box;
		this.sprite = sprite;

		editor = SpriteEditor.instance();
		this.prev = sprite.overridePalette;
		this.next = (SpritePalette) box.getSelectedItem();
	}

	@Override
	public boolean shouldExec()
	{
		return prev != next;
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

		// force combobox to update, but suppress generating a new command
		editor.suppressCommands = true;
		box.setSelectedItem(next);
		editor.suppressCommands = false;

		sprite.overridePalette = next;
	}

	@Override
	public void undo()
	{
		super.undo();

		// force combobox to update, but suppress generating a new command
		editor.suppressCommands = true;
		box.setSelectedItem(prev);
		editor.suppressCommands = false;

		sprite.overridePalette = prev;
	}
}
