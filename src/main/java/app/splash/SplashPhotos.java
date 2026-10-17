package app.splash;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import javax.imageio.ImageIO;

/** Explicit catalog keeps legacy splash artwork and license files out of the photo pool. */
public final class SplashPhotos
{

	private static final String ROOT = "/splash/photos/";

	public record Photo(String filename, String photographer, String source)
	{}

	public record Splash(BufferedImage image, List<Photo> photos)
	{}

	private SplashPhotos()
	{}

	public static List<Photo> catalog() throws IOException
	{
		List<Photo> photos = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(open("catalog.tsv"), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank() || line.startsWith("#"))
					continue;
				String[] fields = line.split("\t");
				if (fields.length != 3 || !fields[0].matches("[a-z0-9-]+\\.jpg"))
					throw new IOException("Invalid splash photo catalog entry");
				photos.add(new Photo(fields[0], fields[1], fields[2]));
			}
		}
		if (photos.isEmpty())
			throw new IOException("Empty splash photo catalog");
		return List.copyOf(photos);
	}

	public static BufferedImage read(Photo photo) throws IOException
	{
		try (InputStream stream = open(photo.filename())) {
			BufferedImage image = ImageIO.read(stream);
			if (image == null)
				throw new IOException("Invalid splash photo: " + photo.filename());
			return image;
		}
	}

	public static Splash create(long seed, int unhinged) throws IOException
	{
		return create(seed, unhinged, false);
	}

	public static Splash createBanner(long seed, int unhinged) throws IOException
	{
		return create(seed, unhinged, true);
	}

	public static Splash createDeluxe(long seed, int unhinged) throws IOException
	{
		Splash base = create(seed, unhinged);
		return new Splash(DeluxeSplashRenderer.render(base.image(), seed, unhinged), base.photos());
	}

	private static Splash create(long seed, int unhinged, boolean banner) throws IOException
	{
		List<Photo> pool = new ArrayList<>(catalog());
		Collections.shuffle(pool, new Random(seed));
		int count = Math.min(pool.size(), 1 + Math.max(0, Math.min(100, unhinged)) / 30);
		List<Photo> selected = List.copyOf(pool.subList(0, count));
		List<BufferedImage> images = new ArrayList<>();
		for (Photo photo : selected) {
			if (Thread.currentThread().isInterrupted())
				throw new IOException("Splash rendering cancelled");
			images.add(read(photo));
		}
		BufferedImage image = banner ? SplashRenderer.renderBanner(images, seed, unhinged) : SplashRenderer.render(images, seed, unhinged);
		return new Splash(image, selected);
	}

	private static InputStream open(String name) throws IOException
	{
		InputStream stream = SplashPhotos.class.getResourceAsStream(ROOT + name);
		if (stream == null)
			throw new IOException("Missing splash resource: " + name);
		return stream;
	}
}
