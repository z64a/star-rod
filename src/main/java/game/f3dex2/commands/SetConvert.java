package game.f3dex2.commands;

import app.input.InvalidInputException;
import game.DataUtils;
import game.f3dex2.BaseF3DEX2;
import game.f3dex2.DisplayList.CommandType;

//  E    C     00aa aaaa  aaab bbbb  bbbb cccc
//  cccc cddd  dddd ddee  eeee eeef  ffff ffff

public class SetConvert extends BaseF3DEX2
{
	int A, B, C, D, E, F;

	public SetConvert(CommandType cmd, Integer ... args) throws InvalidInputException
	{
		super(cmd, args, 2);

		if ((args[0] & 0x00C00000) != 0)
			throw new InvalidInputException("Invalid %s command: %08X", getName(), args[0]);

		A = signExtend9((args[0] >> 13) & 0x1FF);
		B = signExtend9((args[0] >> 4) & 0x1FF);
		C = signExtend9(args[0] & 0xF | (args[1] >>> 27));
		D = signExtend9((args[1] >> 18) & 0x1FF);
		E = signExtend9((args[1] >> 9) & 0x1FF);
		F = signExtend9((args[1] >> 0) & 0x1FF);
	}

	private static int signExtend9(int v)
	{
		return (v << 23) >> 23;
	}

	public SetConvert(CommandType cmd, String ... params) throws InvalidInputException
	{
		super(cmd, params, 6);

		A = DataUtils.parseIntString(params[0]);
		B = DataUtils.parseIntString(params[1]);
		C = DataUtils.parseIntString(params[2]);
		D = DataUtils.parseIntString(params[3]);
		E = DataUtils.parseIntString(params[4]);
		F = DataUtils.parseIntString(params[5]);
	}

	@Override
	public int[] assemble()
	{
		int[] encoded = new int[2];
		encoded[0] = opField;

		int cc = C & 0x1FF;

		encoded[0] |= (A & 0x1FF) << 13;
		encoded[0] |= (B & 0x1FF) << 4;
		encoded[0] |= cc >> 5;

		encoded[1] = cc << 27;
		encoded[1] |= (D & 0x1FF) << 18;
		encoded[1] |= (E & 0x1FF) << 9;
		encoded[1] |= (F & 0x1FF);

		return encoded;
	}

	@Override
	public String getString()
	{
		return String.format("%-16s (%d`, %d`, %d`, %d`, %d`, %d`)", getName(), A, B, C, D, E, F);
	}
}
