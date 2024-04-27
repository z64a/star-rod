package app.input;

import java.io.File;

public class AnonymousSource extends AbstractSource
{
	private static AnonymousSource instance;

	public static AnonymousSource instance()
	{
		if (instance == null)
			instance = new AnonymousSource();

		return instance;
	}

	private AnonymousSource()
	{}

	@Override
	public File getFile()
	{
		return null;
	}

	@Override
	public String getName()
	{
		return "Anonymous";
	}
}
