package renderer.shaders.scene;

import renderer.shaders.BaseShader;
import renderer.shaders.components.TexUnit1D;
import renderer.shaders.components.TexUnit2D;
import renderer.shaders.components.UniformBool;
import renderer.shaders.components.UniformFloatVector;

public class BasicIndexedShader extends BaseShader
{
	public final TexUnit2D texture;
	public final TexUnit1D palette;
	public final UniformFloatVector baseColor;

	public final UniformBool multiplyBaseColor;
	public final UniformBool multiplyVertexColor;
	public final UniformBool enableFiltering;

	public BasicIndexedShader()
	{
		super("IndexedShader", VS_VERT, FS_BASIC_INDEXED);

		texture = new TexUnit2D(program, 0, "u_image");
		palette = new TexUnit1D(program, 1, "u_palette");
		baseColor = new UniformFloatVector(program, "u_baseColor", 1.0f, 1.0f, 1.0f, 1.0f);

		multiplyBaseColor = new UniformBool(program, "u_multiplyBaseColor", false);
		multiplyVertexColor = new UniformBool(program, "u_multiplyVertexColor", false);
		enableFiltering = new UniformBool(program, "u_useFiltering", false);

		initializeCache();
	}
}
