package renderer.shaders.components;

import static org.lwjgl.opengl.GL20.glGetUniformi;
import static org.lwjgl.opengl.GL20.glUniform1i;

import renderer.shaders.BaseUniform;

public class UniformInt extends BaseUniform
{
	private final int defaultValue;
	private int cachedValue;

	public UniformInt(int program, String name, int defaultValue)
	{
		this(true, program, name, defaultValue);
	}

	public UniformInt(boolean resets, int program, String name, int defaultValue)
	{
		super(resets, name, program);
		this.defaultValue = defaultValue;
	}

	@Override
	public void reset()
	{
		cachedValue = defaultValue;
		glUniform1i(location, cachedValue);
	}

	public void set(int newValue)
	{
		if (newValue != cachedValue) {
			cachedValue = newValue;
			glUniform1i(location, cachedValue);
		}
	}

	@Override
	public Integer query()
	{
		return glGetUniformi(program, location);
	}

	@Override
	public void print()
	{
		System.out.println(name);
		System.out.println(" " + query());
	}
}
