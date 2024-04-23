package game.f3dex2.commands;

import app.input.InvalidInputException;
import game.DataUtils;
import game.f3dex2.BaseF3DEX2;
import game.f3dex2.DisplayList.CommandType;

// F0 00 00 00 0[t] [cc c]0 00

public class LoadTLUT extends BaseF3DEX2
{
	public int descriptor;
	public int numColors;

	public LoadTLUT(CommandType cmd, Integer ... args) throws InvalidInputException
	{
		super(cmd, args, 2);

		descriptor = (args[1] >> 24) & 0xF;
		numColors = (((args[1] >> 12) & 0xFFF) >> 2) + 1;
	}

	public LoadTLUT(CommandType cmd, String ... params) throws InvalidInputException
	{
		super(cmd, params, 2);

		if (params[0].equalsIgnoreCase("G_TX_RENDERTILE"))
			descriptor = 0;
		else if (params[0].equalsIgnoreCase("G_TX_LOADTILE"))
			descriptor = 7;
		else
			descriptor = DataUtils.parseIntString(params[0]);

		numColors = DataUtils.parseIntString(params[1]);
	}

	@Override
	public int[] assemble()
	{
		int[] encoded = new int[2];
		encoded[0] = opField;

		encoded[1] |= (descriptor & 0xF) << 24;
		encoded[1] |= (((numColors - 1) & 0x3FF) << 2) << 12;

		return encoded;
	}

	@Override
	public String getString()
	{
		String descName;
		if (descriptor == 0)
			descName = "G_TX_RENDERTILE";
		else if (descriptor == 7)
			descName = "G_TX_LOADTILE";
		else
			descName = String.format("%X", descriptor);

		return String.format("%-16s (%s, %d`)", getName(), descName, numColors);
	}
}
