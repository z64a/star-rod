package renderer.shaders.postprocess;

public class SobelShaderBW extends PostProcessShader
{
	public SobelShaderBW()
	{
		super("SobelShaderBW", "post_sobel_bw.glsl");
		initializeCache();
	}
}
