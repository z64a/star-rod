package renderer.text;

import common.Vector3f;
import game.map.editor.render.PresetColor;
import renderer.shaders.scene.BasicSolidShader;
import renderer.shaders.scene.TextShader;

public class TextStyle
{
	protected TextFont font;

	protected float alpha = 1.0f;
	protected Vector3f color;
	protected float width = 0.6f;
	protected float edge = 0.1f;

	protected boolean centerX;
	protected boolean centerY;

	protected boolean useOutline;
	protected Vector3f outlineColor;
	protected float outlineWidth = 0.7f;
	protected float outlineEdge = 0.1f;

	protected boolean useBackground;
	protected Vector3f backgroundColor;
	protected float backgroundAlpha = 0.5f;
	protected float backgroundPadW = 4.0f;
	protected float backgroundPadH = 2.0f;

	public TextStyle(TextFont font)
	{
		this.font = font;
		color = new Vector3f(1.0f, 1.0f, 1.0f);
		outlineColor = new Vector3f(0.0f, 1.0f, 0.0f);
		backgroundColor = new Vector3f(0.2f, 0.2f, 0.3f);
	}

	public TextStyle setCentered(boolean x, boolean y)
	{
		centerX = x;
		centerY = y;
		return this;
	}

	public TextStyle setAlpha(float alpha)
	{
		this.alpha = alpha;
		return this;
	}

	public TextStyle setThickness(float width, float edge)
	{
		this.width = width;
		this.edge = edge;
		return this;
	}

	public TextStyle setColor(float R, float G, float B)
	{
		color.set(R, G, B);
		return this;
	}

	public TextStyle setColor(PresetColor color)
	{
		setColor(color.r, color.g, color.b);
		return this;
	}

	public TextStyle enableOutline(boolean outlined)
	{
		useOutline = outlined;
		return this;
	}

	public TextStyle setOutlineThickness(float width, float edge)
	{
		outlineWidth = width;
		outlineEdge = edge;
		return this;
	}

	public TextStyle setOutlineColor(float R, float G, float B)
	{
		outlineColor.set(R, G, B);
		return this;
	}

	public TextStyle enableBackground(boolean hasBackground)
	{
		useBackground = hasBackground;
		return this;
	}

	public TextStyle setBackgroundPadding(float padW, float padH)
	{
		backgroundPadW = padW;
		backgroundPadH = padH;
		return this;
	}

	public TextStyle setBackgroundColor(float R, float G, float B)
	{
		backgroundColor.set(R, G, B);
		return this;
	}

	public TextStyle setBackgroundAlpha(float alpha)
	{
		backgroundAlpha = alpha;
		return this;
	}

	public void setBackgroundShaderParams(BasicSolidShader shader, float mulAlpha)
	{
		shader.baseColor.set(backgroundColor.x, backgroundColor.y, backgroundColor.z, backgroundAlpha * mulAlpha);
		shader.multiplyVertexColor.set(false);
	}

	public void setTextShaderParams(TextShader shader, float mulAlpha)
	{
		shader.texture.bind(font.glTexID);
		shader.alpha.set(alpha * mulAlpha);

		shader.color.set(color.x, color.y, color.z);
		shader.width.set(width);
		shader.edge.set(edge);

		shader.hasOutline.set(useOutline);
		if (useOutline) {
			shader.outlineColor.set(outlineColor.x, outlineColor.y, outlineColor.z);
			shader.outlineWidth.set(outlineWidth);
			shader.outlineEdge.set(outlineEdge);
		}
	}
}
