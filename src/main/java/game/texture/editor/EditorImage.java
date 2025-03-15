package game.texture.editor;

import static renderer.buffers.BufferedMesh.VBO_COLOR;
import static renderer.buffers.BufferedMesh.VBO_INDEX;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.IndexColorModel;
import java.io.File;
import java.nio.ByteBuffer;

import common.commands.AbstractCommand;
import game.texture.ImageConverter;
import game.texture.Palette;
import game.texture.Tile;
import game.texture.TileFormat;
import game.texture.editor.Dither.DitherMethod;
import game.texture.editor.dialogs.ConvertOptionsPanel.ConvertSettings;
import game.texture.editor.dialogs.ResizeOptionsPanel.ResizeOptions;
import renderer.buffers.BufferedMesh;
import renderer.shaders.RenderState;
import renderer.shaders.ShaderManager;
import renderer.shaders.scene.BasicSolidShader;

public class EditorImage
{
	// lookup tables for intensity/alpha components --> table indices for indexed I/IA
	private static int[] indexLUT_I4;
	private static int[][] indexLUT_IA4;
	private static int[][] indexLUT_IA8;

	static {
		indexLUT_I4 = createLUT_I(getPaletteI4());
		indexLUT_IA4 = createLUT_IA(getPaletteIA4());
		indexLUT_IA8 = createLUT_IA(getPaletteIA8());
	}

	private BufferedMesh mesh;

	private final ImageEditor editor;

	public final TileFormat format;
	public final int width;
	public final int height;

	private final Pixel[][] pixels;
	private final Color[] palette;
	public final boolean editablePalette;

	private boolean[][] selectionMask;
	private int selectedCount = 0;

	public File source;

	private EditorImage(ImageEditor editor, TileFormat format, int width, int height)
	{
		this.editor = editor;
		this.format = format;
		this.width = width;
		this.height = height;

		pixels = new Pixel[width][height];
		selectionMask = new boolean[width][height];

		// create palette
		switch (format) {
			case CI_4:
				palette = Palette.createDefaultForEditor(16, 0.8f).getColors();
				break;
			case CI_8:
				palette = Palette.createDefaultForEditor(256, 0.8f).getColors();
				break;
			case I_4:
				palette = getPaletteI4();
				break;
			case I_8:
				palette = null;
				break;
			case IA_4:
				palette = getPaletteIA4();
				break;
			case IA_8:
				palette = getPaletteIA8();
				break;
			case IA_16:
				palette = null;
				break;
			case RGBA_16:
				palette = null;
				break;
			case RGBA_32:
				palette = null;
				break;
			case YUV_16:
			default:
				throw new UnsupportedOperationException("Unsupported format: " + format);
		}

		editablePalette = (format.type == TileFormat.TYPE_CI);
	}

	private EditorImage(ImageEditor editor, Palette pal, int width, int height)
	{
		this.editor = editor;
		this.format = pal.size == 16 ? TileFormat.CI_4 : TileFormat.CI_8;
		this.width = width;
		this.height = height;

		pixels = new Pixel[width][height];
		selectionMask = new boolean[width][height];

		palette = pal.getColors();
		editablePalette = true;

		// create palette
		switch (format) {
			case CI_4:
				if (pal.size != 16)
					throw new IllegalStateException("Palette size mismatch for " + format);
				break;
			case CI_8:
				if (pal.size != 256)
					throw new IllegalStateException("Palette size mismatch for " + format);
				break;
			default:
				throw new UnsupportedOperationException("Could not assign palette for format: " + format);
		}
	}

	public EditorImage(ImageEditor editor, Tile tile, File file)
	{
		this(editor, tile.format, tile.width, tile.height);
		source = file;
		mesh = null;

		ByteBuffer glBuffer = ImageConverter.convertToGLBuffer(tile);
		glBuffer.rewind();

		if (tile.palette != null) {
			// read palette from tile
			Color[] colors = tile.palette.getColors();
			for (int i = 0; i < colors.length; i++) {
				Color c = colors[i];
				palette[i] = new Color(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());
			}
		}

		// read pixels from tile
		for (int j = 0; j < tile.height; j++)
			for (int i = 0; i < tile.width; i++) {
				switch (tile.format.type) {
					case TileFormat.TYPE_CI:
						pixels[i][j] = Pixel.getColorIndexed(glBuffer.get());
						break;
					case TileFormat.TYPE_I:
						pixels[i][j] = Pixel.getIntensity(glBuffer.get(), (byte) 255);
						break;
					case TileFormat.TYPE_IA:
						pixels[i][j] = Pixel.getIntensity(glBuffer.get(), glBuffer.get());
						break;
					case TileFormat.TYPE_RGBA:
						pixels[i][j] = Pixel.getRGBA(glBuffer.get(), glBuffer.get(), glBuffer.get(), glBuffer.get());
						break;
				}
			}

		// assign indices for indexed I/IA formats
		for (int j = 0; j < tile.height; j++)
			for (int i = 0; i < tile.width; i++) {
				Pixel pixel = pixels[i][j];
				switch (tile.format) {
					case I_4:
						pixel.index = indexLUT_I4[pixel.r];
						break;
					case IA_4:
						pixel.index = indexLUT_IA4[pixel.r][pixel.a];
						break;
					case IA_8:
						pixel.index = indexLUT_IA8[pixel.r][pixel.a];
						break;
					default:
				}
			}
	}

	public static EditorImage resize(EditorImage oldImage, ResizeOptions resizeOptions)
	{
		int W = resizeOptions.width;
		int H = resizeOptions.height;

		EditorImage newImage = new EditorImage(oldImage.editor, oldImage.format, W, H);
		newImage.source = oldImage.source;

		for (int i = 0; i < oldImage.palette.length; i++) {
			Color c = oldImage.palette[i];
			newImage.palette[i] = new Color(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());
		}

		for (int j = 0; j < H; j++)
			for (int i = 0; i < W; i++) {
				newImage.pixels[i][j] = new Pixel();
			}

		int dx = W - oldImage.width;
		int dy = H - oldImage.height;

		int padLeft = 0;
		switch (resizeOptions.methodH) {
			case FixCenter:
				padLeft = dx / 2;
				break;
			case FixLeft:
				padLeft = -dx;
				break;
			case FixRight:
				padLeft = dx;
				break;
		}

		int padTop = 0;
		switch (resizeOptions.methodV) {
			case FixCenter:
				padTop = dy / 2;
				break;
			case FixTop:
				padTop = dy;
				break;
			case FixBottom:
				padTop = -dy;
				break;
		}

		for (int j = 0; j < H; j++) {
			int jp = j - padTop;
			if (jp < 0 || jp >= oldImage.height)
				continue;

			for (int i = 0; i < W; i++) {
				int ip = i - padLeft;
				if (ip < 0 || ip >= oldImage.width)
					continue;

				newImage.pixels[i][j].sample(oldImage.pixels[ip][jp]);
			}
		}

		return newImage;
	}

	public static EditorImage forcePalette(EditorImage oldImage, Palette pal)
	{
		EditorImage newImage = new EditorImage(oldImage.editor, pal, oldImage.width, oldImage.height);
		newImage.source = oldImage.source;

		for (int j = 0; j < newImage.height; j++)
			for (int i = 0; i < newImage.width; i++) {
				newImage.pixels[i][j] = new Pixel(oldImage.pixels[i][j]);
				Pixel pixel = newImage.pixels[i][j];
				int R, G, B, A;

				// convert to RBGA
				if (oldImage.palette != null) {
					Color c = oldImage.palette[pixel.index];
					R = c.getRed();
					G = c.getGreen();
					B = c.getBlue();
					A = c.getAlpha();
				}
				else if (oldImage.format.type == TileFormat.TYPE_I) {
					R = pixel.r;
					G = pixel.r;
					B = pixel.r;
					A = 255;
				}
				else if (oldImage.format.type == TileFormat.TYPE_IA) {
					R = pixel.r;
					G = pixel.r;
					B = pixel.r;
					A = pixel.a;
				}
				else {
					R = pixel.r;
					G = pixel.g;
					B = pixel.b;
					A = pixel.a;
				}

				pixel.index = finalNearestPaletteIndex(newImage.palette, R, G, B, A);
				pixel.r = newImage.palette[pixel.index].getRed();
				pixel.g = newImage.palette[pixel.index].getGreen();
				pixel.b = newImage.palette[pixel.index].getBlue();
				pixel.a = newImage.palette[pixel.index].getAlpha();
			}

		return newImage;
	}

	private static int finalNearestPaletteIndex(Color[] palette, int r, int g, int b, int a)
	{
		int minScore = Integer.MAX_VALUE;
		int bestIndex = 0;

		for (int i = 0; i < palette.length; i++) {
			int dr = (r - palette[i].getRed());
			int dg = (g - palette[i].getGreen());
			int db = (b - palette[i].getBlue());
			int da = (a - palette[i].getAlpha());

			int score = dr * dr + dg * dg + db * db + da * da;
			if (score < minScore) {
				minScore = score;
				bestIndex = i;
			}
		}

		return bestIndex;
	}

	public static EditorImage convert(EditorImage oldImage, ConvertSettings settings)
	{
		assert (oldImage.format != settings.fmt);

		EditorImage newImage = new EditorImage(oldImage.editor, settings.fmt, oldImage.width, oldImage.height);
		newImage.source = oldImage.source;

		if (newImage.format == TileFormat.CI_8 && oldImage.format == TileFormat.CI_4) {
			// if expanding palette, copy old one
			for (int i = 0; i < oldImage.palette.length; i++) {
				Color c = oldImage.palette[i];
				newImage.palette[i] = new Color(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());
			}

			for (int j = 0; j < newImage.height; j++)
				for (int i = 0; i < newImage.width; i++) {
					newImage.pixels[i][j] = new Pixel(oldImage.pixels[i][j]);
				}
			return newImage;
		}

		for (int j = 0; j < newImage.height; j++)
			for (int i = 0; i < newImage.width; i++) {
				newImage.pixels[i][j] = new Pixel(oldImage.pixels[i][j]);
				Pixel pixel = newImage.pixels[i][j];

				// convert to RBGA
				if (oldImage.palette != null) {
					Color c = oldImage.palette[pixel.index];
					pixel.r = c.getRed();
					pixel.g = c.getGreen();
					pixel.b = c.getBlue();
					pixel.a = c.getAlpha();
				}
				else if (oldImage.format.type == TileFormat.TYPE_I) {
					pixel.r = pixel.r;
					pixel.g = pixel.r;
					pixel.b = pixel.r;
					pixel.a = 255;
				}
				else if (oldImage.format.type == TileFormat.TYPE_IA) {
					pixel.r = pixel.r;
					pixel.g = pixel.r;
					pixel.b = pixel.r;
					pixel.a = pixel.a;
				}
			}

		switch (newImage.format) {
			case CI_4:
			case CI_8:
				reduceCI(newImage, settings.ditherMethod);
				break;
			case I_4:
				convertToGrayscale(newImage, settings, false);
				reduceI(newImage, indexLUT_I4);
				break;
			case I_8:
				convertToGrayscale(newImage, settings, false);
				break;
			case IA_4:
				convertToGrayscale(newImage, settings, true);
				reduceIA(newImage, indexLUT_IA4);
				break;
			case IA_8:
				convertToGrayscale(newImage, settings, true);
				reduceIA(newImage, indexLUT_IA8);
				break;
			case IA_16:
				convertToGrayscale(newImage, settings, true);
				break;
			case RGBA_16:
			case RGBA_32:
				// already done
				break;
			case YUV_16:
				throw new UnsupportedOperationException("Unsupported format: " + settings.fmt);
		}

		return newImage;
	}

	private static void convertToGrayscale(EditorImage image, ConvertSettings settings, boolean hasAlpha)
	{
		for (int j = 0; j < image.height; j++)
			for (int i = 0; i < image.width; i++) {
				Pixel pixel = image.pixels[i][j];

				switch (settings.intensityMethod) {
					case Luminance:
						pixel.r = Math.min(255, Math.round((0.2126f * pixel.r) + (0.7152f * pixel.g) + (0.0722f * pixel.b)));
						break;
					case Balanced:
						pixel.r = Math.min(255, Math.round((0.299f * pixel.r) + (0.587f * pixel.g) + (0.114f * pixel.b)));
						break;
					case Average:
						pixel.r = Math.min(255, Math.round((0.333f * pixel.r) + (0.333f * pixel.g) + (0.333f * pixel.b)));
						break;
				}

				pixel.g = pixel.r;
				pixel.b = pixel.r;

				if (!hasAlpha)
					pixel.a = 255;
			}
	}

	private static void reduceI(EditorImage image, int[] LUT)
	{
		for (int j = 0; j < image.height; j++)
			for (int i = 0; i < image.width; i++) {
				Pixel pixel = image.pixels[i][j];
				pixel.index = LUT[pixel.r];
				Color reduced = image.palette[pixel.index];
				pixel.r = reduced.getRed();
				pixel.g = reduced.getRed();
				pixel.b = reduced.getRed();
				pixel.a = reduced.getAlpha();
			}
	}

	private static void reduceIA(EditorImage image, int[][] LUT)
	{
		for (int j = 0; j < image.height; j++)
			for (int i = 0; i < image.width; i++) {
				Pixel pixel = image.pixels[i][j];
				pixel.index = LUT[pixel.r][pixel.a];
				Color reduced = image.palette[pixel.index];
				pixel.r = reduced.getRed();
				pixel.g = reduced.getRed();
				pixel.b = reduced.getRed();
				pixel.a = reduced.getAlpha();
			}
	}

	private static void reduceCI(EditorImage image, DitherMethod type)
	{
		int[][] packedPixels = new int[image.width][image.height];

		// pack pixels --> ARGB integers
		for (int j = 0; j < image.height; j++)
			for (int i = 0; i < image.width; i++) {
				Pixel pixel = image.pixels[i][j];
				packedPixels[i][j] = (pixel.a << 24) | (pixel.r << 16) | (pixel.g << 8) | (pixel.b << 0);
			}

		int[][] originalPixels = new int[image.width][image.height];
		for (int j = 0; j < image.height; j++)
			for (int i = 0; i < image.width; i++) {
				originalPixels[i][j] = packedPixels[i][j];
			}

		// packedPixels are now indices
		int[] newPalette = Quantize.quantizeImage(packedPixels, (image.format == TileFormat.CI_4) ? 16 : 256);

		if (type != DitherMethod.None)
			packedPixels = Dither.apply(originalPixels, newPalette, type);

		// unpack palette
		for (int i = 0; i < newPalette.length; i++) {
			int packedColor = newPalette[i];
			int R = (packedColor >> 16) & 0xFF;
			int G = (packedColor >> 8) & 0xFF;
			int B = (packedColor >> 0) & 0xFF;
			image.palette[i] = new Color(R, G, B, 255);
		}

		// assign pixels
		for (int j = 0; j < image.height; j++)
			for (int i = 0; i < image.width; i++) {
				int index = packedPixels[i][j];

				Pixel pixel = image.pixels[i][j];
				pixel.index = index;

				pixel.a = image.palette[index].getAlpha();
				pixel.r = image.palette[index].getRed();
				pixel.g = image.palette[index].getGreen();
				pixel.b = image.palette[index].getBlue();
			}
	}

	public Tile getTile()
	{
		BufferedImage bimg;

		if (palette == null) {
			bimg = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

			for (int y = 0; y < height; y++)
				for (int x = 0; x < width; x++) {
					Pixel pixel = pixels[x][y];
					bimg.setRGB(x, (height - 1) - y, pixel.getARGB());
				}
		}
		else {
			int[] cmap = new int[palette.length];
			for (int i = 0; i < palette.length; i++)
				cmap[i] = palette[i].getRGB();

			bimg = new BufferedImage(width, height,
				BufferedImage.TYPE_BYTE_INDEXED,
				new IndexColorModel(
					8, // bits per pixel
					palette.length, // size of color component array
					cmap,
					0, // offset in the map
					true, // has alpha
					0, // the pixel value that should be transparent
					DataBuffer.TYPE_BYTE));

			for (int y = 0; y < height; y++)
				for (int x = 0; x < width; x++) {
					Pixel pixel = pixels[x][y];
					bimg.setRGB(x, (height - 1) - y, palette[pixel.index].getRGB());
				}
		}

		return ImageConverter.getTile(bimg, format);
	}

	public void bindPalette(PaletteSwatchesPanel currentSwatches)
	{
		assert (palette != null);
		currentSwatches.setPalette(palette);
	}

	/**
	 * Drawing
	 */

	public void draw(int mousePixelX, int mousePixelY, Pixel pickedPixel)
	{
		if (selectedCount == 0 || selectionMask[mousePixelX][mousePixelY]) {
			pixels[mousePixelX][mousePixelY].sample(pickedPixel);
			numDrawn++;
		}
	}

	public void sample(int mousePixelX, int mousePixelY, Pixel pickedPixel)
	{
		pickedPixel.sample(pixels[mousePixelX][mousePixelY]);
	}

	/**
	 * Selection Management
	 */

	public void clearSelection()
	{
		if (selectedCount > 0) {
			numDrawnSelected = selectedCount;
			selectionMask = new boolean[width][height];
			selectedCount = 0;
		}
	}

	public void selectionFill(int x, int y)
	{
		boolean[][] flooded = new boolean[width][height];
		Pixel startingPixel = pixels[x][y];

		flood(x, y, startingPixel, flooded);
	}

	public void deselectionFill(int x, int y)
	{
		boolean[][] flooded = new boolean[width][height];
		Pixel startingPixel = pixels[x][y];

		antiflood(x, y, startingPixel, flooded);
	}

	private void flood(int x, int y, Pixel startingPixel, boolean[][] flooded)
	{
		if (x < 0 || x >= width)
			return;

		if (y < 0 || y >= height)
			return;

		if (flooded[x][y])
			return;

		flooded[x][y] = true;

		if (!startingPixel.equals(pixels[x][y]))
			return;

		if (!selectionMask[x][y]) {
			selectionMask[x][y] = true;
			selectedCount++;
			numDrawnSelected++;
		}

		flood(x - 1, y, startingPixel, flooded);
		flood(x + 1, y, startingPixel, flooded);
		flood(x, y - 1, startingPixel, flooded);
		flood(x, y + 1, startingPixel, flooded);
	}

	private void antiflood(int x, int y, Pixel startingPixel, boolean[][] flooded)
	{
		if (x < 0 || x >= width)
			return;

		if (y < 0 || y >= height)
			return;

		if (flooded[x][y])
			return;

		flooded[x][y] = true;

		if (!startingPixel.equals(pixels[x][y]))
			return;

		if (selectionMask[x][y]) {
			selectionMask[x][y] = false;
			selectedCount++;
			numDrawnSelected++;
		}

		antiflood(x - 1, y, startingPixel, flooded);
		antiflood(x + 1, y, startingPixel, flooded);
		antiflood(x, y - 1, startingPixel, flooded);
		antiflood(x, y + 1, startingPixel, flooded);
	}

	public void fillSelection(Pixel pickedPixel)
	{
		if (selectedCount > 0) {
			for (int i = 0; i < width; i++) {
				for (int j = 0; j < height; j++) {
					if (!selectionMask[i][j])
						continue;

					pixels[i][j].sample(pickedPixel);
					numDrawn++;
				}
			}
		}
	}

	public void selectByIndex(int index)
	{
		for (int i = 0; i < width; i++) {
			for (int j = 0; j < height; j++) {
				if (pixels[i][j].index == index) {
					selectionMask[i][j] = true;
					selectedCount++;
					numDrawnSelected++;
				}
			}
		}
	}

	public void select(int i, int j)
	{
		if (!selectionMask[i][j]) {
			selectionMask[i][j] = true;
			selectedCount++;
			numDrawnSelected++;

			assert (selectedCount > 0);
		}
	}

	public void deselect(int i, int j)
	{
		if (selectionMask[i][j]) {
			selectionMask[i][j] = false;
			selectedCount--;
			numDrawnSelected++;

			assert (selectedCount >= 0);
		}
	}

	public int getNumSelected()
	{
		return selectedCount;
	}

	public boolean hasSelectedPixels()
	{
		return selectedCount > 0;
	}

	/**
	 * Rendering
	 */

	protected void renderImage(float selectionColorAmount)
	{
		if (mesh == null)
			mesh = new BufferedMesh(2 * (width * height) * 4, 2 * (width * height) * 2, VBO_INDEX | VBO_COLOR);
		else
			mesh.clear();

		for (int i = 0; i < width; i++)
			for (int j = 0; j < height; j++) {
				if (!selectionMask[i][j])
					buildPixel(i, j);
			}

		RenderState.setColor(1.0f, selectionColorAmount, selectionColorAmount, 1.0f);
		float delta = 0.25f;

		for (int i = 0; i < width; i++)
			for (int j = 0; j < height; j++) {
				if (selectionMask[i][j]) {
					mesh.addQuad(
						mesh.addVertex().setPosition(i - delta, j - delta, 0).getIndex(),
						mesh.addVertex().setPosition(i + 1 + delta, j - delta, 0).getIndex(),
						mesh.addVertex().setPosition(i + 1 + delta, j + 1 + delta, 0).getIndex(),
						mesh.addVertex().setPosition(i - delta, j + 1 + delta, 0).getIndex());
				}
			}

		for (int i = 0; i < width; i++)
			for (int j = 0; j < height; j++) {
				if (selectionMask[i][j])
					buildPixel(i, j);
			}

		mesh.loadBuffers();

		ShaderManager.use(BasicSolidShader.class);
		mesh.render();
	}

	private void buildPixel(int i, int j)
	{
		Pixel px = pixels[i][j];
		if (px == null)
			return;

		if (palette != null) {
			int index = px.index;

			float R = palette[index].getRed() / 255.0f;
			float G = palette[index].getGreen() / 255.0f;
			float B = palette[index].getBlue() / 255.0f;
			float A = palette[index].getAlpha() / 255.0f;

			RenderState.setColor(R, G, B, A);
		}
		else if (format.type == TileFormat.TYPE_I || format.type == TileFormat.TYPE_IA) {
			float I = px.r / 255.0f;
			float A = px.a;

			RenderState.setColor(I, I, I, A);
		}
		else if (format.type == TileFormat.TYPE_RGBA) {
			float R = px.r / 255.0f;
			float G = px.g / 255.0f;
			float B = px.b / 255.0f;
			float A = px.a / 255.0f;

			RenderState.setColor(R, G, B, A);
		}

		mesh.addQuad(
			mesh.addVertex().setPosition(i, j, 0).getIndex(),
			mesh.addVertex().setPosition(i + 1, j, 0).getIndex(),
			mesh.addVertex().setPosition(i + 1, j + 1, 0).getIndex(),
			mesh.addVertex().setPosition(i, j + 1, 0).getIndex());
	}

	private static Color[] getPaletteI4()
	{
		Color[] palette = new Color[16];
		for (int i = 0; i < 16; i++) {
			int intensity = 17 * i; // 0 -> 0, 15 -> 255
			palette[i] = new Color(intensity, intensity, intensity, 255);
		}
		return palette;
	}

	private static Color[] getPaletteIA4()
	{
		Color[] palette = new Color[16];
		for (int i = 0; i < 8; i++) {
			int intensity = Math.round(36.4f * i);
			palette[i] = new Color(intensity, intensity, intensity, 255);
			palette[i + 8] = new Color(intensity, intensity, intensity, 0);
		}
		return palette;
	}

	private static Color[] getPaletteIA8()
	{
		Color[] palette = new Color[256];
		int k = 0;

		for (int j = 0; j < 16; j++) {
			int intensity = 17 * j; // 0 -> 0, 15 -> 255
			for (int i = 15; i >= 8; i--) {
				int alpha = 17 * i; // 0 -> 0, 15 -> 255
				palette[k++] = new Color(intensity, intensity, intensity, alpha);
			}
		}

		for (int j = 0; j < 16; j++) {
			int intensity = 17 * j; // 0 -> 0, 15 -> 255
			for (int i = 7; i >= 0; i--) {
				int alpha = 17 * i; // 0 -> 0, 15 -> 255
				palette[k++] = new Color(intensity, intensity, intensity, alpha);
			}
		}

		/*
		for(int j = 15; j >= 0; j--)
		{
			int alpha = 17 * j; // 0 -> 0, 15 -> 255
			for(int i = 0; i < 8; i++)
			{
				int intensity = 17 * i; // 0 -> 0, 15 -> 255
				palette[k++] = new Color(intensity, intensity, intensity, alpha);
			}
		}
		
		for(int j = 0; j < 16; j++)
		{
			int alpha = 17 * j; // 0 -> 0, 15 -> 255
			for(int i = 15; i >= 8; i--)
			{
				int intensity = 17 * i; // 0 -> 0, 15 -> 255
				palette[k++] = new Color(intensity, intensity, intensity, alpha);
			}
		}
		 */

		return palette;
	}

	private static int[] createLUT_I(Color[] colors)
	{
		int[] LUT = new int[256];

		for (int i = 0; i < 256; i++) {
			int minDist = Integer.MAX_VALUE;
			int mindex = -1;

			for (int k = 0; k < colors.length; k++) {
				Color c = colors[k];
				int dI = (c.getRed() - i);
				int dist = dI * dI;
				if (dist < minDist) {
					minDist = dist;
					mindex = k;
				}
			}

			LUT[i] = mindex;
		}

		return LUT;
	}

	private static int[][] createLUT_IA(Color[] colors)
	{
		int[][] LUT = new int[256][256];

		for (int i = 0; i < 256; i++)
			for (int j = 0; j < 256; j++) {
				int minDist = Integer.MAX_VALUE;
				int mindex = -1;

				for (int k = 0; k < colors.length; k++) {
					Color c = colors[k];
					int dI = (c.getRed() - i);
					int dA = (c.getAlpha() - j);
					int dist = dI * dI + dA * dA;
					if (dist < minDist) {
						minDist = dist;
						mindex = k;
					}
				}

				LUT[i][j] = mindex;
			}

		return LUT;
	}

	private ImageBackup imageBackup = null;
	private SelectionBackup selectionBackup = null;
	private ColorBackup colorBackup = null;
	private int numDrawn = 0;
	private int numDrawnSelected = 0;

	public void startSelection()
	{
		if (selectionBackup == null) {
			selectionBackup = new SelectionBackup(this);
			numDrawnSelected = 0;
		}
	}

	public void endSelection()
	{
		if (selectionBackup == null)
			return;

		if (numDrawnSelected > 0)
			editor.push(new RestoreSelection(this, selectionBackup));

		selectionBackup = null;
	}

	public void startDrawing()
	{
		if (imageBackup == null) {
			imageBackup = new ImageBackup(this);
			numDrawn = 0;
		}
	}

	public void endDrawing()
	{
		if (imageBackup == null)
			return;

		if (numDrawn > 0)
			editor.push(new RestoreImage(this, imageBackup));

		imageBackup = null;
	}

	public void startPaletteEdit(int index)
	{
		colorBackup = new ColorBackup(this, index);
	}

	public void endPaletteEdit(int index)
	{
		if (colorBackup == null)
			return;

		editor.push(new RestoreColor(this, colorBackup, index));

		colorBackup = null;
	}

	public static class ImageBackup
	{
		private Pixel[][] pixels;

		public ImageBackup(EditorImage image)
		{
			pixels = new Pixel[image.width][image.height];

			for (int j = 0; j < image.height; j++)
				for (int i = 0; i < image.width; i++) {
					pixels[i][j] = new Pixel(image.pixels[i][j]);
				}
		}

		public void apply(EditorImage image)
		{
			for (int j = 0; j < image.height; j++)
				for (int i = 0; i < image.width; i++) {
					image.pixels[i][j].sample(pixels[i][j]);
				}
		}
	}

	public static class RestoreImage extends AbstractCommand
	{
		private final EditorImage image;
		private final ImageBackup oldBackup;
		private final ImageBackup newBackup;

		public RestoreImage(EditorImage image, ImageBackup backup)
		{
			super("Modify Image");
			this.image = image;
			this.oldBackup = backup;
			this.newBackup = new ImageBackup(image);
		}

		@Override
		public void exec()
		{
			super.exec();
			newBackup.apply(image);
		}

		@Override
		public void undo()
		{
			super.undo();
			oldBackup.apply(image);
		}
	}

	public static class SelectionBackup
	{
		private boolean[][] selection;
		private int numSelected;

		public SelectionBackup(EditorImage image)
		{
			selection = new boolean[image.width][image.height];

			for (int j = 0; j < image.height; j++)
				for (int i = 0; i < image.width; i++) {
					selection[i][j] = image.selectionMask[i][j];
				}

			numSelected = image.selectedCount;
		}

		public void apply(EditorImage image)
		{
			for (int j = 0; j < image.height; j++)
				for (int i = 0; i < image.width; i++) {
					image.selectionMask[i][j] = selection[i][j];
				}

			image.selectedCount = numSelected;
		}
	}

	public static class RestoreSelection extends AbstractCommand
	{
		private final EditorImage image;
		private final SelectionBackup oldBackup;
		private final SelectionBackup newBackup;

		public RestoreSelection(EditorImage image, SelectionBackup backup)
		{
			super("Modify Selection");
			this.image = image;
			this.oldBackup = backup;
			this.newBackup = new SelectionBackup(image);
		}

		@Override
		public void exec()
		{
			super.exec();
			newBackup.apply(image);
		}

		@Override
		public void undo()
		{
			super.undo();
			oldBackup.apply(image);
		}
	}

	public static class RestoreColor extends AbstractCommand
	{
		private final EditorImage image;
		private final int index;
		private final ColorBackup oldBackup;
		private final ColorBackup newBackup;

		public RestoreColor(EditorImage image, ColorBackup backup, int index)
		{
			super("Modify Palette Color");
			this.image = image;
			this.index = index;
			this.oldBackup = backup;
			this.newBackup = new ColorBackup(image, index);
		}

		@Override
		public void exec()
		{
			super.exec();
			newBackup.apply(image);
			image.editor.setSelectedIndex(index, false);
		}

		@Override
		public void undo()
		{
			super.undo();
			oldBackup.apply(image);
			image.editor.setSelectedIndex(index, false);
		}
	}

	public static class ColorBackup
	{
		private final int index;
		private final int R, G, B, A;

		public ColorBackup(EditorImage image, int index)
		{
			this.index = index;
			Color c = image.palette[index];
			R = c.getRed();
			G = c.getGreen();
			B = c.getBlue();
			A = c.getAlpha();
		}

		public void apply(EditorImage image)
		{
			image.palette[index] = new Color(R, G, B, A);
		}
	}
}
