package app.splash;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import app.config.Config;
import app.config.Options;
import app.config.Options.Scope;

public class SplashTest
{

	@TempDir
	Path tempDir;

	@Test
	public void seedReplaysCompositionWithoutChangingSourcePhotos()
	{
		BufferedImage source = samplePhoto();
		int[] original = pixels(source);
		BufferedImage first = SplashRenderer.render(List.of(source), 1234, 100);
		BufferedImage replay = SplashRenderer.render(List.of(source), 1234, 100);
		BufferedImage reroll = SplashRenderer.render(List.of(source), 1235, 100);

		assertArrayEquals(pixels(first), pixels(replay));
		assertFalse(Arrays.equals(pixels(first), pixels(reroll)));
		assertArrayEquals(original, pixels(source));
	}

	@Test
	public void intensityIsBoundedAndChangesTheComposition()
	{
		List<BufferedImage> photos = List.of(samplePhoto());
		BufferedImage calm = SplashRenderer.render(photos, 42, 0);
		BufferedImage chaotic = SplashRenderer.render(photos, 42, 100);
		assertArrayEquals(pixels(calm), pixels(SplashRenderer.render(photos, 42, Integer.MIN_VALUE)));
		assertArrayEquals(pixels(chaotic), pixels(SplashRenderer.render(photos, 42, Integer.MAX_VALUE)));
		assertFalse(Arrays.equals(pixels(calm), pixels(chaotic)));
	}

	@Test
	public void bundledPhotosHaveCreditsAndBoundedDecodableImages() throws IOException
	{
		List<SplashPhotos.Photo> catalog = SplashPhotos.catalog();
		assertTrue(catalog.size() >= 32);
		HashSet<String> names = new HashSet<>();
		for (SplashPhotos.Photo photo : catalog) {
			assertTrue(names.add(photo.filename()));
			assertFalse(photo.photographer().isBlank());
			assertTrue(photo.source().startsWith("https://www.pexels.com/photo/"));
			BufferedImage image = SplashPhotos.read(photo);
			assertTrue(image.getWidth() <= 800 && image.getHeight() <= 800, photo.filename());
			assertTrue(image.getWidth() > 0 && image.getHeight() > 0);
			try (var stream = SplashPhotos.class.getResourceAsStream("/splash/photos/" + photo.filename())) {
				assertTrue(stream.readAllBytes().length <= 80 * 1024, "Oversized splash photo: " + photo.filename());
			}
		}
	}

	@Test
	public void capitalizationIsCommonAndPunctuationIsRare()
	{
		int[] counts = new int[SplashTypography.TITLES.size()];
		for (int roll = 0; roll < 100; roll++)
			counts[SplashTypography.TITLES.indexOf(SplashTypography.title(roll))]++;
		assertArrayEquals(new int[] { 44, 28, 22, 2, 2, 1, 1 }, counts);
	}

	@Test
	public void bundledFontsAreSmallAndCoverEveryTitle() throws Exception
	{
		int bundled = 0;
		for (SplashTypography.Face face : SplashTypography.Face.values()) {
			if (face.filename == null)
				continue;
			bundled++;
			var font = face.load();
			for (SplashTypography.Title title : SplashTypography.TITLES)
				assertEquals(-1, font.canDisplayUpTo(title.text() + " / "), face.filename + ": " + title.text());
			for (String title : SplashTypography.LOADING_TITLES)
				assertEquals(-1, font.canDisplayUpTo(title), face.filename + ": " + title);
			try (var stream = SplashTypography.class.getResourceAsStream("/splash/fonts/" + face.filename)) {
				assertTrue(stream.readAllBytes().length < 32 * 1024, "Oversized font subset: " + face.filename);
			}
		}
		assertEquals(7, bundled);
	}

	@Test
	public void everyTitleRendersInEveryLayout()
	{
		for (int style = 0; style < SplashRenderer.STYLE_COUNT; style++) {
			for (SplashTypography.Title title : SplashTypography.TITLES) {
				var font = SplashTypography.chooseFont(new Random(style), style, title);
				BufferedImage image = SplashRenderer.render(List.of(), 932L + style, 100, style, title, font);
				assertEquals(SplashRenderer.WIDTH, image.getWidth());
				assertEquals(SplashRenderer.HEIGHT, image.getHeight());
			}
		}
	}

	@Test
	public void photoSelectionExpandsWithIntensityAndRemainsReproducible() throws IOException
	{
		SplashPhotos.Splash calm = SplashPhotos.create(17, 0);
		SplashPhotos.Splash chaotic = SplashPhotos.create(17, 100);
		assertEquals(1, calm.photos().size());
		assertEquals(4, chaotic.photos().size());
		assertEquals(calm.photos().get(0), chaotic.photos().get(0));
		assertEquals(chaotic.photos(), SplashPhotos.create(17, 100).photos());
		assertEquals(SplashRenderer.WIDTH, chaotic.image().getWidth());
		assertEquals(SplashRenderer.HEIGHT, chaotic.image().getHeight());
	}

	@Test
	public void missingPhotoIsReportedAsAnIoFailure()
	{
		assertThrows(IOException.class, () -> SplashPhotos.read(new SplashPhotos.Photo("missing.jpg", "", "")));
	}

	@Test
	public void cancelledPhotoLoadStopsBeforeRendering()
	{
		try {
			Thread.currentThread().interrupt();
			assertThrows(IOException.class, () -> SplashPhotos.create(1, 100));
		}
		finally {
			Thread.interrupted();
		}
	}

	@Test
	public void existingConfigsGainDefaultAndSaveIntensity() throws IOException
	{
		Path path = tempDir.resolve("main.cfg");
		Files.writeString(path, "GameVersion = us\n");
		Config config = new Config(path.toFile(), Scope.Main);
		config.readConfig();
		assertEquals(SplashRenderer.DEFAULT_UNHINGED, config.getInteger(Options.SplashUnhinged));
		config.setInteger(Options.SplashUnhinged, 72);
		config.saveConfigFile();
		Config reloaded = new Config(path.toFile(), Scope.Main);
		reloaded.readConfig();
		assertEquals(72, reloaded.getInteger(Options.SplashUnhinged));
		reloaded.setInteger(Options.SplashUnhinged, -1);
		assertEquals(0, reloaded.getInteger(Options.SplashUnhinged));
		reloaded.setInteger(Options.SplashUnhinged, 101);
		assertEquals(100, reloaded.getInteger(Options.SplashUnhinged));
	}

	private static BufferedImage samplePhoto()
	{
		BufferedImage image = new BufferedImage(96, 64, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setColor(new Color(30, 100, 170));
		g.fillRect(0, 0, 96, 64);
		g.setColor(new Color(210, 120, 40));
		g.fillOval(5, 12, 50, 50);
		g.dispose();
		return image;
	}

	private static int[] pixels(BufferedImage image)
	{
		return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
	}
}
