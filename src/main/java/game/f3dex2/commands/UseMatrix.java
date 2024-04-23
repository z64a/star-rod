package game.f3dex2.commands;

import app.input.InvalidInputException;
import game.f3dex2.BaseF3DEX2;
import game.f3dex2.DisplayList.CommandType;

public class UseMatrix extends BaseF3DEX2
{
	public int addr;

	boolean push;
	boolean load;
	boolean proj;

	String[] optPush = { "NO_PUSH", "PUSH" };
	String[] optLoad = { "MULTIPLY", "LOAD" };
	String[] optProj = { "MODELVIEW", "PROJECTION" };

	/*
	0x04 	projection (default: model view)
	0x02 	load (default: multiply)
	0x01 	push (default: no push)
	 */

	public UseMatrix(CommandType cmd, Integer ... args) throws InvalidInputException
	{
		super(cmd, args, 2);

		if ((args[0] & 0xFFFFFF00) != 0xDA380000)
			throw new InvalidInputException("Invalid use matrix command: %08X", args[0]);

		addr = args[1];

		int modes = args[1] & 0xFF;
		push = ((modes & 1) != 0);
		load = ((modes & 2) != 0);
		proj = ((modes & 4) != 0);
	}

	public UseMatrix(CommandType cmd, String ... params) throws InvalidInputException
	{
		super(cmd, params, 4);

		try {
			addr = (int) Long.parseLong(params[0], 16);
		}
		catch (NumberFormatException e) {
			throw new InvalidInputException("Could not parse address: " + params[0]);
		}

		push = getValue(params[1], optPush);
		load = getValue(params[2], optLoad);
		proj = getValue(params[3], optProj);
	}

	private boolean getValue(String param, String[] options) throws InvalidInputException
	{
		if (param.equalsIgnoreCase(options[0]))
			return false;

		if (param.equalsIgnoreCase(options[1]))
			return true;

		throw new InvalidInputException("Invalid param: %s (expected %s or %s)" + param, options[0], options[1]);
	}

	@Override
	public int[] assemble()
	{
		int[] encoded = new int[2];
		encoded[0] = opField | 0x00380000;

		if (push)
			encoded[0] |= 1;
		if (load)
			encoded[0] |= 2;
		if (proj)
			encoded[0] |= 4;
		encoded[1] = addr;

		return encoded;
	}

	@Override
	public String getString()
	{ return String.format("%-16s (%08X, %s, %s, %s)", getName(), addr,
		optPush[push ? 1 : 0], optLoad[load ? 1 : 0], optProj[proj ? 1 : 0]); }
}
