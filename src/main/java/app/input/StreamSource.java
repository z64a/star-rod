package app.input;

import java.io.File;

public class StreamSource extends AbstractSource
{
	private final String name;

	public StreamSource(String name)
	{
		this.name = name;
	}

	@Override
	public String getName()
	{
		return name;
	}

	@Override
	public File getFile()
	{
		return null;
	}
}
