package game.f3dex2.commands;

import app.input.InvalidInputException;
import game.DataUtils;
import game.f3dex2.BaseF3DEX2;
import game.f3dex2.DisplayList.CommandType;

//	ED [xx x][y yy] 0[m] [vv v][w ww]

public class SetScissor extends BaseF3DEX2
{
	int mode, X, Y, V, W;

	String[] modeOpt = {
			"G_SC_NON_INTERLACE",
			null, // 1
			"G_SC_EVEN_INTERLACE",
			"G_SC_ODD_INTERLACE"
	};

	public SetScissor(CommandType cmd, Integer ... args) throws InvalidInputException
	{
		super(cmd, args, 2);

		if ((args[1] & 0xF0000000) != 0)
			throw new InvalidInputException("Invalid %s command: %08X", getName(), args[0]);

		X = (args[0] >> 12) & 0xFFF;
		Y = args[0] & 0xFFF;

		V = (args[1] >> 12) & 0xFFF;
		W = args[1] & 0xFFF;

		mode = (args[1] >> 24) & 0xF;
	}

	public SetScissor(CommandType cmd, String ... params) throws InvalidInputException
	{
		super(cmd, params, 5);

		if (modeOpt[0].equalsIgnoreCase(params[0]))
			mode = 0;
		else if (modeOpt[1].equalsIgnoreCase(params[0]))
			mode = 1;
		else if (modeOpt[3].equalsIgnoreCase(params[0]))
			mode = 3;
		else
			mode = DataUtils.parseIntString(params[0]);

		X = DataUtils.parseIntString(params[1]);
		Y = DataUtils.parseIntString(params[2]);
		V = DataUtils.parseIntString(params[3]);
		W = DataUtils.parseIntString(params[4]);
	}

	@Override
	public int[] assemble()
	{
		int[] encoded = new int[2];
		encoded[0] = opField;

		encoded[0] |= (X & 0xFFF) << 12;
		encoded[0] |= (Y & 0xFFF);

		encoded[1] |= (mode & 0xF) << 24;
		encoded[1] |= (V & 0xFFF) << 12;
		encoded[1] |= (W & 0xFFF);

		return encoded;
	}

	@Override
	public String getString()
	{
		String modeName = null;
		if (mode >= 0 && mode < 4 && mode != 1)
			modeName = modeOpt[mode];
		else
			modeName = String.format("%X", mode);

		return String.format("%-16s (%s, %d`, %d`, %d`, %d`)", getName(), modeName, X, Y, V, W);
	}
}
