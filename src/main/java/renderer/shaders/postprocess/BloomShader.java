package renderer.shaders.postprocess;

public class BloomShader extends PostProcessShader
{
	public BloomShader()
	{
		super("BlurShader", "post_bloom.glsl");
		initializeCache();
	}

	@Override
	protected int getViewportLevel(int pass)
	{
		switch (pass) {
			case 0: // threshold
			case 1: // gaussian blur 1
			case 2: // gaussian blur 2
				return 0;
			case 3: // gaussian blur 1
			case 4: // gaussian blur 2
				return 1;
			case 5: // gaussian blur 1
			case 6: // gaussian blur 2
				return 2;
			case 7: // gaussian blur 1
			case 8: // gaussian blur 2
				return 3;
			case 9: // final
			default:
				return 0;
		}
	}
}
