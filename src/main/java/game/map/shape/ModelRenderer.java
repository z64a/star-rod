package game.map.shape;

import static org.lwjgl.opengl.GL11.*;
import static renderer.shaders.scene.ModelShader.*;

import game.map.editor.common.BaseCamera;
import game.map.editor.geometry.Vector3f;
import game.map.editor.render.RenderMode;
import game.map.editor.render.Renderer;
import game.map.editor.render.RenderingOptions;
import game.map.editor.render.SortedRenderable;
import game.map.mesh.TexturedMesh;
import game.map.shape.commands.DisplayCommand;
import renderer.shaders.RenderState;
import renderer.shaders.RenderState.PolygonMode;
import renderer.shaders.ShaderManager;
import renderer.shaders.scene.ModelShader;

public abstract class ModelRenderer
{
	public static void drawModel(RenderingOptions opts, Model mdl)
	{
		RenderState.setModelMatrix(null);

		switch (opts.modelSurfaceMode) {
			case WIREFRAME:
				drawModelWireframe(opts, mdl);
				break;

			case TEXTURED:
				drawModelFilled(opts, mdl, true);
				break;

			case SHADED:
				drawModelFilled(opts, mdl, false);
				break;
		}

		if (opts.showBoundingBoxes)
			mdl.AABB.render();

		if (opts.showNormals)
			Renderer.drawNormals(mdl.getMesh());

		// reset state a bit
		RenderState.initDepthWrite();
		RenderState.initDepthFunc();
		RenderState.setEnabledCullFace(false);
		RenderState.setModelMatrix(null);
	}

	private static void drawModelWireframe(RenderingOptions opts, Model mdl)
	{
		TexturedMesh mesh = mdl.getMesh();
		mesh.setVAO();

		ModelShader shader = ShaderManager.use(ModelShader.class);
		RenderMode mode = mdl.renderMode.get();
		shader.translucent.set(mode.translucent);
		shader.textured.set(false);

		// draw solid model

		RenderState.setPolygonMode(PolygonMode.FILL);
		shader.drawMode.set(MODE_LINE_SOLID);

		for (DisplayCommand cmd : mesh.displayListModel) {
			if (cmd instanceof TriangleBatch batch) {
				glDrawArrays(GL_TRIANGLES, batch.bufferStartPos, 3 * batch.triangles.size());
			}
			else if (opts.useGeometryFlags) {
				cmd.doGL();
			}
		}

		// draw edge highlights

		RenderState.setLineWidth(1.0f);
		RenderState.setPolygonMode(PolygonMode.LINE);
		shader.drawMode.set(MODE_LINE_OUTLINE);

		for (DisplayCommand cmd : mesh.displayListModel) {
			if (cmd instanceof TriangleBatch batch) {
				glDrawArrays(GL_TRIANGLES, batch.bufferStartPos, 3 * batch.triangles.size());
			}
			else if (opts.useGeometryFlags) {
				cmd.doGL();
			}
		}
	}

	private static void drawModelFilled(RenderingOptions opts, Model mdl, boolean textured)
	{
		TexturedMesh mesh = mdl.getMesh();
		mesh.setVAO();

		ModelShader shader = ShaderManager.use(ModelShader.class);
		RenderMode mode = mdl.renderMode.get();
		shader.translucent.set(mode.translucent);

		if (textured) {
			if (opts.worldFogEnabled)
				mode.setState(2);
			else if (!mesh.textured)
				mode.setState(1);
			else
				mode.setState(0);
		}

		if (textured)
			shader.useProperties(mdl, opts.useFiltering, opts.useTextureLOD);
		else
			shader.textured.set(false);

		// draw solid model

		RenderState.setPolygonMode(PolygonMode.FILL);
		shader.drawMode.set(MODE_FILL_SOLID);

		for (DisplayCommand cmd : mesh.displayListModel) {
			if (cmd instanceof TriangleBatch batch) {
				glDrawArrays(GL_TRIANGLES, batch.bufferStartPos, 3 * batch.triangles.size());
			}
			else if (opts.useGeometryFlags) {
				cmd.doGL();
			}
		}

		RenderMode.resetState();

		// draw edge highlights

		RenderState.setLineWidth(1.0f);
		RenderState.setPolygonMode(PolygonMode.LINE);
		glEnable(GL_POLYGON_OFFSET_LINE);
		shader.drawMode.set(opts.edgeHighlights ? MODE_FILL_OUTLINE_HIGHLIGHT : MODE_FILL_OUTLINE);

		for (DisplayCommand cmd : mesh.displayListModel) {
			if (cmd instanceof TriangleBatch batch) {
				glDrawArrays(GL_TRIANGLES, batch.bufferStartPos, 3 * batch.triangles.size());
			}
			else if (opts.useGeometryFlags) {
				cmd.doGL();
			}
		}
	}

	public static class RenderableModel implements SortedRenderable
	{
		private final Model mdl;
		private int depth;

		public RenderableModel(Model mdl)
		{
			this.mdl = mdl;
		}

		@Override
		public RenderMode getRenderMode()
		{ return mdl.renderMode.get(); }

		@Override
		public Vector3f getCenterPoint()
		{ return mdl.AABB.getCenter(); }

		@Override
		public void render(RenderingOptions opts, BaseCamera camera)
		{
			ModelRenderer.drawModel(opts, mdl);
		}

		@Override
		public void setDepth(int normalizedDepth)
		{ depth = normalizedDepth; }

		@Override
		public int getDepth()
		{ return depth; }
	}
}
