package renderer.shaders.scene;

import renderer.shaders.BaseShader;
import renderer.shaders.components.UniformFloatVector;

public class PointShader extends BaseShader
{
	public final UniformFloatVector color;

	public PointShader()
	{
		super("PointShader", VS_POINT, FS_POINT);

		color = new UniformFloatVector(program, "u_color", 1.0f, 1.0f, 1.0f, 1.0f);

		initializeCache();
	}
}
