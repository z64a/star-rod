package app.config;

public class ConfigEntryException extends RuntimeException
{
	public final Options opt;

	public ConfigEntryException(Options opt, String msg)
	{
		super(msg);
		this.opt = opt;
	}
}
