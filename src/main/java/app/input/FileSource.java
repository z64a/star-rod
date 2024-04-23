package app.input;

import java.io.File;

public class FileSource extends AbstractSource
{
	private final File file;

	public FileSource(File file)
	{
		this.file = file;
	}

	@Override
	public String getName()
	{ return (file == null) ? "null" : file.getName(); }

	@Override
	public File getFile()
	{ return file; }
}
