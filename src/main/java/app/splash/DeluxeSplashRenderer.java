package app.splash;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Random;

/** A gold finish for the same randomized photo collages and lettering as ordinary splashes. */
final class DeluxeSplashRenderer
{

	private static final Color INK = new Color(27, 17, 7);
	private static final Color GOLD = new Color(222, 175, 66);
	private static final Color LIGHT = new Color(255, 243, 196);
	private static final Color[][] FINISHES = {
			{ new Color(14, 10, 5), new Color(108, 66, 17), new Color(218, 168, 59), new Color(255, 248, 209) },
			{ new Color(18, 12, 9), new Color(104, 77, 35), new Color(207, 178, 107), new Color(255, 251, 229) },
			{ new Color(20, 9, 4), new Color(117, 57, 13), new Color(226, 153, 47), new Color(255, 241, 185) }
	};

	private DeluxeSplashRenderer()
	{}

	static BufferedImage render(BufferedImage source, long seed, int unhinged)
	{
		Random random = new Random(seed ^ 0x70DDE1AEL);
		double chaos = Math.max(0, Math.min(100, unhinged)) / 100.0;
		int width = source.getWidth(), height = source.getHeight();
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		int[] pixels = source.getRGB(0, 0, width, height, null, 0, width);
		Color[] finish = FINISHES[random.nextInt(FINISHES.length)];
		// Remap luminance instead of applying an opaque tint: photo detail and type contrast survive.
		for (int i = 0; i < pixels.length; i++) {
			int rgb = pixels[i];
			double light = (((rgb >> 16) & 255) * 0.2126 + ((rgb >> 8) & 255) * 0.7152 + (rgb & 255) * 0.0722) / 255;
			double position = light * (finish.length - 1);
			int stop = Math.min(finish.length - 2, (int) position);
			double blend = position - stop;
			Color low = finish[stop], high = finish[stop + 1];
			int r = (int) (low.getRed() + (high.getRed() - low.getRed()) * blend);
			int g = (int) (low.getGreen() + (high.getGreen() - low.getGreen()) * blend);
			int b = (int) (low.getBlue() + (high.getBlue() - low.getBlue()) * blend);
			pixels[i] = (r << 16) | (g << 8) | b;
		}
		image.setRGB(0, 0, width, height, pixels, 0, width);

		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setStroke(new BasicStroke(1));
		for (int inset : new int[] { 12, 17 }) {
			g.setColor(inset == 12 ? GOLD : LIGHT);
			g.drawRect(inset, inset, width - inset * 2 - 1, height - inset * 2 - 1);
		}
		for (int i = 0; i < 9 + chaos * 18; i++) {
			double x = 32 + random.nextDouble() * (width - 64);
			double y = random.nextBoolean() ? 30 + random.nextDouble() * 55 : height - 30 - random.nextDouble() * 55;
			double size = 2 + random.nextDouble() * 5;
			Path2D glint = new Path2D.Double();
			for (int point = 0; point < 8; point++) {
				double angle = point * Math.PI / 4;
				double radius = point % 2 == 0 ? size : size * 0.18;
				if (point == 0)
					glint.moveTo(x + Math.cos(angle) * radius, y + Math.sin(angle) * radius);
				else
					glint.lineTo(x + Math.cos(angle) * radius, y + Math.sin(angle) * radius);
			}
			glint.closePath();
			g.setColor(LIGHT);
			g.fill(glint);
		}
		badge(g, width, height, random);
		g.dispose();
		return image;
	}

	private static void badge(Graphics2D g, int width, int height, Random random)
	{
		Graphics2D badge = (Graphics2D) g.create();
		badge.translate(width / 2.0 + (random.nextInt(3) - 1) * 215, height - 45);
		badge.rotate((random.nextDouble() - 0.5) * 0.06);
		Shape plate = new RoundRectangle2D.Double(-116, -23, 232, 46, 10, 10);
		badge.setColor(new Color(14, 8, 3, 235));
		badge.fill(plate);
		badge.setColor(GOLD);
		badge.setStroke(new BasicStroke(2));
		badge.draw(plate);
		Font font = (random.nextBoolean() ? SplashTypography.Face.SERIF : SplashTypography.Face.SANS).font()
			.deriveFont(Map.of(TextAttribute.TRACKING, 0.18f));
		Shape outline = new TextLayout("DELUXE", font, badge.getFontRenderContext()).getOutline(null);
		Rectangle2D bounds = outline.getBounds2D();
		double scale = Math.min(193 / bounds.getWidth(), 27 / bounds.getHeight());
		AffineTransform transform = AffineTransform.getScaleInstance(scale, scale);
		transform.translate(-bounds.getCenterX(), -bounds.getCenterY());
		Shape words = transform.createTransformedShape(outline);
		badge.setColor(INK);
		badge.setStroke(new BasicStroke(2));
		badge.draw(words);
		badge.setPaint(new LinearGradientPaint(0, -15, 0, 15,
			new float[] { 0, 0.45f, 0.5f, 1 }, new Color[] { GOLD, LIGHT, GOLD, LIGHT }));
		badge.fill(words);
		badge.dispose();
	}
}
