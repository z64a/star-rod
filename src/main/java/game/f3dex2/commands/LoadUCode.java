package game.f3dex2.commands;

import app.input.InvalidInputException;
import game.DataUtils;
import game.f3dex2.BaseF3DEX2;
import game.f3dex2.DisplayList.CommandType;

/*
	Loads a new microcode executable into the RSP.

	E1 00 00 00 [dd dd dd dd]
	DD 00 [ss ss] [tt tt tt tt]

	d points to the start of the data section
	t to the start of the text section,
	s specifying the size of the data section
 */
public class LoadUCode extends BaseF3DEX2
{
	int dstart;
	int dsize;
	int tstart;

	public LoadUCode(CommandType cmd, Integer ... args) throws InvalidInputException
	{
		super(cmd, args, 4);

		if (args[0] != 0xE1000000)
			throw new InvalidInputException("%s expected %08X, found %08X", getName(), 0xE1000000, args[2]);

		if ((args[2] & 0xFFFF0000) != 0xDD000000)
			throw new InvalidInputException("Invalid %s command %08X", getName(), args[2]);

		dstart = args[1];
		dsize = args[2] & 0xFFFF;
		tstart = args[3];
	}

	public LoadUCode(CommandType cmd, String ... params) throws InvalidInputException
	{
		super(cmd, params, 3);

		dstart = DataUtils.parseIntString(params[0]);
		dsize = DataUtils.parseIntString(params[1]);
		tstart = DataUtils.parseIntString(params[2]);
	}

	@Override
	public int[] assemble()
	{
		int[] encoded = new int[4];

		encoded[0] = 0xE1000000;
		encoded[1] = dstart;

		encoded[2] = opField;
		encoded[2] |= dsize & 0xFFFF;

		encoded[3] = tstart;

		return encoded;
	}

	@Override
	public String getString()
	{
		// gsSPLoadUcodeEx(tstart, dstart, dsize)

		return String.format("%-16s (%X, %X, %X)", getName(), dstart, dsize, tstart);
	}
}
