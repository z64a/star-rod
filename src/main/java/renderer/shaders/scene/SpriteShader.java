package renderer.shaders.scene;

import renderer.shaders.BaseShader;
import renderer.shaders.components.TexUnit1D;
import renderer.shaders.components.TexUnit2D;
import renderer.shaders.components.UniformBool;
import renderer.shaders.components.UniformColorRGB;
import renderer.shaders.components.UniformFloat;
import renderer.shaders.components.UniformFloatVector;

public final class SpriteShader extends BaseShader
{
	public final TexUnit2D texture;
	public final TexUnit1D palette;
	public final UniformFloat alpha;

	public final UniformBool selectShading;
	public final UniformBool selected;
	public final UniformBool highlighted;
	public final UniformBool useFiltering;

	public final UniformBool useShading;

	public final UniformFloatVector shadingOffset;
	public final UniformColorRGB shadingShadow;
	public final UniformColorRGB shadingHighlight;

	public SpriteShader()
	{
		super("SpriteShader", VS_VERT, FS_SPRITE);

		texture = new TexUnit2D(program, 0, "u_image");
		palette = new TexUnit1D(program, 1, "u_palette");

		alpha = new UniformFloat(program, "u_alpha", 1.0f);

		selectShading = new UniformBool(program, "u_selectedShading", false);
		selected = new UniformBool(program, "u_selected", false);
		highlighted = new UniformBool(program, "u_highlighted", false);
		useFiltering = new UniformBool(program, "u_useFiltering", false);

		useShading = new UniformBool(program, "u_useShading", false);
		shadingOffset = new UniformFloatVector(program, "u_shadingOffset", 0.0f, 0.0f);
		shadingShadow = new UniformColorRGB(program, "u_shadingShadow", 16, 16, 16);
		shadingHighlight = new UniformColorRGB(program, "u_shadingHighlight", 255, 255, 255);

		initializeCache();
	}
}
