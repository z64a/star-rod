package game.f3dex2.commands;

import app.input.InvalidInputException;
import game.DataUtils;
import game.f3dex2.BaseF3DEX2;
import game.f3dex2.DisplayList.CommandType;
import game.texture.TileFormat;

public class SetImg extends BaseF3DEX2
{
	public TileFormat fmt = null;

	public int bitDepth;
	public int fmtType;
	public int addr;
	public int width;

	public SetImg(CommandType cmd, Integer ... args) throws InvalidInputException
	{
		super(cmd, args, 2);

		if ((args[0] & 0x0007F000) != 0)
			throw new InvalidInputException("Invalid %s command: %08X", getName(), args[0]);

		width = (args[0] & 0xFFF) + 1;
		addr = args[1];

		bitDepth = (args[0] >> 19) & 3;
		fmtType = (args[0] >> 21) & 7;

		try {
			fmt = TileFormat.get(fmtType, bitDepth);
		}
		catch (IllegalArgumentException e) {}
	}

	public SetImg(CommandType cmd, String ... params) throws InvalidInputException
	{
		super(cmd, params, -1);

		int N;
		if (params.length == 3) {
			N = 0;
			fmt = TileFormat.getFormat(params[0]);
			if (fmt == null)
				throw new InvalidInputException("%s has unknown image format: %s", getName(), params[0]);
			fmtType = fmt.type;
			bitDepth = fmt.depth;
		}
		else if (params.length == 4) {
			N = 1;
			fmtType = DataUtils.parseIntString(params[0]);
			bitDepth = DataUtils.parseIntString(params[1]);
		}
		else
			throw new InvalidInputException("%s has incorrect number of parameters: %d (expected 3 or 4)", getName(), params.length);

		width = DataUtils.parseIntString(params[N + 1]);

		try {
			addr = (int) Long.parseLong(params[N + 2], 16);
		}
		catch (NumberFormatException e) {
			throw new InvalidInputException("Could not parse address: " + params[N + 2]);
		}
	}

	@Override
	public int[] assemble()
	{
		int[] encoded = new int[2];
		encoded[0] = opField;

		encoded[0] |= (width - 1) & 0xFFF;
		encoded[0] |= (bitDepth & 3) << 19;
		encoded[0] |= (fmtType & 7) << 21;
		encoded[1] = addr;

		return encoded;
	}

	@Override
	public String getString()
	{
		String addrName = String.format("%08X", addr);

		if (fmt == null)
			return String.format("%-16s (%X, %X, %d`, %s) %% invalid format (%d:%d)",
				getName(), fmtType, bitDepth, width, addrName, fmtType, bitDepth);
		else
			return String.format("%-16s (%s, %d`, %s)",
				getName(), fmt.name, width, addrName);
	}
}
