package game.f3dex2.commands;

import app.input.InvalidInputException;
import game.f3dex2.BaseF3DEX2;
import game.f3dex2.DisplayList.CommandType;

/*
	Draws a colored rectangle on the screen. Use command 0xF7 to set the color of the rectangle.

	F6 [VV V][W WW] 00 [XX X][Y YY]

	VVV 	Lower-right corner of rectangle, X-axis
	WWW 	Lower-right corner of rectangle, Y-axis
	XXx 	Upper-left corner of rectangle, X-axis
	YYY 	Upper-left corner of rectangle, Y-axis
 */
public class FillRect extends BaseF3DEX2
{
	public float lrx, lry;
	public float ulx, uly;

	private static final float CVT_FIXED_10_2 = 4.0f;

	public FillRect(CommandType cmd, Integer ... args) throws InvalidInputException
	{
		super(cmd, args, 2);

		if ((args[1] & 0xFF000000) != 0)
			throw new InvalidInputException("Invalid %s command %08X", getName(), args[1]);

		lrx = ((args[0] >> 12) & 0xFFF) / CVT_FIXED_10_2;
		lry = (args[0] & 0xFFF) / CVT_FIXED_10_2;

		ulx = ((args[1] >> 12) & 0xFFF) / CVT_FIXED_10_2;
		uly = (args[1] & 0xFFF) / CVT_FIXED_10_2;
	}

	public FillRect(CommandType cmd, String ... params) throws InvalidInputException
	{
		super(cmd, params, 4);

		try {
			ulx = Float.parseFloat(params[0]);
		}
		catch (NumberFormatException e) {
			throw new InvalidInputException("Could not parse float: " + params[0]);
		}

		try {
			uly = Float.parseFloat(params[1]);
		}
		catch (NumberFormatException e) {
			throw new InvalidInputException("Could not parse float: " + params[1]);
		}

		try {
			lrx = Float.parseFloat(params[2]);
		}
		catch (NumberFormatException e) {
			throw new InvalidInputException("Could not parse float: " + params[2]);
		}

		try {
			lry = Float.parseFloat(params[3]);
		}
		catch (NumberFormatException e) {
			throw new InvalidInputException("Could not parse float: " + params[3]);
		}
	}

	@Override
	public int[] assemble()
	{
		int[] encoded = new int[2];

		encoded[0] = opField;
		encoded[0] |= (Math.round(lrx * CVT_FIXED_10_2) & 0xFFF) << 12;
		encoded[0] |= (Math.round(lry * CVT_FIXED_10_2) & 0xFFF);

		encoded[1] |= (Math.round(ulx * CVT_FIXED_10_2) & 0xFFF) << 12;
		encoded[1] |= (Math.round(uly * CVT_FIXED_10_2) & 0xFFF);

		return encoded;
	}

	@Override
	public String getString()
	{
		// gsDPFillRectangle(ulx, uly, lrx, lry

		return String.format("%-16s (%f, %f, %f, %f)", getName(), ulx, uly, lrx, lry);
	}
}
