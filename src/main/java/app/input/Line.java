package app.input;

import java.util.List;

import game.DataUtils;

public final class Line
{
	public final AbstractSource source;
	public final int lineNum;
	public final String text;

	public Token[] tokens = null;
	public boolean tokenized = false;

	// mutable field this class is a container for
	public String str;

	public Line(AbstractSource source, int lineNum, String text)
	{
		this.source = source;
		this.lineNum = lineNum;
		this.text = text;
		str = text;
	}

	/**
	 * Creates a line at the location of this line with tokens given by strings
	 * @param strings tokens for the new line to contain
	 */
	public Line createLine(String[] strings)
	{
		String sb = String.join(" ", strings);
		Line line = new Line(this.source, this.lineNum, sb);
		line.tokens = new Token[strings.length];
		for (int i = 0; i < strings.length; i++)
			line.tokens[i] = line.createToken(strings[i]);

		tokenized = true;
		return line;
	}

	public Line createLine(String text)
	{
		return new Line(this.source, this.lineNum, text);
	}

	public Line createLine(String fmt, Object ... args)
	{
		return new Line(this.source, this.lineNum, String.format(fmt, args));
	}

	public Token createToken(String text)
	{
		return new Token(this, text);
	}

	public Token createToken(String fmt, Object ... args)
	{
		return new Token(this, String.format(fmt, args));
	}

	public void tokenize()
	{
		tokenize("\\s+");
	}

	public void tokenize(String pattern)
	{
		String[] split = str.split(pattern);

		tokens = new Token[split.length];
		for (int i = 0; i < split.length; i++)
			tokens[i] = new Token(this, split[i]);

		tokenized = true;
	}

	public void gather()
	{
		gather(" ");
	}

	public void gather(String separator)
	{
		if (!tokenized)
			throw new IllegalStateException("Cannot gather unless line has been tokenized!");
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < tokens.length; i++) {
			if (i > 0)
				sb.append(separator);
			sb.append(tokens[i].str);
		}
		str = sb.toString();
	}

	public int numTokens()
	{
		return tokens.length;
	}

	public Token get(int index)
	{
		return tokens[index];
	}

	public String getString(int index)
	{
		return tokens[index].str;
	}

	public int getInt(int index)
	{
		int v;
		try {
			v = DataUtils.parseIntString(tokens[index].str);
		}
		catch (InvalidInputException e) {
			throw new InputFileException(this, "InvalidInputException caused by: %s %nExpected a numeric value.", tokens[index].str);
		}
		return v;
	}

	public int getHex(int index)
	{
		int v;
		try {
			v = (int) Long.parseLong(tokens[index].str, 16);
		}
		catch (NumberFormatException e) {
			throw new InputFileException(this, "NumberFormatException caused by: %s %nExpected a numeric value.", tokens[index].str);
		}
		return v;
	}

	public float getFloat(int index)
	{
		float f;
		try {
			f = Float.parseFloat(tokens[index].str);
		}
		catch (NumberFormatException e) {
			throw new InputFileException(this, "NumberFormatException caused by: %s %nExpected a float value.", tokens[index].str);
		}
		return f;
	}

	public void set(int index, String s)
	{
		tokens[index] = tokens[index].create(s);
	}

	public void set(int index, String fmt, Object ... args)
	{
		tokens[index] = tokens[index].create(fmt, args);
	}

	public void replace(int index, String ... strings)
	{
		Token[] newTokens = new Token[(tokens.length - 1) + strings.length];

		int i = 0;
		for (; i < index; i++)
			newTokens[i] = tokens[i];

		for (; i < index + strings.length; i++)
			newTokens[i] = tokens[index].create(strings[i - index]);

		for (; i < newTokens.length; i++)
			newTokens[i] = tokens[i + 1 - strings.length];

		tokens = newTokens;
	}

	public void replace(String ... strings)
	{
		Token[] newTokens = new Token[strings.length];

		for (int i = 0; i < newTokens.length; i++)
			newTokens[i] = new Token(this, strings[i]);

		tokens = newTokens;
	}

	public void replace(List<String> strings)
	{
		Token[] newTokens = new Token[strings.size()];

		int i = 0;
		for (String s : strings)
			newTokens[i++] = new Token(this, s);

		tokens = newTokens;
	}

	public String trimmedInput()
	{
		return text.replaceAll("\\s+", " ").trim();
	}

	@Override
	public String toString()
	{
		return str;
	}

	public void print()
	{
		if (tokens == null)
			System.out.print(str);
		else
			for (Token t : tokens)
				System.out.print(t + " ");
		System.out.println();
	}
}
