package renderer.buffers;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;

import org.lwjgl.BufferUtils;

import game.map.shape.TransformMatrix;
import renderer.shaders.RenderState;

public class BufferedMesh
{
	// @formatter:off
	public static final int VBO_INDEX 	= 1 << 0;
	public static final int VBO_UV 		= 1 << 1;
	public static final int VBO_COLOR 	= 1 << 2;
	public static final int VBO_AUX 	= 1 << 3;
	// @formatter:on

	private final int minVerts;
	private final int minTris;

	private final boolean hasIndex;
	private final boolean hasUVs;
	private final boolean hasColors;
	private final boolean hasAux;

	private ArrayList<BufferVertex> verts = new ArrayList<>();
	private ArrayList<BufferTriangle> tris = new ArrayList<>();

	private int vao = -1;

	private VBO idxVBO = null;
	private VBO posVBO = null;
	private VBO uvVBO = null;
	private VBO colVBO = null;
	private VBO auxVBO = null;

	private static class BufferTriangle
	{
		private int i, j, k;

		public BufferTriangle(int i, int j, int k)
		{
			this.i = i;
			this.j = j;
			this.k = k;
		}
	}

	public BufferedMesh(int flags)
	{
		this(0, 0, flags);
	}

	public BufferedMesh(int minVerts, int minTris, int flags)
	{
		this.minVerts = minVerts;
		this.minTris = minTris;

		this.hasIndex = (flags & VBO_INDEX) != 0;
		this.hasColors = (flags & VBO_COLOR) != 0;
		this.hasUVs = (flags & VBO_UV) != 0;
		this.hasAux = (flags & VBO_AUX) != 0;
	}

	public void clear()
	{
		verts.clear();
		tris.clear();
	}

	public BufferVertex getVertex(int index)
	{
		return verts.get(index);
	}

	public BufferVertex addVertex()
	{
		BufferVertex vtx = new BufferVertex(verts.size());
		verts.add(vtx);
		return vtx;
	}

	// CCW around outer perimeter
	public void addTriangle(int i, int j, int k)
	{
		tris.add(new BufferTriangle(i, j, k));
	}

	// CCW around outer perimeter
	public void addQuad(int i, int j, int k, int l)
	{
		tris.add(new BufferTriangle(i, j, k));
		tris.add(new BufferTriangle(i, k, l));
	}

	// CCW around outer perimeter
	public void addFan(int i, int ... more)
	{
		for (int j = 0; j < more.length - 1; j++)
			tris.add(new BufferTriangle(i, more[j], more[j + 1]));
	}

	public int getVertexCount()
	{
		return verts.size();
	}

	public int getTriangleCount()
	{
		return tris.size();
	}

	public void loadBuffers()
	{
		if (vao < 0)
			vao = glGenVertexArrays();

		RenderState.setVAO(vao);

		ArrayList<BufferVertex> workingVerts = verts;
		if (!hasIndex) {
			workingVerts = new ArrayList<>(3 * tris.size());
			for (BufferTriangle t : tris) {
				workingVerts.add(verts.get(t.i));
				workingVerts.add(verts.get(t.j));
				workingVerts.add(verts.get(t.k));
			}
		}

		int numVerts = Math.max(minVerts, workingVerts.size());
		int numTris = Math.max(minTris, tris.size());

		// reallocate as needed

		if (hasIndex && (idxVBO == null || idxVBO.numElem < numTris)) {
			if (idxVBO != null)
				glDeleteBuffers(idxVBO.id);

			int id = glGenBuffers();
			idxVBO = new VBO(id, numTris);
		}

		if (posVBO == null || posVBO.numElem < numVerts) {
			if (posVBO != null)
				glDeleteBuffers(posVBO.id);

			int id = glGenBuffers();
			posVBO = new VBO(id, numVerts);
		}

		if (hasColors && (colVBO == null || colVBO.numElem < numVerts)) {
			if (colVBO != null)
				glDeleteBuffers(colVBO.id);

			int id = glGenBuffers();
			colVBO = new VBO(id, numVerts);
		}

		if (hasUVs && (uvVBO == null || uvVBO.numElem < numVerts)) {
			if (uvVBO != null)
				glDeleteBuffers(uvVBO.id);

			int id = glGenBuffers();
			uvVBO = new VBO(id, numVerts);
		}

		if (hasAux && (auxVBO == null || auxVBO.numElem < numVerts)) {
			if (auxVBO != null)
				glDeleteBuffers(auxVBO.id);

			int id = glGenBuffers();
			auxVBO = new VBO(id, numVerts);
		}

		IntBuffer ib = null;
		FloatBuffer fb = null;

		// index buffer

		if (hasIndex) {
			ib = BufferUtils.createIntBuffer(3 * idxVBO.numElem);
			for (BufferTriangle bt : tris)
				ib.put(bt.i).put(bt.j).put(bt.k);
			ib.flip();

			glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, idxVBO.id);
			glBufferData(GL_ELEMENT_ARRAY_BUFFER, ib, GL_STATIC_DRAW);
		}

		// position buffer

		fb = BufferUtils.createFloatBuffer(3 * posVBO.numElem);
		for (BufferVertex bv : workingVerts)
			fb.put(bv.x).put(bv.y).put(bv.z);
		fb.flip();

		glBindBuffer(GL_ARRAY_BUFFER, posVBO.id);
		glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);

		glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);
		glEnableVertexAttribArray(0);

		// color buffer

		if (hasColors) {
			fb = BufferUtils.createFloatBuffer(4 * colVBO.numElem);
			for (BufferVertex bv : workingVerts)
				fb.put(bv.r).put(bv.g).put(bv.b).put(bv.a);
			fb.flip();

			glBindBuffer(GL_ARRAY_BUFFER, colVBO.id);
			glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);

			glVertexAttribPointer(1, 4, GL_FLOAT, false, 0, 0);
			glEnableVertexAttribArray(1);
		}

		// texcoord buffer

		if (hasUVs) {
			fb = BufferUtils.createFloatBuffer(2 * uvVBO.numElem);
			for (BufferVertex bv : workingVerts)
				fb.put(bv.u).put(bv.v);
			fb.flip();

			glBindBuffer(GL_ARRAY_BUFFER, uvVBO.id);
			glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);

			glVertexAttribPointer(2, 2, GL_FLOAT, false, 0, 0);
			glEnableVertexAttribArray(2);
		}

		// aux buffer

		if (hasAux) {
			fb = BufferUtils.createFloatBuffer(2 * auxVBO.numElem);
			for (BufferVertex bv : workingVerts)
				fb.put(bv.aux0).put(bv.aux1);
			fb.flip();

			glBindBuffer(GL_ARRAY_BUFFER, auxVBO.id);
			glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);

			glVertexAttribPointer(3, 2, GL_FLOAT, false, 0, 0);
			glEnableVertexAttribArray(3);
		}
	}

	public void glDelete()
	{
		if (idxVBO != null)
			glDeleteBuffers(idxVBO.id);

		if (posVBO != null)
			glDeleteBuffers(posVBO.id);

		if (colVBO != null)
			glDeleteBuffers(colVBO.id);

		if (uvVBO != null)
			glDeleteBuffers(uvVBO.id);

		if (auxVBO != null)
			glDeleteBuffers(auxVBO.id);
	}

	public void setVAO()
	{
		RenderState.setVAO(vao);
	}

	/**
	 * Render using current model matrix.
	 */
	public void render()
	{
		RenderState.setVAO(vao);

		if (hasIndex)
			glDrawElements(GL_TRIANGLES, 3 * tris.size(), GL_UNSIGNED_INT, 0);
		else
			glDrawArrays(GL_TRIANGLES, 0, 3 * tris.size());
	}

	/**
	 * Render using a given model matrix.
	 */
	public void renderWithTransform(TransformMatrix modelMatrix)
	{
		RenderState.setModelMatrix(modelMatrix);
		RenderState.setVAO(vao);

		if (hasIndex)
			glDrawElements(GL_TRIANGLES, 3 * tris.size(), GL_UNSIGNED_INT, 0);
		else
			glDrawArrays(GL_TRIANGLES, 0, 3 * tris.size());
	}
}
