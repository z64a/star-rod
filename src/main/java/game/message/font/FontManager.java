package game.message.font;

import static app.Directories.DUMP_MSG_FONT;
import static game.message.font.FontKey.*;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.w3c.dom.Element;

import app.Environment;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class FontManager
{
	public static void main(String[] args) throws IOException
	{
		Environment.initialize();
		dump();
		Environment.exit();
	}

	private FontManager()
	{}

	private static FontManager instance = new FontManager();

	public static void dump() throws IOException
	{
		CharSet.dump(instance);

		try (XmlWriter xmw = new XmlWriter(new File(DUMP_MSG_FONT + "fonts.xml"))) {
			XmlTag rootTag = xmw.createTag(TAG_ROOT, false);
			xmw.openTag(rootTag);

			for (FontType font : FontType.values())
				font.toXML(xmw);

			for (CharSet chars : CharSet.values())
				chars.toXML(xmw);

			xmw.closeTag(rootTag);
			xmw.save();
		}
	}

	private static boolean loaded = false;
	private static boolean readyForGL = false;

	public static boolean isLoaded()
	{
		return loaded;
	}

	public static boolean isReadyForGL()
	{
		return readyForGL;
	}

	public static void loadData() throws IOException
	{
		XmlReader xmr = new XmlReader(new File(DUMP_MSG_FONT.toFile(), "fonts.xml"));

		List<Element> fontElems = xmr.getTags(xmr.getRootElement(), TAG_FONT);
		if (fontElems.size() != 4)
			xmr.complain("fonts.xml must contain exactly 4 fonts.");

		List<Element> charsetElems = xmr.getTags(xmr.getRootElement(), TAG_CHARSET);
		if (charsetElems.size() != 3)
			xmr.complain("fonts.xml must contain exactly 3 charsets.");

		for (int i = 0; i < 4; i++)
			FontType.values()[i].fromXML(xmr, fontElems.get(i));

		for (int i = 0; i < 3; i++)
			CharSet.values()[i].fromXML(xmr, charsetElems.get(i));

		CharSet.loadImages(instance);

		loaded = true;
	}

	public static void glLoad() throws IOException
	{
		if (readyForGL)
			glDelete();

		CharSet.glLoad(instance);
		readyForGL = true;
	}

	public static void glDelete()
	{
		CharSet.glDelete(instance);
	}
}
