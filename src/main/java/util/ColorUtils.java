package util;

public abstract class ColorUtils
{
	public static int pack(int[] rgb)
	{
		return pack(rgb[0], rgb[1], rgb[2]);
	}

	public static int pack(int R, int G, int B)
	{
		return (R & 0xFF) << 16 | (G & 0xFF) << 8 | (B & 0xFF);
	}

	public static int getR(int rgb)
	{
		return (rgb >> 16) & 0xFF;
	}

	public static int getG(int rgb)
	{
		return (rgb >> 8) & 0xFF;
	}

	public static int getB(int rgb)
	{
		return rgb & 0xFF;
	}

	public static int[] unpack(int rgb)
	{
		return new int[] { getR(rgb), getG(rgb), getB(rgb) };
	}
}
