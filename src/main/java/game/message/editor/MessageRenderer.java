package game.message.editor;

import java.io.IOException;

import game.message.StringConstants;
import game.message.font.FontManager;
import game.message.font.FontType;
import renderer.shaders.ShaderManager;
import renderer.shaders.scene.FontShader;

public class MessageRenderer
{
	private static final int FONT_SIZE_X = 16;
	private static final int FONT_SIZE_Y = 16;

	public static void init() throws IOException
	{
		if (!FontManager.isLoaded())
			FontManager.loadData();

		if (!FontManager.isReadyForGL())
			FontManager.glLoad();
	}

	public void drawString(int posX, int posY, int size, String text)
	{
		FontShader shader = ShaderManager.use(FontShader.class);
		shader.noiseMode.set(0);
		shader.fadeAlpha.set(1.0f);
		shader.enableDropShadow.set(false);

		for (char c : text.toCharArray()) {
			int index = StringConstants.getIndex(c, true);
			if (index < 0)
				continue;

			int endX = posX + (int) (FONT_SIZE_X * size / 16.0f);
			int endY = posY - (int) (FONT_SIZE_Y * size / 16.0f);
			int charWidth = getCharWidth(FontType.Normal, 0, index, size / 16.0f);

			if (index < FontType.Normal.chars.numChars) {
				FontType.Normal.chars.images[index].glBind(shader.texture);
				FontType.Normal.chars.palettes[0x14].glBind(shader.palette);
				shader.setQuadTexCoords(0, 0, 1, 1);
				shader.setXYQuadCoords(posX, posY, endX, endY, 0); // y flipped
				shader.renderQuad();
			}
			posX += charWidth;
		}
	}

	private static int getCharWidth(FontType font, int subfont, int index, float stringScale)
	{
		if (index < 0xFA) {
			int width;
			if ((index == 0xF7) || (index == 0xF8) || (index == 0xF9))
				width = font.fullspaceWidth[subfont];
			else
				width = font.chars.widths[index];

			if (index == 0xF7)
				return (int) ((width * stringScale) * 0.6);
			if (index == 0xF8)
				return (int) (width * stringScale);
			if (index == 0xF9)
				return (int) ((width * stringScale) * 0.5);
			if (index >= 0xF0)
				return 0; // other control chars
			return (int) (width * stringScale);
		}
		return 0;
	}
}
