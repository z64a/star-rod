package game.f3dex2.commands;

import app.input.InvalidInputException;
import game.DataUtils;
import game.f3dex2.BaseF3DEX2;
import game.f3dex2.DisplayList.CommandType;

// F8 00 00 00 [RR] [GG] [BB] [AA]

public class SetColor extends BaseF3DEX2
{
	int R, G, B, A;

	public SetColor(CommandType cmd, Integer ... args) throws InvalidInputException
	{
		super(cmd, args, 2);

		if ((args[0] & 0xFFFFFF) != 0)
			throw new InvalidInputException("Invalid %s command: %08X", getName(), args[0]);

		this.R = (args[1] >> 24) & 0xFF;
		this.G = (args[1] >> 16) & 0xFF;
		this.B = (args[1] >> 8) & 0xFF;
		this.A = (args[1] >> 0) & 0xFF;
	}

	public SetColor(CommandType cmd, String ... params) throws InvalidInputException
	{
		super(cmd, params, 4);

		R = DataUtils.parseIntString(params[0]);
		G = DataUtils.parseIntString(params[1]);
		B = DataUtils.parseIntString(params[2]);
		A = DataUtils.parseIntString(params[3]);
	}

	@Override
	public int[] assemble()
	{
		int[] encoded = new int[2];
		encoded[0] = opField;

		encoded[1] |= (R & 0xFF) << 24;
		encoded[1] |= (G & 0xFF) << 16;
		encoded[1] |= (B & 0xFF) << 8;
		encoded[1] |= (A & 0xFF) << 0;

		return encoded;
	}

	@Override
	public String getString()
	{
		return String.format("%-16s (%d`, %d`, %d`, %d`)", getName(), R, G, B, A);
	}
}
