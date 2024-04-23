package renderer.shaders.scene;

import renderer.shaders.BaseShader;
import renderer.shaders.components.TexUnit1D;
import renderer.shaders.components.TexUnit2D;
import renderer.shaders.components.UniformBool;
import renderer.shaders.components.UniformFloat;
import renderer.shaders.components.UniformFloatVector;
import renderer.shaders.components.UniformInt;

public class FontShader extends BaseShader
{
	public final TexUnit2D texture;
	public final TexUnit1D palette;
	public final TexUnit2D noise;

	public final UniformBool enableFiltering;
	public final UniformBool enableDropShadow;

	public final UniformInt noiseMode;
	public final UniformFloat noiseAlpha;
	public final UniformFloatVector noiseOffset;

	public final UniformFloat fadeAlpha;

	public FontShader()
	{
		super("FontShader", VS_VERT, FS_FONT);

		texture = new TexUnit2D(program, 0, "mainImage");
		palette = new TexUnit1D(program, 1, "mainPalette");
		noise = new TexUnit2D(program, 2, "noiseImage");

		enableFiltering = new UniformBool(program, "useFiltering", false);
		enableDropShadow = new UniformBool(program, "enableDropShadow", false);

		noiseMode = new UniformInt(program, "noiseMode", 0);
		noiseAlpha = new UniformFloat(program, "noiseAlpha", 1.0f);
		noiseOffset = new UniformFloatVector(program, "noiseOffset", 0.0f, 0.0f);

		fadeAlpha = new UniformFloat(program, "fadeAlpha", 1.0f);

		initializeCache();
	}
}
