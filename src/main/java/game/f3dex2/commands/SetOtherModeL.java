package game.f3dex2.commands;

import app.input.InvalidInputException;
import game.DataUtils;
import game.f3dex2.BaseF3DEX2;
import game.f3dex2.DisplayList.CommandType;
import util.Logger;

/*
	Modifies various bits of the lower half of the RDP Other Modes word.

	E3 00 [ss] [nn] [dd dd dd dd]
	s 	32 - (Amount mode-bits are shifted by) - (Number of bits affected)
	n 	(Number of mode-bits affected) - 1
	d 	Mode-bits
 */

/*
	Parameter 	PPPP AAAA MMMM BBBB
	Cycle No. 	1122 1122 1122 1122

	The P and M values can be any of the following values:

	00: G_BL_CLR_IN: In the first cycle, parameter is color from input pixel. In the second cycle, parameter is the numerator of the formula as computed for the first cycle.
	01: G_BL_CLR_MEM: Takes color from the framebuffer
	10: G_BL_CLR_BL: Takes color from the blend color register
	11: G_BL_CLR_FOG: Takes color from the fog color register

	The A parameter can be set to any of these things:

	00: G_BL_A_IN: Parameter is alpha value of input pixel
	01: G_BL_A_FOG: Alpha value from the fog color register
	10: G_BL_A_SHADE: Calculated alpha value for the pixel, presumably
	11: G_BL_0: Constant 0.0 value

	And the B parameter can be set to any of these:

	00: G_BL_1MA: 1.0 - source alpha
	01: G_BL_A_MEM: Framebuffer alpha value
	10: G_BL_1: Constant 1.0 value
	11: G_BL_0: Constant 0.0 value
 */

public class SetOtherModeL extends BaseF3DEX2
{
	public transient OtherModeOption opt = null;
	public int shift;
	public int length;
	public int value;

	public SetOtherModeL(CommandType cmd, Integer ... args) throws InvalidInputException
	{
		super(cmd, args, 2);

		if ((args[0] & 0x00FF0000) != 0)
			throw new InvalidInputException("Invalid %s command: %08X", getName(), args[0]);

		length = (args[0] & 0xFF) + 1;
		int s = (args[0] >> 8) & 0xFF;
		shift = 32 - length - s;

		value = args[1] >> shift;

		for (OtherModeOption o : OtherModeOption.values()) {
			if (o.shift == shift && o.length == length) {
				opt = o;
				break;
			}
		}
	}

	public SetOtherModeL(CommandType cmd, String ... params) throws InvalidInputException
	{
		super(cmd, params, -1);

		if (params.length < 2)
			throw new InvalidInputException("%s has invalid format!", getName());

		for (OtherModeOption o : OtherModeOption.values()) {
			if (o.name().equalsIgnoreCase(params[0])) {
				opt = o;
				break;
			}
		}

		if (opt == null) {
			if (params.length != 3)
				throw new InvalidInputException("%s has incorrect number of parameters: %d (expected 3)", getName(), params.length);
			shift = DataUtils.parseIntString(params[0]);
			length = DataUtils.parseIntString(params[1]);
			value = DataUtils.parseIntString(params[2]);
			return;
		}

		shift = opt.shift;
		length = opt.length;

		switch (opt) {
			case G_MDSFT_ALPHACOMPARE:
			case G_MDSFT_ZSRCSEL:
				if (params.length != 2)
					throw new InvalidInputException("%s has incorrect number of parameters: %d (expected 2)", getName(), params.length);
				value = DataUtils.parseIntString(params[1]);
				break;

			case G_MDSFT_RENDERMODE:
				if (params.length < 9)
					throw new InvalidInputException("%s has incorrect number of parameters: %d (expected at least 9)", getName(), params.length);
				value = getRenderModeValue(params);
				break;

			default:
				throw new IllegalStateException("Invalid OtherModeL: " + opt);
		}
	}

	@Override
	public int[] assemble()
	{
		int[] encoded = new int[2];
		encoded[0] = opField;

		encoded[0] |= (length - 1) & 0xFF;
		int s = 32 - shift - length;
		encoded[0] |= (s & 0xFF) << 8;

		encoded[1] = value << shift;

		return encoded;
	}

	@Override
	public String getString()
	{
		for (OtherModeOption o : OtherModeOption.values()) {
			if (o.shift == shift && o.length == length) {
				opt = o;
				break;
			}
		}

		if (opt == null) {
			Logger.logfWarning("Could not match other mode option for shift: %X, length: %X", shift, length);
			return String.format("%-16s (%X, %X, %X)", getName(), shift, length, value);
		}

		switch (opt) {
			case G_MDSFT_ALPHACOMPARE:
			case G_MDSFT_ZSRCSEL:
				return String.format("%-16s (%s, %X)", getName(), opt.name(), value);

			case G_MDSFT_RENDERMODE:
				return getRenderModeString();

			default:
				throw new IllegalStateException("Invalid OtherModeL: " + opt);
		}
	}

	private String getRenderModeString()
	{
		int cycInd = value & 0x1FFF;
		int cycDep = (value >> 13) & 0xFFFF;

		StringBuilder sb = new StringBuilder();
		if ((cycInd & 1) != 0)
			sb.append("AA_EN, ");
		if ((cycInd & 2) != 0)
			sb.append("Z_CMP, ");
		if ((cycInd & 4) != 0)
			sb.append("Z_UPD, ");
		if ((cycInd & 8) != 0)
			sb.append("IM_RD, ");

		int CVG_DST = (cycInd >> 5) & 3;
		sb.append(namesCvgDst[CVG_DST] + ", ");

		if ((cycInd & 0x10) != 0)
			sb.append("CLR_ON_CVG, ");
		if ((cycInd & 0x200) != 0)
			sb.append("CVG_X_ALPHA, ");
		if ((cycInd & 0x400) != 0)
			sb.append("ALPHA_CVG_SEL, ");
		if ((cycInd & 0x800) != 0)
			sb.append("FORCE_BL, ");

		int ZMODE = (cycInd >> 7) & 3;
		sb.append(namesZmode[ZMODE] + ", ");

		// (P * A + M - B) / (A + B)
		//	Parameter 	PPPP AAAA MMMM BBBB
		//	Cycle No. 	1122 1122 1122 1122

		int[] P = new int[2];
		int[] M = new int[2];
		int[] A = new int[2];
		int[] B = new int[2];

		P[0] = (cycDep >> 14) & 3;
		P[1] = (cycDep >> 12) & 3;

		A[0] = (cycDep >> 10) & 3;
		A[1] = (cycDep >> 8) & 3;

		M[0] = (cycDep >> 6) & 3;
		M[1] = (cycDep >> 4) & 3;

		B[0] = (cycDep >> 2) & 3;
		B[1] = (cycDep >> 0) & 3;

		String tab = "";

		return String.format("%-12s (%s, ...%n"
			+ "%s                  %s...%n"
			+ "%s                  %s, %s, %s, %s, ...%n"
			+ "%s                  %s, %s, %s, %s)",
			getName(),
			OtherModeOption.G_MDSFT_RENDERMODE.name(),
			tab, sb.toString(),
			tab, namesPM[P[0]], namesA[A[0]], namesPM[M[0]], namesB[B[0]],
			tab, namesPM[P[1]], namesA[A[1]], namesPM[M[1]], namesB[B[1]]);
	}

	private int getRenderModeValue(String[] params) throws InvalidInputException
	{
		if (params.length < 9)
			throw new IllegalStateException("Invalid " + opt + " for SetOtherModeL.");

		int[] P = new int[2];
		int[] M = new int[2];
		int[] A = new int[2];
		int[] B = new int[2];
		int cycleStart = params.length - 8;
		P[0] = getValue(namesPM, params[cycleStart + 0]);
		A[0] = getValue(namesA, params[cycleStart + 1]);
		M[0] = getValue(namesPM, params[cycleStart + 2]);
		B[0] = getValue(namesB, params[cycleStart + 3]);
		P[1] = getValue(namesPM, params[cycleStart + 4]);
		A[1] = getValue(namesA, params[cycleStart + 5]);
		M[1] = getValue(namesPM, params[cycleStart + 6]);
		B[1] = getValue(namesB, params[cycleStart + 7]);

		int cycDep = ((P[0] & 3) << 14) | ((P[1] & 3) << 12) |
			((A[0] & 3) << 10) | ((A[1] & 3) << 8) |
			((M[0] & 3) << 6) | ((M[1] & 3) << 4) |
			((B[0] & 3) << 2) | ((B[1] & 3) << 0);

		int cycInd = 0;
		for (int i = 1; i < cycleStart; i++) {
			String arg = params[i].toUpperCase();
			if (arg.startsWith("CVG_DST")) {
				if (arg.equals(namesCvgDst[0]))
					; // nothing
				else if (arg.equals(namesCvgDst[1]))
					cycInd |= 0x20;
				else if (arg.equals(namesCvgDst[2]))
					cycInd |= 0x40;
				else if (arg.equals(namesCvgDst[3]))
					cycInd |= 0x60;
				else
					throw new IllegalStateException("Invalid param for " + opt + ": " + params[i]);
			}
			else if (arg.startsWith("ZMODE_")) {
				if (arg.equals(namesZmode[0]))
					; // nothing
				else if (arg.equals(namesZmode[1]))
					cycInd |= 0x80;
				else if (arg.equals(namesZmode[2]))
					cycInd |= 0x100;
				else if (arg.equals(namesZmode[3]))
					cycInd |= 0x180;
				else
					throw new IllegalStateException("Invalid param for " + opt + ": " + params[i]);
			}
			else if (arg.equals("AA_EN"))
				cycInd |= 1;
			else if (arg.equals("Z_CMP"))
				cycInd |= 2;
			else if (arg.equals("Z_UPD"))
				cycInd |= 4;
			else if (arg.equals("IM_RD"))
				cycInd |= 8;
			else if (arg.equals("CLR_ON_CVG"))
				cycInd |= 0x10;
			else if (arg.equals("CVG_X_ALPHA"))
				cycInd |= 0x200;
			else if (arg.equals("ALPHA_CVG_SEL"))
				cycInd |= 0x400;
			else if (arg.equals("FORCE_BL"))
				cycInd |= 0x800;
			else if (arg.equals("TEX_EDGE"))
				; // nothing, was 0x8000 in previous microcode
			else
				throw new IllegalStateException("Invalid param for " + opt + ": " + params[i]);
		}

		return ((cycDep & 0xFFFF) << 13) | (cycInd & 0x1FFF);
	}

	private static int getValue(String[] vals, String name) throws InvalidInputException
	{
		for (int i = 0; i < vals.length; i++) {
			if (vals[i].equalsIgnoreCase(name))
				return i;
		}

		return DataUtils.parseIntString(name);
	}

	public static enum OtherModeOption
	{
		// @formatter:off
		G_MDSFT_ALPHACOMPARE	(0, 2),
		G_MDSFT_ZSRCSEL			(2, 1),
		G_MDSFT_RENDERMODE		(3, 29);
		// @formatter:on

		private final int shift;
		private final int length;

		private OtherModeOption(int shift, int length)
		{
			this.shift = shift;
			this.length = length;
		}
	}

	private static String[] namesPM = {
			"G_BL_CLR_IN",
			"G_BL_CLR_MEM",
			"G_BL_CLR_BL",
			"G_BL_CLR_FOG"
	};

	private static String[] namesA = {
			"G_BL_A_IN",
			"G_BL_A_FOG",
			"G_BL_A_SHADE",
			"G_BL_0"
	};

	private static String[] namesB = {
			"G_BL_1MA",
			"G_BL_A_MEM",
			"G_BL_1",
			"G_BL_0"
	};

	private static String[] namesCvgDst = {
			"CVG_DST_CLAMP",
			"CVG_DST_WRAP",
			"CVG_DST_FULL",
			"CVG_DST_SAVE"
	};

	private static String[] namesZmode = {
			"ZMODE_OPA",
			"ZMODE_INTER",
			"ZMODE_XLU",
			"ZMODE_DEC"
	};
}
