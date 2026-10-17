package renderer.shaders.scene;

import renderer.shaders.BaseShader;
import renderer.shaders.components.UniformBool;
import renderer.shaders.components.UniformFloat;
import renderer.shaders.components.UniformFloatVector;

public class LineShader extends BaseShader
{
	public final UniformFloatVector color;
	public final UniformBool useVertexColor;
	public final UniformBool clipDepth;
	public final UniformFloat lineWidth;

	public final UniformFloat dashSize;
	public final UniformFloat dashRatio;
	public final UniformFloat dashSpeedRate;

	public LineShader()
	{
		super("LineShader", VS_LINE, FS_LINE);

		color = new UniformFloatVector(program, "u_color", 1.0f, 1.0f, 1.0f, 1.0f);
		useVertexColor = new UniformBool(program, "u_useVertexColor", true);
		clipDepth = new UniformBool(program, "u_clipDepth", true);
		lineWidth = new UniformFloat(program, "u_lineWidth", 1.0f);

		dashSize = new UniformFloat(program, "u_dashSize", 20.0f);
		dashRatio = new UniformFloat(program, "u_dashRatio", 1.0f);
		dashSpeedRate = new UniformFloat(program, "u_dashSpeedRate", 1.0f);

		initializeCache();
	}
}
