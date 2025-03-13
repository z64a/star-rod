package game.texture;

import static game.texture.TileFormat.TYPE_CI;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;

import java.awt.Color;
import java.awt.image.DataBuffer;
import java.awt.image.IndexColorModel;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

import renderer.shaders.components.TexUnit1D;

public class Palette
{
	public final byte r[], g[], b[], a[];
	public final int size;

	private boolean glBound = false;
	//	private ByteBuffer glBuffer;
	private int glTexID;

	public Palette(short[] pal)
	{
		this.size = pal.length;

		r = new byte[size];
		g = new byte[size];
		b = new byte[size];
		a = new byte[size];

		for (int i = 0; i < size; i++) {
			Color c = unpackColor(pal[i]);
			r[i] = (byte) c.getRed();
			g[i] = (byte) c.getGreen();
			b[i] = (byte) c.getBlue();
			a[i] = (byte) c.getAlpha();
		}
	}

	public Palette(Color[] pal)
	{
		this.size = pal.length;

		r = new byte[size];
		g = new byte[size];
		b = new byte[size];
		a = new byte[size];

		for (int i = 0; i < size; i++) {
			r[i] = (byte) pal[i].getRed();
			g[i] = (byte) pal[i].getGreen();
			b[i] = (byte) pal[i].getBlue();
			a[i] = (byte) pal[i].getAlpha();
		}
	}

	public Palette(Palette pal)
	{
		this(pal.getColors());
	}

	public static Palette createDefaultForSprite()
	{
		Color[] colors = new Color[16];
		colors[0] = new Color(50, 50, 50, 0);
		for (int i = 1; i < colors.length; i++)
			colors[i] = Color.RED;
		return new Palette(colors);
	}

	public static Palette createDefaultForEditor(int num, float brightness)
	{
		Color[] colors = new Color[num];
		for (int i = 0; i < colors.length; i++)
			colors[i] = Color.getHSBColor((float) i / colors.length, 1.0f, brightness);
		return new Palette(colors);
	}

	public IndexColorModel getIndexColorModel()
	{
		// pack to ARGB color space representation
		int[] cmap = new int[size];
		for (int i = 0; i < size; i++)
			cmap[i] = ((a[i] << 24) & 0xFF000000) | ((r[i] << 16) & 0x00FF0000) | ((g[i] << 8) & 0x0000FF00) | (b[i] & 0x000000FF);

		return new IndexColorModel(8, // bits per pixel
			16, // size of color component array
			cmap,
			0, // offset in the map
			true, // has alpha
			0, // the pixel value that should be transparent
			DataBuffer.TYPE_BYTE);
	}

	public Palette(IndexColorModel colorModel, int size)
	{
		this.size = size;

		r = new byte[size];
		g = new byte[size];
		b = new byte[size];
		a = new byte[size];

		int[] colors = new int[colorModel.getMapSize()];
		colorModel.getRGBs(colors);

		for (int i = 0; i < size; i++) {
			a[i] = (byte) (colors[i] >>> 24);
			r[i] = (byte) (colors[i] >>> 16);
			g[i] = (byte) (colors[i] >>> 8);
			b[i] = (byte) (colors[i]);
		}
	}

	public static Palette read(RandomAccessFile raf, TileFormat fmt) throws IOException
	{
		if (fmt.type != TYPE_CI)
			throw new IllegalArgumentException("Can only read palette for color index image.");

		int paletteSize = 1 << fmt.bpp;

		short[] packed = new short[paletteSize];
		for (int i = 0; i < packed.length; i++)
			packed[i] = raf.readShort();

		return new Palette(packed);
	}

	public static Palette read(ByteBuffer bb, TileFormat fmt)
	{
		if (fmt.type != TYPE_CI)
			throw new IllegalArgumentException("Can only read palette for color index image.");

		int paletteSize = 1 << fmt.bpp;

		short[] packed = new short[paletteSize];
		for (int i = 0; i < packed.length; i++)
			packed[i] = bb.getShort();

		return new Palette(packed);
	}

	public byte[] getPaletteBytes()
	{
		ByteBuffer bb = ByteBuffer.allocateDirect(2 * size);

		for (int i = 0; i < size; i++)
			bb.putShort(packIndex(i));

		bb.rewind();
		byte[] bytes = new byte[bb.limit()];
		bb.get(bytes);

		return bytes;
	}

	public void put(ByteBuffer bb)
	{
		for (int i = 0; i < size; i++)
			bb.putShort(packIndex(i));
	}

	public void write(RandomAccessFile raf, int offset) throws IOException
	{
		raf.seek(offset);
		write(raf);
	}

	public void write(RandomAccessFile raf) throws IOException
	{
		for (int i = 0; i < size; i++)
			raf.writeShort(packIndex(i));
	}

	public Color getColor(int i)
	{
		return new Color(r[i] & 0xFF, g[i] & 0xFF, b[i] & 0xFF, a[i] & 0xFF);
	}

	public Color[] getColors()
	{
		Color[] colors = new Color[size];
		for (int i = 0; i < colors.length; i++)
			colors[i] = getColor(i);
		return colors;
	}

	public void setColor(int index, Color c)
	{
		setColor(index, c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());
	}

	public void setColor(int index, int R, int G, int B, int A)
	{
		setColor(index, (byte) R, (byte) G, (byte) B, (byte) A);
	}

	public void setColor(int index, byte R, byte G, byte B, byte A)
	{
		r[index] = R;
		g[index] = G;
		b[index] = B;
		a[index] = A;
	}

	// Packing and unpacking require converting between a 5-bit color channel
	// and an 8-bit color channel. For these operations to be bitwise reversible,
	// truncate with ceil() during unpack and floor() during pack.
	// packed format: RRRRRGGG GGBBBBBA

	private static Color unpackColor(short s)
	{
		int R = (s >>> 11) & 0x1F;
		int G = (s >>> 6) & 0x1F;
		int B = (s >>> 1) & 0x1F;
		int A = ((s & 1) == 1) ? 255 : 0;
		R = (int) Math.ceil(255 * (R / 31.0));
		G = (int) Math.ceil(255 * (G / 31.0));
		B = (int) Math.ceil(255 * (B / 31.0));
		return new Color(R, G, B, A);
	}

	private short packIndex(int i)
	{
		int R = (int) Math.floor(31 * (r[i] / 255.0));
		int G = (int) Math.floor(31 * (g[i] / 255.0));
		int B = (int) Math.floor(31 * (b[i] / 255.0));

		int color = ((a[i] & 0x80) == 0) ? 0 : 1;
		color |= (R & 0x1F) << 11;
		color |= (G & 0x1F) << 6;
		color |= (B & 0x1F) << 1;

		return (short) color;
	}

	public void glLoad()
	{
		if (glBound)
			glDeleteTextures(glTexID);

		ByteBuffer buffer = ByteBuffer.allocateDirect(4 * 256);
		for (int i = 0; i < size; i++) {
			buffer.put(r[i]);
			buffer.put(g[i]);
			buffer.put(b[i]);
			buffer.put(a[i]);
		}
		buffer.rewind();

		glTexID = glGenTextures();
		glActiveTexture(GL_TEXTURE0);
		glBindTexture(GL_TEXTURE_1D, glTexID);
		glBound = true;

		glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_WRAP_S, GL_REPEAT);
		glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_WRAP_T, GL_REPEAT);
		glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

		glTexImage1D(GL_TEXTURE_1D, 0, GL_RGBA8, 256, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
	}

	public void glReload()
	{
		if (!glBound) {
			glLoad();
			return;
		}

		ByteBuffer buffer = ByteBuffer.allocateDirect(4 * 256);
		for (int i = 0; i < size; i++) {
			buffer.put(r[i]);
			buffer.put(g[i]);
			buffer.put(b[i]);
			buffer.put(a[i]);
		}
		buffer.rewind();

		glActiveTexture(GL_TEXTURE0);
		glBindTexture(GL_TEXTURE_1D, glTexID);
		glTexImage1D(GL_TEXTURE_1D, 0, GL_RGBA8, 256, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
	}

	public void glBind(int texUnit)
	{
		glActiveTexture(texUnit);
		glBindTexture(GL_TEXTURE_1D, glTexID);
	}

	public void glBind(TexUnit1D unit)
	{
		unit.bind(glTexID);
	}

	public void glDelete()
	{
		if (glBound)
			glDeleteTextures(glTexID);
	}
}
