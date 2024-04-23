package renderer.buffers;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;

import org.lwjgl.BufferUtils;

import renderer.shaders.RenderState;
import renderer.shaders.ShaderManager;
import renderer.shaders.scene.LineShader;

public abstract class DeferredLineRenderer
{
	private static int vao;
	private static ArrayList<LineBatch> normalBatches = new ArrayList<>();
	private static ArrayList<LineBatch> noDepthBatches = new ArrayList<>();

	private static VBO posVBO = null;
	private static VBO colVBO = null;

	public static void init()
	{
		vao = glGenVertexArrays();
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

	public static HashMap<Float, ArrayList<LineBatch>> split(ArrayList<LineBatch> batches)
	{
		HashMap<Float, ArrayList<LineBatch>> thicknessMap = new HashMap<>();

		for (LineBatch batch : batches) {
			ArrayList<LineBatch> list = thicknessMap.get(batch.lineWidth);
			if (list == null) {
				list = new ArrayList<>();
				thicknessMap.put(batch.lineWidth, list);
			}
			list.add(batch);
		}

		return thicknessMap;
	}

	public static void render()
	{
		RenderState.setLineWidth(1.0f);
		RenderState.setModelMatrix(null);
		RenderState.setVAO(vao);

		ShaderManager.use(LineShader.class);

		if (normalBatches.size() > 0) {
			for (Entry<Float, ArrayList<LineBatch>> entry : split(normalBatches).entrySet()) {
				float width = entry.getKey();
				RenderState.setLineWidth(width == 0.0f ? 1.0f : width);

				int size = loadBuffers(entry.getValue());
				glDrawArrays(GL_LINES, 0, size);
			}
			normalBatches.clear();
		}

		if (noDepthBatches.size() > 0) {
			RenderState.enableDepthTest(false);
			for (Entry<Float, ArrayList<LineBatch>> entry : split(noDepthBatches).entrySet()) {
				float width = entry.getKey();
				RenderState.setLineWidth(width == 0.0f ? 1.0f : width);

				int size = loadBuffers(entry.getValue());
				glDrawArrays(GL_LINES, 0, size);
			}
			RenderState.enableDepthTest(true);
			noDepthBatches.clear();
		}
	}

	public static int loadBuffers(ArrayList<LineBatch> batches)
	{
		ArrayList<BufferVertex> verts = new ArrayList<>();

		for (LineBatch batch : batches) {
			batch.startIndex = verts.size();
			for (BufferLine line : batch.lines) {
				verts.add(batch.verts.get(line.i));
				verts.add(batch.verts.get(line.j));
			}
		}

		int numVerts = verts.size();

		// reallocate as needed

		if (posVBO == null || posVBO.numElem < numVerts) {
			if (posVBO != null)
				glDeleteBuffers(posVBO.id);

			int id = glGenBuffers();
			posVBO = new VBO(id, numVerts);
		}

		if (colVBO == null || colVBO.numElem < numVerts) {
			if (colVBO != null)
				glDeleteBuffers(colVBO.id);

			int id = glGenBuffers();
			colVBO = new VBO(id, numVerts);
		}

		FloatBuffer fb = null;

		// position buffer

		fb = BufferUtils.createFloatBuffer(3 * posVBO.numElem);
		for (BufferVertex bv : verts)
			fb.put(bv.x).put(bv.y).put(bv.z);
		fb.flip();

		glBindBuffer(GL_ARRAY_BUFFER, posVBO.id);
		glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);

		glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);
		glEnableVertexAttribArray(0);

		// color buffer

		fb = BufferUtils.createFloatBuffer(4 * colVBO.numElem);
		for (BufferVertex bv : verts)
			fb.put(bv.r).put(bv.g).put(bv.b).put(bv.a);
		fb.flip();

		glBindBuffer(GL_ARRAY_BUFFER, colVBO.id);
		glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);

		glVertexAttribPointer(1, 4, GL_FLOAT, false, 0, 0);
		glEnableVertexAttribArray(1);

		return verts.size();
	}

	public void freeBuffers()
	{
		if (posVBO != null)
			glDeleteBuffers(posVBO.id);

		if (colVBO != null)
			glDeleteBuffers(colVBO.id);
	}
}
