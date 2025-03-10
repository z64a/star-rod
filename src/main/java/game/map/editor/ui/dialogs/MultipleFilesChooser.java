package game.map.editor.ui.dialogs;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

public class MultipleFilesChooser
{
	private final String title;
	private final String filterName;
	private final String[] filterExts;

	private File currentDirectory = null;
	private List<File> selected = new ArrayList<>();

	public MultipleFilesChooser(File dir, String title, String filterName, String ... filterExts)
	{
		this.title = title;
		StringBuilder sb = new StringBuilder(filterName);
		sb.append(" (");
		for (String s : filterExts)
			sb.append("*.").append(s);
		sb.append(")");
		this.filterName = sb.toString();
		this.filterExts = filterExts;
		currentDirectory = dir;
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

			String paths = TinyFileDialogs.tinyfd_openFileDialog(title, defaultPath, aFilterPatterns, filterName, true);
			if (paths != null) {
				for (String path : paths.split("\\|")) {
					File f = new File(path);
					selected.add(f);
					if (currentDirectory == null)
						currentDirectory = f.getParentFile();
				}
				result = ChooseDialogResult.APPROVE;
			}
			else {
				result = ChooseDialogResult.CANCEL;
				selected = null;
			}
		}

		return result;
	}

	public List<File> getSelectedFiles()
	{
		return selected;
	}
}
