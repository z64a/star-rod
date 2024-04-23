package game.f3dex2.commands;

import app.input.InvalidInputException;
import game.DataUtils;
import game.f3dex2.BaseF3DEX2;
import game.f3dex2.DisplayList.CommandType;

/*
	F2 [SS S][T TT] 0[I] [WW W][H HH]

	SSS 	Upper-left corner of texture to load, S-axis
	TTT 	Upper-left corner of texture to load, T-axis
	I 	Tile descriptor to load into
	W 	(width - 1) « 2
	H 	(height - 1) « 2
*/

public class SetTileSize extends BaseF3DEX2
{
	public int descriptor;
	public int startS, startT;
	public int W, H;

	public SetTileSize(CommandType cmd, Integer ... args) throws InvalidInputException
	{
		super(cmd, args, 2);

		startS = (args[0] >> 12) & 0xFFF;
		startT = (args[0] >> 0) & 0xFFF;

		descriptor = (args[1] >> 24) & 0xF;
		W = (((args[1] >> 12) & 0xFFF) >> 2) + 1;
		H = (((args[1] >> 0) & 0xFFF) >> 2) + 1;
	}

	public SetTileSize(CommandType cmd, String ... params) throws InvalidInputException
	{
		super(cmd, params, 5);

		if (params[0].equalsIgnoreCase("G_TX_RENDERTILE"))
			descriptor = 0;
		else if (params[0].equalsIgnoreCase("G_TX_LOADTILE"))
			descriptor = 7;
		else
			descriptor = DataUtils.parseIntString(params[0]);

		startS = DataUtils.parseIntString(params[1]);
		startT = DataUtils.parseIntString(params[2]);
		W = DataUtils.parseIntString(params[3]);
		H = DataUtils.parseIntString(params[4]);
	}

	@Override
	public int[] assemble()
	{
		int[] encoded = new int[2];
		encoded[0] = opField;

		encoded[0] |= (startS & 0xFFF) << 12;
		encoded[0] |= (startT & 0xFFF);

		encoded[1] |= (descriptor & 0xF) << 24;
		encoded[1] |= (((W - 1) << 2) & 0xFFF) << 12;
		encoded[1] |= (((H - 1) << 2) & 0xFFF);

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

		return String.format("%-16s (%s, %X, %X, %d`, %d`)", getName(), descName, startS, startT, W, H);
	}
}
