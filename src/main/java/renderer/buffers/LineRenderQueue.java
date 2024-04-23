package renderer.buffers;

import game.map.shape.TransformMatrix;
import renderer.shaders.ShaderManager;
import renderer.shaders.scene.LineShader;

public abstract class LineRenderQueue
{
	private static BufferedLines lines;

	public static void init()
	{
		lines = new BufferedLines();
	}

	public static void reset()
	{
		lines.clear();
	}

	public static BufferVertex addVertex()
	{
		return lines.addVertex();
	}

	public static void addLine(int i, int j)
	{
		lines.add(i, j);
	}

	public static void addLine(int ... indices)
	{
		lines.add(indices);
	}

	public static void addLineLoop(int ... indices)
	{
		lines.addLoop(indices);
	}

	/**
	 * Render using current model matrix. Program will be set to LineShader.
	 */
	public static void render(boolean flush)
	{
		LineShader shader = ShaderManager.use(LineShader.class);
		render(shader, flush);
	}

	/**
	 * Render using current model matrix and a given shader.
	 */
	public static void render(LineShader shader, boolean flush)
	{
		assert (shader != null);

		lines.loadBuffers();
		lines.render();

		if (flush)
			reset();
	}

	/**
	 * Render using a given model matrix. Program will be set to LineShader.
	 * @param modelMatrix use NULL for identity matrix
	 */
	public static void renderWithTransform(TransformMatrix modelMatrix, boolean flush)
	{
		LineShader shader = ShaderManager.use(LineShader.class);
		renderWithTransform(shader, modelMatrix, flush);
	}

	/**
	 * Render using a given model matrix and shader.
	 * @param modelMatrix use NULL for identity matrix
	 */
	public static void renderWithTransform(LineShader shader, TransformMatrix modelMatrix, boolean flush)
	{
		assert (shader != null);

		lines.loadBuffers();
		lines.renderWithTransform(modelMatrix);

		if (flush)
			reset();
	}

	public static void delete()
	{
		lines.delete();
	}

	public static void printContents()
	{
		System.out.println("Current line render queue:");
		lines.print();
	}
}
