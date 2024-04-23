package renderer.shaders.components;

import static org.lwjgl.opengl.GL20.glGetUniformf;
import static org.lwjgl.opengl.GL20.glUniform1f;

import renderer.shaders.BaseUniform;

public class UniformFloat extends BaseUniform
{
	private final float defaultValue;
	private float cachedValue;

	public UniformFloat(int program, String name, float defaultValue)
	{
		this(true, program, name, defaultValue);
	}

	public UniformFloat(boolean resets, int program, String name, float defaultValue)
	{
		super(resets, name, program);
		this.defaultValue = defaultValue;
	}

	@Override
	public void reset()
	{
		cachedValue = defaultValue;
		glUniform1f(location, cachedValue);
	}

	public void set(float newValue)
	{
		if (newValue != cachedValue) {
			cachedValue = newValue;
			glUniform1f(location, cachedValue);
		}
	}

	@Override
	public Float query()
	{
		return glGetUniformf(program, location);
	}

	@Override
	public void print()
	{
		System.out.println(name);
		System.out.println(" " + query());
	}
}
