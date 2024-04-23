package renderer.shaders.components;

import static org.lwjgl.opengl.GL20.glUniform3f;

public class UniformColorRGB extends UniformFloatVector
{
	public UniformColorRGB(int program, String name, float r, float g, float b)
	{
		super(program, name, new float[] { r, g, b });
	}

	@Override
	protected void setUniform()
	{
		glUniform3f(location, cachedValue[0] / 255.0f, cachedValue[1] / 255.0f, cachedValue[2] / 255.0f);
	}
}
