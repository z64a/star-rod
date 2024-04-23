package renderer.shaders.postprocess;

public class SobelShaderHSV extends PostProcessShader
{
	public SobelShaderHSV()
	{
		super("SobelShaderHSV", "post_sobel_hsv.glsl");
		initializeCache();
	}
}
