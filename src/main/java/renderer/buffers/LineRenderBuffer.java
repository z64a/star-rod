package renderer.buffers;

import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL15.GL_STREAM_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;
import static org.lwjgl.opengl.GL31.glDrawArraysInstanced;
import static org.lwjgl.opengl.GL33.glVertexAttribDivisor;

import java.nio.FloatBuffer;
import java.util.List;

import org.lwjgl.BufferUtils;

import renderer.shaders.RenderState;
import renderer.shaders.RenderState.PolygonMode;

/**
 * Streams line endpoints to the GPU. Each line is rendered as an instanced
 * triangle quad which the line vertex shader expands in screen space.
 */
final class LineRenderBuffer
{
	private static final int FLOATS_PER_LINE = 14;
	private static final int BYTES_PER_LINE = FLOATS_PER_LINE * Float.BYTES;

	private int vao = -1;
	private int cornerVbo = -1;
	private int lineVbo = -1;
	private int lineCount;

	public void load(LineBatch batch)
	{
		load(List.of(batch));
	}

	public void load(List<LineBatch> batches)
	{
		lineCount = 0;
		for (LineBatch batch : batches)
			lineCount += batch.lines.size();

		if (lineCount == 0)
			return;

		if (vao < 0)
			initialize();

		FloatBuffer data = BufferUtils.createFloatBuffer(FLOATS_PER_LINE * lineCount);
		for (LineBatch batch : batches) {
			for (BufferLine line : batch.lines) {
				BufferVertex start = batch.verts.get(line.i);
				BufferVertex end = batch.verts.get(line.j);

				data.put(start.x).put(start.y).put(start.z);
				data.put(end.x).put(end.y).put(end.z);
				data.put(start.r).put(start.g).put(start.b).put(start.a);
				data.put(end.r).put(end.g).put(end.b).put(end.a);
			}
		}
		data.flip();

		RenderState.setVAO(vao);
		glBindBuffer(GL_ARRAY_BUFFER, lineVbo);
		glBufferData(GL_ARRAY_BUFFER, data, GL_STREAM_DRAW);
	}

	private void initialize()
	{
		vao = glGenVertexArrays();
		RenderState.setVAO(vao);

		// Two triangles. X selects the endpoint and Y selects the side.
		FloatBuffer corners = BufferUtils.createFloatBuffer(12);
		corners.put(new float[] {
				-1.0f, -1.0f,
				+1.0f, -1.0f,
				+1.0f, +1.0f,
				-1.0f, -1.0f,
				+1.0f, +1.0f,
				-1.0f, +1.0f
		}).flip();

		cornerVbo = glGenBuffers();
		glBindBuffer(GL_ARRAY_BUFFER, cornerVbo);
		glBufferData(GL_ARRAY_BUFFER, corners, GL_STATIC_DRAW);
		glVertexAttribPointer(4, 2, GL_FLOAT, false, 0, 0L);
		glEnableVertexAttribArray(4);

		lineVbo = glGenBuffers();
		glBindBuffer(GL_ARRAY_BUFFER, lineVbo);

		setInstancedAttribute(0, 3, 0);
		setInstancedAttribute(1, 3, 3 * Float.BYTES);
		setInstancedAttribute(2, 4, 6 * Float.BYTES);
		setInstancedAttribute(3, 4, 10 * Float.BYTES);
	}

	private static void setInstancedAttribute(int index, int size, long offset)
	{
		glVertexAttribPointer(index, size, GL_FLOAT, false, BYTES_PER_LINE, offset);
		glEnableVertexAttribArray(index);
		glVertexAttribDivisor(index, 1);
	}

	public void render()
	{
		if (lineCount == 0)
			return;

		PolygonMode previousMode = RenderState.getPolygonMode();
		RenderState.setPolygonMode(PolygonMode.FILL);
		RenderState.setVAO(vao);
		glDrawArraysInstanced(GL_TRIANGLES, 0, 6, lineCount);
		RenderState.setPolygonMode(previousMode);
	}

	public void delete()
	{
		if (lineVbo >= 0)
			glDeleteBuffers(lineVbo);
		if (cornerVbo >= 0)
			glDeleteBuffers(cornerVbo);
		if (vao >= 0)
			glDeleteVertexArrays(vao);

		lineVbo = -1;
		cornerVbo = -1;
		vao = -1;
		lineCount = 0;
	}
}
