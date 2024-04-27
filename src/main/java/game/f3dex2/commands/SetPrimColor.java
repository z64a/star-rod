package game.f3dex2.commands;

import app.input.InvalidInputException;
import game.DataUtils;
import game.f3dex2.BaseF3DEX2;
import game.f3dex2.DisplayList.CommandType;

/*
	FA 00 [mm] [ff] [RR] [GG] [BB] [AA]
	m 	Minimum possible LOD value (clamped to this at minimum)
	f 	Primitive LOD fraction for mipmap filtering
	R 	red value
	G 	green value
	B 	blue value
	A 	alpha value
*/

public class SetPrimColor extends BaseF3DEX2
{
	int R, G, B, A;
	float minLOD; // unsigned 0.8 fixed
	float fracLOD; // unsigned 0.8 fixed

	public SetPrimColor(CommandType cmd, Integer ... args) throws InvalidInputException
	{
		super(cmd, args, 2);

		this.minLOD = ((args[0] >> 8) & 0xFF) / 256.0f;
		this.fracLOD = ((args[0] >> 0) & 0xFF) / 256.0f;

		this.R = (args[1] >> 24) & 0xFF;
		this.G = (args[1] >> 16) & 0xFF;
		this.B = (args[1] >> 8) & 0xFF;
		this.A = (args[1] >> 0) & 0xFF;
	}

	public SetPrimColor(CommandType cmd, String ... params) throws InvalidInputException
	{
		super(cmd, params, 6);

		try {
			minLOD = Float.parseFloat(params[0]);
		}
		catch (NumberFormatException e) {
			throw new InvalidInputException("Could not parse floating point value for LOD minimum: " + params[0]);
		}

		if (minLOD < 0.0f || minLOD > 0.99609375f)
			throw new InvalidInputException("LOD minimum must be between 0.0 and 0.99609375, read: " + params[0]);

		try {
			fracLOD = Float.parseFloat(params[1]);
		}
		catch (NumberFormatException e) {
			throw new InvalidInputException("Could not parse floating point value for LOD fraction: " + params[1]);
		}

		if (fracLOD < 0.0f || fracLOD > 0.99609375f)
			throw new InvalidInputException("LOD fraction must be between 0.0 and 0.99609375, read: " + params[1]);

		R = DataUtils.parseIntString(params[2]);
		G = DataUtils.parseIntString(params[3]);
		B = DataUtils.parseIntString(params[4]);
		A = DataUtils.parseIntString(params[5]);
	}

	@Override
	public int[] assemble()
	{
		int[] encoded = new int[2];
		encoded[0] = opField;

		encoded[0] |= (Math.round(minLOD * 256.0f) & 0xFF) << 8;
		encoded[0] |= (Math.round(fracLOD * 256.0f) & 0xFF) << 0;

		encoded[1] |= (R & 0xFF) << 24;
		encoded[1] |= (G & 0xFF) << 16;
		encoded[1] |= (B & 0xFF) << 8;
		encoded[1] |= (A & 0xFF) << 0;

		return encoded;
	}

	@Override
	public String getString()
	{
		return String.format("%-16s (%f, %f, %d`, %d`, %d`, %d`)", getName(), minLOD, fracLOD, R, G, B, A);
	}
}
