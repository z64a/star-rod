package renderer.shaders.scene;

import renderer.shaders.BaseShader;
import renderer.shaders.components.TexUnit2D;
import renderer.shaders.components.UniformBool;
import renderer.shaders.components.UniformFloat;
import renderer.shaders.components.UniformFloatVector;

public class TextShader extends BaseShader
{
	public final TexUnit2D texture;
	public final UniformFloatVector color;

	public final UniformBool hasOutline;
	public final UniformFloatVector outlineColor;

	public final UniformFloat width;
	public final UniformFloat outlineWidth;

	public final UniformFloat edge;
	public final UniformFloat outlineEdge;

	public final UniformFloat alpha;

	public TextShader()
	{
		super("TextShader", VS_VERT, FS_TEXT);

		texture = new TexUnit2D(program, 0, "u_image");
		alpha = new UniformFloat(program, "u_alpha", 1.0f);

		color = new UniformFloatVector(program, "u_color", 1.0f, 1.0f, 1.0f);
		width = new UniformFloat(program, "u_width", 0.5f);
		edge = new UniformFloat(program, "u_edge", 0.1f);

		hasOutline = new UniformBool(program, "u_hasOutline", false);

		outlineColor = new UniformFloatVector(program, "u_outlineColor", 0.0f, 0.0f, 1.0f);
		outlineWidth = new UniformFloat(program, "u_outlineWidth", 0.7f);
		outlineEdge = new UniformFloat(program, "u_outlineEdge", 0.1f);

		initializeCache();
	}
}
