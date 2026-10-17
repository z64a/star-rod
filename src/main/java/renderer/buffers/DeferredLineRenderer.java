package renderer.buffers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;

import renderer.shaders.RenderState;
import renderer.shaders.ShaderManager;
import renderer.shaders.scene.LineShader;

public abstract class DeferredLineRenderer
{
	private static final ArrayList<LineBatch> normalBatches = new ArrayList<>();
	private static final ArrayList<LineBatch> noDepthBatches = new ArrayList<>();
	private static LineRenderBuffer buffer;

	public static void init()
	{
		buffer = new LineRenderBuffer();
	}

	public static void reset()
	{
		normalBatches.clear();
		noDepthBatches.clear();
	}

	public static LineBatch addLineBatch(boolean useDepth)
	{
		LineBatch batch = new LineBatch();
		if (useDepth)
			normalBatches.add(batch);
		else
			noDepthBatches.add(batch);
		return batch;
	}

	private static HashMap<Float, ArrayList<LineBatch>> splitByWidth(ArrayList<LineBatch> batches)
	{
		HashMap<Float, ArrayList<LineBatch>> batchesByWidth = new HashMap<>();
		for (LineBatch batch : batches)
			batchesByWidth.computeIfAbsent(batch.lineWidth, key -> new ArrayList<>()).add(batch);
		return batchesByWidth;
	}

	public static void render()
	{
		RenderState.setModelMatrix(null);
		LineShader shader = ShaderManager.use(LineShader.class);

		renderBatches(shader, normalBatches);

		if (!noDepthBatches.isEmpty()) {
			RenderState.enableDepthTest(false);
			renderBatches(shader, noDepthBatches);
			RenderState.enableDepthTest(true);
		}
	}

	private static void renderBatches(LineShader shader, ArrayList<LineBatch> batches)
	{
		for (Entry<Float, ArrayList<LineBatch>> entry : splitByWidth(batches).entrySet()) {
			float width = entry.getKey() == 0.0f ? 1.0f : entry.getKey();
			RenderState.setLineWidth(width);
			shader.lineWidth.set(RenderState.getLineWidthPixels());
			buffer.load(entry.getValue());
			buffer.render();
		}
		batches.clear();
	}

	public void freeBuffers()
	{
		if (buffer != null)
			buffer.delete();
	}
}
