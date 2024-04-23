package game.f3dex2.commands;

import app.input.InvalidInputException;
import game.DataUtils;
import game.f3dex2.BaseF3DEX2;
import game.f3dex2.DisplayList.CommandType;

public class MoveWord extends BaseF3DEX2
{
	public int index;
	public int offset;
	public int value;

	public MoveWord(CommandType cmd, Integer ... args) throws InvalidInputException
	{
		super(cmd, args, 2);

		index = (args[0] >> 16) & 0xFF;
		offset = args[0] & 0xFFFF;

		value = args[1];
	}

	public MoveWord(CommandType cmd, String ... params) throws InvalidInputException
	{
		super(cmd, params, 3);

		index = -1;
		for (Segment seg : Segment.values()) {
			if (seg.toString().equalsIgnoreCase(params[0]))
				index = seg.index;
		}
		if (index < 0)
			index = DataUtils.parseIntString(params[0]);

		offset = -1;
		for (int i = 0; i < offsetOpt.length; i++) {
			if (offsetOpt[i].equalsIgnoreCase(params[1]))
				offset = offsetVal[i];
		}
		if (offset < 0)
			offset = DataUtils.parseIntString(params[1]);

		value = DataUtils.parseIntString(params[2]);
	}

	@Override
	public int[] assemble()
	{
		int[] encoded = new int[2];
		encoded[0] = opField;

		encoded[0] |= (index & 0xFF) << 16;
		encoded[0] |= (offset & 0xFFFF);
		encoded[1] = value;

		return encoded;
	}

	@Override
	public String getString()
	{
		Segment segment = null;
		String indexName = null;
		String offsetName = null;

		for (Segment seg : Segment.values()) {
			if (seg.index == index)
				segment = seg;
		}

		if (segment != null) {
			indexName = segment.toString();

			for (Offset off : segment.offsets) {
				if (off.value == offset)
					offsetName = off.name;
			}

			if (offsetName == null)
				offsetName = String.format("%X", offset);
		}
		else {
			indexName = String.format("%02X", index);
			offsetName = String.format("%X", offset);
		}

		return String.format("%-16s (%s, %s, %08X)", getName(), indexName, offsetName, value);
	}

	private static enum Segment
	{
		// @formatter:off
		G_MW_MATRIX		(0, matrixOffsets),
		G_MW_NUMLIGHT	(2, numLightOffsets),
		G_MW_CLIP		(4, clipOffsets),
		G_MW_SEGMENT	(6, segmentOffsets),
		G_MW_FOG		(8, fogOffsets),
		G_MW_LIGHTCOL	(10, lightColOffsets),
		G_MW_FORCEMTX	(12),
		G_MW_PERSPNORM	(14);
		// @formatter:on

		private final int index;
		private final Offset[] offsets;

		private Segment(int index)
		{
			this(index, new Offset[0]);
		}

		private Segment(int index, Offset[] offsets)
		{
			this.index = index;
			this.offsets = offsets;
		}
	}

	private static class Offset
	{
		private final String name;
		private final int value;

		private Offset(String name, int value)
		{
			this.name = name;
			this.value = value;
		}
	}

	private static final Offset[] numLightOffsets = new Offset[1];
	static {
		numLightOffsets[0] = new Offset("G_MWO_NUMLIGHT", 0x0);
	}

	private static final Offset[] clipOffsets = new Offset[4];
	static {
		clipOffsets[0] = new Offset("G_MWO_CLIP_RNX", 0x4);
		clipOffsets[1] = new Offset("G_MWO_CLIP_RNY", 0xC);
		clipOffsets[2] = new Offset("G_MWO_CLIP_RPX", 0x14);
		clipOffsets[3] = new Offset("G_MWO_CLIP_RPY", 0x1C);
	}

	private static final Offset[] segmentOffsets = new Offset[16];
	static {
		segmentOffsets[0] = new Offset("G_MWO_SEGMENT_0", 0x0);
		segmentOffsets[1] = new Offset("G_MWO_SEGMENT_1", 0x1);
		segmentOffsets[2] = new Offset("G_MWO_SEGMENT_2", 0x2);
		segmentOffsets[3] = new Offset("G_MWO_SEGMENT_3", 0x3);
		segmentOffsets[4] = new Offset("G_MWO_SEGMENT_4", 0x4);
		segmentOffsets[5] = new Offset("G_MWO_SEGMENT_5", 0x5);
		segmentOffsets[6] = new Offset("G_MWO_SEGMENT_6", 0x6);
		segmentOffsets[7] = new Offset("G_MWO_SEGMENT_7", 0x7);
		segmentOffsets[8] = new Offset("G_MWO_SEGMENT_8", 0x8);
		segmentOffsets[9] = new Offset("G_MWO_SEGMENT_9", 0x9);
		segmentOffsets[10] = new Offset("G_MWO_SEGMENT_A", 0xA);
		segmentOffsets[11] = new Offset("G_MWO_SEGMENT_B", 0xB);
		segmentOffsets[12] = new Offset("G_MWO_SEGMENT_C", 0xC);
		segmentOffsets[13] = new Offset("G_MWO_SEGMENT_D", 0xD);
		segmentOffsets[14] = new Offset("G_MWO_SEGMENT_E", 0xE);
		segmentOffsets[15] = new Offset("G_MWO_SEGMENT_F", 0xF);
	}

	private static final Offset[] fogOffsets = new Offset[1];
	static {
		fogOffsets[0] = new Offset("G_MWO_FOG", 0x0);
	}

	private static final Offset[] lightColOffsets = new Offset[16];
	static {
		lightColOffsets[0] = new Offset("G_MWO_aLIGHT_1", 0x0);
		lightColOffsets[1] = new Offset("G_MWO_bLIGHT_1", 0x4);
		lightColOffsets[2] = new Offset("G_MWO_aLIGHT_2", 0x18);
		lightColOffsets[3] = new Offset("G_MWO_bLIGHT_2", 0x1C);
		lightColOffsets[4] = new Offset("G_MWO_aLIGHT_3", 0x30);
		lightColOffsets[5] = new Offset("G_MWO_bLIGHT_3", 0x34);
		lightColOffsets[6] = new Offset("G_MWO_aLIGHT_4", 0x48);
		lightColOffsets[7] = new Offset("G_MWO_bLIGHT_4", 0x4C);
		lightColOffsets[8] = new Offset("G_MWO_aLIGHT_5", 0x60);
		lightColOffsets[9] = new Offset("G_MWO_bLIGHT_5", 0x64);
		lightColOffsets[10] = new Offset("G_MWO_aLIGHT_6", 0x78);
		lightColOffsets[11] = new Offset("G_MWO_bLIGHT_6", 0x7C);
		lightColOffsets[12] = new Offset("G_MWO_aLIGHT_7", 0x90);
		lightColOffsets[13] = new Offset("G_MWO_bLIGHT_7", 0x94);
		lightColOffsets[14] = new Offset("G_MWO_aLIGHT_8", 0xA8);
		lightColOffsets[15] = new Offset("G_MWO_bLIGHT_8", 0xAC);
	}

	private static final Offset[] matrixOffsets = new Offset[16];
	static {
		matrixOffsets[0] = new Offset("G_MWO_MATRIX_XX_XY_I", 0x0);
		matrixOffsets[1] = new Offset("G_MWO_MATRIX_XZ_XW_I", 0x4);
		matrixOffsets[2] = new Offset("G_MWO_MATRIX_YX_YY_I", 0x8);
		matrixOffsets[3] = new Offset("G_MWO_MATRIX_YZ_YW_I", 0xC);
		matrixOffsets[4] = new Offset("G_MWO_MATRIX_ZX_ZY_I", 0x10);
		matrixOffsets[5] = new Offset("G_MWO_MATRIX_ZZ_ZW_I", 0x14);
		matrixOffsets[6] = new Offset("G_MWO_MATRIX_WX_WY_I", 0x18);
		matrixOffsets[7] = new Offset("G_MWO_MATRIX_WZ_WW_I", 0x1C);
		matrixOffsets[8] = new Offset("G_MWO_MATRIX_XX_XY_F", 0x20);
		matrixOffsets[9] = new Offset("G_MWO_MATRIX_XZ_XW_F", 0x24);
		matrixOffsets[10] = new Offset("G_MWO_MATRIX_YX_YY_F", 0x28);
		matrixOffsets[11] = new Offset("G_MWO_MATRIX_YZ_YW_F", 0x2C);
		matrixOffsets[12] = new Offset("G_MWO_MATRIX_ZX_ZY_F", 0x30);
		matrixOffsets[13] = new Offset("G_MWO_MATRIX_ZZ_ZW_F", 0x34);
		matrixOffsets[14] = new Offset("G_MWO_MATRIX_WX_WY_F", 0x38);
		matrixOffsets[15] = new Offset("G_MWO_MATRIX_WZ_WW_F", 0x3C);
	}

	//not unique! used for encoding
	private static final int[] offsetVal = {
			0x00,
			0x04, 0x0C, 0x14, 0x1C,
			0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F,
			0x00,
			0x00, 0x04, 0x18, 0x1C, 0x30, 0x34, 0x48, 0x4C, 0x60, 0x64, 0x78, 0x7C, 0x90, 0x94, 0xA8, 0xAC,
			0x00, 0x04, 0x08, 0x0C, 0x10, 0x14, 0x18, 0x1C, 0x20, 0x24, 0x28, 0x2C, 0x30, 0x34, 0x38, 0x3C,
			0x10, 0x14, 0x18, 0x1C
	};

	private static final String[] offsetOpt = {
			"G_MWO_NUMLIGHT",

			"G_MWO_CLIP_RNX",
			"G_MWO_CLIP_RNY",
			"G_MWO_CLIP_RPX",
			"G_MWO_CLIP_RPY",

			"G_MWO_SEGMENT_0",
			"G_MWO_SEGMENT_1",
			"G_MWO_SEGMENT_2",
			"G_MWO_SEGMENT_3",
			"G_MWO_SEGMENT_4",
			"G_MWO_SEGMENT_5",
			"G_MWO_SEGMENT_6",
			"G_MWO_SEGMENT_7",
			"G_MWO_SEGMENT_8",
			"G_MWO_SEGMENT_9",
			"G_MWO_SEGMENT_A",
			"G_MWO_SEGMENT_B",
			"G_MWO_SEGMENT_C",
			"G_MWO_SEGMENT_D",
			"G_MWO_SEGMENT_E",
			"G_MWO_SEGMENT_F",

			"G_MWO_FOG",

			"G_MWO_aLIGHT_1",
			"G_MWO_bLIGHT_1",
			"G_MWO_aLIGHT_2",
			"G_MWO_bLIGHT_2",
			"G_MWO_aLIGHT_3",
			"G_MWO_bLIGHT_3",
			"G_MWO_aLIGHT_4",
			"G_MWO_bLIGHT_4",
			"G_MWO_aLIGHT_5",
			"G_MWO_bLIGHT_5",
			"G_MWO_aLIGHT_6",
			"G_MWO_bLIGHT_6",
			"G_MWO_aLIGHT_7",
			"G_MWO_bLIGHT_7",
			"G_MWO_aLIGHT_8",
			"G_MWO_bLIGHT_8",

			"G_MWO_MATRIX_XX_XY_I",
			"G_MWO_MATRIX_XZ_XW_I",
			"G_MWO_MATRIX_YX_YY_I",
			"G_MWO_MATRIX_YZ_YW_I",
			"G_MWO_MATRIX_ZX_ZY_I",
			"G_MWO_MATRIX_ZZ_ZW_I",
			"G_MWO_MATRIX_WX_WY_I",
			"G_MWO_MATRIX_WZ_WW_I",
			"G_MWO_MATRIX_XX_XY_F",
			"G_MWO_MATRIX_XZ_XW_F",
			"G_MWO_MATRIX_YX_YY_F",
			"G_MWO_MATRIX_YZ_YW_F",
			"G_MWO_MATRIX_ZX_ZY_F",
			"G_MWO_MATRIX_ZZ_ZW_F",
			"G_MWO_MATRIX_WX_WY_F",
			"G_MWO_MATRIX_WZ_WW_F",

			"G_MWO_POINT_RGBA",
			"G_MWO_POINT_ST",
			"G_MWO_POINT_XYSCREEN",
			"G_MWO_POINT_ZSCREEN"
	};
}
