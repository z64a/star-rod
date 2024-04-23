package game.message.font;

import static app.Directories.*;
import static game.message.font.FontKey.*;
import static game.texture.TileFormat.CI_4;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.w3c.dom.Element;

import app.Environment;
import assets.AssetManager;
import assets.AssetSubdir;
import game.message.StringConstants;
import game.texture.Palette;
import game.texture.Texture;
import game.texture.Tile;
import util.Logger;
import util.Priority;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlSerializable;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public enum CharSet implements XmlSerializable
{
	// @formatter:off
	Normal		(0x10F1B0, 0x1144B0, 0x10CC10, 0xA6, 0x50, 0x80, 0x10, 0x10, "normal"),
	Title		(0x1149B0, 0x1164A8, 0x10CD48, 0x29, 1, 0x60, 0xC, 0xF, "credits-title"),
	Subtitle	(0x115910, 0x116498, 0x10CD74, 0x29, 1, 0x48, 0xC, 0xC, "credits-name");
	// @formatter:on

	private final int offRasters;
	private final int offPalettes;
	private final int offWidths;
	private final String name;

	public final int numChars;
	public final int numPals;

	public final Tile[] images;
	public final Palette[] palettes;
	public final int[] widths;

	private final int defaultSize;
	public final int defaultX;
	public final int defaultY;
	public int[] texSize;

	private static boolean imagesLoaded = false;

	private CharSet(int offRasters, int offPalettes, int offWidths, int numChars, int numPals, int imgSize, int defaultX, int defaultY, String name)
	{
		this.name = name;

		this.offRasters = offRasters;
		this.offPalettes = offPalettes;
		this.offWidths = offWidths;
		this.numChars = numChars;
		this.numPals = numPals;

		this.defaultSize = imgSize;
		this.defaultX = defaultX;
		this.defaultY = defaultY;
		texSize = new int[] { defaultX, defaultY };

		images = new Tile[numChars];
		palettes = new Palette[numPals];
		widths = new int[numChars];
	}

	@Override
	public void fromXML(XmlReader xmr, Element elem)
	{
		xmr.requiresAttribute(elem, ATTR_NAME);
		String xmlName = xmr.getAttribute(elem, ATTR_NAME);

		if (!name.equals(xmlName))
			xmr.complain("Invalid name for charset: " + xmlName + " (expected " + name + ")");

		List<Element> charElems = xmr.getRequiredTags(elem, TAG_CHARACTER);
		if (charElems.size() != numChars)
			xmr.complain(String.format("%s must have exactly 0x%X characters.", xmlName, numChars));

		for (int j = 0; j < numChars; j++) {
			Element charElem = charElems.get(j);
			xmr.requiresAttribute(charElem, ATTR_WIDTH);
			widths[j] = xmr.readInt(charElem, ATTR_WIDTH);
		}
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag charsTag = xmw.createTag(TAG_CHARSET, false);
		xmw.addAttribute(charsTag, ATTR_NAME, name);
		xmw.openTag(charsTag);

		for (int j = 0; j < numChars; j++) {
			XmlTag charTag = xmw.createTag(TAG_CHARACTER, true);
			xmw.addInt(charTag, ATTR_WIDTH, widths[j]);

			StringBuilder sb = new StringBuilder();
			if (widths[j] < 10)
				sb.append(" ");
			String name = StringConstants.getName(j, this == Normal);
			sb.append(String.format("id = %02X, val = \"%s\" ", j, name));

			xmw.printTag(charTag, sb.toString());
		}

		xmw.closeTag(charsTag);
	}

	public static void loadImages(FontManager manager) throws IOException
	{
		for (CharSet chars : values()) {
			for (int i = 0; i < chars.numPals; i++) {
				String palName = String.format("%02X", i) + ".png";
				try {
					File palFile = AssetManager.get(AssetSubdir.STANDARD_CHARS_PAL, palName);
					Tile img = Tile.load(palFile, CI_4);
					chars.palettes[i] = img.palette;
				}
				catch (IOException e) {
					throw new IOException("Could not load character palette " + palName);
				}
			}

			for (int i = 0; i < chars.numChars; i++) {
				String charName = String.format("%02X", i) + ".png";
				try {
					File imgFile = AssetManager.get(AssetSubdir.STANDARD_CHARS, charName);
					chars.images[i] = Tile.load(imgFile, CI_4);
				}
				catch (IOException e) {
					throw new IOException("Could not load character raster " + charName);
				}
			}
		}

		imagesLoaded = true;
	}

	public static void glLoad(FontManager manager) throws IOException
	{
		if (!imagesLoaded)
			loadImages(manager);

		for (CharSet chars : values()) {
			for (int i = 0; i < chars.numPals; i++)
				chars.palettes[i].glLoad();

			for (int i = 0; i < chars.numChars; i++)
				chars.images[i].glLoad(Texture.WRAP_CLAMP, Texture.WRAP_CLAMP, false);
		}
	}

	public static void glDelete(FontManager manager)
	{
		for (CharSet chars : values()) {
			for (Tile t : chars.images)
				t.glDelete();

			for (Palette p : chars.palettes)
				p.glDelete();
		}
	}

	public static void dump(FontManager manager) throws IOException
	{
		dumpStandard();
		dumpCredits();

		ByteBuffer bb = Environment.getBaseRomBuffer();
		for (CharSet chars : values()) {
			bb.position(chars.offWidths);
			for (int i = 0; i < chars.numChars; i++)
				chars.widths[i] = bb.get() & 0xFF;
		}
	}

	private static void dumpStandard() throws IOException
	{
		ByteBuffer bb = Environment.getBaseRomBuffer();
		Logger.log("Dumping standard font.", Priority.MILESTONE);

		FileUtils.forceMkdir(DUMP_FONT_STD.toFile());
		FileUtils.forceMkdir(DUMP_FONT_STD_PAL.toFile());

		// defaultIDs -- to properly dump buttons
		int[] palettesIDs = new int[0xA6];
		palettesIDs[0x98] = 0x10;
		palettesIDs[0x99] = 0x11;
		palettesIDs[0x9A] = 0x15;
		palettesIDs[0x9B] = 0x15;
		palettesIDs[0x9C] = 0x15;
		palettesIDs[0x9D] = 0x13;
		palettesIDs[0x9E] = 0x13;
		palettesIDs[0x9F] = 0x13;
		palettesIDs[0xA0] = 0x13;
		palettesIDs[0xA1] = 0x12;

		int offRasters = Normal.offRasters;
		int offPalettes = Normal.offPalettes;

		// rasters start at 802EE8D0 == 10F1B0
		for (int i = 0; i < 0xA6; i++) {
			Tile img = new Tile(CI_4, 16, 16);
			img.readImage(bb, offRasters + 0x80 * i, false);
			img.readPalette(bb, offPalettes + 0x10 * palettesIDs[i]);
			img.savePNG(DUMP_FONT_STD + String.format("%02X", i));
		}

		Tile img = new Tile(CI_4, 16, 16);
		img.readImage(bb, offRasters + 0x80 * 3, false);

		for (int p = 0; p < 0x50; p++) {
			img.readPalette(bb, offPalettes + 0x10 * p);
			img.savePNG(DUMP_FONT_STD_PAL + String.format("%02X", p));
		}
	}

	private static void dumpCredits() throws IOException
	{
		ByteBuffer bb = Environment.getBaseRomBuffer();
		Logger.log("Dumping credits font.", Priority.MILESTONE);

		FileUtils.forceMkdir(DUMP_FONT_CR1.toFile());
		FileUtils.forceMkdir(DUMP_FONT_CR1_PAL.toFile());

		FileUtils.forceMkdir(DUMP_FONT_CR2.toFile());
		FileUtils.forceMkdir(DUMP_FONT_CR2_PAL.toFile());

		int offRasters = Title.offRasters;
		int offPalettes = Title.offPalettes;

		// upper case starting at 1149B0
		for (int i = 0; i < 0x29; i++) {
			Tile img = new Tile(CI_4, 15, 12);
			img.readImage(bb, offRasters + 0x60 * i, false);
			img.readPalette(bb, offPalettes);

			img.savePNG(DUMP_FONT_CR1 + String.format("%02X", i));
			if (i == 0x17) // x character
				img.savePNG(DUMP_FONT_CR1_PAL + String.format("%02X", 0));
		}

		offRasters = Subtitle.offRasters;
		offPalettes = Subtitle.offPalettes;

		// lower case starting at 115910
		for (int i = 0; i < 0x29; i++) {
			Tile img = new Tile(CI_4, 12, 12);
			img.readImage(bb, offRasters + 0x48 * i, false);
			img.readPalette(bb, offPalettes);

			img.savePNG(DUMP_FONT_CR2 + String.format("%02X", i));
			if (i == 0x17) // x character
				img.savePNG(DUMP_FONT_CR2_PAL + String.format("%02X", 0));
		}

		// done at case starting at 116498
	}
}
