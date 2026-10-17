package app.splash;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import app.config.Config;
import app.config.Options;
import app.config.Options.Scope;

public class StartupSplashTest
{

	@TempDir
	Path tempDir;

	@Test
	public void cycleStartsAt50PersistsRandomStepsAndShows100BeforeResetting() throws IOException
	{
		Path path = tempDir.resolve("main.cfg");
		Files.writeString(path, "GameVersion = us\nSplashUnhinged = 92\n");
		Random random = new Random(813);
		int previous = StartupSplash.nextLaunch(read(path), random).unhinged();
		assertEquals(50, previous, "An old manual setting must not change the first cycle");
		int launches = 1;
		while (previous < 100) {
			assertTrue(launches++ <= 26, "Cycle never reached its maximum");
			int current = StartupSplash.nextLaunch(read(path), random).unhinged();
			assertTrue(current >= Math.min(100, previous + 2) && current <= Math.min(100, previous + 5));
			previous = current;
		}
		assertEquals(50, read(path).getInteger(Options.SplashNextUnhinged));
		assertEquals(50, StartupSplash.nextLaunch(read(path), random).unhinged());
		assertEquals("us", read(path).getString(Options.GameVersion));
	}

	@Test
	public void initializationAdvancesOnlyOnceWithinTheApplicationProcess() throws IOException
	{
		Config config = new Config(tempDir.resolve("main.cfg").toFile(), Scope.Main);
		StartupSplash.initialize(config);
		byte[] saved = Files.readAllBytes(config.getFile().toPath());
		int next = config.getInteger(Options.SplashNextUnhinged);
		assertTrue(next >= 52 && next <= 55);
		StartupSplash.initialize(config);
		assertEquals(next, config.getInteger(Options.SplashNextUnhinged));
		assertArrayEquals(saved, Files.readAllBytes(config.getFile().toPath()));
	}

	@Test
	public void classicWinningDrawShowsTheOriginalUnmodifiedImage() throws IOException
	{
		Config config = new Config(tempDir.resolve("main.cfg").toFile(), Scope.Main);
		StartupSplash.Selection selection = StartupSplash.nextLaunch(config, classicRoll(0));
		assertTrue(selection.classic());
		assertEquals(52, config.getInteger(Options.SplashNextUnhinged), "Classic launches also advance the cycle");
		BufferedImage actual = StartupSplash.render(selection);
		try (var stream = StartupSplash.class.getResourceAsStream("/splash/classic.png")) {
			BufferedImage original = ImageIO.read(stream);
			assertEquals(original.getWidth(), actual.getWidth());
			assertEquals(original.getHeight(), actual.getHeight());
			assertArrayEquals(pixels(original), pixels(actual));
		}
	}

	@Test
	public void nonWinningDrawsUseTheNormalSeededComposition() throws IOException
	{
		Config config = new Config(tempDir.resolve("main.cfg").toFile(), Scope.Main);
		for (int roll : new int[] { 1, 199 }) {
			StartupSplash.Selection selection = StartupSplash.nextLaunch(config, classicRoll(roll));
			assertFalse(selection.classic());
			assertArrayEquals(pixels(SplashPhotos.create(selection.seed(), selection.unhinged()).image()),
				pixels(StartupSplash.render(selection)));
		}
	}

	private static Random classicRoll(int roll)
	{
		return new Random(12) {
			@Override
			public int nextInt(int bound)
			{
				if (bound == 200)
					return roll;
				assertEquals(4, bound);
				return 0;
			}
		};
	}

	@Test
	public void deluxeOverridesClassicAndReplaysItsProceduralArtwork() throws IOException
	{
		StartupSplash.Selection selected = new StartupSplash.Selection(12, 75, true);
		BufferedImage deluxe = StartupSplash.render(selected, true);
		assertEquals(SplashRenderer.WIDTH, deluxe.getWidth());
		assertEquals(SplashRenderer.HEIGHT, deluxe.getHeight());
		assertArrayEquals(pixels(deluxe), pixels(StartupSplash.render(selected, true)));
		assertFalse(Arrays.equals(pixels(deluxe), pixels(StartupSplash.render(selected, false))));
		assertFalse(Arrays.equals(pixels(deluxe), pixels(StartupSplash.render(new StartupSplash.Selection(13, 75, true), true))));
	}

	private static Config read(Path path) throws IOException
	{
		Config config = new Config(path.toFile(), Scope.Main);
		config.readConfig();
		return config;
	}

	private static int[] pixels(BufferedImage image)
	{
		return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
	}
}
