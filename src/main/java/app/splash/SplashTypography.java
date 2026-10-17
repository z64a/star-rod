package app.splash;

import java.awt.Font;
import java.awt.FontFormatException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import util.Logger;

final class SplashTypography
{

	// Explicit line breaks preserve punctuation in the stacked title treatments.
	record Title(String text, String top, String bottom)
	{}

	static final List<Title> TITLES = List.of(
		new Title("STAR ROD", "STAR", "ROD"),
		new Title("Star Rod", "Star", "Rod"),
		new Title("star rod", "star", "rod"),
		new Title("[ star rod ]", "[ star", "rod ]"),
		new Title("star.rod", "star.", "rod"),
		new Title("star / rod", "star /", "rod"),
		new Title("* star rod *", "* star", "rod *"));

	static final List<String> LOADING_TITLES = List.of("LOADING", "Loading", "loading", "PLEASE WAIT", "Please wait", "please wait");

	private SplashTypography()
	{}

	/** 94% ordinary capitalization; brackets/dot 2% each, slash/asterisks 1% each. */
	static Title title(int roll)
	{
		if (roll < 0 || roll >= 100)
			throw new IllegalArgumentException("Title roll must be in [0, 100)");
		if (roll < 44)
			return TITLES.get(0);
		if (roll < 72)
			return TITLES.get(1);
		if (roll < 94)
			return TITLES.get(2);
		if (roll < 96)
			return TITLES.get(3);
		if (roll < 98)
			return TITLES.get(4);
		return TITLES.get(roll == 98 ? 5 : 6);
	}

	enum Face
	{
		SANS (null, Font.SANS_SERIF, false),
		SERIF (null, Font.SERIF, false),
		MONO (null, Font.MONOSPACED, false),
		BUNGEE ("Bungee-Regular.ttf", Font.SANS_SERIF, true),
		LOBSTER ("Lobster-Regular.ttf", Font.SERIF, false),
		RYE ("Rye-Regular.ttf", Font.SERIF, false),
		MONOTON ("Monoton-Regular.ttf", Font.SANS_SERIF, true),
		RUBIK ("RubikMonoOne-Regular.ttf", Font.MONOSPACED, true),
		CAVEAT ("Caveat-Bold.ttf", Font.SANS_SERIF, false),
		PIXEL ("PressStart2P-Regular.ttf", Font.MONOSPACED, false);

		final String filename;
		private final String fallback;
		private final boolean capitalsOnly;
		private Font cached;

		Face(String filename, String fallback, boolean capitalsOnly)
		{
			this.filename = filename;
			this.fallback = fallback;
			this.capitalsOnly = capitalsOnly;
		}

		Font load() throws IOException, FontFormatException
		{
			if (filename == null)
				return new Font(fallback, Font.BOLD, 100);
			try (InputStream stream = SplashTypography.class.getResourceAsStream("/splash/fonts/" + filename)) {
				if (stream == null)
					throw new IOException("Missing splash font: " + filename);
				return Font.createFont(Font.TRUETYPE_FONT, stream).deriveFont(100f);
			}
		}

		synchronized Font font()
		{
			if (cached == null) {
				try {
					cached = load();
				}
				catch (IOException | FontFormatException e) {
					Logger.logWarning("Couldn't load splash font " + filename + ": " + e.getMessage());
					cached = new Font(fallback, Font.BOLD, 100);
				}
			}
			return cached;
		}
	}

	static Font chooseFont(Random random, int style, Title title)
	{
		return chooseFont(random, style, title.text());
	}

	static Font chooseFont(Random random, int style, String text)
	{
		Face[] pool = switch (style) {
			case 0 -> new Face[] { Face.LOBSTER, Face.CAVEAT, Face.SANS, Face.BUNGEE };
			case 1 -> new Face[] { Face.RYE, Face.SERIF, Face.BUNGEE, Face.RUBIK };
			case 2 -> new Face[] { Face.MONOTON, Face.RUBIK, Face.SANS, Face.PIXEL };
			case 4 -> new Face[] { Face.LOBSTER, Face.SERIF, Face.RYE, Face.MONOTON };
			case 6 -> new Face[] { Face.CAVEAT, Face.LOBSTER, Face.RYE, Face.BUNGEE };
			case 7 -> new Face[] { Face.BUNGEE, Face.RUBIK, Face.MONO, Face.PIXEL };
			default -> Face.values();
		};
		// Some display families draw lowercase as capitals; keep real lowercase variants visibly distinct.
		boolean lowercase = text.chars().anyMatch(Character::isLowerCase);
		List<Face> eligible = Arrays.stream(pool).filter(face -> !lowercase || !face.capitalsOnly).toList();
		return eligible.get(random.nextInt(eligible.size())).font();
	}
}
