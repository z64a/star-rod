package game.f3dex2.commands;

import app.input.InvalidInputException;
import game.DataUtils;
import game.f3dex2.BaseF3DEX2;
import game.f3dex2.DisplayList.CommandType;

/*
	Draws a textured 2D rectangle on the screen

	E4 [xx x][y yy]  0[I] [XX X][Y YY]
	E1 00 00 00      [SS SS] [TT TT]
	F1 00 00 00      [DD DD] [EE EE]

	xxx 	Lower-right corner X coordinate
	yyy 	Lower-right corner Y coordinate
	I 		Tile descriptor to use for rectangle
	XXX 	Upper-left corner X coordinate
	YYY 	Upper-left corner Y coordinate
	SSSS 	Texture S coordinate at upper-left corner
	TTTT 	Texture T coordinate at upper-left corner
	DDDD 	Change in S coordinate over change in X coordinate
	EEEE 	Change in T coordinate over change in Y coordinate
 */
public class TexRect extends BaseF3DEX2
{
	public int descriptor;

	public float lrx, lry;
	public float ulx, uly;

	public float uls, ult;
	public float dsdx, dtdy;

	private static final float CVT_FIXED_10_2 = 4.0f;
	private static final float CVT_FIXED_10_5 = 32.0f;
	private static final float CVT_FIXED_5_10 = 1024.0f;

	public TexRect(CommandType cmd, Integer ... args) throws InvalidInputException
	{
		super(cmd, args, 6);

		if ((args[1] & 0xF0000000) != 0)
			throw new InvalidInputException("Invalid %s command %08X", getName(), args[0]);

		if (args[2] != 0xE1000000)
			throw new InvalidInputException("Invalid %s command %08X", getName(), args[2]);

		if (args[4] != 0xF1000000)
			throw new InvalidInputException("Invalid %s command %08X", getName(), args[4]);

		lrx = ((args[0] >> 12) & 0xFFF) / CVT_FIXED_10_2;
		lry = (args[0] & 0xFFF) / CVT_FIXED_10_2;

		descriptor = (args[1] >> 24) & 0xF;
		ulx = ((args[1] >> 12) & 0xFFF) / CVT_FIXED_10_2;
		uly = (args[1] & 0xFFF) / CVT_FIXED_10_2;

		uls = signExtend16((args[3] >> 16) & 0xFFFF) / CVT_FIXED_10_5;
		ult = signExtend16(args[3] & 0xFFFF) / CVT_FIXED_10_5;

		dsdx = signExtend16((args[5] >> 16) & 0xFFFF) / CVT_FIXED_5_10;
		dtdy = signExtend16(args[5] & 0xFFFF) / CVT_FIXED_5_10;
	}

	private static int signExtend16(int x)
	{
		return (x << 16) >> 16;
	}

	public TexRect(CommandType cmd, String ... params) throws InvalidInputException
	{
		super(cmd, params, 9);

		if (params[0].equalsIgnoreCase("G_TX_RENDERTILE"))
			descriptor = 0;
		else if (params[0].equalsIgnoreCase("G_TX_LOADTILE"))
			descriptor = 7;
		else
			descriptor = DataUtils.parseIntString(params[0]);

		try {
			ulx = Float.parseFloat(params[1]);
		}
		catch (NumberFormatException e) {
			throw new InvalidInputException("Could not parse float: " + params[1]);
		}

		try {
			uly = Float.parseFloat(params[2]);
		}
		catch (NumberFormatException e) {
			throw new InvalidInputException("Could not parse float: " + params[2]);
		}

		try {
			lrx = Float.parseFloat(params[3]);
		}
		catch (NumberFormatException e) {
			throw new InvalidInputException("Could not parse float: " + params[3]);
		}

		try {
			lry = Float.parseFloat(params[4]);
		}
		catch (NumberFormatException e) {
			throw new InvalidInputException("Could not parse float: " + params[4]);
		}

		try {
			uls = Float.parseFloat(params[5]);
		}
		catch (NumberFormatException e) {
			throw new InvalidInputException("Could not parse float: " + params[5]);
		}

		try {
			ult = Float.parseFloat(params[6]);
		}
		catch (NumberFormatException e) {
			throw new InvalidInputException("Could not parse float: " + params[6]);
		}

		try {
			dsdx = Float.parseFloat(params[7]);
		}
		catch (NumberFormatException e) {
			throw new InvalidInputException("Could not parse float: " + params[7]);
		}

		try {
			dtdy = Float.parseFloat(params[8]);
		}
		catch (NumberFormatException e) {
			throw new InvalidInputException("Could not parse float: " + params[8]);
		}
	}

	@Override
	public int[] assemble()
	{
		int[] encoded = new int[6];

		encoded[0] = opField;
		encoded[0] |= (Math.round(lrx * CVT_FIXED_10_2) & 0xFFF) << 12;
		encoded[0] |= (Math.round(lry * CVT_FIXED_10_2) & 0xFFF);

		encoded[1] |= (descriptor & 0xF) << 24;
		encoded[1] |= (Math.round(ulx * CVT_FIXED_10_2) & 0xFFF) << 12;
		encoded[1] |= (Math.round(uly * CVT_FIXED_10_2) & 0xFFF);

		encoded[2] = 0xE1000000;

		encoded[3] |= (Math.round(uls * CVT_FIXED_10_5) & 0xFFFF) << 16;
		encoded[3] |= (Math.round(ult * CVT_FIXED_10_5) & 0xFFFF);

		encoded[4] = 0xF1000000;

		encoded[5] |= (Math.round(dsdx * CVT_FIXED_5_10) & 0xFFFF) << 16;
		encoded[5] |= (Math.round(dtdy * CVT_FIXED_5_10) & 0xFFFF);

		return encoded;
	}

	@Override
	public String getString()
	{
		// gsSPTextureRectangle(tile, ulx, uly, lrx, lry, uls, ult, dsdx, dtdy)

		String descName;
		if (descriptor == 0)
			descName = "G_TX_RENDERTILE";
		else if (descriptor == 7)
			descName = "G_TX_LOADTILE";
		else
			descName = String.format("%X", descriptor);

		return String.format("%-16s (%s, %f, %f, %f, %f, %f, %f, %f, %f)", getName(), descName,
			ulx, uly, lrx, lry, uls, ult, dsdx, dtdy);
	}
}
