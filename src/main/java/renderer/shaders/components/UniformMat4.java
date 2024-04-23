package renderer.shaders.components;

import static org.lwjgl.opengl.GL20.glGetUniformfv;
import static org.lwjgl.opengl.GL20.glUniformMatrix4fv;

import java.nio.FloatBuffer;

import org.lwjgl.BufferUtils;

import game.map.shape.TransformMatrix;
import renderer.shaders.BaseUniform;

public class UniformMat4 extends BaseUniform
{
	private final double[][] defaultValue;
	protected double[][] cachedValue;

	public UniformMat4(int program, String name)
	{
		this(true, program, name);
	}

	public UniformMat4(boolean resets, int program, String name)
	{
		super(resets, name, program);

		this.defaultValue = new double[][] {
				{ 1.0f, 0.0f, 0.0f, 0.0f },
				{ 0.0f, 1.0f, 0.0f, 0.0f },
				{ 0.0f, 0.0f, 1.0f, 0.0f },
				{ 0.0f, 0.0f, 0.0f, 1.0f } };

		this.cachedValue = new double[4][4];
		for (int i = 0; i < 4; i++)
			for (int j = 0; j < 4; j++)
				cachedValue[i][j] = defaultValue[i][j];
	}

	@Override
	public void reset()
	{
		for (int i = 0; i < 4; i++)
			for (int j = 0; j < 4; j++)
				cachedValue[i][j] = defaultValue[i][j];

		setUniform();
	}

	public void set(TransformMatrix mtx)
	{
		if (mtx == null)
			return;

		boolean changed = false;
		for (int i = 0; i < 4; i++)
			for (int j = 0; j < 4; j++) {
				double Mij = mtx.get(i, j);
				if (Mij != cachedValue[i][j]) {
					changed = true;
					cachedValue[i][j] = Mij;
				}
			}

		if (changed)
			setUniform();
	}

	protected void setUniform()
	{
		FloatBuffer buffer = BufferUtils.createFloatBuffer(16);

		for (int col = 0; col < 4; col++)
			for (int row = 0; row < 4; row++)
				buffer.put((float) cachedValue[row][col]);
		buffer.flip();

		glUniformMatrix4fv(location, false, buffer);
	}

	@Override
	public Object query()
	{
		FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
		glGetUniformfv(program, location, buffer);
		return buffer;
	}

	@Override
	public void print()
	{
		FloatBuffer buffer = (FloatBuffer) query();
		System.out.println(name);
		float[][] mtx = new float[4][4];
		for (int i = 0; i < 4; i++)
			for (int j = 0; j < 4; j++)
				mtx[j][i] = buffer.get();

		for (int i = 0; i < 4; i++) {
			for (int j = 0; j < 4; j++)
				System.out.print(" " + mtx[i][j]);
			System.out.println();
		}
	}
}
