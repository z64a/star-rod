package app.splash;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.imageio.ImageIO;

/** Headless visual review: java ... app.splash.SplashPreview [output-directory] [first-seed]. */
public final class SplashPreview
{

	public static void main(String[] args) throws Exception
	{
		Path output = Path.of(args.length > 0 ? args[0] : "build/splash-preview");
		long firstSeed = args.length > 1 ? Long.parseLong(args[1]) : 1700;
		Files.createDirectories(output);
		int[] levels = { 0, 55, 100 };
		BufferedImage sheet = new BufferedImage(1152, 4 * 286, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = sheet.createGraphics();
		g.setColor(new Color(20, 20, 24));
		g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		for (int row = 0; row < 4; row++) {
			long seed = firstSeed + row;
			for (int col = 0; col < levels.length; col++) {
				long start = System.nanoTime();
				SplashPhotos.Splash splash = SplashPhotos.create(seed, levels[col]);
				long elapsed = (System.nanoTime() - start) / 1_000_000;
				String name = "splash-" + seed + "-" + levels[col] + ".png";
				ImageIO.write(splash.image(), "png", output.resolve(name).toFile());
				g.drawImage(splash.image(), col * 384, row * 286 + 30, 384, 256, null);
				g.setColor(Color.WHITE);
				g.drawString("Unhinged " + levels[col] + " / seed " + seed, col * 384 + 10, row * 286 + 21);
				System.out.printf("%s: %d ms%n", name, elapsed);
			}
		}
		g.dispose();
		ImageIO.write(sheet, "png", output.resolve("contact-sheet.png").toFile());

		List<SplashPhotos.Photo> photos = SplashPhotos.catalog();
		BufferedImage sources = new BufferedImage(960, ((photos.size() + 3) / 4) * 190, BufferedImage.TYPE_INT_RGB);
		g = sources.createGraphics();
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
		for (int i = 0; i < photos.size(); i++) {
			BufferedImage image = SplashPhotos.read(photos.get(i));
			int x = (i % 4) * 240, y = (i / 4) * 190;
			double scale = Math.min(240.0 / image.getWidth(), 160.0 / image.getHeight());
			g.drawImage(image, x, y + 25, (int) (image.getWidth() * scale), (int) (image.getHeight() * scale), null);
			g.setColor(Color.WHITE);
			g.drawString(photos.get(i).filename(), x + 5, y + 18);
		}
		g.dispose();
		ImageIO.write(sources, "png", output.resolve("source-photos.png").toFile());
		renderTypography(output, photos);
		renderBanners(output, firstSeed);
		renderDeluxe(output, firstSeed);
	}

	private static void renderDeluxe(Path output, long firstSeed) throws Exception
	{
		BufferedImage sheet = new BufferedImage(1152, 572, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = sheet.createGraphics();
		g.setColor(new Color(20, 20, 24));
		g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		for (int i = 0; i < 6; i++) {
			int level = 50 + (i % 3) * 25;
			BufferedImage deluxe = StartupSplash.render(new StartupSplash.Selection(firstSeed + i, level, false), true);
			ImageIO.write(deluxe, "png", output.resolve("deluxe-" + (firstSeed + i) + ".png").toFile());
			if (i < 3)
				ImageIO.write(deluxe, "png", output.resolve("deluxe-" + level + ".png").toFile());
			int x = i % 3 * 384, y = i / 3 * 286;
			g.drawImage(deluxe, x, y + 30, 384, 256, null);
			g.setColor(Color.WHITE);
			g.drawString("Deluxe / Unhinged " + level + " / seed " + (firstSeed + i), x + 10, y + 21);
		}
		g.dispose();
		ImageIO.write(sheet, "png", output.resolve("deluxe.png").toFile());
	}

	private static void renderBanners(Path output, long firstSeed) throws Exception
	{
		BufferedImage sheet = new BufferedImage(960, 4 * 174, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = sheet.createGraphics();
		g.setColor(new Color(20, 20, 24));
		g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
		for (int i = 0; i < 8; i++) {
			int unhinged = i % 2 == 0 ? 55 : 100;
			long seed = firstSeed + i;
			BufferedImage banner = SplashPhotos.createBanner(seed, unhinged).image();
			int x = i % 2 * 480, y = i / 2 * 174;
			g.drawImage(banner, x, y + 30, null);
			g.setColor(Color.WHITE);
			g.drawString("Unhinged " + unhinged + " / seed " + seed, x + 10, y + 21);
		}
		g.dispose();
		ImageIO.write(sheet, "png", output.resolve("banners.png").toFile());
	}

	private static void renderTypography(Path output, List<SplashPhotos.Photo> photos) throws Exception
	{
		SplashTypography.Face[] faces = {
				SplashTypography.Face.LOBSTER, SplashTypography.Face.RYE, SplashTypography.Face.MONOTON,
				SplashTypography.Face.SERIF, SplashTypography.Face.SERIF, SplashTypography.Face.BUNGEE,
				SplashTypography.Face.CAVEAT, SplashTypography.Face.PIXEL, SplashTypography.Face.RUBIK
		};
		int[] titles = { 1, 0, 0, 5, 6, 0, 3, 4, 0 };
		String[] styles = { "Neon", "Monument", "Echo", "Ransom", "Orbit", "Wave", "Sticker", "Marquee", "Ribbons" };
		BufferedImage sheet = new BufferedImage(1152, 858, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = sheet.createGraphics();
		g.setColor(new Color(20, 20, 24));
		g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		for (int style = 0; style < SplashRenderer.STYLE_COUNT; style++) {
			var font = faces[style].font();
			var title = SplashTypography.TITLES.get(titles[style]);
			BufferedImage image = SplashRenderer.render(List.of(SplashPhotos.read(photos.get((style + 12) % photos.size()))),
				2913L + style, 70, style, title, font);
			ImageIO.write(image, "png", output.resolve("type-" + styles[style].toLowerCase(java.util.Locale.ROOT) + ".png").toFile());
			int x = style % 3 * 384, y = style / 3 * 286;
			g.drawImage(image, x, y + 30, 384, 256, null);
			g.setColor(Color.WHITE);
			g.drawString(styles[style] + " / " + (style == 3 ? "Mixed fonts" : font.getFamily()), x + 10, y + 21);
		}
		g.dispose();
		ImageIO.write(sheet, "png", output.resolve("typography.png").toFile());
	}
}
