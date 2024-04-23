package game.f3dex2.commands;

import app.input.InvalidInputException;
import game.DataUtils;
import game.f3dex2.BaseF3DEX2;
import game.f3dex2.DisplayList.CommandType;

/*
 	FC [aaaa] [ccccc] [eee] [ggg] [iiii] [kkkkk] [bbbb] [jjjj] [mmm] [ooo] [ddd] [fff] [hhh] [lll] [nnn] [ppp]

	a 	variable 'a' for mode 1 (color)
	b 	variable 'b' for mode 1 (color)
	c 	variable 'c' for mode 1 (color)
	d 	variable 'd' for mode 1 (color)
	e 	variable 'e' for mode 1 (alpha)
	f 	variable 'f' for mode 1 (alpha)
	g 	variable 'g' for mode 1 (alpha)
	h 	variable 'h' for mode 1 (alpha)
	i 	variable 'a' for mode 2 (color)
	j 	variable 'b' for mode 2 (color)
	k 	variable 'c' for mode 2 (color)
	l 	variable 'd' for mode 2 (color)
	m 	variable 'e' for mode 2 (alpha)
	n 	variable 'f' for mode 2 (alpha)
	o 	variable 'g' for mode 2 (alpha)
	p 	variable 'h' for mode 2 (alpha)

	F    C     aaaa cccc  czzz xxxe  eeeg gggg
	bbbb ffff  vvvt ttdd  dyyy wwwh  hhuu usss

	gsDPSetCombineLERP(a0, b0, c0, d0, Aa0, Ab0, Ac0, Ad0, a1, b1, c1, d1, Aa1, Ab1, Ac1, Ad1)

	a0	aaaa	Color 'a' value, first cycle
	c0	cccc c	Color 'c' value, first cycle
	Aa0	zzz		Alpha 'a' value, first cycle
	Ac0	xxx		Alpha 'c' value, first cycle
	a1	eee e	Color 'a' value, second cycle
	c1	g gggg	Color 'c' value, second cycle

	b0	bbbb	Color 'b' value, first cycle
	b1	ffff	Color 'b' value, second cycle
	Aa1	vvv		Alpha 'a' value, second cycle
	Ac1	t tt	Alpha 'c' value, second cycle
	d0	dd d	Color 'd' value, first cycle
	Ab0	yyy		Alpha 'b' value, first cycle
	Ad0	www		Alpha 'd' value, first cycle
	d1	h hh	Color 'd' value, second cycle
	Ab1	uu u	Alpha 'b' value, second cycle
	Ad1	sss		Alpha 'd' value, second cycle
 */
public class SetCombine extends BaseF3DEX2
{
	int a0, a1, Aa0, Aa1;
	int b0, b1, Ab0, Ab1;
	int c0, c1, Ac0, Ac1;
	int d0, d1, Ad0, Ad1;

	public SetCombine(CommandType cmd, Integer ... args) throws InvalidInputException
	{
		super(cmd, args, 2);

		a0 = (args[0] >> 20) & 0xF;
		c0 = (args[0] >> 15) & 0x1F;
		Aa0 = (args[0] >> 12) & 0x7;
		Ac0 = (args[0] >> 9) & 0x7;
		a1 = (args[0] >> 5) & 0xF;
		c1 = (args[0] >> 0) & 0x1F;

		b0 = (args[1] >> 28) & 0xF;
		b1 = (args[1] >> 24) & 0xF;
		Aa1 = (args[1] >> 21) & 0x7;
		Ac1 = (args[1] >> 18) & 0x7;
		d0 = (args[1] >> 15) & 0x7;
		Ab0 = (args[1] >> 12) & 0x7;
		Ad0 = (args[1] >> 9) & 0x7;
		d1 = (args[1] >> 6) & 0x7;
		Ab1 = (args[1] >> 3) & 0x7;
		Ad1 = args[1] & 0x7;
	}

	public SetCombine(CommandType cmd, String ... params) throws InvalidInputException
	{
		super(cmd, params, 16);

		a0 = getValue(VALUES_CA, params[0]);
		b0 = getValue(VALUES_CB, params[1]);
		c0 = getValue(VALUES_CC, params[2]);
		d0 = getValue(VALUES_CD, params[3]);

		Aa0 = getValue(VALUES_AA_AB_AD, params[4]);
		Ab0 = getValue(VALUES_AA_AB_AD, params[5]);
		Ac0 = getValue(VALUES_AC, params[6]);
		Ad0 = getValue(VALUES_AA_AB_AD, params[7]);

		a1 = getValue(VALUES_CA, params[8]);
		b1 = getValue(VALUES_CB, params[9]);
		c1 = getValue(VALUES_CC, params[10]);
		d1 = getValue(VALUES_CD, params[11]);

		Aa1 = getValue(VALUES_AA_AB_AD, params[12]);
		Ab1 = getValue(VALUES_AA_AB_AD, params[13]);
		Ac1 = getValue(VALUES_AC, params[14]);
		Ad1 = getValue(VALUES_AA_AB_AD, params[15]);
	}

	@Override
	public int[] assemble()
	{
		int[] encoded = new int[2];
		encoded[0] = opField;

		encoded[0] |= (a0 & 0xF) << 20;
		encoded[0] |= (c0 & 0x1F) << 15;
		encoded[0] |= (Aa0 & 0x7) << 12;
		encoded[0] |= (Ac0 & 0x7) << 9;
		encoded[0] |= (a1 & 0xF) << 5;
		encoded[0] |= (c1 & 0x1F) << 0;

		encoded[1] |= (b0 & 0xF) << 28;
		encoded[1] |= (b1 & 0xF) << 24;
		encoded[1] |= (Aa1 & 0x7) << 21;
		encoded[1] |= (Ac1 & 0x7) << 18;
		encoded[1] |= (d0 & 0x7) << 15;
		encoded[1] |= (Ab0 & 0x7) << 12;
		encoded[1] |= (Ad0 & 0x7) << 9;
		encoded[1] |= (d1 & 0x7) << 6;
		encoded[1] |= (Ab1 & 0x7) << 3;
		encoded[1] |= (Ad1 & 0x7);

		return encoded;
	}

	@Override
	public String getString()
	{
		//	gsDPSetCombineLERP(a0, b0, c0, d0, Aa0, Ab0, Ac0, Ad0, a1, b1, c1, d1, Aa1, Ab1, Ac1, Ad1)

		String tab = "";

		return String.format("%-16s (%s, %s, %s, %s, ...%n"
			+ "%s                  %s, %s, %s, %s, ...%n"
			+ "%s                  %s, %s, %s, %s, ...%n"
			+ "%s                  %s, %s, %s, %s)",
			getName(),
			getString(VALUES_CA, a0), getString(VALUES_CB, b0),
			getString(VALUES_CC, c0), getString(VALUES_CD, d0),
			tab,
			getString(VALUES_AA_AB_AD, Aa0), getString(VALUES_AA_AB_AD, Ab0),
			getString(VALUES_AC, Ac0), getString(VALUES_AA_AB_AD, Ad0),
			tab,
			getString(VALUES_CA, a1), getString(VALUES_CB, b1),
			getString(VALUES_CC, c1), getString(VALUES_CD, d1),
			tab,
			getString(VALUES_AA_AB_AD, Aa1), getString(VALUES_AA_AB_AD, Ab1),
			getString(VALUES_AC, Ac1), getString(VALUES_AA_AB_AD, Ad1)
		);
	}

	private static String getString(String[] vals, int index)
	{
		String s = vals[index];
		if (s != null)
			return s;
		else
			return String.format("%X", index);
	}

	private static int getValue(String[] vals, String name) throws InvalidInputException
	{
		for (int i = 0; i < vals.length; i++) {
			if (vals[i] == null)
				continue;

			if (vals[i].equalsIgnoreCase(name))
				return i;
		}

		return DataUtils.parseIntString(name);
	}

	private static final String[] VALUES_CA = {
			"G_CCMUX_COMBINED",
			"G_CCMUX_TEXEL0",
			"G_CCMUX_TEXEL1",
			"G_CCMUX_PRIMITIVE",

			"G_CCMUX_SHADE",
			"G_CCMUX_ENVIRONMENT",
			"G_CCMUX_1",
			"G_CCMUX_NOISE",

			// all null = G_CCMUX_0
			null, null, null, null,
			null, null, null,
			"G_CCMUX_0"
	};

	private static final String[] VALUES_CB = {
			"G_CCMUX_COMBINED",
			"G_CCMUX_TEXEL0",
			"G_CCMUX_TEXEL1",
			"G_CCMUX_PRIMITIVE",

			"G_CCMUX_SHADE",
			"G_CCMUX_ENVIRONMENT",
			"G_CCMUX_CENTER",
			"G_CCMUX_K4",

			// all null = G_CCMUX_0
			null, null, null, null,
			null, null, null,
			"G_CCMUX_0"
	};

	private static final String[] VALUES_CC = {
			"G_CCMUX_COMBINED",
			"G_CCMUX_TEXEL0",
			"G_CCMUX_TEXEL1",
			"G_CCMUX_PRIMITIVE",

			"G_CCMUX_SHADE",
			"G_CCMUX_ENVIRONMENT",
			"G_CCMUX_SCALE",
			"G_CCMUX_COMBINED_ALPHA",

			"G_CCMUX_TEXEL0_ALPHA",
			"G_CCMUX_TEXEL1_ALPHA",
			"G_CCMUX_PRIMITIVE_ALPHA",
			"G_CCMUX_SHADE_ALPHA",

			"G_CCMUX_ENV_ALPHA",
			"G_CCMUX_LOD_FRACTION",
			"G_CCMUX_PRIM_LOD_FRAC",
			"G_CCMUX_K5",

			// all null = G_CCMUX_0
			null, null, null, null,

			null, null, null, null,
			null, null, null, null,
			null, null, null,
			"G_CCMUX_0"
	};

	private static final String[] VALUES_CD = {
			"G_CCMUX_COMBINED",
			"G_CCMUX_TEXEL0",
			"G_CCMUX_TEXEL1",
			"G_CCMUX_PRIMITIVE",

			"G_CCMUX_SHADE",
			"G_CCMUX_ENVIRONMENT",
			"G_CCMUX_1",
			"G_CCMUX_0",
	};

	private static final String[] VALUES_AA_AB_AD = {
			"G_ACMUX_COMBINED",
			"G_ACMUX_TEXEL0",
			"G_ACMUX_TEXEL1",
			"G_ACMUX_PRIMITIVE",

			"G_ACMUX_SHADE",
			"G_ACMUX_ENVIRONMENT",
			"G_ACMUX_1",
			"G_ACMUX_0"
	};

	private static final String[] VALUES_AC = {
			"G_ACMUX_LOD_FRACTION",
			"G_ACMUX_TEXEL0",
			"G_ACMUX_TEXEL1",
			"G_ACMUX_PRIMITIVE",

			"G_ACMUX_SHADE",
			"G_ACMUX_ENVIRONMENT",
			"G_ACMUX_PRIM_LOD_FRAC",
			"G_ACMUX_0"
	};
}
