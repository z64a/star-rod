package renderer.text;

import static renderer.buffers.BufferedMesh.*;

import game.map.editor.geometry.Vector3f;

import game.map.shape.TransformMatrix;
import renderer.buffers.BufferedMesh;
import renderer.shaders.ShaderManager;
import renderer.shaders.scene.BasicSolidShader;
import renderer.shaders.scene.TextShader;
import util.MathUtil;

public class DrawableString
{
	private static class ScreenBounds
	{
		public float minX = Float.MAX_VALUE;
		public float maxX = Float.MIN_VALUE;
		public float minY = Float.MAX_VALUE;
		public float maxY = Float.MIN_VALUE;
		public int numPoints = 0;

		public void encompass(float posX, float posY)
		{
			if (posX < minX)
				minX = posX;
			if (posX > maxX)
				maxX = posX;
			if (posY < minY)
				minY = posY;
			if (posY > maxY)
				maxY = posY;
			numPoints++;
		}
	}

	private BufferedMesh mesh;
	private TextStyle style;

	private String text = "";
	private String builtText = "";

	private ScreenBounds bounds;
	private float sizeX = 0;
	private float sizeY = 0;

	public boolean enableFade = true;
	private float fadeAlpha = 0.0f;
	private float fadeAlphaGoal = 1.0f;

	public DrawableString(TextStyle style)
	{
		this.style = style;
		mesh = new BufferedMesh(0, 0, VBO_INDEX | VBO_COLOR | VBO_UV | VBO_AUX);
		TextRenderer.register(this);
	}

	public void glDelete()
	{
		mesh.glDelete();
	}

	public void setText(String s)
	{
		text = (s == null) ? "" : s;
		fadeAlphaGoal = 1.0f;
	}

	public void setVisible(boolean value)
	{ fadeAlphaGoal = value ? 1.0f : 0.0f; }

	private void build()
	{
		TextFont font = style.font;
		bounds = new ScreenBounds();
		mesh.clear();

		if (text.isEmpty())
			return;

		float lineOffset = font.lineHeight - font.base;
		float posX = -font.padding;
		float posY = -font.padding - lineOffset;
		float minY = Float.MAX_VALUE;
		float maxY = Float.MIN_VALUE;

		// first pass to get total size
		for (char c : text.toCharArray()) {
			if (c == '\n') {
				posX = -font.padding;
				posY += 1.5f * font.lineHeight;
				continue;
			}

			if (c < 0 || c > 256 || font.chars[c] == null)
				continue;

			TextChar tc = font.chars[c];

			posX += tc.xadvance - 2 * font.padding;
			minY = Math.min(minY, posY + tc.yoffset);
			maxY = Math.max(maxY, posY + tc.yoffset + tc.height);
			sizeX = posX;
			sizeY = maxY - minY;
		}

		// build the quads in second pass
		float dx = -sizeX * 0.5f;
		float dy = -sizeY * 0.5f;
		posX = -font.padding;
		posY = -font.padding - lineOffset;

		for (char c : text.toCharArray()) {
			if (c == '\n') {
				posX = -font.padding;
				posY += 1.5f * font.lineHeight;
				continue;
			}

			if (c < 0 || c > 256 || font.chars[c] == null)
				continue;

			TextChar tc = font.chars[c];

			float x1 = posX + tc.xoffset + dx;
			float x2 = x1 + tc.width;

			float y1 = posY + tc.yoffset + dy;
			float y2 = y1 + tc.height;

			mesh.addQuad(
				mesh.addVertex().setPosition(x1, y1, 0).setUV(tc.u1, tc.v1).getIndex(),
				mesh.addVertex().setPosition(x2, y1, 0).setUV(tc.u2, tc.v1).getIndex(),
				mesh.addVertex().setPosition(x2, y2, 0).setUV(tc.u2, tc.v2).getIndex(),
				mesh.addVertex().setPosition(x1, y2, 0).setUV(tc.u1, tc.v2).getIndex());

			bounds.encompass(x1, y1);
			bounds.encompass(x2, y1);
			bounds.encompass(x2, y2);
			bounds.encompass(x1, y2);

			posX += tc.xadvance - 2 * font.padding;
		}

		mesh.loadBuffers();
	}

	public void draw(float size, float posX, float posY, float dt)
	{
		TransformMatrix mtx = TransformMatrix.identity();
		mtx.translate(posX, posY, 1);
		draw(size, mtx, dt);
	}

	public void draw(float size, TransformMatrix mtx, float dt)
	{
		if (!enableFade)
			fadeAlpha = fadeAlphaGoal;

		if (dt > 0 && fadeAlpha != fadeAlphaGoal) {
			if (Math.abs(fadeAlpha - fadeAlphaGoal) > 0.01)
				fadeAlpha = MathUtil.lerp(1.0f - (float) Math.pow(0.01, dt), fadeAlpha, fadeAlphaGoal);
			else
				fadeAlpha = fadeAlphaGoal;
		}

		if (fadeAlpha <= 0 || text.isEmpty())
			return;

		if (!builtText.equals(text)) {
			build();
			builtText = text;
		}

		float scale = size / style.font.size;
		TransformMatrix bmtx = TransformMatrix.identity();
		bmtx.scale(scale);
		float dx = style.centerX ? 0 : scale * sizeX * 0.5f;
		float dy = style.centerY ? 0 : scale * sizeY * 0.5f;
		bmtx.translate(dx, dy, 0);
		bmtx.concat(mtx);

		if (style.useBackground && bounds.numPoints > 0) {
			BasicSolidShader shader = ShaderManager.use(BasicSolidShader.class);
			style.setBackgroundShaderParams(shader, fadeAlpha);

			Vector3f outMin = bmtx.applyTransform(new Vector3f(bounds.minX, bounds.minY, 0.0f));
			Vector3f outMax = bmtx.applyTransform(new Vector3f(bounds.maxX, bounds.maxY, 0.0f));
			shader.setXYQuadCoords(
				outMin.x - style.backgroundPadW, outMin.y - style.backgroundPadH,
				outMax.x + style.backgroundPadW, outMax.y + style.backgroundPadH, 1.0f);
			shader.renderQuad();
		}

		TextShader shader = ShaderManager.use(TextShader.class);
		style.setTextShaderParams(shader, fadeAlpha);
		mesh.renderWithTransform(bmtx);
	}
}
