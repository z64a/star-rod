package renderer.shaders.scene;

import renderer.shaders.BaseShader;
import renderer.shaders.components.TexUnit1D;
import renderer.shaders.components.TexUnit2D;
import renderer.shaders.components.UniformBool;
import renderer.shaders.components.UniformFloatVector;
import renderer.shaders.components.UniformInt;

public class EntityShader extends BaseShader
{
	public final UniformInt format;
	public final TexUnit2D texture;
	public final TexUnit1D palette;

	public final UniformBool selected;
	public final UniformBool textured;
	public final UniformBool useFiltering;

	public final UniformBool enableFog;
	public final UniformFloatVector fogDist;
	public final UniformFloatVector fogColor;

	public EntityShader()
	{
		super("EntityShader", VS_VERT, FS_ENTITY);

		format = new UniformInt(program, "u_fmt", 0);
		texture = new TexUnit2D(program, 0, "u_img");
		palette = new TexUnit1D(program, 1, "u_pal");

		selected = new UniformBool(program, "u_selected", false);
		textured = new UniformBool(program, "u_textured", false);
		useFiltering = new UniformBool(program, "u_useFiltering", false);

		enableFog = new UniformBool(program, "u_useFog", false);
		fogDist = new UniformFloatVector(program, "u_fogDist", 950.0f, 1000.0f);
		fogColor = new UniformFloatVector(program, "u_fogColor", 0.0f, 0.0f, 0.0f, 0.0f);

		initializeCache();
	}
}
