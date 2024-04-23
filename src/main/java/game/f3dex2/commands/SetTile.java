package game.f3dex2.commands;

import app.input.InvalidInputException;
import game.DataUtils;
import game.f3dex2.BaseF3DEX2;
import game.f3dex2.DisplayList.CommandType;
import game.texture.TileFormat;

/*
	F    5    fffi i0nn | nnnn nnnm mmmm mmmm
	0000 0ttt pppp ccaa | aass ssdd bbbb uuuu

	fff 	Sets color format
	ii 		Sets bit size of pixel
	nnnnnnnnn 	Number of 64-bit values per row
	mmmmmmmmm 	Offset of texture in TMEM

	ttt 	Tile descriptor being modified
	pppp 	Which palette to use for colors (if relevant)
	cc 		Clamp and Mirror flags for the T axis
	aaaa 	Sets how much of T axis is shown before wrapping
	ssss 	Sets the amount to shift T axis values after perspective division
	dd 		Clamp and Mirror flags for the S axis
	bbbb 	Sets how much of S axis is shown before wrapping
	uuuu 	Sets the amount to shift S axis values after perspective division

	gsDPSetTile(fmt, siz, line, tmem, tile, palette, cmT, maskT, shiftT, cmS, maskS, shiftS)
 */

public class SetTile extends BaseF3DEX2
{
	public TileFormat fmt = null;
	public int fmtType;
	public int bitDepth;

	public int descriptor;
	public int line;
	public int offset;
	public int W, H;
	public int palette;
	public int cmT, maskT, shiftT;
	public int cmS, maskS, shiftS;

	public SetTile(CommandType cmd, Integer ... args) throws InvalidInputException
	{
		super(cmd, args, 2);

		fmtType = (args[0] >> 21) & 7;
		bitDepth = (args[0] >> 19) & 3;

		// palettes are set with [000100, 07000000] -- format=0,depth=0 -- which is invalid
		try {
			fmt = TileFormat.get(fmtType, bitDepth);
		}
		catch (IllegalArgumentException e) {}

		line = (args[0] >> 9) & 0x1FF;
		offset = args[0] & 0x1FF;

		descriptor = (args[1] >> 24) & 0x7;
		palette = (args[1] >> 20) & 0xF;

		cmT = (args[1] >> 18) & 0x3;
		maskT = (args[1] >> 14) & 0xF;
		shiftT = (args[1] >> 10) & 0xF;

		cmS = (args[1] >> 8) & 0x3;
		maskS = (args[1] >> 4) & 0xF;
		shiftS = args[1] & 0xF;
	}

	public SetTile(CommandType cmd, String ... params) throws InvalidInputException
	{
		super(cmd, params, -1);

		// gsDPSetTile(fmt, line, tmem, tile, palette, cmT, maskT, shiftT, cmS, maskS, shiftS)

		if (params[0].equalsIgnoreCase("G_TX_RENDERTILE"))
			descriptor = 0;
		else if (params[0].equalsIgnoreCase("G_TX_LOADTILE"))
			descriptor = 7;
		else
			descriptor = DataUtils.parseIntString(params[0]);

		int N;
		if (params.length == 11) {
			N = 0;
			fmt = TileFormat.getFormat(params[1]);
			if (fmt == null)
				throw new InvalidInputException("%s has unknown image format: %s", getName(), params[1]);
			fmtType = fmt.type;
			bitDepth = fmt.depth;
		}
		else if (params.length == 12) {
			N = 1;
			fmtType = DataUtils.parseIntString(params[1]);
			bitDepth = DataUtils.parseIntString(params[2]);
		}
		else
			throw new InvalidInputException("%s has incorrect number of parameters: %d (expected 11 or 12)", getName(), params.length);

		line = DataUtils.parseIntString(params[N + 2]);
		offset = DataUtils.parseIntString(params[N + 3]);
		palette = DataUtils.parseIntString(params[N + 4]);

		cmT = DataUtils.parseIntString(params[N + 5]);
		maskT = DataUtils.parseIntString(params[N + 6]);
		shiftT = DataUtils.parseIntString(params[N + 7]);

		cmS = DataUtils.parseIntString(params[N + 8]);
		maskS = DataUtils.parseIntString(params[N + 9]);
		shiftS = DataUtils.parseIntString(params[N + 10]);
	}

	@Override
	public int[] assemble()
	{
		int[] encoded = new int[2];
		encoded[0] = opField;

		encoded[0] |= (fmtType & 7) << 21;
		encoded[0] |= (bitDepth & 3) << 19;
		encoded[0] |= (line & 0x1FF) << 9;
		encoded[0] |= offset & 0x1FF;

		encoded[1] |= (descriptor & 0x7) << 24;
		encoded[1] |= (palette & 0xF) << 20;
		encoded[1] |= (cmT & 0x3) << 18;
		encoded[1] |= (maskT & 0xF) << 14;
		encoded[1] |= (shiftT & 0xF) << 10;
		encoded[1] |= (cmS & 0x3) << 8;
		encoded[1] |= (maskS & 0xF) << 4;
		encoded[1] |= (shiftS & 0xF);

		return encoded;
	}

	@Override
	public String getString()
	{
		String descName;
		if (descriptor == 0)
			descName = "G_TX_RENDERTILE";
		else if (descriptor == 7)
			descName = "G_TX_LOADTILE";
		else
			descName = String.format("%X", descriptor);

		if (fmt == null) {
			// invalid format
			return String.format("%-16s (%s, %X, %X, %X, %X, %X, %X, %X, %X, %X, %X, %X) %% invalid format (%d:%d)",
				getName(), descName, fmtType, bitDepth,
				line, offset, palette,
				cmT, maskT, shiftT, cmS, maskS, shiftS,
				fmtType, bitDepth);
		}
		else {
			// valid format
			return String.format("%-16s (%s, %s, %X, %X, %X, %X, %X, %X, %X, %X, %X)",
				getName(), descName, fmt.name,
				line, offset, palette,
				cmT, maskT, shiftT, cmS, maskS, shiftS);
		}
	}
}
