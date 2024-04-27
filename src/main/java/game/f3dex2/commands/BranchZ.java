package game.f3dex2.commands;

import app.input.InvalidInputException;
import game.DataUtils;
import game.f3dex2.BaseF3DEX2;
import game.f3dex2.DisplayList.CommandType;

/*
	Tests the Z value of the vertex at a buffer index against a given z-value.

	E1 00 00 00 [dd dd dd dd]
	04 [aa a][b bb] [zz zz zz zz]

	d 	Address of display list to branch to
	a 	Vertex buffer index of vertex to test(*5)
	b 	Vertex buffer index of vertex to test(*2)
	z 	Z value to test against
 */
public class BranchZ extends BaseF3DEX2
{
	int addr;
	int vidx;
	int zval;

	public BranchZ(CommandType cmd, Integer ... args) throws InvalidInputException
	{
		super(cmd, args, 4);

		if (args[2] != 0xE1000000)
			throw new InvalidInputException("%s expected %08X, found %08X", getName(), 0xE1000000, args[2]);

		vidx = (args[0] & 0xFFF) >> 1;
		zval = args[1];
		addr = args[3];

		int vidx5 = (args[0] >> 12) & 0xFFF;
		if (vidx5 != 5 * vidx)
			throw new InvalidInputException("%s has inconsistent vertex buffer indices: %X vs %X", getName(), vidx, vidx5 / 5);
	}

	public BranchZ(CommandType cmd, String ... params) throws InvalidInputException
	{
		super(cmd, params, 3);

		addr = DataUtils.parseIntString(params[0]);
		vidx = DataUtils.parseIntString(params[1]);
		zval = DataUtils.parseIntString(params[2]);
	}

	@Override
	public int[] assemble()
	{
		int[] encoded = new int[4];
		encoded[0] = 0xE1000000;
		encoded[1] = addr;

		encoded[2] = opField;
		encoded[2] |= (2 * vidx) & 0xFFF;
		encoded[2] |= ((5 * vidx) & 0xFFF) << 12;
		encoded[3] = zval;

		return encoded;
	}

	@Override
	public String getString()
	{
		return String.format("%-16s (%08X, %d`, %08X)", getName(), addr, vidx, zval);
	}
}
