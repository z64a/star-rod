package game.f3dex2.commands;

import app.input.InvalidInputException;
import game.DataUtils;
import game.f3dex2.BaseF3DEX2;
import game.f3dex2.DisplayList.CommandType;

// EA [ww w][x xx] [cc] [ss] [dd] [tt]

public class SetKeyGB extends BaseF3DEX2
{
	private float W, X;
	private int C, S, D, T;

	private static final float CVT_FIXED_4_8 = 256.0f;

	public SetKeyGB(CommandType cmd, Integer ... args) throws InvalidInputException
	{
		super(cmd, args, 2);

		W = ((args[0] >> 12) & 0xFFF) / CVT_FIXED_4_8;
		X = ((args[0] >> 0) & 0xFFF) / CVT_FIXED_4_8;
		C = (args[1] >> 24) & 0xFF;
		S = (args[1] >> 16) & 0xFF;
		D = (args[1] >> 8) & 0xFF;
		T = (args[1] >> 0) & 0xFF;
	}

	public SetKeyGB(CommandType cmd, String ... params) throws InvalidInputException
	{
		super(cmd, params, 6);

		try {
			W = Float.parseFloat(params[0]);
		}
		catch (NumberFormatException e) {
			throw new InvalidInputException("Could not parse float: " + params[0]);
		}

		try {
			X = Float.parseFloat(params[1]);
		}
		catch (NumberFormatException e) {
			throw new InvalidInputException("Could not parse float: " + params[1]);
		}

		C = DataUtils.parseIntString(params[2]);
		S = DataUtils.parseIntString(params[3]);
		D = DataUtils.parseIntString(params[4]);
		T = DataUtils.parseIntString(params[5]);
	}

	@Override
	public int[] assemble()
	{
		int[] encoded = new int[2];
		encoded[0] = opField;

		encoded[0] |= (Math.round(W * CVT_FIXED_4_8) & 0xFFF) << 12;
		encoded[0] |= (Math.round(X * CVT_FIXED_4_8) & 0xFFF);

		encoded[1] |= (C & 0xFF) << 24;
		encoded[1] |= (S & 0xFF) << 16;
		encoded[1] |= (D & 0xFF) << 8;
		encoded[1] |= (T & 0xFF) << 0;

		return encoded;
	}

	@Override
	public String getString()
	{ return String.format("%-16s (%f, %f, %d`, %d`, %d`, %d`)", getName(), W, X, C, S, D, T); }
}
