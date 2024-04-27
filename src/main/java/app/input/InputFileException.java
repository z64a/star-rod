package app.input;

import java.io.File;

import util.Logger;

public class InputFileException extends RuntimeException
{
	private final File source;
	private final String sourceName;

	private final int line;

	public InputFileException(InputFileException original)
	{
		super(original.getMessage());
		source = original.source;
		sourceName = original.sourceName;
		line = original.line;
		setStackTrace(original.getStackTrace());
	}

	// token constructors

	public InputFileException(Token token, Throwable t)
	{
		this(token.line, t.getMessage());
		setStackTrace(t.getStackTrace());
	}

	public InputFileException(Token token, String format, Object ... args)
	{
		this(token.line, format, args);
	}

	public InputFileException(Token token, String msg)
	{
		this(token.line, msg);
	}

	// line constructors

	public InputFileException(Line line, Throwable t)
	{
		this(line, t.getMessage());
		setStackTrace(t.getStackTrace());
	}

	public InputFileException(Line line, String format, Object ... args)
	{
		this(line, String.format(format, args));
	}

	public InputFileException(Line line, String msg)
	{
		super(msg);

		if (line != null) {
			this.source = line.source.getFile();
			this.sourceName = (source == null) ? line.source.getName() : source.getName();
			this.line = line.lineNum;
		}
		else {
			this.source = null;
			this.sourceName = "Unknown Source";
			this.line = 0;
		}
	}

	// file constructors

	public InputFileException(File source, Throwable t)
	{
		this(source, -1, t.getMessage());
		setStackTrace(t.getStackTrace());
	}

	public InputFileException(File source, String msg)
	{
		this(source, -1, msg);
	}

	public InputFileException(File source, String format, Object ... args)
	{
		this(source, -1, format, args);
	}

	public InputFileException(File source, int line, String format, Object ... args)
	{
		this(source, line, String.format(format, args));
	}

	public InputFileException(File source, int line, String msg)
	{
		super(msg);
		this.source = source;
		this.sourceName = source.getName();
		this.line = line;
	}

	// source constructors

	public InputFileException(AbstractSource source, Throwable t)
	{
		this(source, -1, t.getMessage());
		setStackTrace(t.getStackTrace());
	}

	public InputFileException(AbstractSource source, String msg)
	{
		this(source, -1, msg);
	}

	public InputFileException(AbstractSource source, String format, Object ... args)
	{
		this(source, -1, format, args);
	}

	public InputFileException(AbstractSource source, int line, String format, Object ... args)
	{
		this(source, line, String.format(format, args));
	}

	public InputFileException(AbstractSource source, int line, String msg)
	{
		super(msg);
		if (source != null) {
			this.source = source.getFile();
			this.sourceName = source.getName();
		}
		else {
			this.source = null;
			this.sourceName = "Unknown Source";
			Logger.printStackTrace(this);
		}
		this.line = line;
	}

	public String getOrigin()
	{
		if (line >= 0)
			return sourceName + String.format(" [Line %d]", line);
		else
			return sourceName;
	}

	public File getSourceFile()
	{
		return source;
	}
}
