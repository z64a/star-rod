package renderer.shaders.components;

import static org.lwjgl.opengl.GL20.*;

import java.nio.FloatBuffer;
import java.util.Arrays;

import org.lwjgl.BufferUtils;

import app.StarRodException;
import renderer.shaders.BaseUniform;

public class UniformFloatVector extends BaseUniform
{
	private final float[] defaultValue;
	protected float[] cachedValue;

	public UniformFloatVector(int program, String name, float ... defaultValue)
	{
		this(true, program, name, defaultValue);
	}

	public UniformFloatVector(boolean resets, int program, String name, float ... defaultValue)
	{
		super(resets, name, program);
		this.defaultValue = defaultValue;
		this.cachedValue = new float[defaultValue.length];
	}

	@Override
	public void reset()
	{
		for (int i = 0; i < defaultValue.length; i++)
			cachedValue[i] = defaultValue[i];
		setUniform();
	}

	public void set(float ... newValue)
	{
		if (newValue == null)
			return;

		if (newValue.length != defaultValue.length)
			throw new StarRodException("Uniform array length mismatch: %s (expected %d)", newValue.length, defaultValue.length);

		if (!Arrays.equals(newValue, cachedValue)) {
			for (int i = 0; i < defaultValue.length; i++)
				cachedValue[i] = newValue[i];
			setUniform();
		}
	}

	protected void setUniform()
	{
		switch (defaultValue.length) {
			case 1:
				glUniform1f(location, cachedValue[0]);
				break;
			case 2:
				glUniform2f(location, cachedValue[0], cachedValue[1]);
				break;
			case 3:
				glUniform3f(location, cachedValue[0], cachedValue[1], cachedValue[2]);
				break;
			case 4:
				glUniform4f(location, cachedValue[0], cachedValue[1], cachedValue[2], cachedValue[3]);
				break;
		}
	}

	@Override
	public Object query()
	{
		FloatBuffer buffer = BufferUtils.createFloatBuffer(cachedValue.length);
		glGetUniformfv(program, location, buffer);
		return buffer;
	}

	@Override
	public void print()
	{
		FloatBuffer buffer = (FloatBuffer) query();
		System.out.println(name);
		for (int i = 0; i < cachedValue.length; i++)
			System.out.print(" " + buffer.get());
		System.out.println();
	}
}
