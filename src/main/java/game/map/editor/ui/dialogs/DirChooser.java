package game.map.editor.ui.dialogs;

import java.io.File;

import org.lwjgl.util.tinyfd.TinyFileDialogs;

public class DirChooser
{
	private String title;

	private File currentDirectory = null;
	private File selected = null;

	public DirChooser(File dir, String title)
	{
		this.title = title;
		currentDirectory = dir;
	}

	public void setCurrentDirectory(File dir)
	{ currentDirectory = dir; }

	public ChooseDialogResult prompt(File dir)
	{
		currentDirectory = dir;
		return prompt();
	}

	public ChooseDialogResult prompt()
	{
		String defaultPath = (currentDirectory == null) ? null : currentDirectory.getAbsolutePath();
		String path = TinyFileDialogs.tinyfd_selectFolderDialog(title, defaultPath);
		if (path == null)
			return ChooseDialogResult.CANCEL;
		selected = new File(path);
		currentDirectory = selected.getParentFile();
		return ChooseDialogResult.APPROVE;
	}

	public File getSelectedFile()
	{ return selected; }
}
