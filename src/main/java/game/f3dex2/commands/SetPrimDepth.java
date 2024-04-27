package game.f3dex2.commands;

import app.input.InvalidInputException;
import game.DataUtils;
import game.f3dex2.BaseF3DEX2;
import game.f3dex2.DisplayList.CommandType;

//  EE 00 00 00 zz zz dd dd

public class SetPrimDepth extends BaseF3DEX2
{
	int Z, deltaZ;

	public SetPrimDepth(CommandType cmd, Integer ... args) throws InvalidInputException
	{
		super(cmd, args, 2);

		if (args[0] != opField)
			throw new InvalidInputException("Invalid %s command: %08X", getName(), args[0]);

		Z = (args[1] >> 16) & 0xFFFF;
		deltaZ = args[1] & 0xFFFF;

		Z = (Z << 16) >> 16;
		deltaZ = (deltaZ << 16) >> 16;
	}

	public SetPrimDepth(CommandType cmd, String ... params) throws InvalidInputException
	{
		super(cmd, params, 2);

		Z = DataUtils.parseIntString(params[0]);
		deltaZ = DataUtils.parseIntString(params[1]);
	}

	@Override
	public int[] assemble()
	{
		int[] encoded = new int[2];
		encoded[0] = opField;

		encoded[1] |= (Z & 0xFFFF) << 16;
		encoded[1] |= (deltaZ & 0xFFFF);

		return encoded;
	}

	@Override
	public String getString()
	{
		return String.format("%-16s (%d`, %d`)", getName(), Z, deltaZ);
	}
}
