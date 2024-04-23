package renderer.shaders.postprocess;

public class CRTShader extends PostProcessShader
{
	public CRTShader()
	{
		super("CRTShader", "post_crt.glsl");
		initializeCache();
	}
}
