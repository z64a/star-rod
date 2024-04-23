package renderer.buffers;

import game.map.shape.TransformMatrix;
import renderer.shaders.ShaderManager;
import renderer.shaders.scene.PointShader;

public abstract class PointRenderQueue
{
	private static BufferedPoints points;

	public static void init()
	{
		points = new BufferedPoints();
	}

	public static void reset()
	{
		points.clear();
	}

	public static BufferVertex addPoint()
	{
		return points.addPoint();
	}

	public static BufferVertex addPoint(float size)
	{
		return points.addPoint(size);
	}

	/**
	 * Render using current model matrix. Program will be set to PointShader.
	 */
	public static void render(boolean flush)
	{
		PointShader shader = ShaderManager.use(PointShader.class);
		render(shader, flush);
	}

	/**
	 * Render using current model matrix and a given shader.
	 */
	public static void render(PointShader shader, boolean flush)
	{
		points.loadBuffers();
		points.render();

		if (flush)
			reset();
	}

	/**
	 * Render using a given model matrix. Program will be set to PointShader.
	 * @param modelMatrix use NULL for identity matrix
	 */
	public static void renderWithTransform(TransformMatrix modelMatrix, boolean flush)
	{
		PointShader shader = ShaderManager.use(PointShader.class);
		renderWithTransform(shader, modelMatrix, flush);
	}

	/**
	 * Render using a given model matrix and shader.
	 * @param modelMatrix use NULL for identity matrix
	 */
	public static void renderWithTransform(PointShader shader, TransformMatrix modelMatrix, boolean flush)
	{
		points.loadBuffers();
		points.renderWithTransform(modelMatrix);

		if (flush)
			reset();
	}

	public void delete()
	{
		points.delete();
	}
}
