package game.texture;

import java.util.HashMap;

public enum TileFormat
{
	I_4("I4", 4, 0, 1), // (4-bit intensity)	16 shades
	I_8("I8", 4, 1, 1), // (8-bit intensity)	256 shades
	IA_4("IA4", 3, 0, 2), // (3-bit intensity / alpha mask)	8 shades, alpha mask
	IA_8("IA8", 3, 1, 2), // (4-bit intensity / 4-bit alpha)	16 shades, 16 opacity levels
	IA_16("IA16", 3, 2, 2), // (8-bit intensity / 8-bit alpha)	256 shades, 256 opacity levels
	CI_4("CI4", 2, 0, 1), // (4-bit color-indexed)	16 colors
	CI_8("CI8", 2, 1, 1), // (8-bit color-indexed)	256 colors
	YUV_16("YUV16", 1, 2, 4), // (unsupported)
	RGBA_16("RGBA16", 0, 2, 4), // (5 bits per channel / alpha mask)
	RGBA_32("RGBA32", 0, 3, 4); // (8 bits per channel)

	public static String validFormats = "I-4, I-8, IA-4, IA-8, IA-16 \n"
		+ "CI-4, CI-8, RGBA-16, RGBA-32";

	public static final int TYPE_RGBA = 0;
	public static final int TYPE_YUV = 1;
	public static final int TYPE_CI = 2;
	public static final int TYPE_IA = 3;
	public static final int TYPE_I = 4;

	public static final int DEPTH_4BPP = 0;
	public static final int DEPTH_8BPP = 1;
	public static final int DEPTH_16BPP = 2;
	public static final int DEPTH_32BPP = 3;

	public final String name;
	public final int type;
	public final int depth;
	public final int bpp;
	public final int glStride;

	private TileFormat(String name, int type, int depth, int glStride)
	{
		this.name = name;
		this.type = type;
		this.depth = depth;
		this.bpp = 4 << depth;
		this.glStride = glStride;
	}

	@Override
	public String toString()
	{
		return name;
	}

	public int getNumBytes(int width, int height)
	{
		return width * height * bpp >> 3;
	}

	public static TileFormat get(int type, int depth)
	{
		switch (type) {
			case 0:
				switch (depth) {
					case 2:
						return RGBA_16;
					case 3:
						return RGBA_32;
				}
				throw new IllegalArgumentException("Invalid bpp for RGBA: " + (4 * 1 << depth));
			case 1:
				if (depth == 2)
					return YUV_16;
				throw new IllegalArgumentException("Invalid bpp for YUV: " + (4 * 1 << depth));
			//throw new UnsupportedOperationException("Format " + YUV_16 + " is not supported.");
			case 2:
				switch (depth) {
					case 0:
						return CI_4;
					case 1:
						return CI_8;
				}
				throw new IllegalArgumentException("Invalid bpp for CI: " + (4 * 1 << depth));
			case 3:
				switch (depth) {
					case 0:
						return IA_4;
					case 1:
						return IA_8;
					case 2:
						return IA_16;
				}
				throw new IllegalArgumentException("Invalid bpp for IA: " + (4 * 1 << depth));
			case 4:
				switch (depth) {
					case 0:
						return I_4;
					case 1:
						return I_8;
				}
				throw new IllegalArgumentException("Invalid bpp for I: " + (4 * 1 << depth));
		}

		String msg = String.format("Invalid image format: fmt = %s and depth = %s", type, depth);
		throw new IllegalArgumentException(msg);
	}

	private static HashMap<String, TileFormat> nameMap = new HashMap<>();
	static {
		for (TileFormat fmt : TileFormat.values())
			nameMap.put(fmt.name, fmt);
	}

	public static TileFormat getFormat(String name)
	{
		return nameMap.get(name.toUpperCase());
	}
}
