package game.map.editor.camera;

import game.map.editor.geometry.Vector3f;

import game.map.BoundingBox;
import game.map.editor.MapEditor;
import game.map.editor.render.PresetColor;
import game.map.editor.render.Renderer;
import game.map.editor.render.RenderingOptions;
import game.map.shape.TransformMatrix;
import renderer.shaders.RenderState;
import renderer.shaders.RenderState.PolygonMode;
import renderer.shaders.ShaderManager;
import renderer.shaders.scene.BasicSolidShader;
import renderer.text.DrawableString;
import renderer.text.TextRenderer;
import renderer.text.TextStyle;

public abstract class MapEditViewport
{
	protected final MapEditor editor;
	protected final Renderer renderer;

	public final ViewType type;
	public MapEditCamera camera;

	public int minX;
	public int minY;
	public int maxX;
	public int maxY;
	protected int sizeX;
	protected int sizeY;

	public boolean wireframeMode;

	private static final TextStyle STYLE_SIZE_16 = new TextStyle(TextRenderer.FONT_ROBOTO)
		.setCentered(false, false)
		.setThickness(0.4f, 0.2f).setColor(PresetColor.YELLOW)
		.enableOutline(true).setOutlineThickness(0.6f, 0.3f).setOutlineColor(0.0f, 0.0f, 0.0f)
		.enableBackground(true).setBackgroundPadding(2.0f, 2.0f).setBackgroundAlpha(0.5f);

	private static final TextStyle STYLE_SIZE_12 = new TextStyle(TextRenderer.FONT_ROBOTO)
		.setCentered(false, false)
		.setThickness(0.4f, 0.2f).setColor(PresetColor.YELLOW)
		.enableOutline(true).setOutlineThickness(0.6f, 0.3f).setOutlineColor(0.15f, 0.15f, 0.15f)
		.enableBackground(true).setBackgroundPadding(2.0f, 2.0f).setBackgroundAlpha(0.5f);

	private DrawableString uiTextLL;
	private boolean recievedUpdateLL;

	private DrawableString uiTextUL;
	private boolean recievedUpdateUL;

	protected MapEditViewport(MapEditor editor, Renderer renderer, ViewType type)
	{
		this(editor, renderer, type, 0, 0, 0, 0);
	}

	protected MapEditViewport(MapEditor editor, Renderer renderer, ViewType type, int minX, int minY, int maxX, int maxY)
	{
		this.editor = editor;
		this.renderer = renderer;
		this.type = type;
		this.minX = minX;
		this.minY = minY;
		this.maxX = maxX;
		this.maxY = maxY;
		sizeX = maxX - minX;
		sizeY = maxY - minY;

		uiTextLL = new DrawableString(STYLE_SIZE_16);
		uiTextLL.setVisible(false);

		uiTextUL = new DrawableString(STYLE_SIZE_12);
		uiTextLL.setVisible(false);
	}

	public void setTextLL(String s, boolean enableFade)
	{
		uiTextLL.setText(s);
		uiTextLL.enableFade = enableFade;
		recievedUpdateLL = true;
	}

	public void setTextUL(String s, boolean enableFade)
	{
		uiTextUL.setText(s);
		uiTextUL.enableFade = enableFade;
		recievedUpdateUL = true;
	}

	public void resize(int minX, int minY, int maxX, int maxY)
	{
		this.minX = minX;
		this.minY = minY;
		this.maxX = maxX;
		this.maxY = maxY;
		sizeX = maxX - minX;
		sizeY = maxY - minY;

		camera.resize();
	}

	public boolean contains(int x, int y)
	{
		boolean h = (minX <= x && maxX > x);
		boolean v = (minY <= y && maxY > y);
		return (h && v);
	}

	public void release()
	{
		camera.release();
	}

	public void setViewport()
	{
		camera.glSetViewport(minX, minY, sizeX, sizeY);
	}

	public Vector3f getProjectionVector()
	{
		switch (type) {
			case TOP:
				return new Vector3f(1.0f, 0.0f, 1.0f);
			case SIDE:
				return new Vector3f(0.0f, 1.0f, 1.0f);
			case FRONT:
				return new Vector3f(1.0f, 1.0f, 0.0f);
			default:
				return new Vector3f(1.0f, 1.0f, 1.0f);
		}
	}

	public abstract boolean allowsDragSelection();

	public abstract BoundingBox getDragSelectionVolume(Vector3f start, Vector3f end);

	public abstract float getScaleFactor(float x, float y, float z);

	public abstract void render(RenderingOptions opts, boolean isActive);

	public void renderFade(float R, float G, float B, float A)
	{
		TransformMatrix projMatrix = TransformMatrix.identity();
		projMatrix.ortho(0.0f, 1.0f, 0.0f, 1.0f, 1.0f, -1.0f);
		RenderState.setProjectionMatrix(projMatrix);
		RenderState.setViewMatrix(null);

		RenderState.setPolygonMode(PolygonMode.FILL);
		BasicSolidShader shader = ShaderManager.use(BasicSolidShader.class);
		shader.baseColor.set(R, G, B, A);
		shader.setXYQuadCoords(0, 0, 1, 1, -1); //TODO is NEAR and FAR flipped? :/
		shader.renderQuad();
	}

	public void renderUI()
	{
		TransformMatrix projMatrix = TransformMatrix.identity();
		projMatrix.ortho(minX, maxX, maxY, minY, -1, 1);
		RenderState.setProjectionMatrix(projMatrix);
		RenderState.setViewMatrix(null);
		RenderState.setPolygonMode(PolygonMode.FILL);

		if (uiTextLL.enableFade) {
			if (recievedUpdateLL)
				recievedUpdateLL = false;
			else
				uiTextLL.setVisible(false);
		}
		uiTextLL.draw(16, minX + 8, maxY - 24, (float) MapEditor.instance().getDeltaTime());

		if (uiTextUL.enableFade) {
			if (recievedUpdateUL)
				recievedUpdateUL = false;
			else
				uiTextUL.setVisible(false);
		}
		uiTextUL.draw(12, minX + 8, minY + 8, (float) MapEditor.instance().getDeltaTime());
	}
}
