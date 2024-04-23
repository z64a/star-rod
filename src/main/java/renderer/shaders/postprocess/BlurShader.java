package renderer.shaders.postprocess;

public class BlurShader extends PostProcessShader
{
	public BlurShader()
	{
		super("BlurShader", "post_blur.glsl");
		initializeCache();
	}
}
