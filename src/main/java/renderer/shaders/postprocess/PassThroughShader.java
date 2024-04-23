package renderer.shaders.postprocess;

public class PassThroughShader extends PostProcessShader
{
	public PassThroughShader()
	{
		super("PassThroughShader", "post_pass_thru.glsl");
		initializeCache();
	}
}
