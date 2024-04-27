package game.texture;

import static game.texture.Texture.AUX;
import static game.texture.Texture.IMG;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL12.GL_TEXTURE_MAX_LEVEL;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import game.map.editor.render.TextureManager;
import renderer.shaders.scene.ModelShader;
import util.Logger;

/**
 * Wraps a {@link Texture} object to add preview thumbnail and gl texture ID.
 */
public class ModelTexture
{
	private final Texture tx;

	public BufferedImage mainPreview = null;
	public BufferedImage auxPreview = null;

	public int modelCount = 0;

	public ModelTexture(Texture tx)
	{
		this.tx = tx;

		BufferedImage mainImage = ImageConverter.convertToBufferedImage(tx.main);
		mainPreview = createPreview(mainImage);

		tx.main.glLoad(tx.hWrap[IMG], tx.vWrap[IMG], tx.hasMipmaps && tx.mipmapList.size() > 0);

		if (tx.main.palette != null)
			tx.main.palette.glLoad();

		if (tx.hasAux) {
			BufferedImage auxImage = ImageConverter.convertToBufferedImage(tx.aux);
			auxPreview = createPreview(auxImage);

			tx.aux.glLoad(tx.hWrap[AUX], tx.vWrap[AUX], false);

			if (tx.aux.palette != null)
				tx.aux.palette.glLoad();
		}

		if (tx.hasMipmaps && tx.mipmapList.size() > 0) {
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, tx.mipmapList.size());

			int curX = tx.main.width;
			int curY = tx.main.height;
			int lod = 1;
			for (Tile mm : tx.mipmapList) {
				if (mm.width * 2 == curX && mm.height * 2 == curY) {
					curX /= 2;
					curY /= 2;
				}
				else
					Logger.logfWarning(
						"Mipmap level %d for %s must be exactly half the size "
							+ "(in both dimensions) of the previous level.",
						lod, tx.name);

				mm.glMipmap(lod);
				lod++;
			}
		}
	}

	public String getName()
	{
		return tx.name;
	}

	public int getHeight()
	{
		return tx.main.height;
	}

	public int getWidth()
	{
		return tx.main.width;
	}

	public boolean hasAux()
	{
		return tx.hasAux;
	}

	public int getAuxHeight()
	{
		return tx.aux.height;
	}

	public int getAuxWidth()
	{
		return tx.aux.width;
	}

	public int getAuxCombine()
	{
		return tx.auxCombine;
	}

	public static float getScaleU(ModelTexture texture)
	{
		return (texture != null) ? 32.0f * texture.tx.main.width : 1024.0f;
	}

	public static float getScaleV(ModelTexture texture)
	{
		return (texture != null) ? 32.0f * texture.tx.main.height : 1024.0f;
	}

	public void clean()
	{
		tx.main.glDelete();

		if (tx.main.palette != null)
			tx.main.palette.glDelete();

		if (tx.hasAux) {
			tx.aux.glDelete();

			if (tx.aux.palette != null)
				tx.aux.palette.glDelete();
		}
	}

	private static BufferedImage createPreview(BufferedImage img)
	{
		/*
		if(img.getWidth() == 96 || img.getHeight() == 96)
		{
			return img; // maybe we should use a copy here?
		}
		*/

		float scaleFactor;
		if (img.getWidth() > img.getHeight())
			scaleFactor = 96.0f / img.getWidth();
		else
			scaleFactor = 96.0f / img.getHeight();

		int newX = (int) (scaleFactor * img.getWidth());
		int newY = (int) (scaleFactor * img.getHeight());

		BufferedImage preview = new BufferedImage(newX, newY, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = preview.createGraphics();
		g.drawImage(img, 0, 0, newX, newY, null);
		g.dispose();

		return preview;
	}

	public void setShaderParameters(ModelShader shader, boolean useFiltering, boolean useLOD)
	{
		int fmt = tx.main.format.type;
		shader.mainFmt.set(fmt);
		tx.main.glBind(shader.mainImg);

		shader.useFiltering.set(useFiltering && tx.filter);
		shader.enableLOD.set(useLOD && tx.hasMipmaps);

		if (fmt == TileFormat.TYPE_CI)
			tx.main.palette.glBind(shader.mainPal);
		else
			TextureManager.missingPalette.glBind(shader.mainPal);

		if (tx.hasAux) {
			fmt = tx.main.format.type;
			shader.auxFmt.set(fmt);
			tx.aux.glBind(shader.auxImg);

			if (fmt == TileFormat.TYPE_CI)
				tx.aux.palette.glBind(shader.auxPal);
			else
				TextureManager.missingPalette.glBind(shader.auxPal);

			switch (tx.auxCombine) {
				case 0x00:
				case 0x08:
					shader.auxCombineMode.set(1);
					break;
				case 0x0D:
					shader.auxCombineMode.set(2);
					break;
				case 0x10:
					shader.auxCombineMode.set(3);
					break;
				default:
					shader.auxCombineMode.set(0);
					break;
			}
		}
		else {
			shader.auxFmt.set(0);

			shader.auxImg.bind(TextureManager.glMissingTextureID);
			TextureManager.missingPalette.glBind(shader.auxPal);
		}
	}
}
