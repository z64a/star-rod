package game.texture;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.FilenameUtils;

import app.input.InputFileException;
import assets.AssetHandle;
import assets.AssetManager;
import assets.AssetSubdir;
import game.texture.TextureArchive.JsonTexture;

public class Texture
{
	public static final int IMG = 1;
	public static final int AUX = 0;

	public static final int WRAP_REPEAT = 0;
	public static final int WRAP_MIRROR = 1;
	public static final int WRAP_CLAMP = 2;

	public final String name;

	public Tile main;
	public Tile aux;
	public List<Tile> mipmapList;

	public int extra;
	public boolean hasAux = false;
	public boolean hasMipmaps = false;

	public int[] hWrap;
	public int[] vWrap;
	public boolean filter;
	public int auxCombine;

	private static final String IMG_KEY = "img";
	private static final String AUX_KEY = "aux";
	private static final String MIPMAP_KEY = "mipmaps";
	private static final String FILTER_KEY = "filter";
	private static final String COMBINE_KEY = "combine";
	private static final String YES = "yes";
	private static final String NO = "no";

	private Texture(String name)
	{
		this.name = name;
	}

	public void print(PrintWriter pw)
	{
		pw.println("tex: " + name);
		pw.println("{");

		pw.println("\timg: " + name + ".png");
		pw.println("\t{");
		pw.println("\t\tformat: " + main.format);
		pw.println("\t\thwrap: " + getWrapName(null, name, hWrap[IMG]));
		pw.println("\t\tvwrap: " + getWrapName(null, name, vWrap[IMG]));
		pw.println("\t}");

		switch (extra) {
			case 0:
				break;
			case 1: // MAIN + MIPMAPS
				pw.println("\t" + MIPMAP_KEY + ": " + YES);
				break;
			case 2: // MAIN + SHARED MASK -- use same format, half height for each
				pw.printf("\t%s: %s_AUX.png%n", AUX_KEY, name);
				pw.println("\t{");
				pw.println("\t\tformat: shared");
				pw.println("\t\thwrap: " + getWrapName(null, name, hWrap[AUX]));
				pw.println("\t\tvwrap: " + getWrapName(null, name, vWrap[AUX]));
				pw.println("\t}");
				break;
			case 3: // MAIN + SEPARATE MASK -- two images have independent attributes
				pw.printf("\t%s: %s_AUX.png%n", AUX_KEY, name);
				pw.println("\t{");
				pw.println("\t\tformat: " + aux.format);
				pw.println("\t\thwrap: " + getWrapName(null, name, hWrap[AUX]));
				pw.println("\t\tvwrap: " + getWrapName(null, name, vWrap[AUX]));
				pw.println("\t}");
				break;
		}

		pw.printf("\t%s: %s%n", FILTER_KEY, (filter ? YES : NO));
		pw.printf("\t%s: %X%n", COMBINE_KEY, auxCombine);

		pw.println("}");
		pw.println();
	}

	public static Texture parseTexture(File archiveFile, String dir, String name, List<String> lines) throws IOException
	{
		Texture tx = new Texture(name);
		String imgName = null;
		String auxName = null;
		String imgFormatName = null;
		String auxFormatName = null;
		tx.hWrap = new int[2];
		tx.vWrap = new int[2];
		boolean convertImg = false;
		boolean convertAux = false;
		ImageAttributes attr;

		Iterator<String> iter = lines.iterator();
		while (iter.hasNext()) {
			String line = iter.next();
			String[] tokens = splitLine(archiveFile, tx.name, line);

			switch (tokens[0]) {
				case IMG_KEY:
					imgName = tokens[1];
					if (!iter.hasNext())
						throw new InputFileException(archiveFile, "(%s) Incomplete texture description.", name);
					line = iter.next();
					if (!line.equals("{"))
						throw new InputFileException(archiveFile, "(%s) Invalid texture description.", name);
					List<String> imgLines = new LinkedList<>();
					while (!(line = iter.next()).equals("}")) {
						imgLines.add(line);
						if (!iter.hasNext())
							throw new InputFileException(archiveFile, "(%s) Incomplete texture description.", name);
					}
					attr = parseImage(archiveFile, tx, imgLines);
					imgFormatName = attr.format;
					if (attr.convert != null)
						convertImg = attr.convert.equals(YES);
					if (attr.hWrap != null)
						tx.hWrap[IMG] = getWrapMode(archiveFile, name, attr.hWrap);
					if (attr.vWrap != null)
						tx.vWrap[IMG] = getWrapMode(archiveFile, name, attr.vWrap);
					break;

				case AUX_KEY:
					auxName = tokens[1];
					if (!iter.hasNext())
						throw new InputFileException(archiveFile, "(%s) Incomplete texture description.", name);
					line = iter.next();
					if (!line.equals("{"))
						throw new InputFileException(archiveFile, "(%s) Invalid texture description.", name);
					List<String> auxLines = new LinkedList<>();
					while (!(line = iter.next()).equals("}")) {
						auxLines.add(line);
						if (!iter.hasNext())
							throw new InputFileException(archiveFile, "(%s) Incomplete texture description.", name);
					}
					attr = parseImage(archiveFile, tx, auxLines);
					auxFormatName = attr.format;
					if (attr.convert != null)
						convertAux = attr.convert.equals(YES);
					if (attr.hWrap != null)
						tx.hWrap[AUX] = getWrapMode(archiveFile, name, attr.hWrap);
					if (attr.vWrap != null)
						tx.vWrap[AUX] = getWrapMode(archiveFile, name, attr.vWrap);
					break;

				case MIPMAP_KEY:
					if (tokens[1].equals(YES)) {
						tx.hasMipmaps = true;
						tx.mipmapList = new LinkedList<>();
					}
					break;

				case FILTER_KEY:
					if (tokens[1].equals(YES))
						tx.filter = true;
					break;

				case COMBINE_KEY:
					tx.auxCombine = (byte) Short.parseShort(tokens[1], 16);
					break;
			}
		}

		if (imgFormatName == null)
			throw new InputFileException(archiveFile, "(%s) Texture does not specify an image.", name);

		TileFormat imgFormat = TileFormat.getFormat(imgFormatName);
		if (imgFormat == null)
			throw new InputFileException(archiveFile, "(%s) Unknown image format: %s", name, imgFormatName);

		TileFormat auxFormat = null;
		if (auxFormatName != null) {
			if (auxFormatName.equals("shared")) {
				tx.extra = 2;
				auxFormat = imgFormat;
			}
			else {
				tx.extra = 3;
				auxFormat = TileFormat.getFormat(auxFormatName);
				if (auxFormat == null)
					throw new InputFileException(archiveFile, "(%s) Unknown aux format: %s", name, auxFormatName);
			}

			tx.hasAux = true;
		}

		if (tx.hasMipmaps) {
			tx.extra = 1;
			if (tx.hasAux)
				throw new InputFileException(archiveFile, "(%s) Texture cannot have both mipmaps and aux.", name);
		}

		tx.main = Tile.load(dir + imgName, imgFormat, convertImg);

		if (tx.hasAux)
			tx.aux = Tile.load(dir + auxName, auxFormat, convertAux);

		if (tx.hasMipmaps) {
			int divisor = 2;
			if (tx.main.width >= (32 >> tx.main.format.depth)) {
				while (true) {
					if (tx.main.width / divisor <= 0)
						break;

					int mmHeight = tx.main.height / divisor;
					int mmWidth = tx.main.width / divisor;

					if (imgName.contains("."))
						imgName = imgName.substring(0, imgName.indexOf("."));

					String mmName = imgName + "_MIPMAP_" + (tx.mipmapList.size() + 1) + ".png";
					Tile mipmap = Tile.load(dir + mmName, imgFormat, convertImg);

					if (mipmap.height != mmHeight)
						throw new InputFileException(archiveFile, "%s has incorrect height: %s instead of %s", mmName, mipmap.height, mmHeight);

					if (mipmap.width != mmWidth)
						throw new InputFileException(archiveFile, "%s has incorrect width: %s instead of %s", mmName, mipmap.width, mmWidth);

					tx.mipmapList.add(mipmap);

					divisor = divisor << 1;
					if (tx.main.width / divisor < (16 >> tx.main.format.depth))
						break;
				}
			}
		}

		return tx;
	}

	public static Texture parseTexture(File source, JsonTexture json) throws IOException
	{
		File dir = source.getParentFile();
		String texName = FilenameUtils.getBaseName(source.getName());
		//File subdir = new File(dir, FilenameUtils.getBaseName(source.getName()));

		Texture tx = new Texture(json.name);
		tx.hWrap = new int[2];
		tx.vWrap = new int[2];

		if (json.name == null) {
			throw new InputFileException(source, "Texture does not specify an name!");
		}
		if (json.main == null) {
			throw new InputFileException(source, "(%s) Texture does not specify an image.", json.name);
		}
		if (json.main.format == null) {
			throw new InputFileException(source, "(%s) Texture does not specify an image format.", json.name);
		}

		tx.hWrap[IMG] = getWrapMode(source, json.name, json.main.hwrap);
		tx.vWrap[IMG] = getWrapMode(source, json.name, json.main.vwrap);

		tx.hasMipmaps = json.hasMipmaps;
		tx.filter = json.filter;

		tx.auxCombine = getCombineMode(source, tx.name, json.combine);

		TileFormat imgFormat = TileFormat.getFormat(json.main.format.toLowerCase());
		if (imgFormat == null) {
			throw new InputFileException(source, "(%s) Unknown image format: %s", json.name, json.main.format);
		}

		TileFormat auxFormat = null;
		if (json.aux != null) {
			if (json.aux.format == null) {
				throw new InputFileException(source, "(%s) Texture does not specify an aux format.", json.name);
			}

			if ("shared".equals(json.aux.format.toLowerCase())) {
				tx.extra = 2;
				auxFormat = imgFormat;
			}
			else {
				tx.extra = 3;
				auxFormat = TileFormat.getFormat(json.aux.format.toLowerCase());
				if (auxFormat == null)
					throw new InputFileException(source, "(%s) Unknown aux format: %s", json.name, json.aux.format);
			}

			tx.hWrap[AUX] = getWrapMode(source, json.name, json.aux.hwrap);
			tx.vWrap[AUX] = getWrapMode(source, json.name, json.aux.vwrap);

			tx.hasAux = true;
		}

		if (tx.hasMipmaps) {
			tx.extra = 1;
			tx.mipmapList = new LinkedList<>();
			if (tx.hasAux)
				throw new InputFileException(source, "(%s) Texture cannot have both mipmaps and aux.", json.name);
		}

		AssetHandle mainAsset = AssetManager.get(AssetSubdir.MAP_TEX, texName + "/" + tx.name + ".png");

		tx.main = Tile.load(mainAsset, imgFormat);

		if (tx.hasAux) {
			AssetHandle auxAsset = AssetManager.get(AssetSubdir.MAP_TEX, texName + "/" + tx.name + "_AUX.png");
			tx.aux = Tile.load(auxAsset, auxFormat);
		}

		if (tx.hasMipmaps) {
			int divisor = 2;
			if (tx.main.width >= (32 >> tx.main.format.depth)) {
				while (true) {
					if (tx.main.width / divisor <= 0)
						break;

					int mmHeight = tx.main.height / divisor;
					int mmWidth = tx.main.width / divisor;

					String mmName = tx.name + "_MM" + (tx.mipmapList.size() + 1);
					AssetHandle mmAsset = AssetManager.get(AssetSubdir.MAP_TEX, texName + "/" + mmName + ".png");
					Tile mipmap = Tile.load(mmAsset, imgFormat);

					if (mipmap.height != mmHeight)
						throw new InputFileException(source, "%s has incorrect height: %s instead of %s", mmName, mipmap.height, mmHeight);

					if (mipmap.width != mmWidth)
						throw new InputFileException(source, "%s has incorrect width: %s instead of %s", mmName, mipmap.width, mmWidth);

					tx.mipmapList.add(mipmap);

					divisor = divisor << 1;
					if (tx.main.width / divisor < (16 >> tx.main.format.depth))
						break;
				}
			}
		}

		return tx;
	}

	private static int getCombineMode(File archiveFile, String texname, String wrap)
	{
		switch (wrap) {
			case "None":
				return 0;
			case "Multiply":
				return 8;
			case "ModulateAlpha":
				return 0xD;
			case "LerpMainAux":
				return 0x10;
			default:
				throw new InputFileException(archiveFile, "(%s) has invalid combine mode: %s", texname, wrap);
		}
	}

	private static int getWrapMode(File archiveFile, String texname, String wrap)
	{
		switch (wrap.toLowerCase()) {
			case "repeat":
				return WRAP_REPEAT;
			case "mirror":
				return WRAP_MIRROR;
			case "clamp":
				return WRAP_CLAMP;
			default:
				throw new InputFileException(archiveFile, "(%s) has invalid wrap mode: %s", texname, wrap);
		}
	}

	private static String getWrapName(File archiveFile, String texname, int id)
	{
		switch (id) {
			case WRAP_REPEAT:
				return "repeat";
			case WRAP_MIRROR:
				return "mirror";
			case WRAP_CLAMP:
				return "clamp";
			default:
				throw new InputFileException(archiveFile, "(%s) has invalid wrap mode: %s", texname, id);
		}
	}

	private static class ImageAttributes
	{
		public String format = null;
		public String convert = null;
		public String hWrap = null;
		public String vWrap = null;
	}

	private static ImageAttributes parseImage(File archiveFile, Texture tx, List<String> lines)
	{
		ImageAttributes attr = new ImageAttributes();

		for (String line : lines) {
			String[] tokens = splitLine(archiveFile, tx.name, line);
			switch (tokens[0]) {
				case "format":
					if (attr.format != null)
						throw new InputFileException(archiveFile, "Format specified more than once (%s)", tx.name);
					attr.format = tokens[1];
					break;
				case "convert":
					if (attr.convert != null)
						throw new InputFileException(archiveFile, "Convert specified more than once (%s)", tx.name);
					attr.convert = tokens[1];
					break;
				case "hwrap":
					if (attr.hWrap != null)
						throw new InputFileException(archiveFile, "hWrap specified more than once (%s)", tx.name);
					attr.hWrap = tokens[1];
					break;
				case "vwrap":
					if (attr.vWrap != null)
						throw new InputFileException(archiveFile, "vWrap specified more than once (%s)", tx.name);
					attr.vWrap = tokens[1];
					break;
			}
		}

		if (attr.format == null)
			throw new InputFileException(archiveFile, "Format was not specified (%s)", tx.name);

		return attr;
	}

	private static String[] splitLine(File archiveFile, String texName, String line)
	{
		String[] tokens = line.split(":\\s*");
		if (tokens.length != 2)
			throw new InputFileException(archiveFile, "Invalid line in texture file: %s (%s)", line, texName);
		return tokens;
	}

	public static float getScale(int shift)
	{
		switch (shift) {
			default:
				return 1.0f;
			case 1:
				return 1.0f / 2.0f;
			case 2:
				return 1.0f / 4.0f;
			case 3:
				return 1.0f / 8.0f;
			case 4:
				return 1.0f / 16.0f;
			case 5:
				return 1.0f / 32.0f;
			case 6:
				return 1.0f / 64.0f;
			case 7:
				return 1.0f / 128.0f;
			case 8:
				return 1.0f / 256.0f;
			case 9:
				return 1.0f / 512.0f;
			case 10:
				return 1.0f / 1024.0f;
			case 11:
				return 32.0f;
			case 12:
				return 16.0f;
			case 13:
				return 8.0f;
			case 14:
				return 4.0f;
			case 15:
				return 2.0f;
		}
	}
}
