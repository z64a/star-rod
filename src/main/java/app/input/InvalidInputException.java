package app.input;

public class InvalidInputException extends Exception
{
	public InvalidInputException(String format, Object ... args)
	{
		this(String.format(format, args));
	}

	public InvalidInputException(String msg)
	{
		super(msg);
	}

	public InvalidInputException(Throwable t)
	{
		super(t.getMessage());
		setStackTrace(t.getStackTrace());
	}
}
