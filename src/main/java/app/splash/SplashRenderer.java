package app.splash;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.List;
import java.util.Random;

import app.splash.SplashTypography.Title;

/** Bounded, offline Java2D collage renderer. A seed can be replayed in the splash preview. */
public final class SplashRenderer
{

	public static final int WIDTH = 768;
	public static final int HEIGHT = 512;
	public static final int BANNER_WIDTH = 480;
	public static final int BANNER_HEIGHT = 144;
	public static final int DEFAULT_UNHINGED = 50;
	static final int STYLE_COUNT = 9;

	private static final Color INK = new Color(17, 13, 28);
	private static final Color PAPER = new Color(255, 245, 216);
	private static final Color[][] PALETTES = {
			{ new Color(255, 105, 65), new Color(61, 225, 214) },
			{ new Color(254, 83, 163), new Color(255, 223, 88) },
			{ new Color(175, 140, 255), new Color(105, 239, 255) },
			{ new Color(215, 255, 76), new Color(251, 91, 165) },
			{ new Color(255, 188, 110), new Color(115, 164, 255) },
			{ new Color(253, 53, 79), new Color(255, 237, 168) },
			{ new Color(112, 239, 179), new Color(250, 165, 185) },
			{ new Color(255, 119, 37), new Color(96, 69, 196) }
	};

	private SplashRenderer()
	{}

	public static BufferedImage render(List<BufferedImage> photos, long seed, int unhinged)
	{
		Random type = new Random(seed ^ 0x59E0213BL);
		int style = type.nextInt(STYLE_COUNT);
		if (unhinged < 30)
			style %= 3;
		Title wording = SplashTypography.title(new Random(seed ^ 0x48F6B2A9L).nextInt(100));
		return render(photos, seed, unhinged, style, wording, SplashTypography.chooseFont(type, style, wording));
	}

	// Also used by the visual review tool to exercise every title, font and layout.
	static BufferedImage render(List<BufferedImage> photos, long seed, int unhinged, int style, Title wording, Font font)
	{
		double chaos = Math.max(0, Math.min(100, unhinged)) / 100.0;
		Random composition = new Random(seed ^ 0x73BFA91DL);
		Random effects = new Random(seed ^ 0x32A1C9EFL);
		Random type = new Random(seed ^ 0x59E0213BL);
		Color[] palette = PALETTES[composition.nextInt(PALETTES.length)];
		BufferedImage canvas = background(photos, seed, chaos, composition, effects, palette);
		Graphics2D g = graphics(canvas);
		if (chaos > 0.35)
			g.setStroke(new BasicStroke(2));

		switch (style) {
			case 0 -> neon(g, type, chaos, palette, wording, font);
			case 1 -> monument(g, type, chaos, palette, wording, font);
			case 2 -> echo(g, type, chaos, palette, wording, font);
			case 3 -> ransom(g, type, chaos, palette, wording);
			case 4 -> orbit(g, type, chaos, palette, wording, font);
			case 5 -> wave(g, type, chaos, palette, wording, font);
			case 6 -> sticker(g, type, chaos, palette, wording, font);
			case 7 -> marquee(g, type, chaos, palette, wording, font);
			case 8 -> ribbons(g, type, chaos, palette, wording, font);
			default -> throw new IllegalArgumentException("Unknown splash title style: " + style);
		}
		if (chaos > 0.65 && effects.nextBoolean()) {
			g.setColor(new Color(8, 3, 20, 30));
			for (int y = 0; y < HEIGHT; y += 4)
				g.drawLine(0, y, WIDTH, y);
		}
		g.dispose();
		return canvas;
	}

	/** Crops the photo composition, then lays out fresh lettering for a shallow banner. */
	public static BufferedImage renderBanner(List<BufferedImage> photos, long seed, int unhinged)
	{
		double chaos = Math.max(0, Math.min(100, unhinged)) / 100.0;
		Random composition = new Random(seed ^ 0x73BFA91DL);
		Random effects = new Random(seed ^ 0x32A1C9EFL);
		Random type = new Random(seed ^ 0x59E0213BL);
		Color[] palette = PALETTES[composition.nextInt(PALETTES.length)];
		BufferedImage backdrop = background(photos, seed, chaos, composition, effects, palette);
		BufferedImage banner = new BufferedImage(BANNER_WIDTH, BANNER_HEIGHT, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = graphics(banner);
		cover(g, backdrop, 0, 0, BANNER_WIDTH, BANNER_HEIGHT, 0.5, 0.5);
		g.translate(BANNER_WIDTH / 2.0, BANNER_HEIGHT / 2.0);
		g.rotate((type.nextDouble() - 0.5) * 0.07 * chaos);
		int style = type.nextInt(4);
		int fontStyle = switch (style) {
			case 0 -> 0;
			case 1 -> 2;
			case 2 -> 6;
			default -> 8;
		};
		String message = SplashTypography.LOADING_TITLES.get(new Random(seed ^ 0x48F6B2A9L).nextInt(SplashTypography.LOADING_TITLES.size()));
		Font font = SplashTypography.chooseFont(type, fontStyle, message);
		Shape words = text(g, message, font, 408, 66);
		switch (style) {
			case 0 -> {
				for (int stroke = 18; stroke >= 6; stroke -= 4) {
					g.setColor(new Color(palette[0].getRed(), palette[0].getGreen(), palette[0].getBlue(), 45));
					g.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
					g.draw(words);
				}
				fillOutlined(g, words, INK, PAPER, 2);
			}
			case 1 -> {
				for (int i = 1 + (int) (chaos * 3); i > 0; i--) {
					Shape echo = AffineTransform.getTranslateInstance(i * 3, -i * 7).createTransformedShape(words);
					g.setColor(palette[i % 2]);
					g.setStroke(new BasicStroke(1.5f));
					g.draw(echo);
				}
				fillOutlined(g, words, PAPER, INK, 5);
			}
			case 2 -> {
				Shape shadow = AffineTransform.getTranslateInstance(5, 7).createTransformedShape(words);
				fillOutlined(g, shadow, INK, INK, 12);
				fillOutlined(g, words, palette[0], PAPER, 10);
				g.setColor(INK);
				g.setStroke(new BasicStroke(1.2f));
				g.draw(words);
			}
			case 3 -> {
				g.setColor(palette[0]);
				g.fillRect(-BANNER_WIDTH, -43, BANNER_WIDTH * 2, 86);
				g.setColor(PAPER);
				g.fillRect(-BANNER_WIDTH, -37, BANNER_WIDTH * 2, 74);
				g.setColor(INK);
				g.fill(words);
			}
		}
		g.dispose();
		return banner;
	}

	private static BufferedImage background(List<BufferedImage> photos, long seed, double chaos,
		Random composition, Random effects, Color[] palette)
	{
		BufferedImage canvas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = graphics(canvas);
		g.setPaint(new GradientPaint(0, 0, INK, WIDTH, HEIGHT, palette[0].darker().darker()));
		g.fillRect(0, 0, WIDTH, HEIGHT);
		if (!photos.isEmpty()) {
			cover(g, photos.get(0), 0, 0, WIDTH, HEIGHT, composition.nextDouble(), composition.nextDouble());
			int layers = Math.min(photos.size() - 1, (int) (chaos * 100) / 30);
			for (int i = 0; i < layers; i++)
				collage(g, photos.get(i + 1), composition, chaos, palette, i);
		}
		g.dispose();
		grade(canvas, effects, chaos, palette);
		SplashEffects.apply(canvas, new Random(seed ^ 0x1568C2D9L), chaos, palette);
		if (chaos > 0.6)
			distort(canvas, effects, chaos);
		g = graphics(canvas);
		g.setPaint(new GradientPaint(0, 100, new Color(0, 0, 0, 0), 0, 400, new Color(9, 5, 20, 90)));
		g.fillRect(0, 0, WIDTH, HEIGHT);
		if (chaos > 0.35)
			ornaments(g, composition, chaos, palette);
		g.dispose();
		return canvas;
	}

	private static Graphics2D graphics(BufferedImage image)
	{
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		return g;
	}

	private static void cover(Graphics2D g, BufferedImage photo, double x, double y, double w, double h, double focusX, double focusY)
	{
		double scale = Math.max(w / photo.getWidth(), h / photo.getHeight());
		Graphics2D clipped = (Graphics2D) g.create();
		clipped.clip(new Rectangle2D.Double(x, y, w, h));
		AffineTransform transform = AffineTransform.getTranslateInstance(
			x - (photo.getWidth() * scale - w) * focusX, y - (photo.getHeight() * scale - h) * focusY);
		transform.scale(scale, scale);
		clipped.drawImage(photo, transform, null);
		clipped.dispose();
	}

	private static void collage(Graphics2D g, BufferedImage photo, Random random, double chaos, Color[] palette, int layer)
	{
		Graphics2D part = (Graphics2D) g.create();
		int mode = random.nextInt(7);
		double cx = 150 + random.nextDouble() * 470;
		double cy = 110 + random.nextDouble() * 290;
		double size = 210 + random.nextDouble() * 160;
		if (mode == 0) {
			// A second world bleeding through the first, with a feathered diagonal edge.
			BufferedImage exposure = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
			Graphics2D eg = graphics(exposure);
			cover(eg, photo, 0, 0, WIDTH, HEIGHT, 0.5, 0.5);
			eg.setComposite(AlphaComposite.DstIn);
			eg.setPaint(new GradientPaint(80, 0, new Color(255, 255, 255, 0), 650, HEIGHT, new Color(255, 255, 255, 230)));
			eg.fillRect(0, 0, WIDTH, HEIGHT);
			eg.dispose();
			part.drawImage(exposure, 0, 0, null);
		}
		else if (mode == 1) {
			Shape portal = new Ellipse2D.Double(cx - size / 2, cy - size / 2, size, size);
			part.setColor(palette[layer % 2]);
			part.setStroke(new BasicStroke(10));
			part.draw(portal);
			part.clip(portal);
			cover(part, photo, cx - size / 2, cy - size / 2, size, size, 0.5, 0.5);
		}
		else if (mode == 2) {
			// Ragged strips suggest physically cut photographs instead of rectangular UI cards.
			Path2D edge = new Path2D.Double();
			edge.moveTo(cx - 140, -20);
			for (int y = -20; y <= HEIGHT + 40; y += 24)
				edge.lineTo(cx - 140 + Math.sin(y * 0.01) * 50 + random.nextInt(22), y);
			edge.lineTo(WIDTH + 20, HEIGHT + 40);
			edge.lineTo(WIDTH + 20, -20);
			edge.closePath();
			part.setColor(PAPER);
			part.setStroke(new BasicStroke(7));
			part.draw(edge);
			part.clip(edge);
			cover(part, photo, cx - 200, 0, WIDTH - cx + 200, HEIGHT, 0.5, 0.5);
		}
		else if (mode == 3) {
			part.translate(cx, cy);
			part.rotate((random.nextDouble() - 0.5) * chaos);
			part.setColor(new Color(0, 0, 0, 95));
			part.fill(new Rectangle2D.Double(-size / 2 + 9, -size * 0.35 + 9, size, size * 0.7));
			part.setColor(PAPER);
			part.fill(new Rectangle2D.Double(-size / 2 - 7, -size * 0.35 - 7, size + 14, size * 0.7 + 14));
			cover(part, photo, -size / 2, -size * 0.35, size, size * 0.7, 0.5, 0.5);
		}
		else if (mode == 4) {
			// Slats interleave a second scene with the background like a lenticular print.
			for (int x = 0; x < WIDTH; x += 100) {
				Graphics2D slat = (Graphics2D) part.create();
				slat.clipRect(x, 0, 38 + (int) (chaos * 25), HEIGHT);
				cover(slat, photo, 0, 0, WIDTH, HEIGHT, 0.5, 0.5);
				slat.dispose();
			}
		}
		else if (mode == 5) {
			Path2D burst = new Path2D.Double();
			for (int point = 0; point < 24; point++) {
				double angle = point * Math.PI / 12;
				double radius = size * (point % 2 == 0 ? 0.65 : 0.4);
				double x = cx + Math.cos(angle) * radius, y = cy + Math.sin(angle) * radius;
				if (point == 0)
					burst.moveTo(x, y);
				else
					burst.lineTo(x, y);
			}
			burst.closePath();
			part.setColor(PAPER);
			part.setStroke(new BasicStroke(9));
			part.draw(burst);
			part.clip(burst);
			cover(part, photo, cx - size * 0.65, cy - size * 0.65, size * 1.3, size * 1.3, 0.5, 0.5);
		}
		else {
			part.translate(cx, HEIGHT / 2.0);
			part.rotate((random.nextDouble() - 0.5) * 0.55);
			part.setColor(INK);
			part.fillRect(-130, -HEIGHT, 260, HEIGHT * 2);
			for (int y = -HEIGHT; y < HEIGHT; y += 164)
				cover(part, photo, -110, y + 6, 220, 148, 0.5, 0.5);
			part.setColor(PAPER);
			for (int y = -HEIGHT; y < HEIGHT; y += 28) {
				part.fillRoundRect(-124, y, 9, 15, 3, 3);
				part.fillRoundRect(115, y, 9, 15, 3, 3);
			}
		}
		part.dispose();
	}

	private static void grade(BufferedImage canvas, Random random, double chaos, Color[] palette)
	{
		int[] pixels = ((DataBufferInt) canvas.getRaster().getDataBuffer()).getData();
		double contrast = 1.04 + chaos * 0.36;
		double tint = 0.04 + chaos * 0.14;
		boolean duotone = random.nextDouble() < chaos * 0.32;
		boolean solarize = chaos > 0.8 && random.nextBoolean();
		int grain = 2 + (int) (chaos * 11);
		for (int y = 0; y < HEIGHT; y++) {
			double dy = (y - HEIGHT / 2.0) / HEIGHT;
			for (int x = 0; x < WIDTH; x++) {
				int i = y * WIDTH + x;
				int p = pixels[i];
				int r = (p >> 16) & 255, green = (p >> 8) & 255, b = p & 255;
				double lum = (r * 0.2126 + green * 0.7152 + b * 0.0722) / 255;
				Color tone = palette[lum > 0.5 ? 0 : 1];
				if (duotone) {
					r = (int) (palette[0].getRed() * lum + palette[1].getRed() * (1 - lum) * 0.3);
					green = (int) (palette[0].getGreen() * lum + palette[1].getGreen() * (1 - lum) * 0.3);
					b = (int) (palette[0].getBlue() * lum + palette[1].getBlue() * (1 - lum) * 0.3);
				}
				if (solarize) {
					r = r > 160 ? 320 - r : r;
					green = green > 160 ? 320 - green : green;
					b = b > 160 ? 320 - b : b;
				}
				double dx = (x - WIDTH / 2.0) / WIDTH;
				double vignette = 1 - (dx * dx + dy * dy) * 0.65;
				int noise = random.nextInt(grain * 2 + 1) - grain;
				r = channel(((r - 128) * contrast + 128 + tone.getRed() * tint) * vignette + noise);
				green = channel(((green - 128) * contrast + 128 + tone.getGreen() * tint) * vignette + noise);
				b = channel(((b - 128) * contrast + 128 + tone.getBlue() * tint) * vignette + noise);
				pixels[i] = (r << 16) | (green << 8) | b;
			}
		}
	}

	private static int channel(double value)
	{
		return Math.max(0, Math.min(255, (int) value));
	}

	private static void distort(BufferedImage canvas, Random random, double chaos)
	{
		int[] target = ((DataBufferInt) canvas.getRaster().getDataBuffer()).getData();
		int[] source = target.clone();
		int shift = 1 + (int) ((chaos - 0.6) * 17);
		boolean mirror = random.nextDouble() < (chaos - 0.6);
		boolean wave = random.nextBoolean();
		double phase = random.nextDouble() * Math.PI * 2;
		for (int y = 0; y < HEIGHT; y++) {
			int wobble = wave ? (int) (Math.sin(y * 0.038 + phase) * shift * 2) : 0;
			for (int x = 0; x < WIDTH; x++) {
				int sx = mirror && x > WIDTH / 2 ? WIDTH - 1 - x : x;
				sx = Math.floorMod(sx + wobble, WIDTH);
				int red = source[y * WIDTH + Math.floorMod(sx + shift, WIDTH)] & 0xFF0000;
				int green = source[y * WIDTH + sx] & 0x00FF00;
				int blue = source[y * WIDTH + Math.floorMod(sx - shift, WIDTH)] & 0x0000FF;
				target[y * WIDTH + x] = red | green | blue;
			}
		}
	}

	private static void ornaments(Graphics2D g, Random random, double chaos, Color[] palette)
	{
		g.setStroke(new BasicStroke(2));
		for (int i = 0; i < (int) (chaos * 13); i++) {
			double x = random.nextInt(WIDTH), y = random.nextInt(HEIGHT);
			double radius = 4 + random.nextInt(22);
			g.setColor(palette[i % 2]);
			if (i % 3 == 0)
				g.draw(new Ellipse2D.Double(x - radius, y - radius, radius * 2, radius * 2));
			else {
				Path2D star = new Path2D.Double();
				for (int point = 0; point < 8; point++) {
					double a = Math.PI * point / 4;
					double r = point % 2 == 0 ? radius : radius * 0.2;
					if (point == 0)
						star.moveTo(x + Math.cos(a) * r, y + Math.sin(a) * r);
					else
						star.lineTo(x + Math.cos(a) * r, y + Math.sin(a) * r);
				}
				star.closePath();
				g.fill(star);
			}
		}
	}

	/** Glyph outlines let every type treatment share sizing, strokes and transforms. */
	private static Shape text(Graphics2D g, String value, Font font, double width, double height)
	{
		Shape outline = new TextLayout(value, font, g.getFontRenderContext()).getOutline(null);
		Rectangle2D bounds = outline.getBounds2D();
		double scale = Math.min(width / bounds.getWidth(), height / bounds.getHeight());
		AffineTransform transform = new AffineTransform();
		transform.scale(scale, scale);
		transform.translate(-bounds.getCenterX(), -bounds.getCenterY());
		return transform.createTransformedShape(outline);
	}

	/** Shared cap-height keeps lowercase and punctuation from expanding to full-size capitals. */
	private static Shape letter(Graphics2D g, char value, Font font, double width, double height)
	{
		Shape outline = new TextLayout(String.valueOf(value), font, g.getFontRenderContext()).getOutline(null);
		Rectangle2D bounds = outline.getBounds2D();
		double capHeight = new TextLayout("STAR", font, g.getFontRenderContext()).getBounds().getHeight();
		double extent = Math.max(Math.abs(bounds.getMinY() + capHeight / 2), Math.abs(bounds.getMaxY() + capHeight / 2));
		double scale = Math.min(width / Math.max(1, bounds.getWidth()), height / Math.max(capHeight, extent * 2));
		AffineTransform transform = AffineTransform.getScaleInstance(scale, scale);
		transform.translate(-bounds.getCenterX(), capHeight / 2);
		return transform.createTransformedShape(outline);
	}

	private static Graphics2D titleSpace(Graphics2D g, Random random, double chaos)
	{
		Graphics2D title = (Graphics2D) g.create();
		title.translate(WIDTH / 2.0, HEIGHT / 2.0 + (random.nextDouble() - 0.5) * 65 * chaos);
		title.rotate((random.nextDouble() - 0.5) * 0.24 * chaos);
		return title;
	}

	private static void fillOutlined(Graphics2D g, Shape shape, Color fill, Color outline, float width)
	{
		g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(outline);
		g.draw(shape);
		g.setColor(fill);
		g.fill(shape);
	}

	private static void neon(Graphics2D g, Random random, double chaos, Color[] palette, Title wording, Font font)
	{
		Graphics2D title = titleSpace(g, random, chaos);
		Shape words = text(title, wording.text(), font, 620, 130);
		if (chaos > 0.65) {
			for (int i = 1; i <= 3; i++) {
				Graphics2D ghost = (Graphics2D) title.create();
				ghost.translate(-i * 7, -i * 38);
				ghost.setColor(new Color(palette[1].getRed(), palette[1].getGreen(), palette[1].getBlue(), 100));
				ghost.setStroke(new BasicStroke(1.5f));
				ghost.draw(words);
				ghost.dispose();
			}
		}
		for (int stroke = 26; stroke >= 6; stroke -= 4) {
			title.setColor(new Color(palette[0].getRed(), palette[0].getGreen(), palette[0].getBlue(), 28));
			title.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			title.draw(words);
		}
		fillOutlined(title, words, new Color(18, 11, 24, 205), palette[0], 5);
		title.setColor(PAPER);
		title.setStroke(new BasicStroke(1.5f));
		title.draw(words);
		title.dispose();
	}

	private static void monument(Graphics2D g, Random random, double chaos, Color[] palette, Title wording, Font font)
	{
		Graphics2D title = titleSpace(g, random, chaos);
		title.shear(-0.13 * chaos, 0);
		for (int row = 0; row < 2; row++) {
			Shape word = text(title, row == 0 ? wording.top() : wording.bottom(), font, 490, 140);
			word = AffineTransform.getTranslateInstance(0, row == 0 ? -78 : 78).createTransformedShape(word);
			for (int depth = 16 + (int) (chaos * 15); depth > 0; depth -= 2) {
				Shape shadow = AffineTransform.getTranslateInstance(depth * 0.8, depth).createTransformedShape(word);
				fillOutlined(title, shadow, palette[1].darker(), INK, 3);
			}
			fillOutlined(title, word, PAPER, INK, 5);
		}
		title.dispose();
	}

	private static void echo(Graphics2D g, Random random, double chaos, Color[] palette, Title wording, Font font)
	{
		Graphics2D title = titleSpace(g, random, chaos);
		Shape words = text(title, wording.text(), font, 610, 115);
		int count = 1 + (int) (chaos * 6);
		for (int i = count; i >= 1; i--) {
			Graphics2D echo = (Graphics2D) title.create();
			echo.translate(i * 7, -i * 23);
			echo.scale(1 - i * 0.055, 1 - i * 0.055);
			echo.setColor(palette[i % 2]);
			echo.setStroke(new BasicStroke(2));
			echo.draw(words);
			echo.dispose();
		}
		fillOutlined(title, words, PAPER, INK, 8);
		title.dispose();
	}

	private static void ransom(Graphics2D g, Random random, double chaos, Color[] palette, Title wording)
	{
		Graphics2D title = titleSpace(g, random, chaos);
		String value = wording.text();
		double advance = 600 / (value.length() - value.chars().filter(c -> c == ' ').count() * 0.55);
		double x = -300;
		for (int i = 0; i < value.length(); i++) {
			if (value.charAt(i) == ' ') {
				x += advance * 0.45;
				continue;
			}
			Graphics2D letter = (Graphics2D) title.create();
			letter.translate(x + advance / 2, (random.nextDouble() - 0.5) * chaos * 85);
			letter.rotate((random.nextDouble() - 0.5) * chaos * 0.7);
			letter.setColor(new Color(0, 0, 0, 120));
			letter.fill(new Rectangle2D.Double(-advance / 2 + 6, -54, advance, 130));
			Color paper = i % 3 == 0 ? palette[0] : i % 3 == 1 ? PAPER : palette[1];
			letter.setColor(paper);
			letter.fill(new Rectangle2D.Double(-advance / 2, -61, advance, 130));
			Shape shape = letter(letter, value.charAt(i), SplashTypography.chooseFont(random, 3, wording), advance * 0.78, 96);
			letter.setColor(INK);
			letter.fill(shape);
			letter.dispose();
			x += advance;
		}
		title.dispose();
	}

	private static void orbit(Graphics2D g, Random random, double chaos, Color[] palette, Title wording, Font font)
	{
		Graphics2D title = titleSpace(g, random, chaos);
		String ring = (wording.text() + " / ").repeat(3);
		double radius = 180 + chaos * 20;
		for (int i = 0; i < ring.length(); i++) {
			if (ring.charAt(i) == ' ')
				continue;
			double angle = i * Math.PI * 2 / ring.length() - Math.PI / 2;
			Graphics2D letter = (Graphics2D) title.create();
			letter.translate(Math.cos(angle) * radius, Math.sin(angle) * radius);
			letter.rotate(angle + Math.PI / 2);
			Shape shape = letter(letter, ring.charAt(i), SplashTypography.Face.MONO.font(), 24, 30);
			fillOutlined(letter, shape, palette[1], INK, 3);
			letter.dispose();
		}
		Shape words = text(title, wording.text(), font, 530, 100);
		fillOutlined(title, words, PAPER, INK, 7);
		title.dispose();
	}

	private static void wave(Graphics2D g, Random random, double chaos, Color[] palette, Title wording, Font font)
	{
		Graphics2D title = titleSpace(g, random, chaos);
		String value = wording.text();
		double advance = 620 / (value.length() - value.chars().filter(c -> c == ' ').count() * 0.55);
		double x = -310;
		double phase = random.nextDouble() * Math.PI * 2;
		for (int i = 0; i < value.length(); i++) {
			if (value.charAt(i) == ' ') {
				x += advance * 0.45;
				continue;
			}
			Graphics2D letter = (Graphics2D) title.create();
			letter.translate(x + advance / 2, Math.sin(i * 0.8 + phase) * chaos * 62);
			letter.rotate(Math.cos(i * 0.8 + phase) * chaos * 0.25);
			Shape shape = letter(letter, value.charAt(i), font, advance * 0.9, 125);
			for (int j = 5; j > 0; j--) {
				Shape shadow = AffineTransform.getTranslateInstance(j * 3, j * 5).createTransformedShape(shape);
				fillOutlined(letter, shadow, palette[j % 2], INK, 2);
			}
			fillOutlined(letter, shape, PAPER, INK, 5);
			letter.dispose();
			x += advance;
		}
		title.dispose();
	}

	private static void sticker(Graphics2D g, Random random, double chaos, Color[] palette, Title wording, Font font)
	{
		Graphics2D title = titleSpace(g, random, chaos);
		Shape words = text(title, wording.text(), font, 605, 155);
		Shape shadow = AffineTransform.getTranslateInstance(10, 14).createTransformedShape(words);
		fillOutlined(title, shadow, INK, INK, 22);
		fillOutlined(title, words, palette[0], PAPER, 21);
		title.setColor(INK);
		title.setStroke(new BasicStroke(1.8f));
		title.draw(words);
		title.dispose();
	}

	private static void marquee(Graphics2D g, Random random, double chaos, Color[] palette, Title wording, Font font)
	{
		Graphics2D title = titleSpace(g, random, chaos);
		title.setColor(new Color(15, 8, 21, 235));
		title.fill(new RoundRectangle2D.Double(-330, -100, 660, 200, 24, 24));
		title.setColor(palette[0]);
		title.setStroke(new BasicStroke(4));
		title.draw(new RoundRectangle2D.Double(-330, -100, 660, 200, 24, 24));
		for (int x = -310; x <= 310; x += 24) {
			title.setColor((x / 24) % 2 == 0 ? PAPER : palette[1]);
			title.fillOval(x - 3, -86, 7, 7);
			title.fillOval(x - 3, 79, 7, 7);
		}
		Shape words = text(title, wording.text(), font, 580, 100);
		fillOutlined(title, words, PAPER, palette[0], 3);
		title.dispose();
	}

	private static void ribbons(Graphics2D g, Random random, double chaos, Color[] palette, Title wording, Font font)
	{
		Graphics2D title = titleSpace(g, random, chaos);
		for (int row = -1; row <= 1; row++) {
			Graphics2D ribbon = (Graphics2D) title.create();
			ribbon.translate(0, row * (100 + chaos * 18));
			ribbon.rotate(row * (0.07 + chaos * 0.08));
			ribbon.setColor(row == 0 ? PAPER : palette[row < 0 ? 0 : 1]);
			ribbon.fillRect(-WIDTH, -47, WIDTH * 2, 94);
			String value = row == 0 ? wording.text() : (wording.text() + " / ").repeat(3);
			Shape words = text(ribbon, value, font, row == 0 ? 600 : 1100, row == 0 ? 75 : 48);
			ribbon.setColor(INK);
			ribbon.fill(words);
			ribbon.dispose();
		}
		title.dispose();
	}
}
