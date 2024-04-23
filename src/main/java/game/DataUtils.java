package game;

import app.input.InvalidInputException;

//TODO remove? remains only for unused(?) f3dex2 dump/parsing code
public class DataUtils
{
	public static int parseIntString(String s) throws InvalidInputException
	{
		try {
			if (s.endsWith("`") || s.endsWith("'"))
				return (int) Long.parseLong(s.substring(0, s.length() - 1));
			else
				return (int) Long.parseLong(s, 16);
		}
		catch (NumberFormatException e) {
			throw new InvalidInputException(e);
		}
	}
}
