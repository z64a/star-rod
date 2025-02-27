package game.sprite;

import static game.texture.TileFormat.CI_4;
import static org.lwjgl.opengl.GL13.GL_CLAMP_TO_BORDER;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;

import javax.swing.ImageIcon;

import org.apache.commons.io.FilenameUtils;

import assets.AssetHandle;
import assets.AssetManager;
import game.texture.ImageConverter;
import game.texture.Palette;
import game.texture.Tile;

public class ImgAsset
{
	private AssetHandle source;

	public final Tile img;
	public SpritePalette boundPal;

	public transient BufferedImage previewImg;
	public transient ImageIcon icon;
	public transient ImageIcon tiny;

	// members used to lay out the editor sprite atlas
	public transient int atlasRow, atlasX, atlasY;
	public transient boolean inUse;

	public ImgAsset(AssetHandle ah) throws IOException
	{
		source = ah;
		img = Tile.load(ah, CI_4);
	}

	public void save() throws IOException
	{
		if (boundPal != null && boundPal.hasPal()) {
			img.palette = boundPal.getPal();
		}

		source = AssetManager.getTopLevel(source);
		img.savePNG(source.getAbsolutePath());
	}

	public String getFileName()
	{
		return source.getName();
	}

	public String getName()
	{
		return FilenameUtils.removeExtension(source.getName());
	}

	@Override
	public String toString()
	{
		return getName();
	}

	public Palette getPalette()
	{
		if (boundPal == null || !boundPal.hasPal()) {
			return img.palette;
		}
		else {
			return boundPal.getPal();
		}
	}

	public void loadEditorImages()
	{
		previewImg = ImageConverter.getIndexedImage(img, getPalette());
		icon = new ImageIcon(getScaledToFit(previewImg, 80));
		tiny = new ImageIcon(getScaledToFit(previewImg, 20));
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

	public void glLoad()
	{
		if (img != null) {
			img.glLoad(GL_CLAMP_TO_BORDER, GL_CLAMP_TO_BORDER, false);
			img.palette.glLoad();
		}
	}

	public void glDelete()
	{
		if (img != null) {
			img.glDelete();
			img.palette.glDelete();
		}
	}
}
