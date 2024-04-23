package game.map.compiler;

public class BuildException extends RuntimeException
{
	public BuildException(String msg)
	{
		super(msg);
	}

	public BuildException(String format, Object ... args)
	{
		super(String.format(format, args));
	}
}
