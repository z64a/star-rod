package game.f3dex2.commands;

import app.input.InvalidInputException;
import game.DataUtils;
import game.f3dex2.BaseF3DEX2;
import game.f3dex2.DisplayList.CommandType;

/*
	03 00 vv vv 00 00 ww ww

	v 	Vertex buffer index of first vertex for bounding volume (*0x02)
	w 	Vertex buffer index of last vertex for bounding volume (*0x02)
 */
public class CullDL extends BaseF3DEX2
{
	int start, end;

	public CullDL(CommandType cmd, Integer ... args) throws InvalidInputException
	{
		super(cmd, args, 2);

		if ((args[0] & 0x00FF0000) != 0)
			throw new InvalidInputException("Invalid %s command %08X", getName(), args[0]);

		if ((args[1] & 0xFFFF0000) != 0)
			throw new InvalidInputException("Invalid %s command %08X", getName(), args[1]);

		start = (args[0] & 0xFFFF) >> 1;
		end = (args[1] & 0xFFFF) >> 1;

		if (start < 0 || start >= 32)
			throw new InvalidInputException("%s vertex is out of buffer range (0-31): %X", getName(), start);

		if (end < 0 || end >= 32)
			throw new InvalidInputException("%s vertex is out of buffer range (0-31): %X", getName(), end);

		if (end <= start)
			throw new InvalidInputException("Invalid %s command: end (%d) <= start (%d)", getName(), end, start);
	}

	public CullDL(CommandType cmd, String ... params) throws InvalidInputException
	{
		super(cmd, params, 2);

		start = DataUtils.parseIntString(params[0]);
		end = DataUtils.parseIntString(params[1]);

		if (start < 0 || start >= 32)
			throw new InvalidInputException("%s vertex is out of buffer range (0-31): %X", getName(), start);

		if (end < 0 || end >= 32)
			throw new InvalidInputException("%s vertex is out of buffer range (0-31): %X", getName(), end);

		if (end <= start)
			throw new InvalidInputException("Invalid %s command: end (%d) <= start (%d)", getName(), end, start);
	}

	@Override
	public int[] assemble()
	{
		int[] encoded = new int[2];
		encoded[0] = opField;

		encoded[0] |= (start << 1) & 0xFFFF;
		encoded[1] |= (end << 1) & 0xFFFF;

		return encoded;
	}

	@Override
	public String getString()
	{
		return String.format("%-16s (%2d`, %2d`)", getName(), start, end);
	}
}
