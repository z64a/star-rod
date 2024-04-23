package renderer.shaders.scene;

import renderer.shaders.BaseShader;
import renderer.shaders.components.TexUnit2D;
import renderer.shaders.components.UniformBool;
import renderer.shaders.components.UniformFloat;
import renderer.shaders.components.UniformFloatVector;

public class BasicTexturedShader extends BaseShader
{
	public final TexUnit2D texture;
	public final UniformFloatVector baseColor;
	public final UniformFloat saturation;

	public final UniformBool selected;
	public final UniformBool multiplyBaseColor;
	public final UniformBool multiplyVertexColor;
	public final UniformBool enableFiltering;

	public BasicTexturedShader()
	{
		super("TexturedShader", VS_VERT, FS_BASIC_TEXTURED);

		texture = new TexUnit2D(program, 0, "u_image");
		baseColor = new UniformFloatVector(program, "u_baseColor", 1.0f, 1.0f, 1.0f, 1.0f);
		saturation = new UniformFloat(program, "u_saturation", 1.0f);

		selected = new UniformBool(program, "u_selected", false);
		multiplyBaseColor = new UniformBool(program, "u_multiplyBaseColor", false);
		multiplyVertexColor = new UniformBool(program, "u_multiplyVertexColor", false);
		enableFiltering = new UniformBool(program, "u_useFiltering", false);

		initializeCache();
	}
}
