package game.f3dex2.commands;

import app.input.InvalidInputException;
import game.DataUtils;
import game.f3dex2.BaseF3DEX2;
import game.f3dex2.DisplayList.CommandType;

// 05 [AA] [BB] [CC] 00 00 00 00

public class Tri1 extends BaseF3DEX2
{
	public int v1, v2, v3;

	public Tri1(CommandType cmd, Integer ... args) throws InvalidInputException
	{
		super(cmd, args, 2);

		v1 = ((args[0] >> 16) & 0xFF) >> 1;
		v2 = ((args[0] >> 8) & 0xFF) >> 1;
		v3 = ((args[0] >> 0) & 0xFF) >> 1;

		if (args[1] != 0)
			throw new InvalidInputException("%s should not have nonzero second word %08X", getName(), args[1]);
	}

	public Tri1(CommandType cmd, String ... params) throws InvalidInputException
	{
		super(cmd, params, 3);

		v1 = DataUtils.parseIntString(params[0]);
		v2 = DataUtils.parseIntString(params[1]);
		v3 = DataUtils.parseIntString(params[2]);

		if (v1 < 0 || v1 >= 32)
			throw new InvalidInputException("%s vertex is out of buffer range (0-31): %X", getName(), v1);

		if (v2 < 0 || v2 >= 32)
			throw new InvalidInputException("%s vertex is out of buffer range (0-31): %X", getName(), v2);

		if (v3 < 0 || v3 >= 32)
			throw new InvalidInputException("%s vertex is out of buffer range (0-31): %X", getName(), v3);
	}

	@Override
	public int[] assemble()
	{
		int[] encoded = new int[2];
		encoded[0] = opField;

		encoded[0] |= ((v1 << 1) & 0xFF) << 16;
		encoded[0] |= ((v2 << 1) & 0xFF) << 8;
		encoded[0] |= ((v3 << 1) & 0xFF) << 0;

		return encoded;
	}

	@Override
	public String getString()
	{ return String.format("%-16s (%2d`, %2d`, %2d`)", getName(), v1, v2, v3); }
}
