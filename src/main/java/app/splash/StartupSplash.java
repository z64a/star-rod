package app.splash;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import javax.imageio.ImageIO;

import app.Environment;
import app.config.Config;
import app.config.Options;
import util.Logger;

/** Chooses startup artwork once per GUI launch and persists the next cycle value. */
public final class StartupSplash
{

	static final int CLASSIC_CHANCE = 200;
	private static volatile Selection current;

	record Selection(long seed, int unhinged, boolean classic)
	{}

	private StartupSplash()
	{}

	public static synchronized void initialize(Config config)
	{
		if (current == null)
			current = nextLaunch(config, ThreadLocalRandom.current());
	}

	static Selection nextLaunch(Config config, Random random)
	{
		int unhinged = config.getInteger(Options.SplashNextUnhinged);
		Selection selected = new Selection(random.nextLong(), unhinged, random.nextInt(CLASSIC_CHANCE) == 0);
		int next = unhinged == 100 ? SplashRenderer.DEFAULT_UNHINGED : Math.min(100, unhinged + 2 + random.nextInt(4));
		config.setInteger(Options.SplashNextUnhinged, next);
		config.saveConfigFile();
		return selected;
	}

	public static BufferedImage render() throws IOException
	{
		Selection selected = current;
		// Standalone loading-window previews don't advance the application's saved cycle.
		if (selected == null)
			selected = new Selection(ThreadLocalRandom.current().nextLong(), SplashRenderer.DEFAULT_UNHINGED, false);
		return render(selected, Environment.isDeluxe());
	}

	static BufferedImage render(Selection selected) throws IOException
	{
		return render(selected, false);
	}

	static BufferedImage render(Selection selected, boolean deluxe) throws IOException
	{
		// The rarer Deluxe launch takes precedence if both easter eggs happen to roll.
		if (deluxe)
			return SplashPhotos.createDeluxe(selected.seed(), selected.unhinged()).image();
		if (selected.classic()) {
			try (InputStream stream = StartupSplash.class.getResourceAsStream("/splash/classic.png")) {
				if (stream == null)
					throw new IOException("Missing classic splash");
				BufferedImage image = ImageIO.read(stream);
				if (image == null)
					throw new IOException("Invalid classic splash");
				return image;
			}
			catch (IOException e) {
				Logger.logWarning("Couldn't load classic splash: " + e.getMessage());
			}
		}
		return SplashPhotos.create(selected.seed(), selected.unhinged()).image();
	}
}
