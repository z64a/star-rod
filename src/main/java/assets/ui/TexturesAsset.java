package assets.ui;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.apache.commons.io.FilenameUtils;

import app.input.IOUtils;
import assets.AssetHandle;
import assets.AssetSubdir;
import util.Logger;

public class TexturesAsset extends AssetHandle
{
	public BufferedImage bimg1;
	public BufferedImage bimg2;
	public BufferedImage bimg3;

	public TexturesAsset(AssetHandle asset)
	{
		super(asset);

		String dirName = FilenameUtils.getBaseName(asset.getName()) + "/";
		File dir = new File(asset.assetDir, AssetSubdir.MAP_TEX + dirName);

		try {
			Collection<File> images = IOUtils.getFilesWithExtension(dir, ".png", false);

			/*
			// pick three largest textures for previews (in terms of square pixels)
			// a bit slow and unnecessary
			TreeMap<Integer,File> sizeMap = new TreeMap<>();
			for (File imgFile : images) {
				sizeMap.put(getImageSize(imgFile), imgFile);
			}
			images = sizeMap.values();
			
			List<File> list = new ArrayList<>(images);
			Collections.reverse(list);
			*/

			// pick three textures randomly for previews
			List<File> list = new ArrayList<>(images);
			Collections.shuffle(list);

			if (list.size() > 0) {
				bimg1 = getPreview(list.get(0));
			}
			if (list.size() > 1) {
				bimg2 = getPreview(list.get(1));
			}
			if (list.size() > 2) {
				bimg3 = getPreview(list.get(2));
			}
		}
		catch (IOException e) {
			Logger.logError("IOException while gathering previews from " + dirName);
		}
	}

	private int getImageSize(File imgFile) throws IOException
	{
		try (ImageInputStream in = ImageIO.createImageInputStream(imgFile)) {
			final Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
			if (readers.hasNext()) {
				ImageReader reader = readers.next();
				try {
					reader.setInput(in);
					int width = reader.getWidth(0);
					int height = reader.getHeight(0);
					return width * height;
				}
				finally {
					reader.dispose();
				}
			}
		}
		return 0;
	}

	private static BufferedImage getPreview(File imgFile)
	{
		try {
			BufferedImage bimg = ImageIO.read(imgFile);
			if (bimg == null)
				return null;

			int targetWidth = 32;
			int targetHeight = 32;
			float ratio = (float) bimg.getHeight() / (float) bimg.getWidth();
			if (ratio <= 1.0f) {
				targetHeight = (int) Math.ceil(targetWidth * ratio);
			}
			else {
				targetWidth = Math.round(targetHeight / ratio);
			}

			BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
			Graphics2D g2d = resized.createGraphics();
			g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g2d.drawImage(bimg, 0, 0, targetWidth, targetHeight, null);
			g2d.dispose();
			return resized;
		}
		catch (IOException e) {
			return null;
		}
	}
}
