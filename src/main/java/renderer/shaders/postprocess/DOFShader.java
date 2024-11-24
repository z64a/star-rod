package renderer.shaders.postprocess;

import common.BaseCamera;
import game.map.editor.MapEditor;
import game.map.scripts.ScriptData;
import renderer.shaders.components.UniformFloat;

public class DOFShader extends PostProcessShader
{
	public final UniformFloat znear;
	public final UniformFloat zfar;

	public DOFShader()
	{
		super("DOFShader", "post_dof.glsl");

		znear = new UniformFloat(program, "u_znear", 1.0f);
		zfar = new UniformFloat(program, "u_zfar", 1024.0f);
		initializeCache();
	}

	@Override
	protected void setAdditionalUniforms()
	{
		float near = BaseCamera.NEAR_CLIP;
		float far = BaseCamera.FAR_CLIP;

		if (MapEditor.instance().usingInGameCameraProperties()) {
			ScriptData scriptData = MapEditor.instance().map.scripts;
			near = scriptData.camNearClip.get();
			far = scriptData.camFarClip.get();
		}

		znear.set(near);
		zfar.set(far);
	}

	@Override
	protected int getViewportLevel(int pass)
	{
		switch (pass) {
			case 0: // depth
				return 0;
			case 1: // downsample
			case 2: // bokeh
			case 3: // scene blur 1
			case 4: // scene blur 2
				return 1;
			case 5: // mix
			default:
				return 5;
		}
	}
}
