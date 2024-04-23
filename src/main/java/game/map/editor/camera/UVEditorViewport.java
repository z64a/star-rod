package game.map.editor.camera;

import static org.lwjgl.opengl.GL11.*;

import java.util.LinkedList;
import java.util.List;

import game.map.editor.geometry.Vector3f;

import game.map.BoundingBox;
import game.map.editor.MapEditor;
import game.map.editor.MapEditor.EditorMode;
import game.map.editor.render.PresetColor;
import game.map.editor.render.Renderer;
import game.map.editor.render.RenderingOptions;
import game.map.mesh.Triangle;
import game.map.mesh.Vertex;
import game.map.shape.Model;
import renderer.buffers.BufferVertex;
import renderer.buffers.PointRenderQueue;
import renderer.buffers.TriangleRenderQueue;
import renderer.shaders.RenderState;
import renderer.shaders.RenderState.PolygonMode;
import renderer.shaders.ShaderManager;
import renderer.shaders.scene.ModelShader;

public class UVEditorViewport extends MapEditViewport
{
	private Model mdl = null;
	private List<Triangle> triangles = new LinkedList<>();

	public UVEditorViewport(MapEditor editor, Renderer renderer)
	{
		super(editor, renderer, ViewType.FRONT);
		wireframeMode = true;
		camera = new OrthographicCamera(this, ViewType.FRONT);
	}

	public UVEditorViewport(MapEditor editor, Renderer renderer, int minX, int minY, int maxX, int maxY)
	{
		super(editor, renderer, ViewType.FRONT, minX, minY, maxX, maxY);
		wireframeMode = true;
		camera = new OrthographicCamera(this, ViewType.FRONT);
	}

	public void setModel(Model mdl)
	{ this.mdl = mdl; }

	public boolean setTriangles(List<Triangle> newTriangles)
	{
		boolean changed = (triangles.size() != newTriangles.size());
		if (!changed) {
			for (int i = 0; i < triangles.size(); i++) {
				changed = !triangles.get(i).equals(newTriangles.get(i));
				if (changed)
					break;
			}
		}

		triangles = newTriangles;

		if (changed) {
			BoundingBox aabb = new BoundingBox();
			for (Triangle t : newTriangles)
				for (Vertex v : t.vert)
					aabb.encompass(v.uv.getU(), v.uv.getV(), 0);

			camera.centerOn(aabb);
		}

		return changed;
	}

	@Override
	public float getScaleFactor(float x, float y, float z)
	{
		return 2.0f * ((OrthographicCamera) camera).getZoomLevel();
	}

	@Override
	public void render(RenderingOptions opts, boolean isActive)
	{
		setViewport();
		Renderer.setFogEnabled(editor.map.scripts, false);

		glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
		glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

		camera.glLoadProjection();
		camera.glLoadTransform();
		RenderState.setModelMatrix(null);

		if (mdl != null && mdl.getMesh().textured)
			drawBackground(opts);

		if (editor.gridEnabled) {
			RenderState.setModelMatrix(null);
			RenderState.enableDepthTest(false);
			camera.drawBackground();
			RenderState.enableDepthTest(true);
		}

		if (triangles != null)
			drawUVs();

		if (editor.showAxes)
			renderer.drawAxes(1.0f);

		if (editor.getEditorMode() == EditorMode.EditUVs)
			editor.selectionManager.uvSelection.render(renderer, this);

		if (editor.draggingBox)
			renderer.renderSelectionBox(editor.dragBoxStartPoint, editor.dragBoxEndPoint);

		renderUI();
	}

	private void drawBackground(RenderingOptions opts)
	{
		RenderState.setPolygonMode(PolygonMode.FILL);

		ModelShader shader = ShaderManager.use(ModelShader.class);
		shader.selected.set(false);
		shader.textured.set(true);
		shader.useProperties(mdl, opts.useFiltering, false);
		shader.enableFog.set(false);
		shader.drawMode.set(ModelShader.MODE_FILL_SOLID);

		Vector3f pos = camera.getPosition();
		float zoom = ((OrthographicCamera) camera).getZoomLevel();
		float fovX = (sizeX / 2.0f) * zoom;
		float fovY = (sizeY / 2.0f) * zoom;

		// the range of UVs displayed in the viewport, clamped to min/max values
		float uMin = (pos.x - fovX);
		float uMax = (pos.x + fovX);
		float vMin = (pos.y - fovY);
		float vMax = (pos.y + fovY);
		if (uMin < Short.MIN_VALUE)
			uMin = Short.MIN_VALUE;
		if (uMax > Short.MAX_VALUE)
			uMax = Short.MAX_VALUE;
		if (vMin < Short.MIN_VALUE)
			vMin = Short.MIN_VALUE;
		if (vMax > Short.MAX_VALUE)
			vMax = Short.MAX_VALUE;

		shader.setXYQuadCoords(uMin, vMin, uMax, vMax, -1.0f);
		shader.setQuadTexCoords(uMin, vMin, uMax, vMax);

		shader.renderQuad();
	}

	private void drawUVs()
	{
		RenderState.setColor(PresetColor.WHITE);
		RenderState.setPointSize(10.0f);
		RenderState.setLineWidth(2.0f);

		RenderState.setPolygonMode(PolygonMode.LINE);
		for (Triangle t : triangles) {
			int i = TriangleRenderQueue.addVertex().setPosition(t.vert[0].uv.getU(), t.vert[0].uv.getV(), 1.0f).getIndex();
			int j = TriangleRenderQueue.addVertex().setPosition(t.vert[1].uv.getU(), t.vert[1].uv.getV(), 1.0f).getIndex();
			int k = TriangleRenderQueue.addVertex().setPosition(t.vert[2].uv.getU(), t.vert[2].uv.getV(), 1.0f).getIndex();
			TriangleRenderQueue.addTriangle(i, j, k);
		}
		TriangleRenderQueue.renderWithTransform(null, true);
		RenderState.setPolygonMode(PolygonMode.FILL);

		for (Triangle t : triangles)
			for (Vertex vtx : t.vert) {
				BufferVertex bv = PointRenderQueue.addPoint().setPosition(vtx.uv.getU(), vtx.uv.getV(), 2.0f);
				if (vtx.uv.selected)
					bv.setColor(PresetColor.RED);
				else
					bv.setColor(PresetColor.YELLOW);
			}

		PointRenderQueue.render(true);
	}

	@Override
	public boolean allowsDragSelection()
	{
		return true;
	}

	@Override
	public BoundingBox getDragSelectionVolume(Vector3f start, Vector3f end)
	{
		BoundingBox selectionVolume = new BoundingBox();
		Vector3f clampedStart = new Vector3f(start);
		Vector3f clampedEnd = new Vector3f(end);

		clampedStart.z = Float.MAX_VALUE;
		clampedEnd.z = -Float.MAX_VALUE;

		selectionVolume.encompass((int) clampedStart.x, (int) clampedStart.y, (int) clampedStart.z);
		selectionVolume.encompass((int) clampedEnd.x, (int) clampedEnd.y, (int) clampedEnd.z);

		return selectionVolume;
	}
}
