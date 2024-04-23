package game.f3dex2.commands;

import app.input.InvalidInputException;
import game.DataUtils;
import game.f3dex2.BaseF3DEX2;
import game.f3dex2.DisplayList.CommandType;

/*
	D7: G_TEXTURE

	Sets the texture scaling factor.

	D7 00 [xx] [nn] [ss ss] [tt tt]

	xx = 00LL Lddd
	n 	Enable/Disable Tile Descriptor (2=enable, 0=disable)
	s 	Scaling factor for S axis (horizontal)
	t 	Scaling factor for T axis (vertical)
	L 	Maximum number of mipmap levels aside from the first
	d 	Tile descriptor to enable/disable
*/

public class Texture extends BaseF3DEX2
{
	public boolean enable;
	public int descriptor;

	public float scalingS, scalingT;
	public int levels;

	public Texture(CommandType cmd, Integer ... args) throws InvalidInputException
	{
		super(cmd, args, 2);

		if ((args[0] & 0x00FFC0FD) != 0)
			throw new InvalidInputException("Invalid %s command %08X", getName(), args[0]);

		descriptor = (args[0] >> 8) & 0x7;
		enable = (args[0] & 2) != 0;
		levels = (args[0] >> 11) & 0x7;

		scalingS = ((args[1] >> 16) & 0xFFFF) / 65536.0f;
		scalingT = (args[1] & 0xFFFF) / 65536.0f;
	}

	public Texture(CommandType cmd, String ... params) throws InvalidInputException
	{
		super(cmd, params, 5);

		if (params[0].equalsIgnoreCase("G_TX_RENDERTILE"))
			descriptor = 0;
		else if (params[0].equalsIgnoreCase("G_TX_LOADTILE"))
			descriptor = 7;
		else
			descriptor = DataUtils.parseIntString(params[0]);

		try {
			scalingS = Float.parseFloat(params[1]);
		}
		catch (NumberFormatException e) {
			throw new InvalidInputException("Could not parse float: " + params[1]);
		}

		try {
			scalingT = Float.parseFloat(params[2]);
		}
		catch (NumberFormatException e) {
			throw new InvalidInputException("Could not parse float: " + params[2]);
		}

		levels = DataUtils.parseIntString(params[3]);
		enable = Boolean.parseBoolean(params[4]);
	}

	@Override
	public int[] assemble()
	{
		int[] encoded = new int[2];
		encoded[0] = opField;

		encoded[0] |= (descriptor & 0x7) << 8;
		encoded[0] |= (levels & 0x7) << 11;
		if (enable)
			encoded[0] |= 2;

		encoded[1] |= (Math.round(scalingS * 65536.0f) & 0xFFFF) << 16;
		encoded[1] |= Math.round(scalingT * 65536.0f) & 0xFFFF;

		return encoded;
	}

	@Override
	public String getString()
	{
		// gsSPTexture(scaleS, scaleT, level, tile, on)

		String descName;
		if (descriptor == 0)
			descName = "G_TX_RENDERTILE";
		else if (descriptor == 7)
			descName = "G_TX_LOADTILE";
		else
			descName = String.format("%X", descriptor);

		return String.format("%-16s (%s, %f, %f, %X, %b)", getName(), descName, scalingS, scalingT, levels, enable);
	}
}
