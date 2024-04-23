package renderer.shaders.scene;

import renderer.shaders.BaseShader;
import renderer.shaders.components.UniformColorRGBA;
import renderer.shaders.components.UniformFloatVector;
import renderer.shaders.components.UniformInt;

public final class HitShader extends BaseShader
{
	public static final int MODE_FILL_SOLID = 0;
	public static final int MODE_FILL_OUTLINE = 1;
	public static final int MODE_LINE_SOLID = 4;
	public static final int MODE_LINE_OUTLINE = 5;

	public final UniformInt drawMode;
	public final UniformFloatVector selectColor;

	public HitShader()
	{
		super("HitShader", VS_VERT, FS_HIT);

		drawMode = new UniformInt(program, "u_drawMode", 0);
		selectColor = new UniformColorRGBA(program, "u_selectColor", 255, 255, 255, 255);

		initializeCache();
	}
}
