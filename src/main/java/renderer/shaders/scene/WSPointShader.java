package renderer.shaders.scene;

import renderer.shaders.BaseShader;
import renderer.shaders.components.UniformFloatVector;

public class WSPointShader extends BaseShader
{
	public final UniformFloatVector color;

	public WSPointShader()
	{
		super("WSPointShader", VS_VERT, FS_POINT_WS);

		color = new UniformFloatVector(program, "u_color", 1.0f, 1.0f, 1.0f, 1.0f);

		initializeCache();
	}
}
