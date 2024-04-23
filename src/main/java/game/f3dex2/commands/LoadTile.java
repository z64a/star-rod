package game.f3dex2.commands;

import app.input.InvalidInputException;
import game.DataUtils;
import game.f3dex2.BaseF3DEX2;
import game.f3dex2.DisplayList.CommandType;

/*
	F4  [ss s][t tt] 0[i] [uu u][v vv]
	s 	Upper-left corner of tile, S-axis
	t 	Upper-left corner of tile, T-axis
	i 	Tile descriptor being loaded to
	u 	Lower-right corner of tile, S-axis
	v 	Lower-right corner of tile, T-axis

	(s, t) specifies the upper-left corner of the texture in RAM, offset from its earlier-specified origin
	(u, v) specifies the lower-right corner of the texture to load.
	All coordinate values are in unsigned fixed-point 10.2 format (range 0 ≤ n ≤ 1023.75).
*/

public class LoadTile extends BaseF3DEX2
{
	public int descriptor;
	public float uls, ult;
	public float lrs, lrt;

	public LoadTile(CommandType cmd, Integer ... args) throws InvalidInputException
	{
		super(cmd, args, 2);

		uls = ((args[0] >> 12) & 0xFFF) / 4.0f;
		ult = ((args[0] >> 0) & 0xFFF) / 4.0f;

		descriptor = (args[1] >> 24) & 0xF;
		lrs = ((args[1] >> 12) & 0xFFF) / 4.0f;
		lrt = ((args[1] >> 0) & 0xFFF) / 4.0f;
	}

	public LoadTile(CommandType cmd, String ... params) throws InvalidInputException
	{
		super(cmd, params, 5);

		if (params[0].equalsIgnoreCase("G_TX_RENDERTILE"))
			descriptor = 0;
		else if (params[0].equalsIgnoreCase("G_TX_LOADTILE"))
			descriptor = 7;
		else
			descriptor = DataUtils.parseIntString(params[0]);

		try {
			uls = Float.parseFloat(params[1]);
		}
		catch (NumberFormatException e) {
			throw new InvalidInputException("Could not parse float: " + params[1]);
		}

		try {
			ult = Float.parseFloat(params[2]);
		}
		catch (NumberFormatException e) {
			throw new InvalidInputException("Could not parse float: " + params[2]);
		}

		try {
			lrs = Float.parseFloat(params[3]);
		}
		catch (NumberFormatException e) {
			throw new InvalidInputException("Could not parse float: " + params[3]);
		}

		try {
			lrt = Float.parseFloat(params[4]);
		}
		catch (NumberFormatException e) {
			throw new InvalidInputException("Could not parse float: " + params[4]);
		}
	}

	@Override
	public int[] assemble()
	{
		int[] encoded = new int[2];
		encoded[0] = opField;

		encoded[0] |= (Math.round(uls * 4.0f) & 0xFFF) << 12;
		encoded[0] |= (Math.round(ult * 4.0f) & 0xFFF);

		encoded[1] |= (descriptor & 0xF) << 24;
		encoded[1] |= (Math.round(lrs * 4.0f) & 0xFFF) << 12;
		encoded[1] |= (Math.round(lrt * 4.0f) & 0xFFF);

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

		return String.format("%-16s (%s, %f, %f, %f, %f)", getName(), descName, uls, ult, lrs, lrt);
	}
}
