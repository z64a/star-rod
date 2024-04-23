package app.input;

import java.io.File;

public class DummySource extends AbstractSource
{
	private final String name;

	public DummySource(String name)
	{
		this.name = name;
	}

	@Override
	public File getFile()
	{ return null; }

	@Override
	public String getName()
	{ return name; }
}
