package renderer.shaders.scene;

import renderer.shaders.BaseShader;
import renderer.shaders.components.UniformBool;
import renderer.shaders.components.UniformFloatVector;

public class BasicSolidShader extends BaseShader
{
	public final UniformFloatVector baseColor;
	public final UniformBool multiplyVertexColor;

	public BasicSolidShader()
	{
		super("SolidShader", VS_VERT, FS_BASIC_SOLID);

		baseColor = new UniformFloatVector(program, "u_baseColor", 1.0f, 1.0f, 1.0f, 1.0f);
		multiplyVertexColor = new UniformBool(program, "u_multiplyVertexColor", true);

		initializeCache();
	}
}
