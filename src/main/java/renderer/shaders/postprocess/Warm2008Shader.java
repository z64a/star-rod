package renderer.shaders.postprocess;

import java.awt.image.BufferedImage;

import game.map.editor.render.TextureManager;

public class Warm2008Shader extends PostProcessShader
{
	private int lutTexID = 0;

	public Warm2008Shader()
	{
		super("Warm2008Shader", "post_2008.glsl");
		initializeCache();

		BufferedImage bimg = TextureManager.loadEditorImage("lut_2008_warm.png");
		if (bimg != null)
			lutTexID = TextureManager.bindBufferedImage(bimg);
	}

	@Override
	protected void setAdditionalUniforms()
	{
		lut.bind(lutTexID);
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
