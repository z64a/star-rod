package app.splash;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Random;

/** Optional photo treatments run before lettering so the title stays readable. */
final class SplashEffects
{

	private SplashEffects()
	{}

	static void apply(BufferedImage image, Random random, double chaos, Color[] palette)
	{
		if (chaos < 0.25 || random.nextDouble() > 0.2 + chaos * 0.65)
			return;
		switch (random.nextInt(6)) {
			case 0 -> halftone(image, chaos);
			case 1 -> lightLeak(image, random, chaos, palette[0]);
			case 2 -> pixelate(image, chaos);
			case 3 -> posterize(image, chaos);
			case 4 -> tears(image, random, chaos);
			case 5 -> {
				if (chaos > 0.7)
					kaleidoscope(image, random);
				else
					lightLeak(image, random, chaos, palette[1]);
			}
		}
	}

	private static void halftone(BufferedImage image, double chaos)
	{
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setComposite(AlphaComposite.SrcOver.derive((float) (0.2 + chaos * 0.35)));
		g.setColor(new Color(255, 243, 210));
		g.fillRect(0, 0, image.getWidth(), image.getHeight());
		g.setColor(new Color(22, 13, 38));
		int step = 6 + (int) (chaos * 4);
		for (int y = step / 2; y < image.getHeight(); y += step) {
			for (int x = step / 2; x < image.getWidth(); x += step) {
				int rgb = image.getRGB(x, y);
				double light = (((rgb >> 16) & 255) * 0.21 + ((rgb >> 8) & 255) * 0.72 + (rgb & 255) * 0.07) / 255;
				double radius = (1 - light) * step * 0.6;
				g.fill(new Ellipse2D.Double(x - radius, y - radius, radius * 2, radius * 2));
			}
		}
		g.dispose();
	}

	private static void lightLeak(BufferedImage image, Random random, double chaos, Color color)
	{
		Graphics2D g = image.createGraphics();
		float x = random.nextBoolean() ? 0 : image.getWidth();
		float y = (float) (image.getHeight() * random.nextDouble());
		g.setPaint(new RadialGradientPaint(x, y, image.getWidth() * 0.8f,
			new float[] { 0, 0.4f, 1 }, new Color[] {
					new Color(255, 223, 166, (int) (100 + chaos * 100)),
					new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) (70 + chaos * 70)),
					new Color(255, 128, 90, 0)
			}));
		g.fillRect(0, 0, image.getWidth(), image.getHeight());
		g.dispose();
	}

	private static void pixelate(BufferedImage image, double chaos)
	{
		int cell = 3 + (int) (chaos * 10);
		BufferedImage tiny = new BufferedImage(image.getWidth() / cell, image.getHeight() / cell, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = tiny.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.drawImage(image, 0, 0, tiny.getWidth(), tiny.getHeight(), null);
		g.dispose();
		g = image.createGraphics();
		g.setComposite(AlphaComposite.SrcOver.derive((float) (0.35 + chaos * 0.5)));
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		g.drawImage(tiny, 0, 0, image.getWidth(), image.getHeight(), null);
		g.dispose();
	}

	private static void posterize(BufferedImage image, double chaos)
	{
		int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
		int levels = 6 - (int) (chaos * 3);
		for (int i = 0; i < pixels.length; i++) {
			int rgb = pixels[i];
			int r = (((rgb >> 16) & 255) * levels / 256) * 255 / (levels - 1);
			int g = (((rgb >> 8) & 255) * levels / 256) * 255 / (levels - 1);
			int b = ((rgb & 255) * levels / 256) * 255 / (levels - 1);
			pixels[i] = (r << 16) | (g << 8) | b;
		}
	}

	private static void tears(BufferedImage image, Random random, double chaos)
	{
		int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
		int[] source = pixels.clone();
		int width = image.getWidth(), height = image.getHeight();
		for (int strip = 0; strip < 2 + chaos * 10; strip++) {
			int top = random.nextInt(height);
			int bottom = Math.min(height, top + 3 + random.nextInt(25));
			int offset = (int) ((random.nextDouble() - 0.5) * chaos * 150);
			for (int y = top; y < bottom; y++)
				for (int x = 0; x < width; x++)
					pixels[y * width + x] = source[y * width + Math.floorMod(x + offset, width)];
		}
	}

	private static void kaleidoscope(BufferedImage image, Random random)
	{
		int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
		int[] source = pixels.clone();
		int width = image.getWidth(), height = image.getHeight();
		double sector = Math.PI / (3 + random.nextInt(3));
		double phase = random.nextDouble() * Math.PI * 2;
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				double dx = x - width / 2.0, dy = y - height / 2.0;
				double radius = Math.hypot(dx, dy);
				double angle = Math.atan2(dy, dx) + Math.PI + phase;
				angle = Math.abs(angle % (sector * 2) - sector);
				int sx = Math.floorMod((int) (width / 2.0 + Math.cos(angle) * radius), width);
				int sy = Math.floorMod((int) (height / 2.0 + Math.sin(angle) * radius), height);
				pixels[y * width + x] = source[sy * width + sx];
			}
		}
	}
}
