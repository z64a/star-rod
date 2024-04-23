package game.f3dex2.commands;

import app.input.InvalidInputException;
import game.DataUtils;
import game.f3dex2.BaseF3DEX2;
import game.f3dex2.DisplayList.CommandType;

/*
 	01 0[N N]0 [II] [SS SS SS SS]

	N 	Number of vertices to write
	I 	Where to start writing vertices inside the vertex buffer (start = II - N*2)
	S 	Segmented address to load vertices from
 */

public class LoadVertex extends BaseF3DEX2
{
	public int pos;
	public int num;
	public int addr;

	public LoadVertex(CommandType cmd, Integer ... args) throws InvalidInputException
	{
		super(cmd, args, 2);

		if ((args[0] & 0x00F00F00) != 0)
			throw new InvalidInputException("Invalid %s command %08X", getName(), args[0]);

		num = (args[0] >> 12) & 0xFF;
		pos = (args[0] & 0xFF);
		pos = (pos >> 1) - num;
		addr = args[1];
	}

	public LoadVertex(CommandType cmd, String ... params) throws InvalidInputException
	{
		super(cmd, params, 3);

		try {
			addr = (int) Long.parseLong(params[0], 16);
		}
		catch (NumberFormatException e) {
			throw new InvalidInputException("Could not parse address: " + params[0]);
		}

		num = DataUtils.parseIntString(params[1]);
		pos = DataUtils.parseIntString(params[2]);

		if (num <= 0 || num > 32)
			throw new InvalidInputException("Can't load %d vertices to the vertex buffer!", num);

		if (pos < 0 || pos >= 32)
			throw new InvalidInputException("%d is not a valid position in the vertex buffer!", pos);
	}

	@Override
	public int[] assemble()
	{
		int[] encoded = new int[2];
		encoded[0] = opField;

		int packedPos = (pos + num) << 1;

		encoded[0] |= (num & 0xFF) << 12;
		encoded[0] |= packedPos & 0xFF;

		encoded[1] = addr;

		return encoded;
	}

	@Override
	public String getString()
	{
		// gsSPVertex(vaddr, numv, vbidx)

		return String.format("%-16s (%08X, %d`, %X)", getName(), addr, num, pos);
	}
}
