package renderer.text;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import javax.imageio.ImageIO;

import app.Resource;
import app.Resource.ResourceType;
import app.StarRodException;
import game.map.editor.render.TextureManager;

public class TextFont
{
	protected final String name;
	protected final int size;
	protected final int base;
	protected final int lineHeight;
	protected final int padding;

	private final BufferedImage bimg;
	protected final TextChar[] chars;

	private boolean glLoaded;
	protected int glTexID;

	public TextFont(String fontName)
	{
		this.name = fontName;

		int size = 50;
		int lineHeight = 50;
		int base = 50;
		int padding = 8;

		List<String> lines = Resource.getText(ResourceType.Font, fontName + ".fnt");
		try {
			bimg = ImageIO.read(Resource.getStream(ResourceType.Font, fontName + ".png"));
		}
		catch (IOException e) {
			throw new StarRodException(e);
		}

		chars = new TextChar[256];
		for (String line : lines) {
			if (line.startsWith("chars")) {
				// ignore
			}
			else if (line.startsWith("char")) {
				TextChar tc = new TextChar(line, bimg.getWidth(), bimg.getHeight());
				chars[tc.index] = tc;
			}
			else if (line.startsWith("info")) {
				line = line.trim().replaceAll("\".*?\"", "\"\""); // remove strings

				String[] tokens = line.trim().split("\\s+");

				if (!tokens[0].equals("info"))
					throw new IllegalArgumentException("Invalid line: " + line);

				for (int i = 1; i < tokens.length; i++) {
					int eqPos = tokens[i].indexOf("=");
					if (eqPos < 1)
						throw new IllegalArgumentException("Invalid line: " + line);

					String key = tokens[i].substring(0, eqPos);
					String value = tokens[i].substring(eqPos + 1);

					switch (key) {
						// @formatter:off
						case "size":		size = Integer.parseInt(value); break;
						case "base":		base = Integer.parseInt(value); break;
						case "lineHeight":	lineHeight = Integer.parseInt(value); break;
						case "padding":		padding = Integer.parseInt(value.substring(0, value.indexOf(","))); break;
						// @formatter:on
					}
				}
			}
		}

		this.size = size;
		this.base = base;
		this.lineHeight = lineHeight;
		this.padding = padding > 4 ? (padding - 2) : padding;
	}

	public void glLoad()
	{
		if (glLoaded)
			glDeleteTextures(glTexID);
		glLoaded = true;

		ByteBuffer buffer = TextureManager.createByteBuffer(bimg);

		glTexID = glGenTextures();
		glBindTexture(GL_TEXTURE_2D, glTexID);

		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

		glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, bimg.getWidth(),
			bimg.getHeight(), 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
	}

	public void glDelete()
	{
		if (glLoaded)
			glDeleteTextures(glTexID);
		glLoaded = false;
	}
}
