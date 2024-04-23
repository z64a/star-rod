package renderer.shaders.scene;

import renderer.shaders.BaseShader;
import renderer.shaders.components.TexUnit2D;
import renderer.shaders.components.UniformFloat;
import renderer.shaders.components.UniformFloatVector;

public class MarkerShader extends BaseShader
{
	public final TexUnit2D texture;
	public final UniformFloatVector color;

	public final UniformFloat time;

	public MarkerShader()
	{
		super("MarkerShader", VS_VERT, FS_MARKER);

		texture = new TexUnit2D(program, 0, "u_image");
		color = new UniformFloatVector(program, "u_tintColor", 1.0f, 1.0f, 1.0f, 1.0f);

		time = new UniformFloat(program, "u_time", 0.0f);

		initializeCache();
	}
}
