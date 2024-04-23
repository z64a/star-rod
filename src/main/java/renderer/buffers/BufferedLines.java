package renderer.buffers;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import org.lwjgl.BufferUtils;

import game.map.shape.TransformMatrix;
import renderer.shaders.RenderState;

public class BufferedLines extends LineBatch
{
	private int vao = -1;

	private VBO idxVBO = null;
	private VBO posVBO = null;
	private VBO colVBO = null;

	public BufferedLines()
	{}

	public void clear()
	{
		priority = 0;
		verts.clear();
		lines.clear();
	}

	public void loadBuffers()
	{
		if (lines.isEmpty())
			return;

		if (vao < 0)
			vao = glGenVertexArrays();

		RenderState.setVAO(vao);

		int numVerts = verts.size();
		int numLines = lines.size();

		// reallocate as needed

		if (idxVBO == null || idxVBO.numElem < numLines) {
			if (idxVBO != null)
				glDeleteBuffers(idxVBO.id);

			int id = glGenBuffers();
			idxVBO = new VBO(id, numLines);
		}

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

		IntBuffer ib = null;
		FloatBuffer fb = null;

		// index buffer

		ib = BufferUtils.createIntBuffer(2 * idxVBO.numElem);
		for (BufferLine ln : lines)
			ib.put(ln.i).put(ln.j);
		ib.flip();

		glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, idxVBO.id);
		glBufferData(GL_ELEMENT_ARRAY_BUFFER, ib, GL_STATIC_DRAW);

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
	}

	public void delete()
	{
		if (idxVBO != null)
			glDeleteBuffers(idxVBO.id);

		if (posVBO != null)
			glDeleteBuffers(posVBO.id);

		if (colVBO != null)
			glDeleteBuffers(colVBO.id);

		glDeleteVertexArrays(vao);
	}

	/**
	 * Render using current model matrix
	 */
	public void render()
	{
		if (lines.isEmpty())
			return;

		RenderState.setVAO(vao);
		glDrawElements(GL_LINES, 2 * lines.size(), GL_UNSIGNED_INT, 0);
	}

	/**
	 * Render using a given model matrix
	 */
	public void renderWithTransform(TransformMatrix modelMatrix)
	{
		RenderState.setModelMatrix(modelMatrix);

		if (lines.isEmpty())
			return;

		RenderState.setVAO(vao);
		glDrawElements(GL_LINES, 2 * lines.size(), GL_UNSIGNED_INT, 0);
	}

	public void print()
	{
		for (BufferVertex bv : verts)
			System.out.println(bv);
		for (BufferLine line : lines)
			System.out.println(line);
	}
}
