package app.input;

import game.DataUtils;

public final class Token
{
	public final Line line;
	public final String text;

	// mutable field this class is a container for
	public String str;

	public Token(Line line, String text)
	{
		this.line = line;
		this.text = text;
		str = text;
	}

	@Override
	public String toString()
	{
		return str;
	}

	public String printLine()
	{
		return line.trimmedInput();
	}

	public Token create(String text)
	{
		return new Token(line, text);
	}

	public Token create(String fmt, Object ... args)
	{
		return new Token(line, String.format(fmt, args));
	}

	public Token[] split(String pattern)
	{
		String[] split = str.split(pattern);
		Token[] tokens = new Token[split.length];
		for (int i = 0; i < split.length; i++)
			tokens[i] = new Token(line, split[i]);
		return tokens;
	}

	public int getIntValue() throws InvalidInputException
	{ return DataUtils.parseIntString(str); }

	public int getHexValue() throws InvalidInputException
	{
		try {
			return (int) Long.parseLong(str, 16);
		}
		catch (NumberFormatException e) {
			throw new InvalidInputException(e);
		}
	}
}
