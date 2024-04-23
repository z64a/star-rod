package game.map.editor.commands;

public class ChangeTextureArchive extends AbstractCommand
{
	private final String oldName;
	private final String newName;

	public ChangeTextureArchive(String name)
	{
		super("Use Texture Archive " + name);

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

		editor.map.texName = newName;
		editor.needsTextureReload = true;
	}

	@Override
	public void undo()
	{
		super.undo();

		editor.map.texName = oldName;
		editor.needsTextureReload = true;
	}
}
