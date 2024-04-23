package game.texture;

import static game.texture.TileFormat.*;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.IndexColorModel;
import java.nio.ByteBuffer;

public class ImageConverter
{
	/**
	 * Creates ARGB BufferedImage from PM64 Image
	 */
	public static BufferedImage convertToBufferedImage(Tile img)
	{
		switch (img.format) {
			case RGBA_16:
			case RGBA_32:
				return getImage_RGBA(img);
			case IA_4:
			case IA_8:
			case IA_16:
				return getImage_IA(img);
			case I_4:
			case I_8:
				return getImage_I(img);
			case CI_4:
			case CI_8:
				return getImage_CI(img);
			case YUV_16:
			default:
				throw new UnsupportedOperationException("Unsupported format " + img.format);
		}
	}

	public static ByteBuffer unpack(Tile img)
	{
		switch (img.format) {
			case RGBA_16:
				return unpack_RGBA_16bpp(img);
			case RGBA_32:
				return unpack_RGBA_32bpp(img);
			case IA_4:
				return unpack_IA_4bpp(img);
			case IA_8:
				return unpack_IA_8bpp(img);
			case IA_16:
				return unpack_IA_16bpp(img);
			case I_4:
				return unpack_I_4bpp(img);
			case I_8:
				return unpack_I_8bpp(img);
			case CI_4:
				return unpack_CI_4bpp(img);
			case CI_8:
				return unpack_CI_8bpp(img);
			case YUV_16:
			default:
				throw new UnsupportedOperationException("Unsupported format " + img.format);
		}
	}

	public static ByteBuffer pack(Tile img, ByteBuffer unpacked)
	{
		switch (img.format) {
			case RGBA_16:
				return unpack_RGBA_16bpp(img);
			case RGBA_32:
				return unpack_RGBA_32bpp(img);
			case IA_4:
				return unpack_IA_4bpp(img);
			case IA_8:
				return unpack_IA_8bpp(img);
			case IA_16:
				return unpack_IA_16bpp(img);
			case I_4:
				return unpack_I_4bpp(img);
			case I_8:
				return unpack_I_8bpp(img);
			case CI_4:
				return unpack_CI_4bpp(img);
			case CI_8:
				return unpack_CI_8bpp(img);
			case YUV_16:
			default:
				throw new UnsupportedOperationException("Unsupported format " + img.format);
		}
	}

	public static ByteBuffer pack(Tile img)
	{
		switch (img.format) {
			case RGBA_16:
				return unpack_RGBA_16bpp(img);
			case RGBA_32:
				return unpack_RGBA_32bpp(img);
			case IA_4:
				return unpack_IA_4bpp(img);
			case IA_8:
				return unpack_IA_8bpp(img);
			case IA_16:
				return unpack_IA_16bpp(img);
			case I_4:
				return unpack_I_4bpp(img);
			case I_8:
				return unpack_I_8bpp(img);
			case CI_4:
				return unpack_CI_4bpp(img);
			case CI_8:
				return unpack_CI_8bpp(img);
			case YUV_16:
			default:
				throw new UnsupportedOperationException("Unsupported format " + img.format);
		}
	}

	public static ByteBuffer convertToGLBuffer(Tile img)
	{
		ByteBuffer bb = unpack(img);

		// opengl textures start in lower left corner, so we need to flip vertically
		bb.rewind();
		ByteBuffer flipped = ByteBuffer.allocateDirect(bb.limit());

		int rowSize = img.width * img.format.glStride;
		for (int row = img.height - 1; row >= 0; row--) {
			byte[] rowBytes = new byte[rowSize];
			bb.position(row * rowSize);
			bb.get(rowBytes);
			flipped.put(rowBytes);
		}

		return flipped;
	}

	/**
	 * Creates PM64 Image from ARGB BufferedImage
	 */
	public static Tile getTile(BufferedImage bimg, TileFormat fmt)
	{
		switch (fmt) {
			case RGBA_16:
				return getTile_RGBA_16bpp(bimg);
			case RGBA_32:
				return getTile_RGBA_32bpp(bimg);
			case IA_4:
				return getTile_IA_4bpp(bimg);
			case IA_8:
				return getTile_IA_8bpp(bimg);
			case IA_16:
				return getTile_IA_16bpp(bimg);
			case I_4:
				return getTile_I_4bpp(bimg);
			case I_8:
				return getTile_I_8bpp(bimg);
			case CI_4:
				return convert_CI(bimg, fmt);
			case CI_8:
				return convert_CI(bimg, fmt);
			case YUV_16:
			default:
				throw new UnsupportedOperationException("Unsupported format " + fmt);
		}
	}

	private static Tile convert_CI(BufferedImage bimg, TileFormat format)
	{
		// only stricty true for CI_8. if we used 16 color palettes for CI_4, its type would be TYPE_BYTE_BINARY.
		if (format.type == TYPE_CI && bimg.getType() != BufferedImage.TYPE_BYTE_INDEXED)
			throw new ImageFormatException("Image is not color index format!");

		Tile img = new Tile(format, bimg.getHeight(), bimg.getWidth());

		DataBufferByte dataBuffer = (DataBufferByte) (bimg.getRaster().getDataBuffer());
		byte data[] = dataBuffer.getData();

		if (format == CI_4 && img.width % 2 != 0)
			throw new ImageFormatException(CI_4 + " image width must be a multiple of 2!");
		//	if(format == CI_8 && img.width % 8 != 0)
		//		throw new ImageFormatException(CI_8 + " image width must be a multiple of 8!");

		img.raster.rewind();
		int k = 0;
		for (int y = 0; y < img.height; y++)
			for (int x = 0; x < img.width; x++) {
				if (format == CI_4) {
					byte i = data[k++];
					byte j = data[k++];
					img.raster.put((byte) (i << 4 | j));
					x++;
				}
				else {
					img.raster.put(data[k++]);
				}
			}

		int paletteSize = 1 << format.bpp;

		IndexColorModel colorModel = (IndexColorModel) bimg.getColorModel();
		img.palette = new Palette(colorModel, paletteSize);

		return img;
	}

	private static ByteBuffer unpack_RGBA_16bpp(Tile img)
	{
		int stride = 4; // R, G, B, alpha
		ByteBuffer bb = ByteBuffer.allocateDirect(stride * img.width * img.height);

		img.raster.rewind();
		for (int y = 0; y < img.height; y++)
			for (int x = 0; x < img.width; x++) {
				Color c = unpackColor(img.raster.getShort());
				bb.put((byte) c.getRed());
				bb.put((byte) c.getGreen());
				bb.put((byte) c.getBlue());
				bb.put((byte) c.getAlpha());
			}

		return bb;
	}

	private static ByteBuffer unpack_RGBA_32bpp(Tile img)
	{
		int stride = 4; // R, G, B, alpha
		ByteBuffer bb = ByteBuffer.allocateDirect(stride * img.width * img.height);

		img.raster.rewind();
		for (int y = 0; y < img.height; y++)
			for (int x = 0; x < img.width; x++) {
				int v = img.raster.getInt();
				int R = (v >>> 24) & 0xFF;
				int G = (v >>> 16) & 0xFF;
				int B = (v >>> 8) & 0xFF;
				int A = v & 0xFF;

				bb.put((byte) R);
				bb.put((byte) G);
				bb.put((byte) B);
				bb.put((byte) A);
			}

		return bb;
	}

	private static BufferedImage getImage_RGBA(Tile img)
	{
		ByteBuffer bb;
		if (img.format == RGBA_16)
			bb = unpack_RGBA_16bpp(img);
		else if (img.format == RGBA_32)
			bb = unpack_RGBA_32bpp(img);
		else
			throw new IllegalStateException("Image is not RGBA format: " + img.format);

		BufferedImage tex = new BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_ARGB);

		bb.rewind();
		for (int y = 0; y < img.height; y++)
			for (int x = 0; x < img.width; x++) {
				int R = (bb.get() & 0xFF);
				int G = (bb.get() & 0xFF);
				int B = (bb.get() & 0xFF);
				int A = (bb.get() & 0xFF);

				Color c = new Color(R, G, B, A);

				//		if(c.getAlpha() != 255)
				//			img.alpha = AlphaMode.TRANSLUCENT;

				tex.setRGB(x, y, c.getRGB());
			}

		return tex;
	}

	private static Tile getTile_RGBA_16bpp(BufferedImage bimg)
	{
		Tile img = new Tile(RGBA_16, bimg.getHeight(), bimg.getWidth());
		img.raster.rewind();

		for (int y = 0; y < img.height; y++)
			for (int x = 0; x < img.width; x++) {
				Color c = new Color(bimg.getRGB(x, y), true);

				int R = (int) Math.floor(31 * (c.getRed() / 255.0));
				int G = (int) Math.floor(31 * (c.getGreen() / 255.0));
				int B = (int) Math.floor(31 * (c.getBlue() / 255.0));

				int color = ((c.getAlpha() & 0x80) == 0) ? 0 : 1;
				color |= (R & 0x1F) << 11;
				color |= (G & 0x1F) << 6;
				color |= (B & 0x1F) << 1;

				img.raster.putShort((short) color);
			}

		return img;
	}

	private static Tile getTile_RGBA_32bpp(BufferedImage bimg)
	{
		Tile img = new Tile(RGBA_32, bimg.getHeight(), bimg.getWidth());
		img.raster.rewind();

		for (int y = 0; y < img.height; y++)
			for (int x = 0; x < img.width; x++) {
				Color c = new Color(bimg.getRGB(x, y), true);

				int v = c.getAlpha();
				v = c.getBlue() << 8 | v;
				v = c.getGreen() << 16 | v;
				v = c.getRed() << 24 | v;

				img.raster.putInt(v);
			}

		return img;
	}

	/**
	 * Gets a bytebuffer for an IA image.
	 * Texel format: [intensity] [alpha]
	 */
	private static ByteBuffer unpack_IA_4bpp(Tile img)
	{
		int stride = 2; // intensity, alpha
		ByteBuffer bb = ByteBuffer.allocateDirect(stride * img.width * img.height);

		img.raster.rewind();
		for (int y = 0; y < img.height; y++)
			for (int x = 0; x < img.width; x += 2) {
				byte b = img.raster.get();
				byte h = (byte) (b >>> 4 & 0x0F);
				byte l = (byte) (b & 0x0F);

				int I1 = (h >>> 1) & 0x0F;
				int A1 = ((h & 0x01) == 1) ? 255 : 0;
				I1 = (int) Math.ceil(255 * (I1 / 7.0));

				int I2 = (l >>> 1) & 0x0F;
				int A2 = ((l & 0x01) == 1) ? 255 : 0;
				I2 = (int) Math.ceil(255 * (I2 / 7.0));

				bb.put((byte) I1);
				bb.put((byte) A1);
				bb.put((byte) I2);
				bb.put((byte) A2);
			}

		return bb;
	}

	/**
	 * Gets a bytebuffer for an IA image.
	 * Texel format: [intensity] [alpha]
	 */
	private static ByteBuffer unpack_IA_8bpp(Tile img)
	{
		int stride = 2; // intensity, alpha
		ByteBuffer bb = ByteBuffer.allocateDirect(stride * img.width * img.height);

		img.raster.rewind();
		for (int y = 0; y < img.height; y++)
			for (int x = 0; x < img.width; x++) {
				byte b = img.raster.get();
				int I = (b >>> 4) & 0x0F;
				int A = b & 0x0F;

				I = (int) Math.ceil(255 * (I / 15.0));
				A = (int) Math.ceil(255 * (A / 15.0));

				bb.put((byte) I);
				bb.put((byte) A);
			}

		return bb;
	}

	/**
	 * Gets a bytebuffer for an IA image.
	 * Texel format: [intensity] [alpha]
	 */
	private static ByteBuffer unpack_IA_16bpp(Tile img)
	{
		int stride = 2; // intensity, alpha
		ByteBuffer bb = ByteBuffer.allocateDirect(stride * img.width * img.height);

		img.raster.rewind();
		for (int y = 0; y < img.height; y++)
			for (int x = 0; x < img.width; x++) {
				bb.put(img.raster.get());
				bb.put(img.raster.get());
			}

		return bb;
	}

	private static BufferedImage getImage_IA(Tile img)
	{
		ByteBuffer bb;
		if (img.format == IA_4)
			bb = unpack_IA_4bpp(img);
		else if (img.format == IA_8)
			bb = unpack_IA_8bpp(img);
		else if (img.format == IA_16)
			bb = unpack_IA_16bpp(img);
		else
			throw new IllegalStateException("Image is not IA format: " + img.format);

		BufferedImage tex = new BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_ARGB);

		bb.rewind();
		for (int y = 0; y < img.height; y++)
			for (int x = 0; x < img.width; x++) {
				int I = (bb.get() & 0xFF);
				int A = (bb.get() & 0xFF);

				//			if(A != 255)
				//				img.alpha = AlphaMode.TRANSLUCENT;

				Color c = new Color(I, I, I, A);
				tex.setRGB(x, y, c.getRGB());
			}

		return tex;
	}

	/**
	 * Automatically converts to 'dumb' intensity: I = (R + G + B) / 3<BR>
	 * Better would be I = 0.2126 R + 0.7152 G + 0.0722 B<BR>
	 * Alpha mask uses 50% threshold.
	 */
	private static Tile getTile_IA_4bpp(BufferedImage bimg)
	{
		Tile img = new Tile(IA_4, bimg.getHeight(), bimg.getWidth());
		img.raster.rewind();

		for (int y = 0; y < img.height; y++)
			for (int x = 0; x < img.width;) {
				Color c1 = new Color(bimg.getRGB(x++, y), true);
				Color c2 = new Color(bimg.getRGB(x++, y), true);

				int I1 = (c1.getRed() + c1.getBlue() + c1.getGreen()) / 3;
				int A1 = c1.getAlpha();

				int I2 = (c2.getRed() + c2.getBlue() + c2.getGreen()) / 3;
				int A2 = c2.getAlpha();

				I1 = (int) Math.floor(7 * (I1 / 255.0));
				I2 = (int) Math.floor(7 * (I2 / 255.0));

				A1 = (A1 > 128) ? 1 : 0;
				A2 = (A2 > 128) ? 1 : 0;

				byte h = (byte) (I1 << 1 | A1);
				byte l = (byte) (I2 << 1 | A2);

				img.raster.put((byte) (h << 4 | l));
			}

		return img;
	}

	/**
	 * Automatically converts to 'dumb' intensity: I = (R + G + B) / 3<BR>
	 * Better would be I = 0.2126 R + 0.7152 G + 0.0722 B
	 */
	private static Tile getTile_IA_8bpp(BufferedImage bimg)
	{
		Tile img = new Tile(IA_8, bimg.getHeight(), bimg.getWidth());

		for (int y = 0; y < img.height; y++)
			for (int x = 0; x < img.width; x++) {
				Color c = new Color(bimg.getRGB(x, y), true);

				int I = (c.getRed() + c.getBlue() + c.getGreen()) / 3;
				int A = c.getAlpha();

				I = (int) Math.floor(15 * (I / 255.0));
				A = (int) Math.floor(15 * (A / 255.0));

				byte b = (byte) (I << 4 | A);
				img.raster.put(b);
			}

		return img;
	}

	/**
	 * Automatically converts to 'dumb' intensity: I = (R + G + B) / 3<BR>
	 * Better would be I = 0.2126 R + 0.7152 G + 0.0722 B
	 */
	private static Tile getTile_IA_16bpp(BufferedImage bimg)
	{
		Tile img = new Tile(IA_16, bimg.getHeight(), bimg.getWidth());
		img.raster.rewind();

		for (int y = 0; y < img.height; y++)
			for (int x = 0; x < img.width; x++) {
				Color c = new Color(bimg.getRGB(x, y), true);

				int I = (c.getRed() + c.getBlue() + c.getGreen()) / 3;
				int A = c.getAlpha();

				img.raster.put((byte) I);
				img.raster.put((byte) A);
			}

		return img;
	}

	/**
	 * Gets a bytebuffer for an I image.
	 * Texel format: [intensity]
	 */
	private static ByteBuffer unpack_I_4bpp(Tile img)
	{
		int stride = 1; // intensity
		ByteBuffer bb = ByteBuffer.allocateDirect(stride * img.width * img.height);

		img.raster.rewind();
		for (int y = 0; y < img.height; y++)
			for (int x = 0; x < img.width; x += 2) {
				byte b = img.raster.get();
				int I1 = (b >>> 4) & 0x0F;
				int I2 = b & 0x0F;
				I1 = (int) Math.ceil(255 * (I1 / 15.0));
				I2 = (int) Math.ceil(255 * (I2 / 15.0));

				bb.put((byte) I1);
				bb.put((byte) I2);
			}

		return bb;
	}

	/**
	 * Gets a bytebuffer for an I image.
	 * Texel format: [intensity]
	 */
	private static ByteBuffer unpack_I_8bpp(Tile img)
	{
		int stride = 1; // intensity
		ByteBuffer bb = ByteBuffer.allocateDirect(stride * img.width * img.height);

		img.raster.rewind();
		for (int y = 0; y < img.height; y++)
			for (int x = 0; x < img.width; x++) {
				bb.put(img.raster.get());
			}

		return bb;
	}

	private static BufferedImage getImage_I(Tile img)
	{
		ByteBuffer bb;
		if (img.format == I_4)
			bb = unpack_I_4bpp(img);
		else if (img.format == I_8)
			bb = unpack_I_8bpp(img);
		else
			throw new IllegalStateException("Image is not I format: " + img.format);

		BufferedImage tex = new BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_ARGB);

		bb.rewind();
		for (int y = 0; y < img.height; y++)
			for (int x = 0; x < img.width; x++) {
				int I = (bb.get() & 0xFF);
				Color c = new Color(I, I, I, 255);
				tex.setRGB(x, y, c.getRGB());
			}

		return tex;
	}

	/**
	 * Automatically converts to 'dumb' intensity: I = (R + G + B) / 3<BR>
	 * Better would be I = 0.2126 R + 0.7152 G + 0.0722 B<BR>
	 */
	private static Tile getTile_I_4bpp(BufferedImage bimg)
	{
		Tile img = new Tile(I_4, bimg.getHeight(), bimg.getWidth());

		img.raster.rewind();
		for (int y = 0; y < img.height; y++)
			for (int x = 0; x < img.width;) {
				Color c1 = new Color(bimg.getRGB(x++, y), true);
				Color c2 = new Color(bimg.getRGB(x++, y), true);

				int I1 = (c1.getRed() + c1.getBlue() + c1.getGreen()) / 3;
				int I2 = (c2.getRed() + c2.getBlue() + c2.getGreen()) / 3;

				I1 = (int) Math.floor(15 * (I1 / 255.0));
				I2 = (int) Math.floor(15 * (I2 / 255.0));

				img.raster.put((byte) (I1 << 4 | I2));
			}

		return img;
	}

	/**
	 * Automatically converts to 'dumb' intensity: I = (R + G + B) / 3<BR>
	 * Better would be I = 0.2126 R + 0.7152 G + 0.0722 B<BR>
	 */
	private static Tile getTile_I_8bpp(BufferedImage bimg)
	{
		Tile img = new Tile(I_8, bimg.getHeight(), bimg.getWidth());

		img.raster.rewind();
		for (int y = 0; y < img.height; y++)
			for (int x = 0; x < img.width; x++) {
				Color c = new Color(bimg.getRGB(x, y), true);
				int I = (c.getRed() + c.getBlue() + c.getGreen()) / 3;
				img.raster.put((byte) I);
			}

		return img;
	}

	/**
	 * Gets a bytebuffer for a CI image.
	 * Texel format: [index]
	 */
	private static ByteBuffer unpack_CI_4bpp(Tile img)
	{
		int stride = 1; // index
		ByteBuffer bb = ByteBuffer.allocateDirect(stride * img.width * img.height);

		img.raster.rewind();
		for (int y = 0; y < img.height; y++)
			for (int x = 0; x < img.width; x += 2) {
				byte b = img.raster.get();
				int i = (b >>> 4) & 0x0F;
				int j = b & 0x0F;

				bb.put((byte) i);
				bb.put((byte) j);
			}

		return bb;
	}

	/**
	 * Gets a bytebuffer for a CI image.
	 * Texel format: [index]
	 */
	private static ByteBuffer unpack_CI_8bpp(Tile img)
	{
		int stride = 1; // index
		ByteBuffer bb = ByteBuffer.allocateDirect(stride * img.width * img.height);

		img.raster.rewind();
		for (int y = 0; y < img.height; y++)
			for (int x = 0; x < img.width; x++) {
				bb.put(img.raster.get());
			}

		return bb;
	}

	private static BufferedImage getImage_CI(Tile img)
	{
		ByteBuffer bb;
		if (img.format == CI_4)
			bb = unpack_CI_4bpp(img);
		else if (img.format == CI_8)
			bb = unpack_CI_8bpp(img);
		else
			throw new IllegalStateException("Image is not CI format: " + img.format);

		BufferedImage tex = new BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_ARGB);
		Color[] colors = img.palette.getColors();

		bb.rewind();
		for (int y = 0; y < img.height; y++)
			for (int x = 0; x < img.width; x++) {
				int i = (bb.get() & 0xFF);
				Color c = colors[i];

				//	if(c.getAlpha() != 255)
				//		map.alpha = AlphaMode.TRANSPARENT;

				tex.setRGB(x, y, c.getRGB());
			}

		return tex;
	}

	public static BufferedImage getIndexedImage(Tile img, Palette pal)
	{
		ByteBuffer bb;
		if (img.format == CI_4)
			bb = unpack_CI_4bpp(img);
		else if (img.format == CI_8)
			bb = unpack_CI_8bpp(img);
		else
			throw new IllegalStateException("Image is not CI format: " + img.format);

		BufferedImage tex = new BufferedImage(img.width, img.height, BufferedImage.TYPE_BYTE_INDEXED, pal.getIndexColorModel());
		Color[] colors = pal.getColors();

		bb.rewind();
		for (int y = 0; y < img.height; y++)
			for (int x = 0; x < img.width; x++) {
				int i = (bb.get() & 0xFF);
				Color c = colors[i];

				//	if(c.getAlpha() != 255)
				//		map.alpha = AlphaMode.TRANSPARENT;

				tex.setRGB(x, y, c.getRGB());
			}

		return tex;
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

	private short packColor(int r, int g, int b, int a)
	{
		int R = (int) Math.floor(31 * (r / 255.0));
		int G = (int) Math.floor(31 * (g / 255.0));
		int B = (int) Math.floor(31 * (b / 255.0));

		int color = ((a & 0x80) == 0) ? 0 : 1;
		color |= (R & 0x1F) << 11;
		color |= (G & 0x1F) << 6;
		color |= (B & 0x1F) << 1;

		return (short) color;
	}

	public static class ImageFormatException extends RuntimeException
	{
		public ImageFormatException(String msg)
		{
			super(msg);
		}
	}
}
