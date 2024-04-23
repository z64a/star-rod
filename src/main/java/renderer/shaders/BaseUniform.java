package renderer.shaders;

import static org.lwjgl.opengl.GL20.glGetUniformLocation;

public abstract class BaseUniform
{
	protected final String name;
	protected final int program;
	protected final int location;
	protected final boolean resets;

	protected BaseUniform(boolean resets, String name, int program)
	{
		this.resets = resets;
		this.name = name;
		this.program = program;
		this.location = glGetUniformLocation(program, name);

		//	if(location == -1)
		//		throw new StarRodException("Could not find uniform in shader: " + name);
	}

	public abstract void reset();

	public abstract Object query();

	public abstract void print();
}
