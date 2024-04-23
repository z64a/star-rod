package renderer.text;

import java.util.List;

import util.identity.IdentityArrayList;

public abstract class TextRenderer
{
	public static final TextFont FONT_MONO = new TextFont("AzeretMono");
	public static final TextFont FONT_ROBOTO = new TextFont("Roboto");

	private static List<DrawableString> stringsToCleanup = new IdentityArrayList<>();

	public static void init()
	{
		FONT_MONO.glLoad();
		FONT_ROBOTO.glLoad();
	}

	public static void glDelete()
	{
		for (DrawableString str : stringsToCleanup)
			str.glDelete();

		FONT_MONO.glDelete();
		FONT_ROBOTO.glDelete();
	}

	public static void register(DrawableString str)
	{
		stringsToCleanup.add(str);
	}
}
