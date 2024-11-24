package game.map.editor.camera;

import static org.lwjgl.opengl.GL11.*;

import java.util.List;

import common.Vector3f;
import game.map.BoundingBox;
import game.map.Map;
import game.map.MapObject;
import game.map.editor.MapEditor;
import game.map.editor.MapEditor.EditorMode;
import game.map.editor.MapEditor.PerspCameraMode;
import game.map.editor.render.Renderer;
import game.map.editor.render.RenderingOptions;
import game.map.editor.render.RenderingOptions.SurfaceMode;
import game.map.editor.render.SortedRenderable;
import game.map.editor.selection.SelectionManager;
import game.map.hit.CameraZoneData;
import renderer.buffers.DeferredLineRenderer;
import renderer.shaders.RenderState;

public class OrthographicViewport extends MapEditViewport
{
	public OrthographicViewport(MapEditor editor, Renderer renderer, ViewType type)
	{
		super(editor, renderer, type);
		wireframeMode = true;
		camera = new OrthographicCamera(this, type);
	}

	public OrthographicViewport(MapEditor editor, Renderer renderer, int minX, int minY, int maxX, int maxY, ViewType type)
	{
		super(editor, renderer, type, minX, minY, maxX, maxY);
		wireframeMode = true;
		camera = new OrthographicCamera(this, type);
	}

	public float getViewWorldSizeX()
	{
		return ((OrthographicCamera) camera).getZoomLevel() * sizeX;
	}

	public BoundingBox getViewingVolume()
	{
		BoundingBox viewportVolume = new BoundingBox();
		Vector3f min = new Vector3f();
		Vector3f max = new Vector3f();

		float width = ((OrthographicCamera) camera).getZoomLevel() * sizeX;
		float height = ((OrthographicCamera) camera).getZoomLevel() * sizeY;

		switch (type) {
			case TOP:
				max.y = Float.MAX_VALUE;
				min.y = -Float.MAX_VALUE;
				max.x = camera.pos.x + width / 2;
				min.x = camera.pos.x - width / 2;
				max.z = camera.pos.z + height / 2;
				min.z = camera.pos.z - height / 2;
				break;

			case SIDE:
				max.x = Float.MAX_VALUE;
				min.x = -Float.MAX_VALUE;
				max.z = camera.pos.z + width / 2;
				min.z = camera.pos.z - width / 2;
				max.y = camera.pos.y + height / 2;
				min.y = camera.pos.y - height / 2;
				break;

			case FRONT:
				max.z = Float.MAX_VALUE;
				min.z = -Float.MAX_VALUE;
				max.x = camera.pos.x + width / 2;
				min.x = camera.pos.x - width / 2;
				max.y = camera.pos.y + height / 2;
				min.y = camera.pos.y - height / 2;
				break;

			case PERSPECTIVE:
				throw new IllegalStateException();
		}

		viewportVolume.encompass(min);
		viewportVolume.encompass(max);

		return viewportVolume;
	}

	@Override
	public float getScaleFactor(float x, float y, float z)
	{
		return Math.max(2.0f * ((OrthographicCamera) camera).getZoomLevel(), 0.275f);
	}

	@Override
	public void render(RenderingOptions opts, boolean isActive)
	{
		DeferredLineRenderer.reset();
		opts.modelSurfaceMode = wireframeMode ? SurfaceMode.WIREFRAME : SurfaceMode.TEXTURED;

		boolean onlyDrawModels = (opts.editorMode != EditorMode.Modify && opts.editorMode != EditorMode.Scripts);

		setViewport();
		Renderer.setFogEnabled(editor.map.scripts, false);

		glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
		glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

		camera.glLoadProjection();
		camera.glLoadTransform();
		RenderState.setModelMatrix(null);

		if (editor.gridEnabled)
			camera.drawBackground();

		Map shapeMap = editor.getGeometryMap();
		Map hitMap = editor.getCollisionMap();

		List<SortedRenderable> renderables = Renderer.getRenderables(opts, shapeMap.modelTree, editor.map.markerTree, false);
		renderables = Renderer.sortByRenderDepth(camera, renderables);
		Renderer.drawOpaque(opts, camera, renderables);
		Renderer.drawTranslucent(opts, camera, renderables);

		if (!onlyDrawModels) {
			renderer.drawColliders(opts, hitMap.colliderTree);
			renderer.drawZones(opts, hitMap.zoneTree);
			// markers drawn last in ortho views so they render in front of translucent faces
			renderer.drawMarkers(opts, editor.map.markerTree, this);
		}

		// done drawing map objects, time to draw helpers and UI elements

		if (opts.selectionMode == SelectionManager.SelectionMode.VERTEX)
			renderer.drawVertices(editor.selectionManager.getVertices(), true);

		RenderState.enableDepthTest(false);
		if (opts.selectionMode == SelectionManager.SelectionMode.POINT) {
			List<MapObject> selectedObjects = MapEditor.instance().selectionManager.getSelectedObjects();
			for (MapObject obj : selectedObjects) {
				if (obj.hasSelectablePoints())
					obj.renderPoints(opts, renderer, this);
			}
		}

		if (editor.getCameraMode() == PerspCameraMode.PLAY_IN_EDITOR) {
			CameraZoneData controlData = editor.getCameraControlData();
			if (controlData != null && editor.pieDrawCameraInfo) {
				controlData.drawHelpers(renderer, editor.getCameraController(), false);
			}
		}

		// draw various editor helpers and hints
		switch (opts.editorMode) {
			case Modify:
			case Scripts:
				editor.selectionManager.currentSelection.render(renderer, this);
				break;

			case VertexPaint:
				renderer.drawPaintSphere(editor.paintPickHit);
				break;
			default:
		}
		RenderState.enableDepthTest(true);

		if (!opts.thumbnailMode) {
			editor.cursor3D.render(this, opts, camera.pos);
			if (opts.spriteShading != null)
				opts.spriteShading.render(this, opts, camera.pos);
		}

		renderer.drawGeometryPreviews(editor);

		DeferredLineRenderer.render();

		if (editor.draggingBox)
			renderer.renderSelectionBox(editor.dragBoxStartPoint, editor.dragBoxEndPoint);

		renderUI();
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

		switch (type) {
			case TOP:
				clampedStart.y = Float.MAX_VALUE;
				clampedEnd.y = -Float.MAX_VALUE;
				break;
			case SIDE:
				clampedStart.x = Float.MAX_VALUE;
				clampedEnd.x = -Float.MAX_VALUE;
				break;
			case FRONT:
				clampedStart.z = Float.MAX_VALUE;
				clampedEnd.z = -Float.MAX_VALUE;
				break;
			case PERSPECTIVE:
				throw new IllegalStateException();
		}

		selectionVolume.encompass((int) clampedStart.x, (int) clampedStart.y, (int) clampedStart.z);
		selectionVolume.encompass((int) clampedEnd.x, (int) clampedEnd.y, (int) clampedEnd.z);

		return selectionVolume;
	}
}
