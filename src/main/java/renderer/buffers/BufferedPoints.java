package renderer.buffers;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;

import java.nio.FloatBuffer;
import java.util.ArrayList;

import org.lwjgl.BufferUtils;

import game.map.shape.TransformMatrix;
import renderer.shaders.RenderState;

public class BufferedPoints
{
	private int vao = -1;

	private VBO posVBO = null;
	private VBO colVBO = null;
	private VBO auxVBO = null;

	private ArrayList<BufferVertex> points;

	public BufferedPoints()
	{
		points = new ArrayList<>();
	}

	public void clear()
	{
		points.clear();
	}

	public BufferVertex addPoint()
	{
		return addPoint(RenderState.getPointSize());
	}

	public BufferVertex addPoint(float size)
	{
		BufferVertex vtx = new BufferVertex(points.size());
		points.add(vtx);

		vtx.setPointSize(size);

		return vtx;
	}

	public void loadBuffers()
	{
		if (points.isEmpty())
			return;

		if (vao < 0)
			vao = glGenVertexArrays();

		RenderState.setVAO(vao);

		int numVerts = points.size();

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

		if (auxVBO == null || auxVBO.numElem < numVerts) {
			if (auxVBO != null)
				glDeleteBuffers(auxVBO.id);

			int id = glGenBuffers();
			auxVBO = new VBO(id, numVerts);
		}

		FloatBuffer fb = null;

		// position buffer

		fb = BufferUtils.createFloatBuffer(3 * posVBO.numElem);
		for (BufferVertex bv : points)
			fb.put(bv.x).put(bv.y).put(bv.z);
		fb.flip();

		glBindBuffer(GL_ARRAY_BUFFER, posVBO.id);
		glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);

		glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);
		glEnableVertexAttribArray(0);

		// color buffer

		fb = BufferUtils.createFloatBuffer(4 * colVBO.numElem);
		for (BufferVertex bv : points)
			fb.put(bv.r).put(bv.g).put(bv.b).put(bv.a);
		fb.flip();

		glBindBuffer(GL_ARRAY_BUFFER, colVBO.id);
		glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);

		glVertexAttribPointer(1, 4, GL_FLOAT, false, 0, 0);
		glEnableVertexAttribArray(1);

		// aux buffer

		fb = BufferUtils.createFloatBuffer(2 * auxVBO.numElem);
		for (BufferVertex bv : points)
			fb.put(bv.aux0).put(bv.aux1);
		fb.flip();

		glBindBuffer(GL_ARRAY_BUFFER, auxVBO.id);
		glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);

		glVertexAttribPointer(3, 2, GL_FLOAT, false, 0, 0);
		glEnableVertexAttribArray(3);
	}

	public void delete()
	{
		if (posVBO != null)
			glDeleteBuffers(posVBO.id);

		if (colVBO != null)
			glDeleteBuffers(colVBO.id);

		if (auxVBO != null)
			glDeleteBuffers(auxVBO.id);

		glDeleteVertexArrays(vao);
	}

	/**
	 * Render using current model matrix
	 */
	public void render()
	{
		if (points.isEmpty())
			return;

		RenderState.setVAO(vao);
		glDrawArrays(GL_POINTS, 0, points.size());
	}

	/**
	 * Render using a given model matrix
	 */
	public void renderWithTransform(TransformMatrix modelMatrix)
	{
		RenderState.setModelMatrix(modelMatrix);

		if (points.isEmpty())
			return;

		RenderState.setVAO(vao);
		glDrawArrays(GL_POINTS, 0, points.size());
	}
}
