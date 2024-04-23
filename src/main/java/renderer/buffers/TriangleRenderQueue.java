package renderer.buffers;

import static renderer.buffers.BufferedMesh.*;

import game.map.shape.TransformMatrix;
import renderer.shaders.BaseShader;
import renderer.shaders.ShaderManager;
import renderer.shaders.scene.BasicSolidShader;

public class TriangleRenderQueue
{
	private static BufferedMesh mesh;

	public static void init()
	{
		mesh = new BufferedMesh(0, 0, VBO_INDEX | VBO_COLOR | VBO_UV | VBO_AUX);
	}

	public static void reset()
	{
		mesh.clear();
	}

	public static BufferVertex addVertex()
	{
		return mesh.addVertex();
	}

	public static void addTriangle(int i, int j, int k)
	{
		mesh.addTriangle(i, j, k);
	}

	public static void addQuad(int i, int j, int k, int l)
	{
		mesh.addQuad(i, j, k, l);
	}

	public static void render(boolean flush)
	{
		BasicSolidShader shader = ShaderManager.use(BasicSolidShader.class);
		render(shader, flush);
	}

	/**
	 * Render using current model matrix.
	 */
	public static void render(BaseShader shader, boolean flush)
	{
		mesh.loadBuffers();
		mesh.render();

		if (flush)
			reset();
	}

	public static void renderWithTransform(TransformMatrix modelMatrix, boolean flush)
	{
		BasicSolidShader shader = ShaderManager.use(BasicSolidShader.class);
		renderWithTransform(shader, modelMatrix, flush);
	}

	/**
	 * Render using a given model matrix.
	 */
	public static void renderWithTransform(BaseShader shader, TransformMatrix modelMatrix, boolean flush)
	{
		mesh.loadBuffers();
		mesh.renderWithTransform(modelMatrix);

		if (flush)
			reset();
	}

	public static void delete()
	{
		mesh.glDelete();
	}
}
