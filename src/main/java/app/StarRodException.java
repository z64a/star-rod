package app;

import java.io.File;

public class StarRodException extends RuntimeException
{
	public final File log;

	public StarRodException(String fmt, Object ... args)
	{
		super(String.format(fmt, args));
		log = null;
	}

	public StarRodException(Throwable t)
	{
		this(t, null);
	}

	public StarRodException(Throwable t, File logFile)
	{
		super(t.getMessage());
		setStackTrace(t.getStackTrace());
		log = logFile;
	}
}
