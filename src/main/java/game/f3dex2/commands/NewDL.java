package game.f3dex2.commands;

import app.input.InvalidInputException;
import game.f3dex2.BaseF3DEX2;
import game.f3dex2.DisplayList.CommandType;

public class NewDL extends BaseF3DEX2
{
	public int addr;
	boolean jump;

	String[] opt = { "CALL", "JUMP" };

	public NewDL(CommandType cmd, Integer ... args) throws InvalidInputException
	{
		super(cmd, args, 2);

		if ((args[0] & 0x00FEFFFF) != 0)
			throw new InvalidInputException("Invalid %s command: %08X", getName(), args[0]);

		addr = args[1];
		jump = (((args[0] >> 16) & 0x1) != 0);
	}

	public NewDL(CommandType cmd, String ... params) throws InvalidInputException
	{
		super(cmd, params, 2);

		jump = getValue(params[0], opt);

		try {
			addr = (int) Long.parseLong(params[1], 16);
		}
		catch (NumberFormatException e) {
			throw new InvalidInputException("Could not parse address: " + params[0]);
		}
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
		encoded[0] = opField;

		if (jump)
			encoded[0] |= 0x10000;
		encoded[1] = addr;

		return encoded;
	}

	@Override
	public String getString()
	{ return String.format("%-16s (%s, %08X)", getName(), opt[jump ? 1 : 0], addr); }
}
