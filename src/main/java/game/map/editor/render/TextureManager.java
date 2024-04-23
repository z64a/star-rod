package game.map.editor.render;

import static org.lwjgl.opengl.GL11.*;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.commons.io.FilenameUtils;
import org.lwjgl.BufferUtils;

import app.Directories;
import app.Resource;
import app.Resource.ResourceType;
import app.StarRodMain;
import assets.AssetHandle;
import assets.AssetManager;
import game.map.Map;
import game.map.editor.ui.SwingGUI;
import game.map.mesh.TexturedMesh;
import game.map.shape.Model;
import game.texture.ModelTexture;
import game.texture.Palette;
import game.texture.Texture;
import game.texture.TextureArchive;
import util.Logger;
import util.Priority;

/**
 * All GL textures must be bound and unbound using this class.
 * It is static because there can only be one GL context (Display is static).
 */
public abstract class TextureManager
{
	public static HashMap<String, ModelTexture> textureMap;
	public static List<ModelTexture> textureList;

	public static BufferedImage background;
	public static Image miniBackground;
	public static Palette missingPalette;
	public static int glBackground;
	public static int glMissingTextureID;
	public static int glNoBackgoundTexID;
	public static int glMarkerTexID;
	public static int glLightTexID;

	public static HashMap<String, Integer> textureCount;
	public static int untexturedCount = 0;

	static {
		background = loadEditorImage("tex_background.png");
		miniBackground = background.getScaledInstance(48, 48, java.awt.Image.SCALE_SMOOTH);

		textureMap = new HashMap<>();
		textureList = new LinkedList<>();
	}

	public static BufferedImage loadEditorImage(String resourceName)
	{
		InputStream is = Resource.getStream(ResourceType.EditorAsset, resourceName);
		if (is == null) {
			Logger.log("Unable to find resource " + resourceName, Priority.ERROR);
			return null;
		}

		try {
			return ImageIO.read(is);
		}
		catch (IOException e) {
			Logger.log("Unable to load resource " + resourceName, Priority.ERROR);
			StarRodMain.displayStackTrace(e);
			e.printStackTrace();
			return null;
		}
	}

	public static void bindEditorTextures()
	{
		BufferedImage image;

		image = loadEditorImage("notexture.png");
		glMissingTextureID = bindBufferedImage(image);

		missingPalette = Palette.createDefaultForEditor(16, 0.6f);
		missingPalette.glLoad();

		image = loadEditorImage("nobg.png");
		glNoBackgoundTexID = bindBufferedImage(image);

		image = loadEditorImage("block_mono.png");
		glMarkerTexID = bindBufferedImage(image);

		image = loadEditorImage("tex_light.png");
		glLightTexID = bindBufferedImage(image);

		glBackground = bindBufferedImage(background);
	}

	public static ModelTexture get(String name)
	{
		ModelTexture tex = textureMap.get(name);
		if (tex != null)
			return tex;

		if (name.endsWith("tif")) {
			name = name.substring(4, name.length() - 3);
			tex = textureMap.get(name);
		}

		return tex;
	}

	public static void clear()
	{
		for (ModelTexture t : textureList)
			t.clean();

		textureMap.clear();
		textureList.clear();
	}

	/**
	 * First part of loading a new texture archive. Loads the texture image files and
	 * binds them to openGL texture objects.
	 * @return successfully loaded all textures
	 */
	public static boolean load(String texArchiveName)
	{
		TextureArchive ta;

		try {
			AssetHandle ah = AssetManager.getTextureArchive(texArchiveName);
			if (!ah.exists())
				return false;
			if (FilenameUtils.getExtension(ah.getName()).equals(Directories.EXT_OLD_TEX)) {
				ta = TextureArchive.loadLegacy(ah);
			}
			else {
				ta = TextureArchive.load(ah);
			}
		}
		catch (IOException e) {
			Logger.log("Could not load texture archive " + texArchiveName);
			e.printStackTrace();
			return false;
		}

		int loaded = 0;
		for (Texture tx : ta.textureList) {
			ModelTexture texture = new ModelTexture(tx);
			textureMap.put(tx.name, texture);
			textureList.add(texture);
			loaded++;
		}

		Logger.log("Loaded " + loaded + " textures from " + texArchiveName);
		return true;
	}

	/**
	 * Second part of loading a new texture archive. Associates TexturedMeshes with a
	 * EditorTexture objects according to their textureName.
	 */
	public static void assignModelTextures(Map map)
	{
		for (ModelTexture tex : textureList)
			tex.modelCount = 0;
		untexturedCount = 0;

		for (Model mdl : map.modelTree) {
			ModelTexture tex = null;
			TexturedMesh mesh = mdl.getMesh();
			if (!mesh.textureName.isEmpty())
				tex = get(mesh.textureName);
			mesh.texture = tex;
			mesh.textured = (tex != null);

			if (mesh.textured)
				tex.modelCount++;
			else
				untexturedCount++;
		}
	}

	public static void increment(Model mdl)
	{
		if (mdl.hasMesh())
			increment(mdl.getMesh().texture);
	}

	public static void increment(ModelTexture texture)
	{
		if (texture != null)
			texture.modelCount++;
		else
			untexturedCount++;
		SwingGUI.instance().updateTextureCount(texture);
	}

	public static void decrement(Model mdl)
	{
		if (mdl.hasMesh())
			decrement(mdl.getMesh().texture);
	}

	public static void decrement(ModelTexture texture)
	{
		if (texture != null)
			texture.modelCount--;
		else
			untexturedCount--;
		SwingGUI.instance().updateTextureCount(texture);
	}

	public static int loadTexture(File f)
	{
		try {
			BufferedImage bimg = ImageIO.read(f);
			return (bimg == null) ? -1 : TextureManager.bindBufferedImage(bimg);
		}
		catch (IOException e) {
			Logger.logError(e.getMessage());
			return -1;
		}
	}

	public static int bindBufferedImage(BufferedImage image)
	{
		ByteBuffer buffer = createByteBuffer(image);

		int textureID = glGenTextures();
		glBindTexture(GL_TEXTURE_2D, textureID);

		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);

		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

		glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, image.getWidth(),
			image.getHeight(), 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
		return textureID;
	}

	public static ByteBuffer createByteBuffer(BufferedImage image)
	{
		int[] pixels = new int[image.getWidth() * image.getHeight()];
		image.getRGB(0, 0, image.getWidth(), image.getHeight(), pixels, 0, image.getWidth());

		ByteBuffer buffer = BufferUtils.createByteBuffer(image.getWidth() * image.getHeight() * 4);

		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				int pixel = pixels[(image.getHeight() - 1 - y) * image.getWidth() + x];
				buffer.put((byte) ((pixel >> 16) & 0xFF));
				buffer.put((byte) ((pixel >> 8) & 0xFF));
				buffer.put((byte) (pixel & 0xFF));
				buffer.put((byte) ((pixel >> 24) & 0xFF));
			}
		}

		buffer.flip();

		return buffer;
	}
}
