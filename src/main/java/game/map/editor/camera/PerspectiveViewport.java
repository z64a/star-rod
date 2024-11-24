package game.map.editor.camera;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

import java.util.List;

import common.Vector3f;
import game.map.BoundingBox;
import game.map.Map;
import game.map.MapObject;
import game.map.editor.BasicProfiler;
import game.map.editor.MapEditor;
import game.map.editor.MapEditor.EditorMode;
import game.map.editor.MapEditor.PerspCameraMode;
import game.map.editor.PaintManager;
import game.map.editor.render.Renderer;
import game.map.editor.render.RenderingOptions;
import game.map.editor.render.RenderingOptions.SurfaceMode;
import game.map.editor.render.SortedRenderable;
import game.map.editor.selection.SelectionManager;
import game.map.hit.CameraZoneData;
import game.map.scripts.ScriptData;
import game.map.shape.TransformMatrix;
import renderer.FrameBuffer;
import renderer.buffers.DeferredLineRenderer;
import renderer.shaders.RenderState;
import renderer.shaders.RenderState.PolygonMode;
import renderer.shaders.postprocess.PostProcessFX;

public class PerspectiveViewport extends MapEditViewport
{
	public static boolean doPerspProfiling = false;

	private FrameBuffer sceneBuffer;
	private FrameBuffer effectBufferA;
	private FrameBuffer effectBufferB;

	public PerspectiveViewport(MapEditor editor, Renderer renderer)
	{
		super(editor, renderer, ViewType.PERSPECTIVE);
		wireframeMode = false;

		sceneBuffer = new FrameBuffer(true);
		effectBufferA = new FrameBuffer(false);
		effectBufferB = new FrameBuffer(false);
	}

	public PerspectiveViewport(MapEditor editor, Renderer renderer, int minX, int minY, int maxX, int maxY)
	{
		super(editor, renderer, ViewType.PERSPECTIVE, minX, minY, maxX, maxY);
		wireframeMode = false;
		camera = new PerspFreeCamera(this);
	}

	public void setCamera(PerspBaseCamera cam)
	{
		camera = cam;
	}

	@Override
	public float getScaleFactor(float x, float y, float z)
	{
		float dx = camera.pos.x - x;
		float dy = camera.pos.y - y;
		float dz = camera.pos.z - z;

		//	double fov = ((PerspBaseCamera)camera).getvfov();
		//	double fovScale = Math.tan(Math.toRadians(fov / 2));
		double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

		if (editor.usingInGameCameraProperties())
			return (float) (0.06f + Math.pow(distance / 240.0, 0.8));
		else
			return (float) (0.06f + Math.pow(distance / 120.0, 0.8));
	}

	@Override
	public void render(RenderingOptions opts, boolean isActive)
	{
		BasicProfiler profiler = null;
		if (doPerspProfiling) {
			profiler = new BasicProfiler();
			profiler.begin();
		}

		sceneBuffer.bind(opts.canvasSizeX, opts.canvasSizeY);

		// clear whole buffer
		RenderState.setViewport(0, 0, opts.canvasSizeX, opts.canvasSizeY);
		glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
		glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);

		// draw the actual viewport contents

		sceneBuffer.setViewport(minX, minY, sizeX, sizeY);
		// allow camera to create sub-viewport
		camera.glSetViewport(minX, minY, sizeX, sizeY);
		renderWorld(opts, isActive, profiler);

		// prepare render state for postprocess rendering
		TransformMatrix projMtx = TransformMatrix.identity();
		projMtx.ortho(0, 1, 0, 1, -1, 1);
		RenderState.setProjectionMatrix(projMtx);
		RenderState.setViewMatrix(null);
		RenderState.setModelMatrix(null);
		RenderState.setPolygonMode(PolygonMode.FILL);

		// do post processing passes
		PostProcessFX effect = opts.postProcessFX;
		FrameBuffer prevBuffer = sceneBuffer;
		FrameBuffer nextBuffer = effectBufferA;

		if (effect != PostProcessFX.NONE) {
			// ensure there is enough room in the effect buffer textures for downsampling
			int reqSizeX = Math.max(sceneBuffer.sizeX, 800) * 2;
			int reqSizeY = Math.max(sceneBuffer.sizeY, 600);

			glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
			effectBufferA.bind(reqSizeX, reqSizeY);
			RenderState.setViewport(0, 0, reqSizeX, reqSizeY);
			glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
			effectBufferB.bind(reqSizeX, reqSizeY);
			RenderState.setViewport(0, 0, reqSizeX, reqSizeY);
			glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

			for (int i = 0; i < effect.getNumPasses(); i++) {
				effect.apply(i, sceneBuffer, nextBuffer, prevBuffer, opts.time);

				// swap effect buffers
				prevBuffer = nextBuffer;
				if (nextBuffer == effectBufferA)
					nextBuffer = effectBufferB;
				else
					nextBuffer = effectBufferA;
			}

			if (doPerspProfiling)
				profiler.record("post-proc");
		}

		glBindFramebuffer(GL_FRAMEBUFFER, 0);

		// set viewport to final position and clear
		RenderState.setViewport(minX, minY, sizeX, sizeY);
		glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
		glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

		// render final viewport
		PostProcessFX.NONE.apply(0, sceneBuffer, null, prevBuffer, opts.time);

		glBindFramebuffer(GL_READ_FRAMEBUFFER, sceneBuffer.getFrameBuffer());
		glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
		glBlitFramebuffer(minX, minY, minX + sizeX, minY + sizeY,
			minX, minY, minX + sizeX, minY + sizeY,
			GL_DEPTH_BUFFER_BIT,
			GL_NEAREST);

		glBindFramebuffer(GL_FRAMEBUFFER, 0);

		if (doPerspProfiling)
			profiler.print();
	}

	private void renderWorld(RenderingOptions opts, boolean isActive, BasicProfiler profiler)
	{
		DeferredLineRenderer.reset();
		RenderState.setPolygonMode(PolygonMode.FILL);
		RenderState.setDepthWrite(true);

		if (opts.editorMode == EditorMode.VertexPaint)
			opts.modelSurfaceMode = wireframeMode ? SurfaceMode.WIREFRAME : PaintManager.getRenderMode();
		else
			opts.modelSurfaceMode = wireframeMode ? SurfaceMode.WIREFRAME : SurfaceMode.TEXTURED;

		boolean onlyDrawModels = (opts.editorMode != EditorMode.Modify && opts.editorMode != EditorMode.Scripts);

		if (opts.screenFade >= 1.0f) {
			glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
			glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
			return;
		}

		ScriptData scriptData = editor.map.scripts;
		if (editor.useMapBackgroundColor)
			glClearColor(scriptData.bgColorR.get() / 255.0f, scriptData.bgColorG.get() / 255.0f, scriptData.bgColorB.get() / 255.0f, 1.0f);
		else
			glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
		glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

		Renderer.setFogEnabled(editor.map.scripts, editor.usingInGameCameraProperties());

		if (!wireframeMode && (editor.map.hasBackground || !editor.useMapBackgroundColor))
			camera.drawBackground();

		camera.glLoadProjection();
		camera.glLoadTransform();
		RenderState.setModelMatrix(null);

		if (doPerspProfiling)
			profiler.record("setup");

		Map shapeMap = editor.getGeometryMap();
		Map hitMap = editor.getCollisionMap();

		List<SortedRenderable> renderables = Renderer.getRenderables(opts, shapeMap.modelTree, editor.map.markerTree, true);
		if (!opts.thumbnailMode)
			editor.cursor3D.addRenderables(renderables, this);

		renderables = Renderer.sortByRenderDepth(camera, renderables);

		if (doPerspProfiling)
			profiler.record("depth sort");

		Renderer.drawOpaque(opts, camera, renderables);

		if (doPerspProfiling)
			profiler.record("pass 1");

		if (!onlyDrawModels)
			renderer.drawMarkers(opts, editor.map.markerTree, this);

		if (!opts.thumbnailMode) {
			editor.cursor3D.render(this, opts, camera.pos);

			if (opts.spriteShading != null)
				opts.spriteShading.render(this, opts, camera.pos);
		}

		if (doPerspProfiling)
			profiler.record("markers");

		Renderer.drawTranslucent(opts, camera, renderables);

		if (doPerspProfiling)
			profiler.record("pass 2");

		if (!onlyDrawModels) {
			renderer.drawColliders(opts, hitMap.colliderTree);
			renderer.drawZones(opts, hitMap.zoneTree);
		}

		if (doPerspProfiling)
			profiler.record("hit");

		// now draw points and lines once all triangulated geometry has been rendered

		if (opts.selectionMode == SelectionManager.SelectionMode.VERTEX)
			renderer.drawVertices(editor.selectionManager.getVertices(), false);

		if (opts.selectionMode == SelectionManager.SelectionMode.POINT) {
			List<MapObject> selectedObjects = MapEditor.instance().selectionManager.getSelectedObjects();
			for (MapObject obj : selectedObjects) {
				if (obj.hasSelectablePoints())
					obj.renderPoints(opts, renderer, this);
			}
		}

		if (doPerspProfiling)
			profiler.record("points");

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

		renderer.drawGeometryPreviews(editor);

		DeferredLineRenderer.render();

		if (editor.showAxes)
			renderer.drawAxes(2.0f);

		if (opts.screenFade != 0.0f)
			renderFade(0.0f, 0.0f, 0.0f, opts.screenFade);

		renderUI();

		if (doPerspProfiling)
			profiler.record("lines");
	}

	@Override
	public boolean allowsDragSelection()
	{
		return false;
	}

	@Override
	public BoundingBox getDragSelectionVolume(Vector3f start, Vector3f end)
	{
		return null;
	}
}
