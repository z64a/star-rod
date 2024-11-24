package game.map.editor.render;

import static org.lwjgl.opengl.ARBDepthClamp.GL_DEPTH_CLAMP;
import static org.lwjgl.opengl.GL11.*;
import static renderer.shaders.scene.ModelShader.MODE_LINE_OUTLINE;
import static renderer.shaders.scene.ModelShader.MODE_LINE_SOLID;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import common.BaseCamera;
import common.Vector3f;
import game.entity.EntityInfo;
import game.map.editor.MapEditor;
import game.map.editor.MapEditor.IShutdownListener;
import game.map.editor.PaintManager;
import game.map.editor.camera.MapEditViewport;
import game.map.editor.selection.PickRay.PickHit;
import game.map.hit.Collider;
import game.map.hit.Zone;
import game.map.marker.Marker;
import game.map.marker.Marker.MarkerType;
import game.map.mesh.AbstractMesh;
import game.map.mesh.Triangle;
import game.map.mesh.Vertex;
import game.map.scripts.FogSettings;
import game.map.scripts.ScriptData;
import game.map.shape.Model;
import game.map.shape.ModelRenderer.RenderableModel;
import game.map.shape.TransformMatrix;
import game.map.shape.TriangleBatch;
import renderer.buffers.BufferVertex;
import renderer.buffers.CubeMesh;
import renderer.buffers.DeferredLineRenderer;
import renderer.buffers.LineBatch;
import renderer.buffers.LineRenderQueue;
import renderer.buffers.LineSphere;
import renderer.buffers.PointRenderQueue;
import renderer.buffers.TriangleRenderQueue;
import renderer.shaders.RenderState;
import renderer.shaders.RenderState.PolygonMode;
import renderer.shaders.ShaderManager;
import renderer.shaders.scene.BasicSolidShader;
import renderer.shaders.scene.EntityShader;
import renderer.shaders.scene.HitShader;
import renderer.shaders.scene.LineShader;
import renderer.shaders.scene.ModelShader;
import renderer.shaders.scene.PointShader;
import renderer.text.TextRenderer;
import util.MathUtil;

public class Renderer implements IShutdownListener
{
	private static Renderer instance = null;

	private LineSphere sphere36;
	private LineSphere sphere48;
	private CubeMesh cube;

	// paint sphere + interp info
	private float paintPosX, paintPosY, paintPosZ, paintAlpha;
	private boolean paintHitMiss = true;

	public static Renderer instance()
	{
		return instance;
	}

	@Override
	public void shutdown()
	{
		instance = null;
	}

	public Renderer(MapEditor editor)
	{
		if (instance != null)
			throw new IllegalStateException("There can be only one Renderer");
		instance = this;
		editor.registerOnShutdown(this);

		glCullFace(GL_BACK);

		glEnable(GL_SCISSOR_TEST);
		glEnable(GL_DEPTH_CLAMP);

		RenderState.init();
		ShadowRenderer.init();
		DeferredLineRenderer.init();
		TextRenderer.init();

		EntityInfo.loadModels();

		sphere36 = new LineSphere(36, 36, 3);
		sphere48 = new LineSphere(48, 48, 3);
		cube = new CubeMesh();
	}

	public void renderTexturedCube(TransformMatrix mtx)
	{
		cube.renderWithTransform(mtx);
	}

	public void renderLineSphere36(TransformMatrix mtx)
	{
		sphere36.renderWithTransform(mtx);
	}

	public void renderLineSphere48(TransformMatrix mtx)
	{
		sphere48.renderWithTransform(mtx);
	}

	// animated selection box
	private static double omega = 2.0 * Math.PI / 1.6;
	private static double slowOmega = 2.0 * Math.PI / 2.4;
	private static double fastOmega = 2.0 * Math.PI / 0.8;
	private static float color = 0.0f;
	private static float pulse = 0.0f;
	private static float slowPulse = 0.0f;
	private static float fastPulse = 0.0f;

	public static float getPulse()
	{
		return pulse;
	}

	public static float getSlowPulse()
	{
		return slowPulse;
	}

	public static float getFastPulse()
	{
		return fastPulse;
	}

	public static float interpColor(float min, float max)
	{
		return min + color * (max - min);
	}

	public static void updateColors(double time)
	{
		pulse = 0.5f * (float) Math.sin(omega * time);
		pulse = 0.5f + pulse * pulse; // more pleasing

		slowPulse = 0.5f * (float) Math.sin(slowOmega * time);
		slowPulse = 0.5f + slowPulse * slowPulse; // more pleasing

		fastPulse = 0.5f * (float) Math.sin(fastOmega * time);
		fastPulse = 0.5f + fastPulse * fastPulse; // more pleasing

		color = 0.5f * (float) Math.sin(omega * time);
		color = 0.5f + color * color; // more pleasing
	}

	private static final Comparator<SortedRenderable> DEPTH_COMPARATOR = (obj1, obj2) -> obj1.getDepth() - obj2.getDepth();

	private static final int[] LAYERS = {
			8000000,
			7500000,
			7000000,
			6000000,
			5500000,
			1000000,
			700000,
			0
	};

	public static List<SortedRenderable> getRenderables(RenderingOptions opts, Iterable<Model> models, Iterable<Marker> markers, boolean includeNPCs)
	{
		List<SortedRenderable> renderables = new ArrayList<>(100);

		if (models != null)
			for (Model mdl : models) {
				if (mdl.shouldDraw())
					renderables.add(new RenderableModel(mdl));
			}

		if (markers != null)
			for (Marker m : markers) {
				if (m.shouldDraw() && (m.type != MarkerType.NPC || includeNPCs))
					m.addRenderables(opts, renderables);
			}

		return renderables;
	}

	public static List<SortedRenderable> sortByRenderDepth(BaseCamera cam, List<SortedRenderable> renderables)
	{
		TransformMatrix tx = TransformMatrix.identity();
		tx = TransformMatrix.multiply(cam.viewMatrix, tx);
		tx = TransformMatrix.multiply(cam.projMatrix, tx);

		for (SortedRenderable renderable : renderables) {
			float sceneDepth = tx.applyTransform(renderable.getCenterPoint()).z;
			int normalizedDepth = MathUtil.clamp((int) (5000.0f + sceneDepth), 0, 10000); // -5000-5000 --> 0-10000 (clamped)
			renderable.setDepth(normalizedDepth);
		}

		List<SortedRenderable> sortedList = new ArrayList<>(renderables.size());

		for (int layer : LAYERS) {
			List<SortedRenderable> layerList = new ArrayList<>(renderables.size());
			for (SortedRenderable renderable : renderables) {
				if (renderable.getRenderMode().depth == layer)
					layerList.add(renderable);
			}

			Collections.sort(layerList, DEPTH_COMPARATOR);
			Collections.reverse(layerList);
			sortedList.addAll(layerList);
		}

		return sortedList;
	}

	public static void drawOpaque(RenderingOptions opts, BaseCamera camera, List<SortedRenderable> renderables)
	{
		for (SortedRenderable renderable : renderables) {
			RenderMode mode = renderable.getRenderMode();

			if (mode.depth > 2999999)
				continue;

			renderable.render(opts, camera);
		}
	}

	public static void drawTranslucent(RenderingOptions opts, BaseCamera camera, List<SortedRenderable> renderables)
	{
		for (SortedRenderable renderable : renderables) {
			RenderMode mode = renderable.getRenderMode();

			if (mode.depth <= 2999999)
				continue;

			renderable.render(opts, camera);
		}
	}

	public void drawColliders(RenderingOptions opts, Iterable<Collider> colliders)
	{
		glPolygonOffset(-1.2f, -1.2f);
		glEnable(GL_POLYGON_OFFSET_FILL);

		RenderState.setDepthWrite(false);
		RenderState.setLineWidth(1.0f);
		RenderState.setModelMatrix(null);

		for (Collider c : colliders) {
			if (!c.shouldDraw())
				continue;

			AbstractMesh mesh = c.getMesh();
			mesh.setVAO();

			HitShader shader = ShaderManager.use(HitShader.class);
			shader.selectColor.set(0.0f, 1.0f, 0.0f, 0.50f);

			// draw solid model
			RenderState.setPolygonMode(PolygonMode.FILL);
			shader.drawMode.set(MODE_LINE_SOLID);
			for (TriangleBatch batch : mesh.getBatches())
				glDrawArrays(GL_TRIANGLES, batch.bufferStartPos, 3 * batch.triangles.size());

			// draw edge highlights
			RenderState.setPolygonMode(PolygonMode.LINE);
			shader.drawMode.set(MODE_LINE_OUTLINE);
			for (TriangleBatch batch : mesh.getBatches())
				glDrawArrays(GL_TRIANGLES, batch.bufferStartPos, 3 * batch.triangles.size());
		}

		RenderState.setDepthWrite(true);

		for (Collider c : colliders) {
			if (!c.shouldDraw())
				continue;

			if (opts.showBoundingBoxes)
				c.AABB.render();

			if (opts.showNormals)
				drawNormals(c.mesh);
		}

		glDisable(GL_POLYGON_OFFSET_FILL);
	}

	public void drawZones(RenderingOptions opts, Iterable<Zone> zones)
	{
		glPolygonOffset(-1.3f, -1.3f);
		glEnable(GL_POLYGON_OFFSET_FILL);

		RenderState.setDepthWrite(false);
		RenderState.setLineWidth(1.0f);
		RenderState.setModelMatrix(null);

		for (Zone z : zones) {
			if (!z.shouldDraw())
				continue;

			AbstractMesh mesh = z.getMesh();
			mesh.setVAO();

			HitShader shader = ShaderManager.use(HitShader.class);
			shader.selectColor.set(0.0f, 1.0f, 0.0f, 0.50f);

			// draw solid model
			RenderState.setPolygonMode(PolygonMode.FILL);
			shader.drawMode.set(MODE_LINE_SOLID);
			for (TriangleBatch batch : mesh.getBatches())
				glDrawArrays(GL_TRIANGLES, batch.bufferStartPos, 3 * batch.triangles.size());

			// draw edge highlights
			RenderState.setPolygonMode(PolygonMode.LINE);
			shader.drawMode.set(MODE_LINE_OUTLINE);
			for (TriangleBatch batch : mesh.getBatches())
				glDrawArrays(GL_TRIANGLES, batch.bufferStartPos, 3 * batch.triangles.size());
		}

		RenderState.setDepthWrite(true);

		for (Zone z : zones) {
			if (!z.shouldDraw())
				continue;

			if (opts.showBoundingBoxes)
				z.AABB.render();

			if (opts.showNormals)
				drawNormals(z.mesh);
		}

		glDisable(GL_POLYGON_OFFSET_FILL);
	}

	public void drawMarkers(RenderingOptions opts, Iterable<Marker> markers, MapEditViewport view)
	{
		for (Marker m : markers) {
			if (!m.shouldDraw())
				continue;

			m.render(opts, view, this);
		}
	}

	public void drawVertices(Iterable<Vertex> vertices, boolean ignoreDepth)
	{
		RenderState.setPointSize(8.0f);

		for (Vertex v : vertices) {
			BufferVertex bv = PointRenderQueue.addPoint();
			bv.setPosition(v.getCurrentX(), v.getCurrentY(), v.getCurrentZ());

			if (v.selected)
				bv.setColor(PresetColor.WHITE);
			else
				bv.setColor(PresetColor.YELLOW);
		}

		//	glPushAttrib(GL_ENABLE_BIT);
		//	glEnable(GL_POLYGON_OFFSET_POINT);
		//	glPolygonOffset(-1.5f, -1.5f);

		PointShader shader = ShaderManager.use(PointShader.class);
		RenderState.setDepthWrite(false);

		if (ignoreDepth) {
			RenderState.enableDepthTest(false);
			shader.color.set(1.0f, 1.0f, 1.0f, 1.0f);
			PointRenderQueue.render(shader, false);
			RenderState.enableDepthTest(true);
		}
		else {
			RenderState.enableDepthTest(false);
			shader.color.set(1.0f, 1.0f, 1.0f, 0.4f);
			PointRenderQueue.render(shader, false);
			RenderState.enableDepthTest(true);

			shader.color.set(1.0f, 1.0f, 1.0f, 1.0f);
			PointRenderQueue.render(shader, true);
		}

		RenderState.setDepthWrite(true);
	}

	public static void drawNormals(AbstractMesh mesh)
	{
		float normalsLength = MapEditor.instance().getNormalsLength();

		RenderState.setColor(PresetColor.YELLOW);
		RenderState.setLineWidth(1.0f);

		LineBatch batch = DeferredLineRenderer.addLineBatch(true).setLineWidth(1.0f);
		for (Triangle t : mesh) {
			Vector3f center = t.getCenter();
			Vector3f normal = t.getNormal();

			if (normal == null)
				continue;

			float backLength = t.doubleSided ? normalsLength : 0.0f;

			int i = batch.addVertex().setPosition(
				center.x - backLength * normal.x,
				center.y - backLength * normal.y,
				center.z - backLength * normal.z).getIndex();
			int j = batch.addVertex().setPosition(
				center.x + normalsLength * normal.x,
				center.y + normalsLength * normal.y,
				center.z + normalsLength * normal.z).getIndex();
			batch.add(i, j);
		}
	}

	public static void queueStipple(Vector3f a, Vector3f b, float dashLength)
	{
		queueStipple(a.x, a.y, a.z, b.x, b.y, b.z, dashLength);
	}

	public static void queueStipple(float ax, float ay, float az, float bx, float by, float bz, float dashLength)
	{
		float dx = (bx - ax);
		float dy = (by - ay);
		float dz = (bz - az);

		float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		int n = Math.round(len / dashLength);
		if (n > 200)
			n = 200;
		if (n % 2 == 0)
			n++;

		dx /= n;
		dy /= n;
		dz /= n;

		for (int i = 0; i < n; i += 2) {
			int j = i + 1;
			LineRenderQueue.addLine(
				LineRenderQueue.addVertex().setPosition(ax + i * dx, ay + i * dy, az + i * dz).getIndex(),
				LineRenderQueue.addVertex().setPosition(ax + j * dx, ay + j * dy, az + j * dz).getIndex());
		}
	}

	// by convention: x = red, y = green, z = blue
	public void drawAxes(float lineWidth)
	{
		RenderState.setLineWidth(lineWidth);

		LineRenderQueue.addLine(
			LineRenderQueue.addVertex().setPosition(Short.MIN_VALUE, 0, 0).setColor(PresetColor.RED).getIndex(),
			LineRenderQueue.addVertex().setPosition(Short.MAX_VALUE, 0, 0).setColor(PresetColor.RED).getIndex());

		LineRenderQueue.addLine(
			LineRenderQueue.addVertex().setPosition(0, Short.MIN_VALUE, 0).setColor(PresetColor.GREEN).getIndex(),
			LineRenderQueue.addVertex().setPosition(0, Short.MAX_VALUE, 0).setColor(PresetColor.GREEN).getIndex());

		LineRenderQueue.addLine(
			LineRenderQueue.addVertex().setPosition(0, 0, Short.MIN_VALUE).setColor(PresetColor.BLUE).getIndex(),
			LineRenderQueue.addVertex().setPosition(0, 0, Short.MAX_VALUE).setColor(PresetColor.BLUE).getIndex());

		LineRenderQueue.render(true);
	}

	public void drawPaintSphere(PickHit hit)
	{
		RenderState.setDepthWrite(false);

		double dt = MapEditor.instance().getDeltaTime();

		// slight visual interp for paint sphere pos
		if (hit == null || hit.missed()) {
			paintHitMiss = true;
			paintAlpha = paintAlpha * (float) Math.pow(0.001, dt);

			//	if(paintAlpha > 0.5f)
			//		paintAlpha = paintAlpha * (float)Math.pow(0.10, dt);// = (0.0f + paintAlpha) / 2;
			//	else
			//		paintAlpha -= 2.0*dt;
			if (paintAlpha < 0.1)
				paintAlpha = 0.0f;
		}
		else {
			if (paintHitMiss) {
				paintPosX = hit.point.x;
				paintPosY = hit.point.y;
				paintPosZ = hit.point.z;
			}
			else {
				paintPosX = (hit.point.x + paintPosX) / 2;
				paintPosY = (hit.point.y + paintPosY) / 2;
				paintPosZ = (hit.point.z + paintPosZ) / 2;
			}
			paintHitMiss = false;
			paintAlpha = (1.0f + paintAlpha) / 2;
			if (paintAlpha > 0.99)
				paintAlpha = 1.0f;
		}

		if (paintAlpha == 0.0f) {
			RenderState.setDepthWrite(true);
			return;
		}

		TransformMatrix mtx;
		LineShader shader = ShaderManager.use(LineShader.class);
		shader.useVertexColor.set(false);
		shader.color.set(color, 1.0f, color, paintAlpha);

		if (PaintManager.shouldDrawInnerRadius()) {
			float radius1 = PaintManager.getInnerBrushRadius();
			mtx = TransformMatrix.identity();
			mtx.scale(radius1);
			mtx.translate(paintPosX, paintPosY, paintPosZ);
			renderLineSphere48(mtx);
		}

		float radius2 = PaintManager.getOuterBrushRadius();
		mtx = TransformMatrix.identity();
		mtx.scale(radius2);
		mtx.translate(paintPosX, paintPosY, paintPosZ);
		sphere48.renderWithTransform(mtx);

		RenderState.setColor(color, 1.0f, color, paintAlpha);
		RenderState.setModelMatrix(null);
		RenderState.setPointSize(10.0f);

		RenderState.enableDepthTest(false);
		PointRenderQueue.addPoint().setPosition(paintPosX, paintPosY, paintPosZ);
		PointRenderQueue.render(true);
		RenderState.enableDepthTest(true);

		RenderState.setDepthWrite(true);
	}

	public void renderSelectionBox(Vector3f startPoint, Vector3f endPoint)
	{
		RenderState.setLineWidth(1.0f);
		RenderState.setPointSize(8.0f);
		RenderState.setColor(PresetColor.WHITE);

		int mmm = LineRenderQueue.addVertex().setPosition(startPoint.x, startPoint.y, startPoint.z).getIndex();
		int Mmm = LineRenderQueue.addVertex().setPosition(endPoint.x, startPoint.y, startPoint.z).getIndex();
		int mMm = LineRenderQueue.addVertex().setPosition(startPoint.x, endPoint.y, startPoint.z).getIndex();
		int MMm = LineRenderQueue.addVertex().setPosition(endPoint.x, endPoint.y, startPoint.z).getIndex();
		int mmM = LineRenderQueue.addVertex().setPosition(startPoint.x, startPoint.y, endPoint.z).getIndex();
		int MmM = LineRenderQueue.addVertex().setPosition(endPoint.x, startPoint.y, endPoint.z).getIndex();
		int mMM = LineRenderQueue.addVertex().setPosition(startPoint.x, endPoint.y, endPoint.z).getIndex();
		int MMM = LineRenderQueue.addVertex().setPosition(endPoint.x, endPoint.y, endPoint.z).getIndex();
		LineRenderQueue.addLine(mmm, Mmm, MMm, mMm, mmm);
		LineRenderQueue.addLine(mmM, MmM, MMM, mMM, mmM);
		LineRenderQueue.addLine(mmm, mmM);
		LineRenderQueue.addLine(Mmm, MmM);
		LineRenderQueue.addLine(mMm, mMM);
		LineRenderQueue.addLine(MMm, MMM);

		RenderState.enableDepthTest(false);
		LineRenderQueue.render(true);
		RenderState.enableDepthTest(true);
	}

	public void drawGeometryPreviews(MapEditor editor)
	{
		List<PreviewGeometry> previews = new ArrayList<>();

		if (editor.generatePrimitivePreview.visible)
			previews.add(editor.generatePrimitivePreview);

		if (editor.generateFromTrianglesPreview.visible)
			previews.add(editor.generateFromTrianglesPreview);

		if (editor.generateFromPathsPreview.visible)
			previews.add(editor.generateFromPathsPreview);

		if (editor.drawGeometryPreview.visible)
			previews.add(editor.drawGeometryPreview);

		if (previews.isEmpty())
			return;

		RenderState.setModelMatrix(null);
		RenderState.setLineWidth(2.0f);
		RenderState.setDepthWrite(false);

		for (PreviewGeometry preview : previews) {
			RenderState.enableDepthTest(preview.useDepth);

			if (preview.batch != null && preview.batch.triangles.size() > 0) {
				RenderState.setPolygonMode(PolygonMode.LINE);
				RenderState.setColor(preview.color.x, preview.color.y, preview.color.z);

				for (Triangle t : preview.batch.triangles) {
					TriangleRenderQueue.addTriangle(
						TriangleRenderQueue.addVertex()
							.setPosition(t.vert[0].getCurrentX(), t.vert[0].getCurrentY(), t.vert[0].getCurrentZ())
							.getIndex(),
						TriangleRenderQueue.addVertex()
							.setPosition(t.vert[1].getCurrentX(), t.vert[1].getCurrentY(), t.vert[1].getCurrentZ())
							.getIndex(),
						TriangleRenderQueue.addVertex()
							.setPosition(t.vert[2].getCurrentX(), t.vert[2].getCurrentY(), t.vert[2].getCurrentZ())
							.getIndex());
				}

				switch (preview.drawMode) {
					case ANIM_EDGES: {
						LineShader shader = ShaderManager.use(LineShader.class);
						shader.dashSize.set(20.0f);
						shader.dashRatio.set(0.5f);
						shader.dashSpeedRate.set(1.0f);
						TriangleRenderQueue.render(shader, true);
					}
						break;

					case EDGES: {
						LineShader shader = ShaderManager.use(LineShader.class);
						shader.dashRatio.set(1.0f);
						TriangleRenderQueue.render(shader, true);
					}
						break;

					case FILLED:
						BasicSolidShader shader = ShaderManager.use(BasicSolidShader.class);
						TriangleRenderQueue.render(shader, false);
						RenderState.setPolygonMode(PolygonMode.FILL);
						shader.baseColor.set(preview.color.x, preview.color.y, preview.color.z, 0.5f);
						TriangleRenderQueue.render(shader, true);
						break;
				}
			}

			if (!preview.edges.isEmpty()) {
				RenderState.setColor(preview.color.x, preview.color.y, preview.color.z);

				int[] indices = new int[preview.edges.size()];
				for (int i = 0; i < preview.edges.size(); i++) {
					Vector3f v = preview.edges.get(i);
					indices[i] = LineRenderQueue.addVertex().setPosition(v.x, v.y, v.z).getIndex();
				}
				LineRenderQueue.addLine(indices);

				LineRenderQueue.render(true);
			}

			if (!preview.points.isEmpty()) {
				RenderState.setColor(preview.color.x, preview.color.y, preview.color.z);
				for (Vector3f v : preview.points)
					PointRenderQueue.addPoint().setPosition(v.x, v.y, v.z);
				PointRenderQueue.render(true);
			}
		}

		RenderState.enableDepthTest(true);
		RenderState.setDepthWrite(true);
	}

	public void drawCircularVolume(int cx, int cy, int cz, int radius, int h, float r, float g, float b, float a)
	{
		if (radius == 0)
			return;

		int N = 2 * Math.round(1.0f + (float) (radius / Math.sqrt(radius)));
		int[][] indices = new int[2][N + 1];

		for (int i = 0; i < N; i++) {
			float x = radius * (float) Math.cos(2 * i * Math.PI / N);
			float z = radius * (float) Math.sin(2 * i * Math.PI / N);
			indices[0][i] = LineRenderQueue.addVertex().setColor(r, g, b, a).setPosition(cx + x, cy, cz + z).getIndex();
			indices[1][i] = LineRenderQueue.addVertex().setColor(r, g, b, a).setPosition(cx + x, cy + h, cz + z).getIndex();
			LineRenderQueue.addLine(indices[0][i], indices[1][i]);
		}
		indices[0][N] = indices[0][0];
		indices[1][N] = indices[1][0];

		LineRenderQueue.addLine(indices[0]);
		LineRenderQueue.addLine(indices[1]);

		LineRenderQueue.render(true);
	}

	public void drawRectangularVolume(int x1, int y1, int z1, int x2, int y2, int z2, float r, float g, float b, float a)
	{
		if ((x2 - x1) == 0 && (z2 - z1) == 0)
			return;

		int mmm = LineRenderQueue.addVertex().setColor(r, g, b, a).setPosition(x1, y1, z1).getIndex();
		int mMm = LineRenderQueue.addVertex().setColor(r, g, b, a).setPosition(x1, y2, z1).getIndex();
		int mmM = LineRenderQueue.addVertex().setColor(r, g, b, a).setPosition(x1, y1, z2).getIndex();
		int mMM = LineRenderQueue.addVertex().setColor(r, g, b, a).setPosition(x1, y2, z2).getIndex();
		int Mmm = LineRenderQueue.addVertex().setColor(r, g, b, a).setPosition(x2, y1, z1).getIndex();
		int MMm = LineRenderQueue.addVertex().setColor(r, g, b, a).setPosition(x2, y2, z1).getIndex();
		int MmM = LineRenderQueue.addVertex().setColor(r, g, b, a).setPosition(x2, y1, z2).getIndex();
		int MMM = LineRenderQueue.addVertex().setColor(r, g, b, a).setPosition(x2, y2, z2).getIndex();

		LineRenderQueue.addLine(mmm, Mmm, MMm, mMm, mmm);
		LineRenderQueue.addLine(mmM, MmM, MMM, mMM, mmM);
		LineRenderQueue.addLine(mmm, mmM);
		LineRenderQueue.addLine(Mmm, MmM);
		LineRenderQueue.addLine(mMm, mMM);
		LineRenderQueue.addLine(MMm, MMM);

		LineRenderQueue.render(true);
	}

	public static void setFogEnabled(ScriptData scripts, boolean enabled)
	{
		ModelShader modelShader = ShaderManager.get(ModelShader.class);
		EntityShader entityShader = ShaderManager.get(EntityShader.class);
		FogSettings fog;

		if (enabled) {
			fog = scripts.worldFogSettings;
			modelShader.useProgram(true);
			modelShader.enableFog.set(fog.enabled.get());
			modelShader.fogDist.set(fog.start.get(), fog.end.get());
			modelShader.fogColor.set(fog.R.get(), fog.G.get(), fog.B.get(), fog.A.get());

			fog = scripts.entityFogSettings;
			entityShader.useProgram(true);
			entityShader.enableFog.set(fog.enabled.get());
			entityShader.fogDist.set(fog.start.get(), fog.end.get());
			entityShader.fogColor.set(fog.R.get(), fog.G.get(), fog.B.get(), fog.A.get());
		}
		else {
			modelShader.useProgram(true);
			modelShader.enableFog.set(false);

			entityShader.useProgram(true);
			entityShader.enableFog.set(false);
		}
	}
}
