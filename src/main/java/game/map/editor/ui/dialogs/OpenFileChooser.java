package game.map.editor.ui.dialogs;

import java.io.File;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

public class OpenFileChooser
{
	private final String title;
	private String baseFilterName;
	private String filterName;
	private String[] filterExts;

	private File currentDirectory = null;
	private File selected = null;

	public OpenFileChooser(File dir, String title, String baseFilterName, String ... filterExts)
	{
		this.title = title;
		currentDirectory = dir;

		this.baseFilterName = baseFilterName;
		setExtenstions(filterExts);
	}

	public void setDirectoryContaining(File dir)
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

			String path = TinyFileDialogs.tinyfd_openFileDialog(title, defaultPath, aFilterPatterns, filterName, false);
			if (path != null) {
				result = ChooseDialogResult.APPROVE;
				selected = new File(path);
				currentDirectory = selected.getParentFile();
			}
			else {
				result = ChooseDialogResult.CANCEL;
				selected = null;
			}
		}

		return result;
	}

	public File getSelectedFile()
	{
		return selected;
	}

	public String[] getExtensions()
	{
		return filterExts;
	}

	public void setExtenstions(String ... exts)
	{
		filterExts = exts;

		StringBuilder sb = new StringBuilder(baseFilterName);
		sb.append(" (");
		for (String s : filterExts)
			sb.append("*.").append(s);
		sb.append(")");
	}
}
