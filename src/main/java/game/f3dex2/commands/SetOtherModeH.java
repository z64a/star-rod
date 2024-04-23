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
public class SetOtherModeH extends BaseF3DEX2
{
	public int shift;
	public int length;
	public int value;

	public SetOtherModeH(CommandType cmd, Integer ... args) throws InvalidInputException
	{
		super(cmd, args, 2);

		if ((args[0] & 0x00FF0000) != 0)
			throw new InvalidInputException("Invalid %s command: %08X", getName(), args[0]);

		length = (args[0] & 0xFF) + 1;
		int s = (args[0] >> 8) & 0xFF;
		shift = 32 - length - s;

		value = args[1] >> shift;
	}

	public SetOtherModeH(CommandType cmd, String ... params) throws InvalidInputException
	{
		super(cmd, params, -1);

		if (params.length != 2 && params.length != 3)
			throw new InvalidInputException("%s has incorrect number of parameters: %d (expected 2 or 3)", getName(), params.length);

		if (params.length == 3) {
			shift = DataUtils.parseIntString(params[0]);
			length = DataUtils.parseIntString(params[1]);
			value = DataUtils.parseIntString(params[2]);
			return;
		}

		OtherModeOption opt = null;
		for (OtherModeOption o : OtherModeOption.values()) {
			if (o.name().equalsIgnoreCase(params[0])) {
				opt = o;
				break;
			}
		}

		if (opt == null)
			throw new InvalidInputException("%s has invalid option: %s", getName(), params[0]);

		shift = opt.shift;
		length = opt.length;

		OtherModeValue val = null;
		for (OtherModeValue v : opt.values) {
			if (v.name.equalsIgnoreCase(params[1])) {
				val = v;
				break;
			}
		}

		if (val != null)
			value = val.value;
		else
			value = DataUtils.parseIntString(params[1]);
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
		OtherModeOption opt = null;
		OtherModeValue val = null;

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

		for (OtherModeValue v : opt.values) {
			if (v.value == value)
				val = v;
		}

		if (val == null)
			return String.format("%-16s (%s, %X)", getName(), opt.name(), value);

		return String.format("%-16s (%s, %s)", getName(), opt.name(), val.name);
	}

	private static enum OtherModeOption
	{
		// @formatter:off
		G_MDSFT_ALPHADITHER		( 4, 2, valsAlphaDither),
		G_MDSFT_RGBDITHER		( 6, 2, valsColorDither),
		G_MDSFT_COMBKEY			( 8, 1, new OtherModeValue[] {}),
		G_MDSFT_TEXTCONV		( 9, 3, valsTextConv),
		G_MDSFT_TEXTFILT 		(12, 2, valsTextFilt),
		G_MDSFT_TEXTLUT 		(14, 2, valsTextLUT),
		G_MDSFT_TEXTLOD 		(16, 1, valsTextLOD),
		G_MDSFT_TEXTDETAIL 		(17, 2, valsTextDetail),
		G_MDSFT_TEXTPERSP 		(19, 1, new OtherModeValue[] {}),
		G_MDSFT_CYCLETYPE 		(20, 2, valsCycleType),
		//G_MDSFT_COLORDITHER 	(22, 1, null),
		G_MDSFT_PIPELINE 		(23, 1, valsPipeline);
		// @formatter:on

		private final int shift;
		private final int length;
		private final OtherModeValue[] values;

		private OtherModeOption(int shift, int length)
		{
			this(shift, length, new OtherModeValue[0]);
		}

		private OtherModeOption(int shift, int length, OtherModeValue[] values)
		{
			this.shift = shift;
			this.length = length;
			this.values = values;
		}
	}

	private static class OtherModeValue
	{
		private final String name;
		private final int value;

		private OtherModeValue(String name, int value)
		{
			this.name = name;
			this.value = value;
		}
	}

	private static final OtherModeValue[] valsAlphaDither = new OtherModeValue[4];
	static {
		valsAlphaDither[0] = new OtherModeValue("G_AD_PATTERN", 0);
		valsAlphaDither[1] = new OtherModeValue("G_AD_NOTPATTERN", 1);
		valsAlphaDither[2] = new OtherModeValue("G_AD_NOISE", 2);
		valsAlphaDither[3] = new OtherModeValue("G_AD_DISABLE", 3);
	}

	private static final OtherModeValue[] valsColorDither = new OtherModeValue[4];
	static {
		valsColorDither[0] = new OtherModeValue("G_CD_MAGICSQ", 0);
		valsColorDither[1] = new OtherModeValue("G_CD_BAYER", 1);
		valsColorDither[2] = new OtherModeValue("G_CD_NOISE", 2);
		valsColorDither[3] = new OtherModeValue("G_CD_DISABLE", 3);
	}

	private static final OtherModeValue[] valsTextConv = new OtherModeValue[3];
	static {
		valsTextConv[0] = new OtherModeValue("G_TC_CONV", 0);
		valsTextConv[1] = new OtherModeValue("G_TC_FILTCONV", 5);
		valsTextConv[2] = new OtherModeValue("G_TC_FILT", 6);
	}

	private static final OtherModeValue[] valsTextFilt = new OtherModeValue[3];
	static {
		valsTextFilt[0] = new OtherModeValue("G_TF_POINT", 0);
		valsTextFilt[1] = new OtherModeValue("G_TF_BILERP", 2);
		valsTextFilt[2] = new OtherModeValue("G_TF_AVERAGE", 3);
	}

	private static final OtherModeValue[] valsTextLUT = new OtherModeValue[3];
	static {
		valsTextLUT[0] = new OtherModeValue("G_TT_NONE", 0);
		valsTextLUT[1] = new OtherModeValue("G_TT_RGBA16", 2);
		valsTextLUT[2] = new OtherModeValue("G_TT_IA16", 3);
	}

	private static final OtherModeValue[] valsTextLOD = new OtherModeValue[2];
	static {
		valsTextLOD[0] = new OtherModeValue("G_TL_TILE", 0);
		valsTextLOD[1] = new OtherModeValue("G_TL_LOD", 1);
	}

	private static final OtherModeValue[] valsTextDetail = new OtherModeValue[3];
	static {
		valsTextDetail[0] = new OtherModeValue("G_TD_CLAMP", 0);
		valsTextDetail[1] = new OtherModeValue("G_TD_SHARPEN", 1);
		valsTextDetail[2] = new OtherModeValue("G_TD_DETAIL", 2);
	}

	private static final OtherModeValue[] valsCycleType = new OtherModeValue[4];
	static {
		valsCycleType[0] = new OtherModeValue("G_CYC_1CYCLE", 0);
		valsCycleType[1] = new OtherModeValue("G_CYC_2CYCLE", 1);
		valsCycleType[2] = new OtherModeValue("G_CYC_COPY", 2);
		valsCycleType[3] = new OtherModeValue("G_CYC_FILL", 3);
	}

	private static final OtherModeValue[] valsPipeline = new OtherModeValue[2];
	static {
		valsPipeline[0] = new OtherModeValue("G_PM_NPRIMITIVE", 0);
		valsPipeline[1] = new OtherModeValue("G_PM_1PRIMITIVE", 1);
	}
}
