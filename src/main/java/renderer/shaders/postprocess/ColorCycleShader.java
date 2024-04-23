package renderer.shaders.postprocess;

public class ColorCycleShader extends PostProcessShader
{
	public ColorCycleShader()
	{
		super("ColorCycleShader", "post_color_cycle.glsl");
		initializeCache();
	}
}
