package game.map.editor.commands;

import common.commands.AbstractCommand;
import game.map.editor.MapEditor;

public class ChangeTextureArchive extends AbstractCommand
{
	private final String oldName;
	private final String newName;

	public ChangeTextureArchive(String name)
	{
		super("Use Texture Archive " + name);

		MapEditor editor = MapEditor.instance();
		this.oldName = editor.map.texName;
		this.newName = name;
	}

	@Override
	public boolean shouldExec()
	{
		return !newName.isEmpty() && !newName.equals(oldName);
	}

	@Override
	public void exec()
	{
		super.exec();

		MapEditor editor = MapEditor.instance();
		editor.map.texName = newName;
		editor.needsTextureReload = true;
	}

	@Override
	public void undo()
	{
		super.undo();

		MapEditor editor = MapEditor.instance();
		editor.map.texName = oldName;
		editor.needsTextureReload = true;
	}
}
