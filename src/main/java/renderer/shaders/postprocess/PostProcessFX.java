package renderer.shaders.postprocess;

import renderer.FrameBuffer;
import renderer.shaders.ShaderManager;

public enum PostProcessFX
{
	// @formatter:off
	NONE		("None"),
	CRT			("CRT Display"),
	BLUR		("Gaussian Blur", 2),
	DOF			("Depth of Field", 6),
	BLOOM		("Bloom", 10),
	DARK_2008	("2008 (Dark)", 10),
	WARM_2008	("2008 (Warm)", 10),
	SEPIA		("Sepia Tone"),
	DEPTH		("Scene Depth"),
	SOBEL_BW	("Edges (White)"),
	SOBEL_HSV	("Edges (Colorful)"),
	COLORS		("Cycle Colors");
	// @formatter:on

	private final String name;
	private final int numPasses;

	private PostProcessFX(String name)
	{
		this(name, 1);
	}

	private PostProcessFX(String name, int numPasses)
	{
		this.name = name;
		this.numPasses = numPasses;
	}

	@Override
	public String toString()
	{
		return name;
	}

	public int getNumPasses()
	{
		return numPasses;
	}

	// do not include this in constructor! we want to keep shaders lazy-loaded
	// postprocess shaders can either use different shaders in each pass or switch on the u_pass uniform
	private PostProcessShader getShader(int pass)
	{
		switch (this) {
			// @formatter:off
			case NONE:		return ShaderManager.get(PassThroughShader.class);
			case SEPIA:		return ShaderManager.get(SepiaShader.class);
			case DEPTH:		return ShaderManager.get(DepthShader.class);
			case DOF:		return ShaderManager.get(DOFShader.class);
			case CRT:		return ShaderManager.get(CRTShader.class);
			case BLUR:		return ShaderManager.get(BlurShader.class);
			case BLOOM:		return ShaderManager.get(BloomShader.class);
			case DARK_2008:	return ShaderManager.get(Dark2008Shader.class);
			case WARM_2008:	return ShaderManager.get(Warm2008Shader.class);
			case SOBEL_BW:	return ShaderManager.get(SobelShaderBW.class);
			case SOBEL_HSV:	return ShaderManager.get(SobelShaderHSV.class);
			default:		return ShaderManager.get(ColorCycleShader.class);
			// @formatter:on
		}
	}

	public void apply(int pass, FrameBuffer sceneBuffer, FrameBuffer outBuffer, FrameBuffer inBuffer, float time)
	{
		getShader(pass).apply(pass, sceneBuffer, outBuffer, inBuffer, time);
	}
}
