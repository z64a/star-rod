package renderer.shaders.postprocess;

public class SepiaShader extends PostProcessShader
{
	public SepiaShader()
	{
		super("SepiaShader", "post_sepia.glsl");
		initializeCache();
	}
}
