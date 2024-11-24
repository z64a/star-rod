package renderer.shaders.postprocess;

import common.BaseCamera;
import game.map.editor.MapEditor;
import game.map.scripts.ScriptData;
import renderer.shaders.components.UniformFloat;

public class DepthShader extends PostProcessShader
{
	public final UniformFloat znear;
	public final UniformFloat zfar;

	public DepthShader()
	{
		super("SceneDepthShader", "post_depth.glsl");
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
}
