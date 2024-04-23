package game.f3dex2;

import app.input.InvalidInputException;
import game.f3dex2.DisplayList.CommandType;

public class NoArg extends BaseF3DEX2
{
	public NoArg(CommandType cmd, Integer ... args) throws InvalidInputException
	{
		super(cmd, args, 2);
	}

	public NoArg(CommandType cmd, String ... args) throws InvalidInputException
	{
		super(cmd, args, 0);
	}

	@Override
	public String getString()
	{ return getName(); }
}
