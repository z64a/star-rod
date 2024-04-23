package game.f3dex2.commands;

import app.input.InvalidInputException;
import game.DataUtils;
import game.f3dex2.BaseF3DEX2;
import game.f3dex2.DisplayList.CommandType;

/*
	Takes a block of memory from an address and puts it in the location pointed to by an index and an offset.

	DC [nn] [oo] [ii] [aa aa aa aa]

	n 	(((Size in bytes of memory to be moved) » 3)+1)*8
	o 	Offset from indexed base address (*8)
	i 	Index into table of DMEM addresses
	a 	Segmented address of memory
 */
public class MoveMem extends BaseF3DEX2
{
	public static String[] optLocations = {
			"G_MV_MMTX",
			"G_MV_PMTX",
			"G_MV_VIEWPORT",
			"G_MV_LIGHT",
			"G_MV_POINT",
			"G_MV_MATRIX"
	};

	public static int[] valLocations = {
			2, 6, 8, 10, 12, 14
	};

	public int size;
	public int offset;
	public int location;
	public int addr;

	public MoveMem(CommandType cmd, Integer ... args) throws InvalidInputException
	{
		super(cmd, args, 2);

		int n = (args[0] >> 16) & 0xFF;
		size = ((n >> 3) + 1) * 8;

		offset = ((args[0] >> 8) & 0xFF) * 8;
		location = args[0] & 0xFF;

		addr = args[1];
	}

	public MoveMem(CommandType cmd, String ... params) throws InvalidInputException
	{
		super(cmd, params, 4);

		location = -1;
		for (int i = 0; i < optLocations.length; i++) {
			if (optLocations[i].equalsIgnoreCase(params[0]))
				location = valLocations[i];
		}
		if (location == -1)
			location = DataUtils.parseIntString(params[0]);

		offset = DataUtils.parseIntString(params[1]);

		size = DataUtils.parseIntString(params[2]);

		try {
			addr = (int) Long.parseLong(params[3], 16);
		}
		catch (NumberFormatException e) {
			throw new InvalidInputException("Could not parse address: " + params[3]);
		}
	}

	@Override
	public int[] assemble()
	{
		int[] encoded = new int[2];
		encoded[0] = opField;

		int n = ((size - 1) / 8 & 0x1F) << 3;
		encoded[0] |= (n & 0xFF) << 16;
		encoded[0] |= ((offset / 8) & 0xFF) << 8;
		encoded[0] |= (location & 0xFF);

		encoded[1] = addr;

		return encoded;
	}

	@Override
	public String getString()
	{
		// gsMoveMem(index, offset, size, address)

		String addrName = String.format("%08X", addr);

		String locationName = null;
		for (int i = 0; i < valLocations.length; i++) {
			if (location == valLocations[i])
				locationName = optLocations[i];
		}
		if (locationName == null)
			locationName = String.format("%X", location);

		return String.format("%-16s (%s, %X, %X, %s)", getName(), locationName, offset, size, addrName);
	}
}
