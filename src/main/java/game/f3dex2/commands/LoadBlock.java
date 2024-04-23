package game.f3dex2.commands;

import app.input.InvalidInputException;
import game.DataUtils;
import game.f3dex2.BaseF3DEX2;
import game.f3dex2.DisplayList.CommandType;

/*
	F3: G_LOADBLOCK

	Determines how much data to load after SETTIMG

	F3 [SS S][T TT] 0[I] [XX X][D DD]

	SSS 	Upper-left corner of texture to load, S-axis
	TTT 	Upper-left corner of texture to load, T-axis
	I 		Tile descriptor to load into
	XXX 	Number of texels to load to TMEM, minus one
	DDD 	dxt
*/

public class LoadBlock extends BaseF3DEX2
{
	public int descriptor;

	public float uls, ult;
	public float dxt;
	public int texels;

	private static final float CVT_FIXED_10_2 = 4.0f;
	private static final float CVT_FIXED_1_11 = 2048.0f;

	public LoadBlock(CommandType cmd, Integer ... args) throws InvalidInputException
	{
		super(cmd, args, 2);

		if ((args[1] & 0xF0000000) != 0)
			throw new InvalidInputException("Invalid %s command %08X", getName(), args[0]);

		uls = ((args[0] >> 12) & 0xFFF) / CVT_FIXED_10_2;
		ult = (args[0] & 0xFFF) / CVT_FIXED_10_2;

		descriptor = (args[1] >> 24) & 0xF;
		texels = ((args[1] >> 12) & 0xFFF) + 1;
		dxt = (args[1] & 0xFFF) / CVT_FIXED_1_11;
	}

	public LoadBlock(CommandType cmd, String ... params) throws InvalidInputException
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

		texels = DataUtils.parseIntString(params[3]);

		try {
			dxt = Float.parseFloat(params[4]);
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

		encoded[0] |= (Math.round(uls * CVT_FIXED_10_2) & 0xFFF) << 12;
		encoded[0] |= (Math.round(ult * CVT_FIXED_10_2) & 0xFFF);

		encoded[1] |= (descriptor & 0xF) << 24;
		encoded[1] |= ((texels - 1) & 0xFFF) << 12;
		encoded[1] |= Math.round(dxt * CVT_FIXED_1_11) & 0xFFF;

		return encoded;
	}

	@Override
	public String getString()
	{
		// gsDPLoadBlock(tile, uls, ult, texels, dxt)

		String descName;
		if (descriptor == 0)
			descName = "G_TX_RENDERTILE";
		else if (descriptor == 7)
			descName = "G_TX_LOADTILE";
		else
			descName = String.format("%X", descriptor);

		return String.format("%-16s (%s, %f, %f, %X, %f)", getName(), descName, uls, ult, texels, dxt);
	}
}
