package renderer.shaders.components;

import static org.lwjgl.opengl.GL20.glUniform4f;

public class UniformColorRGBA extends UniformFloatVector
{
	public UniformColorRGBA(int program, String name, float r, float g, float b, float a)
	{
		super(false, program, name, new float[] { r, g, b, a });
	}

	public UniformColorRGBA(boolean resets, int program, String name, float r, float g, float b, float a)
	{
		super(resets, program, name, new float[] { r, g, b, a });
	}

	@Override
	protected void setUniform()
	{
		glUniform4f(location, cachedValue[0] / 255.0f, cachedValue[1] / 255.0f, cachedValue[2] / 255.0f, cachedValue[3] / 255.0f);
	}
}
