package game.message.editor;

import static game.message.StringConstants.StringEffect.*;
import static game.texture.TileFormat.CI_8;
import static org.lwjgl.opengl.GL11.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.List;
import java.util.Random;

import org.lwjgl.BufferUtils;

import assets.ExpectedAsset;
import game.SimpleItem;
import game.map.Axis;
import game.map.editor.common.MouseInput;
import game.map.editor.geometry.Vector3f;
import game.map.editor.render.PresetColor;
import game.map.editor.render.TextureManager;
import game.map.shape.TransformMatrix;
import game.message.MessageBoxes;
import game.message.StringConstants;
import game.message.StringConstants.ControlCharacter;
import game.message.StringConstants.StringFunction;
import game.message.StringConstants.StringStyle;
import game.message.font.FontManager;
import game.message.font.FontType;
import game.sprite.ImgAsset;
import game.sprite.Sprite;
import game.sprite.SpriteLoader;
import game.sprite.SpriteLoader.SpriteSet;
import game.sprite.SpriteRaster;
import game.texture.ImageConverter;
import game.texture.Palette;
import game.texture.Texture;
import game.texture.Tile;
import renderer.GLUtils;
import renderer.buffers.LineRenderQueue;
import renderer.shaders.BaseShader;
import renderer.shaders.RenderState;
import renderer.shaders.ShaderManager;
import renderer.shaders.scene.BasicIndexedShader;
import renderer.shaders.scene.BasicSolidShader;
import renderer.shaders.scene.BasicTexturedShader;
import renderer.shaders.scene.FontShader;
import util.Logger;

public class StringRenderer
{
	private final MessageEditor editor;

	public final TransformMatrix projMatrix;
	public final TransformMatrix viewMatrix;

	private int glBackgroundTexID;
	private int glNoiseTexID;

	private Random rng;
	private boolean useFiltering = true;

	private int frameCounter = 0;

	// extra resources
	private final SpriteLoader spriteLoader;
	private Tile[] glItemPreviews = null;

	private BufferedImage varImage = null;
	private boolean varImageLoaded = false;
	private int glVarTexID;

	public StringRenderer(MessageEditor editor) throws IOException
	{
		this.editor = editor;

		projMatrix = new TransformMatrix();
		viewMatrix = new TransformMatrix();

		rng = new Random();
		loadTextures();
		loadItemIcons();

		spriteLoader = new SpriteLoader();
	}

	public void cleanup()
	{
		MessageBoxes.glDelete();
		FontManager.glDelete();

		glDeleteTextures(glBackgroundTexID);
		glDeleteTextures(glNoiseTexID);
		glDeleteTextures(glVarTexID);

		if (glItemPreviews != null) {
			for (Tile tile : glItemPreviews) {
				if (tile == null)
					continue;

				tile.glDelete();
				tile.palette.glDelete();
			}
		}
	}

	private void loadTextures() throws IOException
	{
		Tile img;
		BufferedImage bimg;

		glLoadNoise();

		// background
		try {
			File imgFile = ExpectedAsset.KMR_BG.getFile();
			img = Tile.load(imgFile, CI_8);
			bimg = ImageConverter.convertToBufferedImage(img);
			glBackgroundTexID = glLoadImage(bimg);
		}
		catch (IOException e) {
			Logger.logError("Could not load background asset: " + ExpectedAsset.KMR_BG.getPath());
		}

		MessageBoxes.loadImages();
		MessageBoxes.glLoad();

		FontManager.loadData();
		FontManager.glLoad();
	}

	private void glLoadNoise()
	{
		BufferedImage bimg = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
		for (int i = 0; i < 256; i++)
			for (int j = 0; j < 256; j++) {
				int I = rng.nextInt() & 0xFF;
				bimg.setRGB(i, j, (0xFF << 24) | (I << 16) | (I << 8) | I);
			}

		ByteBuffer buffer = TextureManager.createByteBuffer(bimg);

		int glID = glGenTextures();
		glBindTexture(GL_TEXTURE_2D, glID);

		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);

		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

		glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, bimg.getWidth(),
			bimg.getHeight(), 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);

		glNoiseTexID = glID;
	}

	private void loadItemIcons() throws IOException
	{
		List<SimpleItem> items = game.ProjectDatabase.getItemList();

		glItemPreviews = new Tile[items.size()];
		for (int i = 0; i < items.size(); i++) {
			Tile tile = items.get(i).iconTile;
			if (tile == null)
				continue;

			tile.glLoad(Texture.WRAP_CLAMP, Texture.WRAP_CLAMP, false);
			tile.palette.glLoad();

			glItemPreviews[i] = tile;
		}

		Logger.log("Loaded icon previews");
	}

	public void setVarImage(BufferedImage bimg)
	{
		if (varImageLoaded)
			glDeleteTextures(glVarTexID);

		glVarTexID = glLoadImage(bimg);
		varImage = bimg;
		varImageLoaded = true;
	}

	private static int glLoadImage(BufferedImage bimg)
	{
		ByteBuffer buffer = TextureManager.createByteBuffer(bimg);

		int glID = glGenTextures();
		glBindTexture(GL_TEXTURE_2D, glID);

		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);

		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

		glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, bimg.getWidth(),
			bimg.getHeight(), 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);

		return glID;
	}

	private final void drawBackground(int glTexID)
	{
		projMatrix.ortho(0.0f, 1.0f, 1.0f, 0.0f, 1.0f, -1.0f);
		RenderState.setProjectionMatrix(projMatrix);
		RenderState.setViewMatrix(null);

		float hfov = 105;
		float right = (hfov + 360) / 360;
		float scaleX = RenderState.getViewportSizeX() / 600.0f;

		BasicTexturedShader shader = ShaderManager.use(BasicTexturedShader.class);
		shader.texture.bind(glTexID);
		shader.setXYQuadCoords(0, 0, 1, 1, 0);
		shader.setQuadTexCoords(0, 1, right * scaleX, 0);
		shader.renderQuad();
	}

	private static void drawGrid(StringPrinter printer)
	{
		RenderState.setLineWidth(1.5f);

		int xstart = printer.windowBasePosX + printer.windowTextStartX;
		int ystart = printer.windowBasePosY + printer.windowTextStartY;
		int xmax = ((printer.clipMaxX - xstart) / 10) * 10;
		int ymax = ((printer.clipMaxY - ystart) / 10) * 10;

		for (int i = 0; i <= xmax; i += 10) {
			float alpha = ((i % 20) == 0) ? 0.5f : 0.2f;
			int v1 = LineRenderQueue.addVertex()
				.setPosition(xstart + i, ystart, 0)
				.setColor(0.15f, 0.15f, 0.15f, alpha)
				.getIndex();
			int v2 = LineRenderQueue.addVertex()
				.setPosition(xstart + i, ystart + ymax, 0)
				.setColor(0.15f, 0.15f, 0.15f, alpha)
				.getIndex();
			LineRenderQueue.addLine(v1, v2);
		}

		for (int i = 0; i <= ymax; i += 10) {
			float alpha = ((i % 20) == 0) ? 0.5f : 0.2f;
			int v1 = LineRenderQueue.addVertex()
				.setPosition(xstart, ystart + i, 0)
				.setColor(0.15f, 0.15f, 0.15f, alpha)
				.getIndex();
			int v2 = LineRenderQueue.addVertex()
				.setPosition(xstart + xmax, ystart + i, 0)
				.setColor(0.15f, 0.15f, 0.15f, alpha)
				.getIndex();
			LineRenderQueue.addLine(v1, v2);
		}

		RenderState.setDepthWrite(false);
		LineRenderQueue.render(true);
		RenderState.setDepthWrite(true);
	}

	private static void drawBackgroundGuide(StringPrinter printer)
	{
		int v1, v2, v3, v4;
		RenderState.setLineWidth(2.0f);

		RenderState.setColor(PresetColor.RED);
		v1 = LineRenderQueue.addVertex().setPosition(0, 0, 0).getIndex();
		v2 = LineRenderQueue.addVertex().setPosition(320, 0, 0).getIndex();
		v3 = LineRenderQueue.addVertex().setPosition(320, 240, 0).getIndex();
		v4 = LineRenderQueue.addVertex().setPosition(0, 240, 0).getIndex();
		LineRenderQueue.addLine(v1, v2, v3, v4, v1);

		RenderState.setColor(PresetColor.BLUE);
		v1 = LineRenderQueue.addVertex().setPosition(12, 20, 0).getIndex();
		v2 = LineRenderQueue.addVertex().setPosition(320 - 12, 20, 0).getIndex();
		v3 = LineRenderQueue.addVertex().setPosition(320 - 12, 240 - 20, 0).getIndex();
		v4 = LineRenderQueue.addVertex().setPosition(12, 240 - 20, 0).getIndex();
		LineRenderQueue.addLine(v1, v2, v3, v4, v1);

		//	LineRenderQueue.printContents();

		RenderState.setDepthWrite(false);
		LineRenderQueue.render(true);
		RenderState.setDepthWrite(true);
	}

	private static void drawForegroundGuide(StringPrinter printer)
	{
		int v1, v2, v3, v4;
		RenderState.setLineWidth(1.5f);

		RenderState.setColor(PresetColor.GREEN);
		v1 = LineRenderQueue.addVertex().setPosition(printer.windowBasePosX, printer.windowBasePosY, 0).getIndex();
		v2 = LineRenderQueue.addVertex().setPosition(printer.windowBasePosX, printer.windowBasePosY + printer.windowSizeY, 0).getIndex();
		v3 = LineRenderQueue.addVertex().setPosition(printer.windowBasePosX + printer.windowSizeX, printer.windowBasePosY + printer.windowSizeY, 0)
			.getIndex();
		v4 = LineRenderQueue.addVertex().setPosition(printer.windowBasePosX + printer.windowSizeX, printer.windowBasePosY, 0).getIndex();
		LineRenderQueue.addLine(v1, v2, v3, v4, v1);

		RenderState.setColor(PresetColor.TEAL);
		v1 = LineRenderQueue.addVertex().setPosition(printer.rewindArrowX, printer.rewindArrowY, 0).getIndex();
		v2 = LineRenderQueue.addVertex().setPosition(printer.rewindArrowX, printer.rewindArrowY + 24, 0).getIndex();
		v3 = LineRenderQueue.addVertex().setPosition(printer.rewindArrowX + 24, printer.rewindArrowY + 24, 0).getIndex();
		v4 = LineRenderQueue.addVertex().setPosition(printer.rewindArrowX + 24, printer.rewindArrowY, 0).getIndex();
		LineRenderQueue.addLine(v1, v2, v3, v4, v1);

		RenderState.setColor(PresetColor.YELLOW);
		v1 = LineRenderQueue.addVertex().setPosition(printer.clipMinX, printer.clipMinY, 0).getIndex();
		v2 = LineRenderQueue.addVertex().setPosition(printer.clipMinX, printer.clipMaxY, 0).getIndex();
		v3 = LineRenderQueue.addVertex().setPosition(printer.clipMaxX, printer.clipMaxY, 0).getIndex();
		v4 = LineRenderQueue.addVertex().setPosition(printer.clipMaxX, printer.clipMinY, 0).getIndex();
		LineRenderQueue.addLine(v1, v2, v3, v4, v1);

		RenderState.setDepthWrite(false);
		LineRenderQueue.render(true);
		RenderState.setDepthWrite(true);
	}

	private static final void drawAxes(float lineWidth)
	{
		RenderState.setLineWidth(lineWidth);

		RenderState.setColor(PresetColor.RED);
		LineRenderQueue.addLine(
			LineRenderQueue.addVertex().setPosition(0, 0, 0).getIndex(),
			LineRenderQueue.addVertex().setPosition(Short.MAX_VALUE, 0, 0).getIndex());

		RenderState.setColor(PresetColor.GREEN);
		LineRenderQueue.addLine(
			LineRenderQueue.addVertex().setPosition(0, 0, 0).getIndex(),
			LineRenderQueue.addVertex().setPosition(0, Short.MAX_VALUE, 0).getIndex());

		LineRenderQueue.render(true);
	}

	public Vector3f getMousePosition(MouseInput mouse, boolean useDepth)
	{
		int mouseX = mouse.getPosX();
		int mouseY = mouse.getPosY();

		int width = editor.glCanvasWidth();
		int height = editor.glCanvasHeight();

		IntBuffer viewport = BufferUtils.createIntBuffer(16);
		viewport.put(0);
		viewport.put(0);
		viewport.put(width);
		viewport.put(height);
		viewport.rewind();

		float winX = mouseX;
		float winY = mouseY;
		float winZ = 0;

		if (useDepth) {
			// this is the expensive part, reading z from the depth buffer
			FloatBuffer fb = BufferUtils.createFloatBuffer(1);
			glReadPixels(mouseX, mouseY, 1, 1, GL_DEPTH_COMPONENT, GL_FLOAT, fb);
			winZ = fb.get();
		}

		FloatBuffer position = BufferUtils.createFloatBuffer(3);
		GLUtils.gluUnProject(winX, winY, winZ, viewMatrix.toFloatBuffer(), projMatrix.toFloatBuffer(), viewport, position);

		return new Vector3f(position.get(), position.get(), position.get());
	}

	public void render(MouseInput mouse, StringPrinter printer, float cameraX, float cameraY, float cameraZoom, float cameraYaw)
	{
		assert (printer != null);

		drawBackground(glBackgroundTexID);

		float halfW = (editor.glCanvasWidth() / 2.0f) * cameraZoom;
		float halfH = (editor.glCanvasHeight() / 2.0f) * cameraZoom;
		projMatrix.ortho(-halfW, halfW, halfH, -halfH, 1, 0x20000);
		//	projMatrix.perspective(60, (float)editor.glCanvasWidth() / editor.glCanvasHeight(), 1, 0x2000);
		RenderState.setProjectionMatrix(projMatrix);

		viewMatrix.setIdentity();
		viewMatrix.translate(new Vector3f(-cameraX, -cameraY, -400.0f * cameraZoom));
		viewMatrix.rotate(Axis.Y, cameraYaw);
		RenderState.setViewMatrix(viewMatrix);
		RenderState.setModelMatrix(null);

		if (editor.shouldShowViewportGuides())
			drawBackgroundGuide(printer);

		// drawAxes(1.0f);

		DrawState state = new DrawState(printer);
		state.useCulling = editor.isCullingEnabled();

		printer.drawMessageBox();

		FontShader shader = ShaderManager.use(FontShader.class);
		shader.enableFiltering.set(useFiltering);
		shader.fadeAlpha.set(1.0f);

		float scrollY = printer.scrollAmount * (state.currentFont.chars.defaultY * state.stringScaleY + printer.style.lineOffset);
		int printOffsetX = 0;
		int printOffsetY = -(int) (scrollY);

		state.printPosX = 0;
		state.printPosY = 0;
		printer.drawBuffer.rewind();

		read_buf:
		while (printer.drawBuffer.position() < printer.printPos) {
			byte charByte = printer.drawBuffer.get();
			int charInt = charByte & 0xFF;

			if (charInt == StringPrinter.PAGE_END_MARK) {
				continue;
			}
			else if (charInt == StringPrinter.BUFFER_FILL) {
				break; // end of string
			}
			else if (charInt < 0xF0) {
				int charWidth = MessageUtil.getCharWidth(state.currentFont, state.fontVariant, charInt, state.stringScaleX, state.charWidthOverride,
					0);
				if (charInt < state.currentFont.chars.numChars)
					renderChar(printer, state, printOffsetX, printOffsetY, charInt);
				state.printPosX += charWidth;
			}
			else if (charInt == 0xF7 || charInt == 0xF8 || charInt == 0xF9) {
				int charWidth = MessageUtil.getCharWidth(state.currentFont, state.fontVariant, charInt, state.stringScaleX, state.charWidthOverride,
					0);
				state.printPosX += charWidth;
			}
			else {
				switch (ControlCharacter.decodeMap.get(charByte)) {
					case ENDL: // 0xF0
						state.printPosX = 0;
						state.printPosY += state.currentFont.chars.defaultY * state.stringScaleY + printer.style.lineOffset;
						break;

					case NEXT: // 0xFB
						break;

					case WAIT: // 0xF1
						throw new IllegalStateException(this.getClass().getSimpleName() + " should not encounter WAIT");

					case PAUSE: // 0xF2
						printer.drawBuffer.get();
						throw new IllegalStateException(this.getClass().getSimpleName() + " should not encounter PAUSE");

					case VARIANT0: // 0xF3
					case VARIANT1: // 0xF4
					case VARIANT2: // 0xF5
					case VARIANT3: // 0xF6
						state.fontVariant = charInt - 0xF3;
						break;

					// 0xF7-0xF9 are spaces
					// 0xFA = unknown

					case STYLE: // 0xFC
						handleStyle(printer, state);
						break;

					case END: // 0xFD
						break read_buf;

					// 0xFE = unused afaik
					case FUNC: // 0xFF
						handleFunction(printer, state, printOffsetX, printOffsetY);
						break;
				}
			}
		}

		frameCounter++;

		RenderState.setModelMatrix(null);
		if (editor.shouldShowGrid())
			drawGrid(printer);

		// drawForegroundGuide(printer);

		drawUI(mouse, printer);
	}

	private void drawUI(MouseInput mouse, StringPrinter printer)
	{
		// set UI matrices
		TransformMatrix mtx = TransformMatrix.identity();
		mtx.ortho(0, editor.glCanvasWidth(), 0, editor.glCanvasHeight(), -1, 1);
		RenderState.setProjectionMatrix(mtx);
		RenderState.setViewMatrix(null);
		RenderState.setModelMatrix(null);

		RenderState.enableDepthTest(false);

		if (mouse.hasLocation()) {
			Vector3f worldPos = getMousePosition(mouse, false);
			int mx = Math.round(worldPos.x) - (printer.windowBasePosX + printer.windowTextStartX);
			int my = Math.round(worldPos.y) - (printer.windowBasePosY + printer.windowTextStartY);
			drawString(0x14, 8, editor.glCanvasHeight() - 4, 16, String.format("Cursor Pos:  %3d, %2d", mx, my));
		}

		float bufferFillPercent = 100.0f * printer.compiledLength / StringPrinter.MAX_LENGTH;

		if (bufferFillPercent > 100.0f) {
			drawString(0x12, 176, editor.glCanvasHeight() - 4, 16, String.format("ERROR: String buffer at %.1f%% capacity! (%d/%d)",
				bufferFillPercent, printer.compiledLength, StringPrinter.MAX_LENGTH));
		}
		else if (bufferFillPercent > 75.0f) {
			int palID = 5;
			if (bufferFillPercent > 90.0f)
				palID = 6;
			if (bufferFillPercent > 95.0f)
				palID = 7;
			drawString(palID, 176, editor.glCanvasHeight() - 4, 16, String.format("WARNING: String buffer at %.1f%% capacity! (%d/%d)",
				bufferFillPercent, printer.compiledLength, StringPrinter.MAX_LENGTH));
		}

		RenderState.enableDepthTest(true);
	}

	private void handleStyle(StringPrinter printer, DrawState state)
	{
		switch (StringStyle.decodeMap.get(printer.drawBuffer.get())) {
			case RIGHT:
			case LEFT:
			case CENTER:
			case TATTLE:
			case CHOICE:
				state.textColor = 0xA;
				break;
			case INSPECT:
			case UPGRADE:
			case NARRATE:
			case STYLE_F:
				state.textColor = 0;
				break;
			case SIGN:
				state.textColor = 0x18;
				break;
			case LAMPPOST:
				state.textColor = 0x1C;
				break;
			case POSTCARD:
			case POPUP:
			case STYLE_B:
				state.textColor = 0;
				break;
			case EPILOGUE:
				state.textColor = 0;
				break;
		}
	}

	private void handleFunction(StringPrinter printer, DrawState state, int relativePosX, int relativePosY)
	{
		int startX = state.getScreenPosX(relativePosX);
		int startY = state.getScreenPosY(relativePosY);
		StringFunction func = StringFunction.decodeMap.get(printer.drawBuffer.get());

		switch (func) {
			case CURSOR:
			case OPTION:
			case SET_CANCEL:
			case END_CHOICE:
				printer.drawBuffer.get();
				break; // completely ignored by editor

			case VAR:
				printer.drawBuffer.get();
				break; // already handled during encoding

			case FONT:
				int fontType = (printer.drawBuffer.get() & 0xFF);
				switch (fontType) {
					case 0:
						state.currentFont = FontType.Normal;
						break;
					case 1:
						state.currentFont = FontType.Menus;
						break;
					case 2:
						state.currentFont = FontType.Menus;
						break;
					case 3:
						state.currentFont = FontType.Title;
						break;
					case 4:
						state.currentFont = FontType.Subtitle;
						break;
				}
				break;

			case VARIANT:
				state.fontVariant = (printer.drawBuffer.get() & 0xFF);
				break;

			case ANIM_DONE:
				if (printer.hasAnim()) {
					int spriteID = printer.getAnimSprite();
					int rasterID = printer.getAnimRaster();

					Sprite npc = spriteLoader.getSprite(SpriteSet.Npc, spriteID);
					if (npc != null && !npc.areTexturesLoaded())
						npc.loadTextures();

					if (npc != null && npc.rasters.getSize() > rasterID) {
						BasicIndexedShader shader = ShaderManager.use(BasicIndexedShader.class);
						SpriteRaster raster = npc.rasters.get(rasterID);
						ImgAsset front = raster.getFront();
						if (front != null) {
							front.img.glBind(shader.texture);
							front.getPalette().glBind(shader.palette);
							drawClippedQuad(shader, state, startX, startX + front.img.width, startY, startY + front.img.height);
						}
					}
					else {
						BasicSolidShader shader = ShaderManager.use(BasicSolidShader.class);
						float f = 0.5f + (float) Math.cos(Math.toRadians(16 * frameCounter)) * 0.25f;
						shader.baseColor.set(1.0f, f, f, 1.0f);
						drawClippedQuad(shader, state, startX, startX + 0x20, startY, startY + 0x40);
					}
				}
				break;

			case IMAGE:
				printer.drawBuffer.get(); // index -- ignored
				int posX = printer.drawBuffer.getShort() & 0xFFFF;
				int posY = printer.drawBuffer.get() & 0xFF;
				int border = printer.drawBuffer.get() & 0xFF;
				int alpha = printer.drawBuffer.get() & 0xFF;
				int alphaStep = printer.drawBuffer.get() & 0xFF;

				if (varImageLoaded) {
					BasicTexturedShader shader = ShaderManager.use(BasicTexturedShader.class);
					shader.texture.bind(glVarTexID);
					shader.multiplyBaseColor.set(true);
					shader.baseColor.set(1.0f, 1.0f, 1.0f, alpha / 255.0f);
					drawQuad(shader, posX, posX + varImage.getWidth(), posY, posY + varImage.getHeight());
				}
				else {
					BasicSolidShader shader = ShaderManager.use(BasicSolidShader.class);
					float f = 0.5f + (float) Math.cos(Math.toRadians(16 * frameCounter)) * 0.25f;
					shader.baseColor.set(1.0f, f, f, alpha / 255.0f);
					drawQuad(shader, posX, posX + 150, posY, posY + 105);
				}
				break;

			case HIDE_IMAGE:
				printer.drawBuffer.get(); // fade out time
				break;

			case INLINE_IMAGE:
				printer.drawBuffer.get(); // index -- ignored

				if (varImageLoaded) {
					BasicTexturedShader shader = ShaderManager.use(BasicTexturedShader.class);
					shader.texture.bind(glVarTexID);
					drawClippedQuad(shader, state, startX, startX + varImage.getWidth(), startY, startY + varImage.getHeight());
				}
				else {
					BasicSolidShader shader = ShaderManager.use(BasicSolidShader.class);
					float f = 0.5f + (float) Math.cos(Math.toRadians(16 * frameCounter)) * 0.25f;
					shader.baseColor.set(1.0f, f, f, 1.0f);
					drawClippedQuad(shader, state, startX, startX + 0x40, startY, startY + 0x40);
				}
				break;

			case ITEM_ICON:
				int upperItemID = (printer.drawBuffer.get() & 0xFF);
				int lowerItemID = (printer.drawBuffer.get() & 0xFF);
				int itemID = (upperItemID << 8) | lowerItemID;

				if (glItemPreviews != null && glItemPreviews.length > itemID && glItemPreviews[itemID] != null) {
					BasicIndexedShader shader = ShaderManager.use(BasicIndexedShader.class);
					glItemPreviews[itemID].glBind(shader.texture);
					glItemPreviews[itemID].palette.glBind(shader.palette);
					drawClippedQuad(shader, state, startX, startX + 0x20, startY, startY + 0x20);
				}
				else {
					BasicSolidShader shader = ShaderManager.use(BasicSolidShader.class);
					float f = 0.5f + (float) Math.cos(Math.toRadians(16 * frameCounter)) * 0.25f;
					shader.baseColor.set(1.0f, f, f, 1.0f);
					drawClippedQuad(shader, state, startX, startX + 0x20, startY, startY + 0x20);
				}
				break;

			case COLOR:
				state.textColor = (printer.drawBuffer.get() & 0xFF);
				if (state.textColor < 0)
					state.textColor = 0;
				int numPals = state.currentFont.chars.numPals;
				if (state.textColor >= numPals)
					state.textColor = numPals - 1;
				break;

			case PUSH_COLOR:
				state.savedColor = state.textColor;
				break;
			case POP_COLOR:
				state.textColor = state.savedColor;
				break;

			case PUSH_POS:
				state.savedPosX = state.printPosX;
				state.savedPosY = state.printPosY;
				break;
			case POP_POS:
				state.printPosX = state.savedPosX;
				state.printPosY = state.savedPosY;
				break;

			case SPACING:
				state.charWidthOverride = (printer.drawBuffer.get() & 0xFF);
				break;

			case SIZE:
				int sx = (printer.drawBuffer.get() & 0xFF);
				int sy = (printer.drawBuffer.get() & 0xFF);
				state.stringScaleX = sx / 16.0f;
				state.stringScaleY = sy / 16.0f;
				state.charScaleX = state.stringScaleX;
				state.charScaleY = state.stringScaleY;
				break;
			case SIZE_RESET:
				state.stringScaleX = 1.0f;
				state.stringScaleY = 1.0f;
				state.charScaleX = state.stringScaleX;
				state.charScaleY = state.stringScaleY;
				break;

			case SET_X: // [80129EB4]
				int upper = (printer.drawBuffer.get() & 0xFF);
				int lower = (printer.drawBuffer.get() & 0xFF);
				state.printPosX = (upper << 8) | lower;
				break;
			case SET_Y: // [80129ED8]
				state.printPosY = (printer.drawBuffer.get() & 0xFF);
				break;
			case RIGHT: // [80129F0C]
				state.printPosX += (printer.drawBuffer.get() & 0xFF);
				break;
			case DOWN: // [80129F38]
				state.printPosY += (printer.drawBuffer.get() & 0xFF);
				break;
			case UP: // [80129F64]
				state.printPosY -= (printer.drawBuffer.get() & 0xFF);
				break;
			case CENTER_X: // [8012AAEC]
				state.centerPos = (printer.drawBuffer.get() & 0xFF);
				break;

			case START_FX:
				int startFXType = (printer.drawBuffer.get() & 0xFF);
				state.effectActive[startFXType] = true;
				switch (startFXType) {
					case 0x2:
						state.effectActive[3] = false;
						break;
					case 0x3:
						state.effectActive[2] = false;
						state.fadeNoiseAlpha = (printer.drawBuffer.get() & 0xFF) / 255.0f;
						break;
					case 0x5:
						state.fadeJitterArg = (printer.drawBuffer.get() & 0xFF);
						break;
					case 0x7:
						state.fadeAlpha = (printer.drawBuffer.get() & 0xFF) / 255.0f;
						break;
					case 0x6:
					case 0x9:
						state.savedFxColor = state.textColor;
						break;
					case 0xC:
					case 0xD:
						state.savedFxCharScaleX = state.charScaleX;
						state.savedFxCharScaleY = state.charScaleY;
						break;
				}
				break;

			case END_FX:
				int endFXType = (printer.drawBuffer.get() & 0xFF);
				// restore saved state (if it has been saved)
				if (state.effectActive[endFXType]) {
					switch (endFXType) {
						case 0x2:
						case 0x3:
							// end clears the flags for these,
							// but will not stop the effect since its
							// part of the display list
							break;
						case 0x6:
						case 0x9:
							state.textColor = state.savedFxColor;
							state.effectActive[endFXType] = false;
							break;
						case 0xC:
						case 0xD:
							state.charScaleX = state.savedFxCharScaleX;
							state.charScaleY = state.savedFxCharScaleY;
							state.effectActive[endFXType] = false;
							break;
						default:
							state.effectActive[endFXType] = false;
					}
				}
				break;

			case FUNC_03:
				//XXX unknown, some graphics state reset?
				break;

			case SETVOICE:
			case VOICE:
			case VOLUME:
			case SPEED:
			case SCROLL:
			case DELAY_OFF:
			case DELAY_ON:
			case SKIP_OFF:
			case INPUT_OFF:
			case INPUT_ON:
			case ENABLE_CDOWN:
				break; // irrelevant to renderer

			case SET_REWIND:
				printer.drawBuffer.get();
				break; // irrelevant to renderer

			case YIELD:
				break;

			case SET_CURSOR:
				break;

			case ANIM_SPRITE:
			case ANIM_DELAY:
			case ANIM_LOOP:
				throw new IllegalStateException("LOGIC ERROR: " + func.name + " in encodeFunctionArgs!");
		}
	}

	public void drawString(int palID, int posX, int posY, int size, String text)
	{
		FontShader shader = ShaderManager.use(FontShader.class);
		shader.noiseMode.set(0);
		shader.fadeAlpha.set(1.0f);
		shader.enableDropShadow.set(false);

		FontType font = FontType.Normal;

		for (char c : text.toCharArray()) {
			int index = StringConstants.getIndex(c, true);
			if (index < 0)
				continue;

			int endX = posX + (int) (font.chars.defaultX * size / 16.0f);
			int endY = posY - (int) (font.chars.defaultY * size / 16.0f);
			int charWidth = MessageUtil.getCharWidth(font, 0, index, size / 16.0f, 0, 0);

			if (index < font.chars.numChars) {
				font.chars.images[index].glBind(shader.texture);
				font.chars.palettes[palID].glBind(shader.palette);
				drawQuad(shader, posX, endX, posY, endY);
			}
			posX += charWidth;
		}
	}

	private void renderChar(StringPrinter printer, DrawState state, int relativePosX, int relativePosY, int charIndex)
	{
		FontShader shader = ShaderManager.use(FontShader.class);

		int[] lookahead = new int[4];
		for (int i = 0; i < lookahead.length; i++)
			lookahead[i] = (printer.drawBuffer.get() & 0xFF);
		printer.drawBuffer.position(printer.drawBuffer.position() - lookahead.length);

		//XXX correct?
		state.charScaleX = state.stringScaleX;
		state.charScaleY = state.stringScaleY;
		float alpha = 1.0f;

		if (state.effectActive[BLUR.code]) // 0x20
		{
			// fade code from [8012ADC4]
			alpha = 0.35f;
		}

		if (state.effectActive[SHAKE.code]) {
			// code from [8012AE50]
			relativePosX += rng.nextInt() % 2;
			relativePosY += rng.nextInt() % 2;
		}

		if (state.effectActive[WAVE.code]) // 0x2
		{
			// code from [8012AEBC]
			double dsx = state.stringScaleX - 1.0;
			double dsy = state.stringScaleY - 1.0;

			//NOTE: game uses a message-specific 'local' frame counter here
			double angle = frameCounter * (int) (20.0 - dsx * 5.0) - state.visibleCharCount * (int) (45.0 - dsx * 15.0);
			relativePosX += Math.cos(Math.toRadians(angle)) * (dsx + 1.6);
			relativePosY += Math.cos(Math.toRadians(angle + 270.0)) * (dsy + 1.6);
		}
		if (state.effectActive[GLOBAL_WAVE.code]) // 0x200
		{
			// code from [8012B0BC]
			double dsx = state.stringScaleX - 1.0;
			double dsy = state.stringScaleY - 1.0;

			//NOTE: game uses a 'global' frame counter shared by all messages here
			double angle = frameCounter * (int) (20.0 - dsx * 5.0) - state.visibleCharCount * 45.0;
			relativePosX += Math.cos(Math.toRadians(angle)) * (dsx + 1.6);
			relativePosY += Math.cos(Math.toRadians(angle + 270.0)) * (dsy + 1.6);
		}

		if (state.effectActive[RAINBOW.code] || state.effectActive[GLOBAL_RAINBOW.code]) // 0x440 -- either rainbow
		{
			// code from [8012B1B4]

			// NOTE: global overrides local here, but they cant stack so we dont need to represent it
			int i = Math.abs(state.visibleCharCount - frameCounter / 3);
			state.textColor = i % 10; // original code: = i + (i / 10) * -10;
		}

		// properly respect precedence of these effects
		if (state.effectActive[RISE_PRINT.code] || state.effectActive[GROW_PRINT.code]) {
			if (!printer.donePrinting
				&& (lookahead[0] != ControlCharacter.ENDL.code) && (lookahead[1] != ControlCharacter.ENDL.code)
				&& (lookahead[2] != ControlCharacter.ENDL.code) && (lookahead[3] != ControlCharacter.ENDL.code)) {
				if (state.effectActive[RISE_PRINT.code]) {
					// from [8012B4F0]
					float dummyScale = 0.25f; //XXX needed to match, unknown why
					if (lookahead[0] == StringPrinter.BUFFER_FILL) {
						state.charScaleX = 1.7f * state.stringScaleX;
						state.charScaleY = 1.7f * state.stringScaleY;
						relativePosX -= 6.0f * state.stringScaleY;
						relativePosY -= dummyScale * 6.0f * state.stringScaleY;
					}
					else if (lookahead[1] == StringPrinter.BUFFER_FILL) {
						state.charScaleX = 1.4f * state.stringScaleX;
						state.charScaleY = 1.4f * state.stringScaleY;
						relativePosX -= 3.0f * state.stringScaleY;
						relativePosY -= dummyScale * 3.0f * state.stringScaleY;
					}
					else if (lookahead[2] == StringPrinter.BUFFER_FILL) {
						state.charScaleX = 1.2f * state.stringScaleX;
						state.charScaleY = 1.2f * state.stringScaleY;
						relativePosX -= 2.0f * state.stringScaleY;
						relativePosY -= dummyScale * 2.0f * state.stringScaleY;
					}
				}
				else if (state.effectActive[GROW_PRINT.code]) {
					// from [8012B740]
					if (lookahead[0] == StringPrinter.BUFFER_FILL) {
						state.charScaleX = 0.3f * state.stringScaleX;
						state.charScaleY = 0.3f * state.stringScaleY;
						relativePosX += 5;
						relativePosY += 5;
					}
					else if (lookahead[1] == StringPrinter.BUFFER_FILL) {
						state.charScaleX = 0.5f * state.stringScaleX;
						state.charScaleY = 0.5f * state.stringScaleY;
						relativePosX += 3;
						relativePosY += 3;
					}
					else if (lookahead[2] == StringPrinter.BUFFER_FILL) {
						state.charScaleX = 0.75f * state.stringScaleX;
						state.charScaleY = 0.75f * state.stringScaleY;
						relativePosX += 2;
						relativePosY += 2;
					}
				}
			}
		}
		else if (state.effectActive[SIZE_JITTER.code] || state.effectActive[SIZE_WAVE.code]) {
			float scale = 1.0f;

			if (state.effectActive[SIZE_JITTER.code]) // 0x2000 -- size jitter
			{
				// code from [8012B8BC]
				scale = 0.75f + 0.5f * (rng.nextInt(101) / 100.0f);
			}
			else if (state.effectActive[SIZE_WAVE.code]) // 0x4000 -- size wave
			{
				// code from [8012BA24]
				int i = (frameCounter - state.visibleCharCount) * 15;
				scale = 1.0f + 0.25f * (float) Math.cos(Math.toRadians(i + ((i >> 3) / 45) * 360.0));
			}

			state.charScaleX *= scale;
			state.charScaleY *= scale;

			if (scale >= 1.0) {
				// [8012BACC]
				int posOffset = (int) (scale * 8.0 - 8.5);
				relativePosX -= posOffset;
				relativePosY -= posOffset;
			}
			else {
				// [8012BB7C]
				int posOffset = (int) (8.0 - 8.0 * scale);
				relativePosX += posOffset;
				relativePosY += posOffset;
			}
		}

		shader.noiseMode.set(0); // initial value

		if (state.effectActive[DITHER_FADE.code]) //0x80 -- fade
		{
			alpha *= state.fadeAlpha;
			shader.noiseMode.set(7);
			shader.noiseAlpha.set(state.fadeAlpha);
			shader.noiseOffset.set(rng.nextFloat(), rng.nextFloat());
		}
		else {
			if (state.effectActive[NOISE_OUTLINE.code]) // 0x04 -- noise
			{
				shader.noiseMode.set(2);
				shader.noiseOffset.set(rng.nextFloat(), rng.nextFloat());

				// add Gfx:
				// SET_COMBINE		FC70FEE1 FFFFF3F9

				// color: G_CCMUX_NOISE * G_CCMUX_TEXEL0
				// alpha: G_ACMUX_TEXEL0
			}

			if (state.effectActive[STATIC.code]) // 0x10000 -- faded noise
			{
				shader.noiseMode.set(3);
				shader.noiseAlpha.set(state.fadeNoiseAlpha);
				shader.noiseOffset.set(rng.nextFloat(), rng.nextFloat());

				// add Gfx:
				// SET_ENVCOLOR		FB000000	arg << 0x18 | arg << 0x10 | arg << 8;
				// SET_COMBINE		FC72FEE5	11FCF279

				// color: (G_CCMUX_NOISE - G_CCMUX_TEXEL0) * G_CCMUX_ENVIRONMENT + G_CCMUX_TEXEL0
				// alpha: G_ACMUX_TEXEL0
			}
		}

		shader.fadeAlpha.set(alpha);

		int baseOffset = 0;
		if (state.fontVariant < state.currentFont.numVariants && state.fontVariant >= 0)
			baseOffset = state.currentFont.baseHeightOffset[state.fontVariant];

		int startX = state.getScreenPosX(relativePosX);
		int startY = state.getScreenPosY(relativePosY + baseOffset);
		int endX = startX + (int) (state.currentFont.chars.defaultX * state.charScaleX);
		int endY = startY + (int) (state.currentFont.chars.defaultY * state.charScaleY);

		if (state.effectActive[DROP_SHADOW.code]) // 0x8000 -- drop shadow
		{
			shader.enableDropShadow.set(true);
			drawFontQuad(shader, state,
				state.currentFont.chars.images[charIndex],
				state.currentFont.chars.palettes[state.getColor()],
				startX + 2, endX + 2, startY + 2, endY + 2);
			shader.enableDropShadow.set(false);
		}

		if (state.effectActive[BLUR.code]) // 0x20 -- FadedJitter
		{
			// code from [8012BDF4]
			for (int i = 0; i < 5; i++) {
				int jx = (state.fadeJitterArg == 2) ? 0 : rng.nextInt(3) - 1;
				int jy = (state.fadeJitterArg == 1) ? 0 : rng.nextInt(3) - 1;

				drawFontQuad(shader, state,
					state.currentFont.chars.images[charIndex],
					state.currentFont.chars.palettes[state.getColor()],
					startX + jx, endX + jx, startY + jy, endY + jy);
			}
		}
		else {
			drawFontQuad(shader, state,
				state.currentFont.chars.images[charIndex],
				state.currentFont.chars.palettes[state.getColor()],
				startX, endX, startY, endY);
		}

		state.visibleCharCount++;
	}

	private static class DrawState
	{
		public final StringPrinter printer;

		private FontType currentFont = FontType.Normal;
		public int fontVariant = 0;

		private boolean[] effectActive;
		private float fadeAlpha = 1.0f;
		private float fadeNoiseAlpha = 0;
		private int fadeJitterArg = 0;

		private int textColor = 0xA;

		private int visibleCharCount = 0;

		private boolean useCulling = true;

		private int printPosX = 0;
		private int printPosY = 0;

		private int centerPos = 0;

		private int savedColor;
		private int savedPosX = 0;
		private int savedPosY = 0;
		private int savedFxColor = -1;
		private float savedFxCharScaleX = 1.0f;
		private float savedFxCharScaleY = 1.0f;

		private int charWidthOverride = 0;

		private float charScaleX = 1.0f;
		private float charScaleY = 1.0f;

		private float stringScaleX = 1.0f;
		private float stringScaleY = 1.0f;

		private DrawState(StringPrinter printer)
		{
			this.printer = printer;
			effectActive = new boolean[StringConstants.StringEffect.values().length];
		}

		public int getColor()
		{
			switch (currentFont) {
				case Normal:
				case Menus:
				default:
					return textColor;
				case Title:
				case Subtitle:
					return 0;
			}
		}

		public int getScreenPosX(int offsetX)
		{
			int centerOffset = 0;
			if (centerPos == 0xFF)
				centerOffset = 160 - (printer.stringWidth / 2);
			else if (centerPos != 0)
				centerOffset = centerPos - (printer.stringWidth / 2);

			if (centerOffset == 0)
				return printer.windowBasePosX + printer.windowTextStartX + printPosX + offsetX;
			else
				return centerOffset + printPosX + offsetX;
		}

		public int getScreenPosY(int offsetY)
		{
			return printer.windowBasePosY + printer.windowTextStartY + printPosY + offsetY;
		}
	}

	private void drawFontQuad(FontShader shader, DrawState state, Tile img, Palette pal, float x1, float x2, float y1, float y2)
	{
		img.glBind(shader.texture);
		pal.glBind(shader.palette);
		shader.noise.bind(glNoiseTexID);
		drawClippedQuad(shader, state, x1, x2, y1, y2);
	}

	private static final void drawClippedQuad(BaseShader shader, DrawState state, float x1, float x2, float y1, float y2)
	{
		if (state.useCulling)
			drawClippedQuad(shader, state.printer, x1, x2, y1, y2);
		else
			drawQuad(shader, x1, x2, y1, y2);
	}

	private static final void drawClippedQuad(BaseShader shader, StringPrinter printer, float x1, float x2, float y1, float y2)
	{
		float x1c = x1;
		float x2c = x2;

		float y1c = y1;
		float y2c = y2;

		float u1c = 0.0f;
		float u2c = 1.0f;

		float v1c = 0.0f;
		float v2c = 1.0f;

		assert (x1 < x2);
		assert (y1 < y2);

		if ((x2 < printer.clipMinX) || (x1 > printer.clipMaxX) || (y2 < printer.clipMinY) || (y1 > printer.clipMaxY))
			return;

		if (x1 < printer.clipMinX) {
			x1c = printer.clipMinX;
			u1c = ((printer.clipMinX - x1) / (x2 - x1));
		}

		if (x2 > printer.clipMaxX) {
			x2c = printer.clipMaxX;
			u2c = 1.0f - ((x2 - printer.clipMaxX) / (x2 - x1));
		}

		if (y1 < printer.clipMinY) {
			y1c = printer.clipMinY;
			v2c = 1.0f - ((printer.clipMinY - y1) / (y2 - y1));
		}

		if (y2 > printer.clipMaxY) {
			y2c = printer.clipMaxY;
			v1c = ((y2 - printer.clipMaxY) / (y2 - y1));
		}

		shader.setQuadTexCoords(u1c, v1c, u2c, v2c);
		shader.setXYQuadCoords(x1c, y2c, x2c, y1c, 0); // y flipped
		shader.renderQuad();
	}

	private static final void drawQuad(BaseShader shader, float x1, float x2, float y1, float y2)
	{
		shader.setQuadTexCoords(0, 0, 1, 1);
		shader.setXYQuadCoords(x1, y2, x2, y1, 0); // y flipped
		shader.renderQuad();
	}
}
