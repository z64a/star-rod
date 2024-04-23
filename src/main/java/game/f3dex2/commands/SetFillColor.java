package game.f3dex2.commands;

import app.input.InvalidInputException;
import game.DataUtils;
import game.f3dex2.BaseF3DEX2;
import game.f3dex2.DisplayList.CommandType;

public class SetFillColor extends BaseF3DEX2
{
	int value;

	public SetFillColor(CommandType cmd, Integer ... args) throws InvalidInputException
	{
		super(cmd, args, 2);

		if ((args[0] & 0x00FFFFFF) != 0)
			throw new InvalidInputException("Invalid %s command: %08X", getName(), args[0]);

		value = args[1];
	}

	public SetFillColor(CommandType cmd, String ... params) throws InvalidInputException
	{
		super(cmd, params, 1);
		value = DataUtils.parseIntString(params[0]);
	}

	@Override
	public int[] assemble()
	{
		int[] encoded = new int[2];
		encoded[0] = opField;
		encoded[1] = value;
		return encoded;
	}

	@Override
	public String getString()
	{ return String.format("%-16s (%08X)", getName(), value); }
}
