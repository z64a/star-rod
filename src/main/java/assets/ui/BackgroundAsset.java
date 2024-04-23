package assets.ui;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;

import javax.imageio.ImageIO;

import assets.AssetHandle;

public class BackgroundAsset extends AssetHandle
{
	public BufferedImage bimg;
	public BufferedImage thumbnail;

	public BackgroundAsset(AssetHandle asset)
	{
		super(asset);

		try {
			bimg = ImageIO.read(asset);
			thumbnail = resizeImage(bimg, 64);
		}
		catch (IOException e) {
			bimg = null;
		}
	}

	private static BufferedImage resizeImage(BufferedImage src, int targetHeight)
	{
		float ratio = ((float) src.getHeight() / (float) src.getWidth());
		int targetWidth = Math.round(targetHeight / ratio);

		BufferedImage bi = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
		Graphics2D g2d = bi.createGraphics();
		g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g2d.drawImage(src, 0, 0, targetWidth, targetHeight, null);
		g2d.dispose();
		return bi;
	}
}
