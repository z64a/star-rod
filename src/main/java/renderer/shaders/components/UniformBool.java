package renderer.shaders.components;

import static org.lwjgl.opengl.GL11.GL_FALSE;
import static org.lwjgl.opengl.GL11.GL_TRUE;
import static org.lwjgl.opengl.GL20.glGetUniformi;
import static org.lwjgl.opengl.GL20.glUniform1i;

import renderer.shaders.BaseUniform;

public class UniformBool extends BaseUniform
{
	private final boolean defaultValue;
	private boolean cachedValue;

	public UniformBool(int program, String name, boolean defaultValue)
	{
		this(true, program, name, defaultValue);
	}

	public UniformBool(boolean resets, int program, String name, boolean defaultValue)
	{
		super(resets, name, program);
		this.defaultValue = defaultValue;
	}

	@Override
	public void reset()
	{
		cachedValue = defaultValue;
		glUniform1i(location, cachedValue ? GL_TRUE : GL_FALSE);
	}

	public void set(boolean newValue)
	{
		if (newValue != cachedValue) {
			cachedValue = newValue;
			glUniform1i(location, cachedValue ? GL_TRUE : GL_FALSE);
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
