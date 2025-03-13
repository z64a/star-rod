package game.sprite.editor;

import java.awt.Image;
import java.awt.image.BufferedImage;

import javax.swing.ImageIcon;

import game.sprite.ImgAsset;
import game.sprite.PalAsset;
import game.texture.ImageConverter;
import game.texture.Palette;
import game.texture.Tile;

public class ImgPreview
{
	private static final int TINY_SIZE = 20;

	private static final BufferedImage BLANK_IMG = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
	private static final ImageIcon BLANK_ICON = new ImageIcon(BLANK_IMG);
	private static final ImageIcon BLANK_TINY = new ImageIcon(getScaledToFit(BLANK_IMG, TINY_SIZE));

	private BufferedImage image;
	private ImageIcon icon;
	private ImageIcon tiny;

	public void load(ImgAsset imgAsset, PalAsset palAsset)
	{
		load(imgAsset.img, palAsset.pal);
	}

	public void load(ImgAsset imgAsset)
	{
		load(imgAsset.img, imgAsset.img.palette);
	}

	private void load(Tile img, Palette pal)
	{
		image = ImageConverter.getIndexedImage(img, pal);
		icon = new ImageIcon(getScaledToFit(image, 80));
		tiny = new ImageIcon(getScaledToFit(image, TINY_SIZE));
	}

	public void loadMissing()
	{
		image = BLANK_IMG;
		icon = BLANK_ICON;
		tiny = BLANK_TINY;
	}

	public void set(ImgPreview other)
	{
		// shallow copy OK because these are not mutated
		image = other.image;
		icon = other.icon;
		tiny = other.tiny;
	}

	public BufferedImage getImage()
	{
		return image;
	}

	public ImageIcon getFullIcon()
	{
		return icon;
	}

	public ImageIcon getTinyIcon()
	{
		return tiny;
	}

	private static Image getScaledToFit(BufferedImage in, int maxSize)
	{
		if (in.getHeight() > in.getWidth()) {
			if (in.getHeight() > maxSize) {
				return in.getScaledInstance(
					(int) (in.getWidth() * (maxSize / (float) in.getHeight())), maxSize, java.awt.Image.SCALE_SMOOTH);
			}
		}
		else {
			if (in.getWidth() > maxSize) {
				return in.getScaledInstance(maxSize,
					(int) (in.getHeight() * (maxSize / (float) in.getWidth())), java.awt.Image.SCALE_SMOOTH);
			}
		}

		return in.getScaledInstance(in.getWidth(), in.getHeight(), java.awt.Image.SCALE_DEFAULT);
	}

}
