package game.map.editor.ui.dialogs;

import java.io.File;

import javax.swing.JOptionPane;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import app.SwingUtils;

public class SaveFileChooser
{
	private final String title;
	private final String filterName;
	private final String[] filterExts;

	private File currentDirectory = null;
	private File selected = null;

	public SaveFileChooser(File dir, String title, String filterName, String ... filterExts)
	{
		this.title = title;
		this.filterName = filterName;
		this.filterExts = filterExts;
		currentDirectory = dir;
	}

	public void setCurrentDirectory(File dir)
	{
		currentDirectory = dir;
	}

	public ChooseDialogResult prompt(File dir)
	{
		currentDirectory = dir;
		return prompt();
	}

	public ChooseDialogResult prompt()
	{
		String defaultPath = (currentDirectory == null) ? null : currentDirectory.getAbsolutePath();
		ChooseDialogResult result;

		try (MemoryStack stack = MemoryStack.stackPush()) {
			PointerBuffer aFilterPatterns = stack.mallocPointer(filterExts.length);
			for (String s : filterExts)
				aFilterPatterns.put(stack.UTF8("*." + s));
			aFilterPatterns.flip();

			String path = TinyFileDialogs.tinyfd_saveFileDialog(title, defaultPath, aFilterPatterns, filterName);
			if (path != null) {
				selected = new File(path);
				result = checkForOverwrite(selected) ? ChooseDialogResult.APPROVE : ChooseDialogResult.CANCEL;
			}
			else {
				result = ChooseDialogResult.CANCEL;
			}
		}

		if (result == ChooseDialogResult.CANCEL)
			selected = null;
		if (result == ChooseDialogResult.APPROVE)
			currentDirectory = selected.getParentFile();

		return result;
	}

	public File getSelectedFile()
	{
		return selected;
	}

	public boolean checkForOverwrite(File f)
	{
		if (!f.exists())
			return true;

		int choice = SwingUtils.getConfirmDialog()
			.setTitle("File Already Exists")
			.setMessage("Overwrite existing file?")
			.choose();

		switch (choice) {
			case JOptionPane.YES_OPTION:
				return true;
			case JOptionPane.CANCEL_OPTION:
				return false;
			default:
				return false;
		}
	}
}
