package game.texture.editor;

public class Dither
{
	private static class RGBTriple
	{
		public final int[] channels;

		public RGBTriple(int color)
		{
			int r = (color >> 16) & 0xFF;
			int g = (color >> 8) & 0xFF;
			int b = (color >> 0) & 0xFF;
			channels = new int[] { r, g, b };
		}
	}

	private static int plus_truncate_uchar(int a, int b)
	{
		if ((a & 0xFF) + b < 0)
			return 0;
		else if ((a & 0xFF) + b > 255)
			return 255;
		else
			return (a + b);
	}

	private static int findNearestColor(RGBTriple color, RGBTriple[] palette)
	{
		int minDistanceSquared = Integer.MAX_VALUE;
		int bestIndex = 0;
		for (int n = 0; n < palette.length; n++) {
			int dR = (color.channels[0] & 0xFF) - (palette[n].channels[0] & 0xFF);
			int dG = (color.channels[1] & 0xFF) - (palette[n].channels[1] & 0xFF);
			int Bdiff = (color.channels[2] & 0xFF) - (palette[n].channels[2] & 0xFF);
			int distanceSquared = dR * dR + dG * dG + Bdiff * Bdiff;
			if (distanceSquared < minDistanceSquared) {
				minDistanceSquared = distanceSquared;
				bestIndex = n;
			}
		}
		return bestIndex;
	}

	private static final float[][] FS_kernel = {
			{ 0f, 0f, 7.0f / 16.0f },
			{ 3.0f / 16.0f, 5.0f / 16.0f, 1.0f / 16.0f }
	};

	private static final float[][] JJN_kernel = {
			{ 0f, 0f, 0f, 7.0f / 42.0f, 5.0f / 42.0f },
			{ 3.0f / 42.0f, 5.0f / 42.0f, 7.0f / 42.0f, 5.0f / 42.0f, 3.0f / 42.0f },
			{ 1.0f / 42.0f, 3.0f / 42.0f, 5.0f / 42.0f, 3.0f / 42.0f, 1.0f / 42.0f }
	};

	private static final float[][] S_kernel = {
			{ 0f, 0f, 0f, 5.0f / 32.0f, 3.0f / 32.0f },
			{ 2.0f / 32.0f, 4.0f / 32.0f, 5.0f / 32.0f, 4.0f / 32.0f, 2.0f / 32.0f },
			{ 0, 2.0f / 32.0f, 3.0f / 32.0f, 2.0f / 32.0f, 0f }
	};

	private static int[][] perform(RGBTriple[][] image, RGBTriple[] palette, int Nrow, int Ncol, float[][] kernel)
	{
		int[][] result = new int[image.length][image[0].length];

		int W = image.length;
		int H = image[0].length;
		int rowStart = Ncol / 2;

		for (int y = 0; y < H; y++)
			for (int x = 0; x < W; x++) {
				RGBTriple currentPixel = image[x][y];
				int index = findNearestColor(currentPixel, palette);
				result[x][y] = index;

				for (int i = 0; i < Ncol; i++) {
					int yp = y + i;
					if (yp >= H)
						continue;

					for (int j = 0; j < Nrow; j++) {
						int xp = x - rowStart + j;
						if (xp >= W || xp < 0)
							continue;

						float scalar = kernel[i][j];
						if (scalar == 0)
							continue;

						for (int c = 0; c < 3; c++) {
							int error = (currentPixel.channels[c] & 0xFF) - (palette[index].channels[c] & 0xFF);
							image[xp][yp].channels[c] = plus_truncate_uchar(image[xp][yp].channels[c], Math.round(error * scalar));
						}
					}
				}
			}

		return result;
	}

	public static enum DitherMethod
	{
		None("None"),
		FloydSteinberg("Floyd-Steinberg"),
		JarvisJudiceNinke("Jarvis, Judice, & Ninke"),
		Sierra("Sierra");

		private final String name;

		private DitherMethod(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	public static int[][] apply(int[][] packedRGB, int[] pal, DitherMethod type)
	{
		RGBTriple[] palette = new RGBTriple[pal.length];
		for (int i = 0; i < palette.length; i++)
			palette[i] = new RGBTriple(pal[i]);

		// indices --> RGB
		int w = packedRGB.length;
		int h = packedRGB[0].length;
		RGBTriple[][] image = new RGBTriple[w][h];
		for (int x = w; x-- > 0;)
			for (int y = h; y-- > 0;) {
				image[x][y] = new RGBTriple(packedRGB[x][y]);
			}

		switch (type) {
			case FloydSteinberg:
				return perform(image, palette, 3, 2, FS_kernel);
			case JarvisJudiceNinke:
				return perform(image, palette, 5, 3, JJN_kernel);
			case Sierra:
				return perform(image, palette, 5, 3, S_kernel);
			default:
				throw new IllegalArgumentException("Unsupported dither type: " + type);

		}
	}
}
