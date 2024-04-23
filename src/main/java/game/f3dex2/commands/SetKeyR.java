package game.f3dex2.commands;

import app.input.InvalidInputException;
import game.DataUtils;
import game.f3dex2.BaseF3DEX2;
import game.f3dex2.DisplayList.CommandType;

// EB 00 00 00 0[w ww] [cc] [ss]

public class SetKeyR extends BaseF3DEX2
{
	float W;
	int C, S;

	public SetKeyR(CommandType cmd, Integer ... args) throws InvalidInputException
	{
		super(cmd, args, 2);

		if ((args[0] & 0x00FFFFFF) != 0)
			throw new InvalidInputException("Invalid %s command: %08X", getName(), args[0]);
		if ((args[1] & 0xF0000000) != 0)
			throw new InvalidInputException("Invalid %s command: %08X", getName(), args[1]);

		W = ((args[1] >> 16) & 0xFFF) / 256.0f;
		C = (args[1] >> 8) & 0xFF;
		S = (args[1] >> 0) & 0xFF;
	}

	public SetKeyR(CommandType cmd, String ... params) throws InvalidInputException
	{
		super(cmd, params, 3);

		try {
			W = Float.parseFloat(params[0]);
		}
		catch (NumberFormatException e) {
			throw new InvalidInputException("Could not parse float: " + params[0]);
		}

		C = DataUtils.parseIntString(params[1]);
		S = DataUtils.parseIntString(params[2]);
	}

	@Override
	public int[] assemble()
	{
		int[] encoded = new int[2];
		encoded[0] = opField;

		encoded[1] |= ((int) (256f * W) & 0xFFF) << 16;
		encoded[1] |= (C & 0xFF) << 8;
		encoded[1] |= (S & 0xFF) << 0;

		return encoded;
	}

	@Override
	public String getString()
	{ return String.format("%-16s (%f, %d`, %d`)", getName(), W, C, S); }
}
