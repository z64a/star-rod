package game.texture;

import static game.texture.Texture.*;
import static game.texture.TileFormat.CI_4;
import static game.texture.TileFormat.TYPE_CI;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL14.GL_MIRRORED_REPEAT;
import static org.lwjgl.opengl.GL30.*;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.Arrays;

import javax.imageio.ImageIO;

import org.apache.commons.io.FileUtils;

import app.StarRodException;
import ar.com.hjg.pngj.ImageInfo;
import ar.com.hjg.pngj.ImageLineByte;
import ar.com.hjg.pngj.PngWriter;
import ar.com.hjg.pngj.chunks.PngChunkPLTE;
import ar.com.hjg.pngj.chunks.PngChunkTRNS;
import game.texture.ImageConverter.ImageFormatException;
import renderer.shaders.components.TexUnit2D;

public class Tile
{
	public final TileFormat format;
	public final int height, width;

	public final ByteBuffer raster;
	public Palette palette;

	private boolean glLoaded = false;
	//	private ByteBuffer glBuffer;
	private int glTexID;

	public Tile(TileFormat fmt, int h, int w)
	{
		format = fmt;
		height = h;
		width = w;

		raster = ByteBuffer.allocateDirect(w * h * format.bpp >> 3);
		palette = null;
	}

	public Tile(Tile original)
	{
		format = original.format;
		height = original.height;
		width = original.width;

		// copy raster
		raster = ByteBuffer.allocate(original.raster.capacity());
		original.raster.rewind();
		raster.put(original.raster);
		original.raster.rewind();
		raster.flip();

		palette = new Palette(original.palette);
	}

	public Tile doubleSize()
	{
		Tile doubled = new Tile(format, height * 2, width * 2);

		// copy raster
		doubled.raster.rewind();
		raster.rewind();
		for (int y = 0; y < height; y++) {
			int position = raster.position();
			for (int z = 0; z < 2; z++) // double rows
			{
				raster.position(position);
				for (int x = 0; x < width; x++) {
					switch (format.bpp) {
						case 4: //  4 bpp
							int packed = raster.get() & 0xFF;
							int upper = (packed >> 4);
							int lower = (packed & 0xF);
							x++;
							doubled.raster.put((byte) (upper << 4 | upper));
							doubled.raster.put((byte) (lower << 4 | lower));
							break;
						case 8: //  8 bpp
							byte px8 = raster.get();
							doubled.raster.put(px8);
							doubled.raster.put(px8);
							break;
						case 16: // 16 bpp
							short px16 = raster.getShort();
							doubled.raster.putShort(px16);
							doubled.raster.putShort(px16);
							break;
						case 32: // 32 bpp
							int px32 = raster.getInt();
							doubled.raster.putInt(px32);
							doubled.raster.putInt(px32);
							break;
					}

				}
			}
		}
		raster.rewind();
		doubled.raster.flip();

		doubled.palette = new Palette(palette);
		return doubled;
	}

	public void readImage(RandomAccessFile raf, int offset, boolean flip) throws IOException
	{
		raf.seek(offset);
		readImage(raf, flip);
	}

	public void readImage(RandomAccessFile raf, boolean flip) throws IOException
	{
		byte[] bytes = new byte[raster.limit()];
		raf.read(bytes);

		if (!flip) {
			raster.put(bytes);
			return;
		}

		int rowsize = width * format.bpp >> 3;
		for (int row = height - 1; row >= 0; row--)
			raster.put(bytes, rowsize * row, rowsize);
	}

	public void readImage(ByteBuffer bb, int offset, boolean flip)
	{
		bb.position(offset);
		readImage(bb, flip);
	}

	public void readImage(ByteBuffer bb, boolean flip)
	{
		byte[] bytes = new byte[raster.limit()];
		bb.get(bytes);

		if (!flip) {
			raster.put(bytes);
			return;
		}

		int rowsize = width * format.bpp >> 3;
		for (int row = height - 1; row >= 0; row--)
			raster.put(bytes, rowsize * row, rowsize);
	}

	public void readPalette(RandomAccessFile raf, int offset) throws IOException
	{
		raf.seek(offset);
		readPalette(raf);
	}

	public void readPalette(RandomAccessFile raf) throws IOException
	{
		palette = Palette.read(raf, format);
	}

	public void readPalette(ByteBuffer bb, int offset)
	{
		bb.position(offset);
		readPalette(bb);
	}

	public void readPalette(ByteBuffer bb)
	{
		palette = Palette.read(bb, format);
	}

	public byte[] getRasterBytes()
	{
		raster.rewind();
		byte[] bytes = new byte[raster.limit()];
		raster.get(bytes);
		return bytes;
	}

	public void putRaster(ByteBuffer bb, boolean flip)
	{
		raster.rewind();
		byte[] bytes = new byte[raster.limit()];
		raster.get(bytes);

		if (!flip) {
			bb.put(bytes);
			return;
		}

		int rowsize = width * format.bpp >> 3;
		for (int row = height - 1; row >= 0; row--)
			bb.put(bytes, rowsize * row, rowsize);
	}

	public void writeRaster(RandomAccessFile raf, int offset, boolean flip) throws IOException
	{
		raf.seek(offset);
		writeRaster(raf, flip);
	}

	public void writeRaster(RandomAccessFile raf, boolean flip) throws IOException
	{
		raster.rewind();
		byte[] bytes = new byte[raster.limit()];
		raster.get(bytes);

		if (!flip) {
			raf.write(bytes);
			return;
		}

		int rowsize = width * format.bpp >> 3;
		for (int row = height - 1; row >= 0; row--)
			raf.write(bytes, rowsize * row, rowsize);
	}

	public static Tile load(String filename, TileFormat format) throws IOException
	{
		return load(new File(filename), format, false);
	}

	public static Tile load(File in, TileFormat format) throws IOException
	{
		return load(in, format, false);
	}

	public static Tile load(String filename, TileFormat format, boolean convert) throws IOException
	{
		return load(new File(filename), format, convert);
	}

	public static Tile load(File in, TileFormat format, boolean convert)
	{
		BufferedImage bimg;
		Tile tile = null;
		try {
			bimg = ImageIO.read(in);
			tile = ImageConverter.getTile(bimg, format);
		}
		catch (IOException e) {
			throw new StarRodException("Exception loading image: %s %n%s", in.getAbsolutePath(), e.getMessage());
		}
		catch (ImageFormatException e) {
			throw new ImageFormatException(e.getMessage() + "\n" + in.getAbsolutePath());
		}
		return tile;
	}

	public void savePNG(String filename) throws IOException
	{
		if (!filename.endsWith(".png"))
			filename += ".png";
		File out = new File(filename);
		FileUtils.touch(out);

		if (format.type == TYPE_CI) {
			saveColorIndexedPNG(out);
			return;
		}

		BufferedImage bimg = ImageConverter.convertToBufferedImage(this);
		ImageIO.write(bimg, "png", out);

		/*
		Image img = load(filename, format);
		raster.rewind();
		img.raster.rewind();
		while(raster.hasRemaining())
		{
			byte b1 = raster.get();
			byte b2 = img.raster.get();
			//System.out.printf("%02X -> %02X%n", b1, b2);
			assert(b1 == b2);
		}
		// assert(raster.equals(img.raster));
		 */
	}

	/**
	 * Saves this {@link Tile} as an color-indexed PNG using the PNGJ library. This
	 * is necessary because the default ImageIO library automatically reorders the
	 * color palette, which breaks the ability to make palette swaps. All color-index
	 * formats are saved with 256 colors palettes, since most image editing software
	 * pad the palette to 256 anyway. This convention MUST be taked into consideration
	 * when color-indexed files are loaded.
	 * @param out
	 */
	private void saveColorIndexedPNG(File out)
	{
		raster.rewind();

		int paletteSize = 1 << format.bpp;
		ImageInfo info = new ImageInfo(width, height, 8, false, false, true); // 8 -> format.bpp

		PngWriter writer = new PngWriter(out, info);
		writer.setCompLevel(9);

		PngChunkPLTE paletteChunk = new PngChunkPLTE(info);
		paletteChunk.setNentries(256); // paletteSize
		for (int i = 0; i < paletteSize; i++)
			paletteChunk.setEntry(i, (palette.r[i] & 0xFF), (palette.g[i] & 0xFF), (palette.b[i] & 0xFF));

		PngChunkTRNS alphaChunk = new PngChunkTRNS(info);
		alphaChunk.setNentriesPalAlpha(256); // paletteSize
		for (int i = 0; i < paletteSize; i++)
			alphaChunk.setEntryPalAlpha(i, (palette.a[i] & 0xFF));

		writer.getMetadata().queueChunk(paletteChunk);
		writer.getMetadata().queueChunk(alphaChunk);

		for (int i = 0; i < height; i++) {
			ImageLineByte line = new ImageLineByte(info);

			byte[] scanline = line.getScanlineByte();
			for (int j = 0; j < width;) {
				byte b = raster.get();

				if (format == CI_4) {
					scanline[j++] = (byte) ((b >>> 4) & 0xF);
					scanline[j++] = (byte) (b & 0xF);
				}
				else {
					scanline[j++] = b;
				}
			}
			writer.writeRow(line, i);
		}
		writer.end();
	}

	public static BufferedImage readTGA(File file) throws IOException
	{
		if (!file.exists())
			throw new FileNotFoundException(file.getAbsolutePath());

		byte[] header = new byte[18];
		int len = (int) file.length() - header.length;
		if (len < 0)
			throw new IllegalStateException("file not big enough to contain header: " + file.getAbsolutePath());
		byte[] data = new byte[len];

		RandomAccessFile raf = new RandomAccessFile(file, "r");
		raf.read(header);
		raf.read(data);
		raf.close();

		if ((header[0] | header[1]) != 0)
			throw new IllegalStateException(file.getAbsolutePath());
		if (header[2] != 2)
			throw new IllegalStateException(file.getAbsolutePath());
		int w = 0, h = 0;
		w |= (header[12] & 0xFF) << 0;
		w |= (header[13] & 0xFF) << 8;
		h |= (header[14] & 0xFF) << 0;
		h |= (header[15] & 0xFF) << 8;

		boolean alpha;
		if ((w * h) * 3 == data.length)
			alpha = false;
		else if ((w * h) * 4 == data.length)
			alpha = true;
		else
			throw new IllegalStateException(file.getAbsolutePath());
		if (!alpha && (header[16] != 24))
			throw new IllegalStateException(file.getAbsolutePath());
		if (alpha && (header[16] != 32))
			throw new IllegalStateException(file.getAbsolutePath());
		if ((header[17] & 15) != (alpha ? 8 : 0))
			throw new IllegalStateException(file.getAbsolutePath());

		BufferedImage dst = new BufferedImage(w, h, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
		int[] pixels = ((DataBufferInt) dst.getRaster().getDataBuffer()).getData();
		if (pixels.length != w * h)
			throw new IllegalStateException(file.getAbsolutePath());
		if (data.length != pixels.length * (alpha ? 4 : 3))
			throw new IllegalStateException(file.getAbsolutePath());

		if (alpha) {
			for (int i = 0, p = (pixels.length - 1) * 4; i < pixels.length; i++, p -= 4) {
				pixels[i] |= ((data[p + 0]) & 0xFF) << 0;
				pixels[i] |= ((data[p + 1]) & 0xFF) << 8;
				pixels[i] |= ((data[p + 2]) & 0xFF) << 16;
				pixels[i] |= ((data[p + 3]) & 0xFF) << 24;
			}
		}
		else {
			for (int i = 0, p = (pixels.length - 1) * 3; i < pixels.length; i++, p -= 3) {
				pixels[i] |= ((data[p + 0]) & 0xFF) << 0;
				pixels[i] |= ((data[p + 1]) & 0xFF) << 8;
				pixels[i] |= ((data[p + 2]) & 0xFF) << 16;
			}
		}

		if ((header[17] >> 4) == 1) {
			// ok
		}
		else if ((header[17] >> 4) == 0) {
			// flip horizontally

			for (int y = 0; y < h; y++) {
				int w2 = w / 2;
				for (int x = 0; x < w2; x++) {
					int a = (y * w) + x;
					int b = (y * w) + (w - 1 - x);
					int t = pixels[a];
					pixels[a] = pixels[b];
					pixels[b] = t;
				}
			}
		}
		else
			throw new UnsupportedOperationException(file.getAbsolutePath());

		return dst;
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + format.hashCode();
		result = prime * result + height;
		result = prime * result + Arrays.hashCode(getRasterBytes());
		return prime * result + width;
	}

	public void glLoad(int hWrap, int vWrap, boolean mipmaps)
	{
		if (glLoaded)
			glDeleteTextures(glTexID);

		ByteBuffer buffer = ImageConverter.convertToGLBuffer(this);
		buffer.rewind();

		glTexID = glGenTextures();
		glActiveTexture(GL_TEXTURE0);
		glBindTexture(GL_TEXTURE_2D, glTexID);
		glLoaded = true;

		int hWrapMode;
		switch (hWrap) {
			default:
			case WRAP_REPEAT:
				hWrapMode = GL_REPEAT;
				break;
			case WRAP_MIRROR:
				hWrapMode = GL_MIRRORED_REPEAT;
				break;
			case WRAP_CLAMP:
				hWrapMode = (format.type == TileFormat.TYPE_IA) ? GL_CLAMP_TO_BORDER : GL_CLAMP_TO_EDGE;
				break; // handle edge cases without having the original display lists
		}

		int vWrapMode;
		switch (vWrap) {
			default:
			case WRAP_REPEAT:
				vWrapMode = GL_REPEAT;
				break;
			case WRAP_MIRROR:
				vWrapMode = GL_MIRRORED_REPEAT;
				break;
			case WRAP_CLAMP:
				vWrapMode = (format.type == TileFormat.TYPE_IA) ? GL_CLAMP_TO_BORDER : GL_CLAMP_TO_EDGE;
				break; // handle edge cases without having the original display lists
		}

		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, hWrapMode);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, vWrapMode);

		if (mipmaps)
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_LINEAR);
		else
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);

		// n64 three-point filtering is done in the shader
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

		/*
		GL_TEXTURE_MIN_FILTER:

		GL_NEAREST_MIPMAP_NEAREST
		GL_NEAREST_MIPMAP_LINEAR
		GL_LINEAR_MIPMAP_NEAREST
		GL_LINEAR_MIPMAP_LINEAR
		 */

		switch (format.glStride) {
			case 1:
				glTexImage2D(GL_TEXTURE_2D, 0, GL_R8, width, height, 0, GL_RED, GL_UNSIGNED_BYTE, buffer);
				break;
			case 2:
				glTexImage2D(GL_TEXTURE_2D, 0, GL_RG8, width, height, 0, GL_RG, GL_UNSIGNED_BYTE, buffer);
				break;
			case 4:
				glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
				break;
			default:
				throw new UnsupportedOperationException("Unsupported format " + format);
		}
	}

	public void glMipmap(int level)
	{
		ByteBuffer buffer = ImageConverter.convertToGLBuffer(this);
		buffer.rewind();

		switch (format.glStride) {
			case 1:
				glTexImage2D(GL_TEXTURE_2D, level, GL_R8, width, height, 0, GL_RED, GL_UNSIGNED_BYTE, buffer);
				break;
			case 2:
				glTexImage2D(GL_TEXTURE_2D, level, GL_RG8, width, height, 0, GL_RG, GL_UNSIGNED_BYTE, buffer);
				break;
			case 4:
				glTexImage2D(GL_TEXTURE_2D, level, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
				break;
			default:
				throw new UnsupportedOperationException("Unsupported format " + format);
		}
	}

	public void glBind(int texUnit)
	{
		glActiveTexture(texUnit);
		glBindTexture(GL_TEXTURE_2D, glTexID);
	}

	public void glBind(TexUnit2D unit)
	{
		unit.bind(glTexID);
	}

	public void glDelete()
	{
		if (glLoaded)
			glDeleteTextures(glTexID);
	}

	public static Tile getSpritePaletteImage()
	{
		Palette pal = Palette.createDefaultForSprite();
		Tile t = new Tile(TileFormat.CI_4, 4, 64);
		for (int i = 0; i < 4; i++)
			for (int j = 0; j < 16; j++) {
				t.raster.put((byte) (j << 4 | j));
				t.raster.put((byte) (j << 4 | j));
			}
		t.palette = pal;
		return t;
	}
}
