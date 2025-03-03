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
	public BufferedImage previewImg;
	public ImageIcon icon;
	public ImageIcon tiny;

	public void load(ImgAsset imgAsset, PalAsset palAsset)
	{
		load(imgAsset.img, palAsset.pal);
	}

	public void load(Tile img, Palette pal)
	{
		previewImg = ImageConverter.getIndexedImage(img, pal);
		icon = new ImageIcon(getScaledToFit(previewImg, 80));
		tiny = new ImageIcon(getScaledToFit(previewImg, 20));
	}

	public void set(ImgPreview other)
	{
		// shallow copy OK because these are not mutated
		previewImg = other.previewImg;
		icon = other.icon;
		tiny = other.tiny;
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
