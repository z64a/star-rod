package renderer.buffers;

import game.map.shape.TransformMatrix;
import renderer.shaders.RenderState;
import renderer.shaders.ShaderManager;
import renderer.shaders.scene.LineShader;

public class BufferedLines extends LineBatch
{
	private final LineRenderBuffer buffer = new LineRenderBuffer();

	public void clear()
	{
		priority = 0;
		verts.clear();
		lines.clear();
	}

	public void loadBuffers()
	{
		buffer.load(this);
	}

	public void delete()
	{
		buffer.delete();
	}

	/**
	 * Render using the current model matrix and active line shader.
	 */
	public void render()
	{
		render(ShaderManager.get(LineShader.class));
	}

	public void render(LineShader shader)
	{
		shader.lineWidth.set(RenderState.getLineWidthPixels());
		buffer.render();
	}

	/**
	 * Render using a given model matrix and the active line shader.
	 */
	public void renderWithTransform(TransformMatrix modelMatrix)
	{
		renderWithTransform(ShaderManager.get(LineShader.class), modelMatrix);
	}

	public void renderWithTransform(LineShader shader, TransformMatrix modelMatrix)
	{
		RenderState.setModelMatrix(modelMatrix);
		render(shader);
	}

	public void print()
	{
		for (BufferVertex vertex : verts)
			System.out.println(vertex);
		for (BufferLine line : lines)
			System.out.println(line);
	}
}
