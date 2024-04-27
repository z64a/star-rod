package game.f3dex2.commands;

import app.input.InvalidInputException;
import game.DataUtils;
import game.f3dex2.BaseF3DEX2;
import game.f3dex2.DisplayList.CommandType;

public class PopMatrix extends BaseF3DEX2
{
	public int num;

	public PopMatrix(CommandType cmd, Integer ... args) throws InvalidInputException
	{
		super(cmd, args, 2);

		if (args[0] != 0xD8380002)
			throw new InvalidInputException("Invalid pop matrix command %08X", args[0]);

		num = args[1] / 64;
	}

	public PopMatrix(CommandType cmd, String ... params) throws InvalidInputException
	{
		super(cmd, params, 1);

		num = DataUtils.parseIntString(params[0]);
	}

	@Override
	public int[] assemble()
	{
		int[] encoded = new int[2];
		encoded[0] = opField | 0x00380002;
		encoded[1] = num * 64;
		return encoded;
	}

	@Override
	public String getString()
	{
		return String.format("%-16s (%d`)", getName(), num);
	}
}
