package game.f3dex2.commands;

import app.input.InvalidInputException;
import game.DataUtils;
import game.f3dex2.BaseF3DEX2;
import game.f3dex2.DisplayList.CommandType;

// 06 [AA] [BB] [CC] 00 [AA] [CC] [DD]

public class Quad extends BaseF3DEX2
{
	int v1, v2, v3, v4;

	public Quad(CommandType cmd, Integer ... args) throws InvalidInputException
	{
		super(cmd, args, 2);
		v1 = ((args[0] >> 16) & 0xFF) >> 1;
		v2 = ((args[0] >> 8) & 0xFF) >> 1;
		v3 = ((args[0] >> 0) & 0xFF) >> 1;

		int v1b = ((args[1] >> 16) & 0xFF) >> 1;
		int v3b = ((args[1] >> 8) & 0xFF) >> 1;
		v4 = ((args[1] >> 0) & 0xFF) >> 1;

		if (v1 != v1b)
			throw new InvalidInputException("%s first and fourth vertices must match: %X and %X", getName(), v1, v1b);

		if (v3 != v3b)
			throw new InvalidInputException("%s third and fifth vertices must match: %X and %X", getName(), v3, v3b);
	}

	public Quad(CommandType cmd, String ... params) throws InvalidInputException
	{
		super(cmd, params, 3);

		v1 = DataUtils.parseIntString(params[0]);
		v2 = DataUtils.parseIntString(params[1]);
		v3 = DataUtils.parseIntString(params[2]);
		v4 = DataUtils.parseIntString(params[3]);

		checkRanges();
	}

	private void checkRanges() throws InvalidInputException
	{
		if (v1 < 0 || v1 >= 32)
			throw new InvalidInputException("%s vertex is out of buffer range (0-31): %X", getName(), v1);

		if (v2 < 0 || v2 >= 32)
			throw new InvalidInputException("%s vertex is out of buffer range (0-31): %X", getName(), v2);

		if (v3 < 0 || v3 >= 32)
			throw new InvalidInputException("%s vertex is out of buffer range (0-31): %X", getName(), v3);

		if (v4 < 0 || v4 >= 32)
			throw new InvalidInputException("%s vertex is out of buffer range (0-31): %X", getName(), v4);
	}

	@Override
	public int[] assemble()
	{
		int[] encoded = new int[2];
		encoded[0] = opField;

		encoded[0] |= ((v1 << 1) & 0xFF) << 16;
		encoded[0] |= ((v2 << 1) & 0xFF) << 8;
		encoded[0] |= ((v3 << 1) & 0xFF) << 0;

		encoded[1] |= ((v1 << 1) & 0xFF) << 16;
		encoded[1] |= ((v3 << 1) & 0xFF) << 8;
		encoded[1] |= ((v4 << 1) & 0xFF) << 0;

		return encoded;
	}

	@Override
	public String getString()
	{
		return String.format("%-16s (%2d`, %2d`, %2d`, %2d`)", getName(), v1, v2, v3, v4);
	}
}
