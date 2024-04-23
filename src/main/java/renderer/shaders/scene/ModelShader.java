package renderer.shaders.scene;

import game.map.editor.MapEditor;
import game.map.editor.render.TextureManager;
import game.map.shape.Model;
import game.texture.ModelTexture;
import renderer.shaders.BaseShader;
import renderer.shaders.components.TexUnit1D;
import renderer.shaders.components.TexUnit2D;
import renderer.shaders.components.UniformBool;
import renderer.shaders.components.UniformColorRGBA;
import renderer.shaders.components.UniformFloat;
import renderer.shaders.components.UniformFloatVector;
import renderer.shaders.components.UniformInt;

public final class ModelShader extends BaseShader
{
	public static final int MODE_FILL_SOLID = 0;
	public static final int MODE_FILL_OUTLINE = 1;
	public static final int MODE_FILL_OUTLINE_HIGHLIGHT = 2;
	public static final int MODE_LINE_SOLID = 4;
	public static final int MODE_LINE_OUTLINE = 5;

	public final UniformInt mainFmt;
	public final TexUnit2D mainImg;
	public final TexUnit1D mainPal;

	public final UniformInt auxFmt;
	public final TexUnit2D auxImg;
	public final TexUnit1D auxPal;

	public final UniformInt drawMode;

	public final UniformBool selected;
	public final UniformBool textured;
	public final UniformBool translucent;

	public final UniformInt auxCombineMode;

	private final UniformFloatVector auxScale;
	public final UniformFloatVector auxOffset;
	public final UniformFloatVector mainScroll;
	public final UniformFloatVector auxScroll;

	public final UniformBool enableFog;
	public final UniformFloatVector fogDist;
	public final UniformColorRGBA fogColor;

	public final UniformBool useFiltering;

	public final UniformBool enableLOD;
	public final UniformFloat lodBias;

	public ModelShader()
	{
		super("ModelShader", VS_VERT, FS_MODEL);

		mainFmt = new UniformInt(program, "mainFormat", 0);
		mainImg = new TexUnit2D(program, 0, "mainImage");
		mainPal = new TexUnit1D(program, 1, "mainPalette");

		auxFmt = new UniformInt(program, "auxFormat", 0);
		auxImg = new TexUnit2D(program, 2, "auxImage");
		auxPal = new TexUnit1D(program, 3, "auxPalette");

		drawMode = new UniformInt(program, "drawMode", 0);

		selected = new UniformBool(program, "selected", false);
		textured = new UniformBool(program, "textured", false);
		translucent = new UniformBool(program, "translucent", false);
		useFiltering = new UniformBool(program, "useFiltering", false);

		enableFog = new UniformBool(false, program, "useFog", false);
		fogDist = new UniformFloatVector(false, program, "fogDist", 950.0f, 1000.0f);
		fogColor = new UniformColorRGBA(false, program, "fogColor", 255, 255, 255, 255);

		auxCombineMode = new UniformInt(program, "auxCombineMode", 0);

		auxScale = new UniformFloatVector(program, "auxScale", 1.0f, 1.0f);
		auxOffset = new UniformFloatVector(program, "auxOffset", 0.0f, 0.0f);

		mainScroll = new UniformFloatVector(program, "mainScroll", 0.0f, 0.0f);
		auxScroll = new UniformFloatVector(program, "auxScroll", 0.0f, 0.0f);

		enableLOD = new UniformBool(program, "useLOD", false);

		lodBias = new UniformFloat(program, "lodBias", 0);

		initializeCache();
	}

	public void useProperties(Model mdl, boolean useFiltering, boolean useLOD)
	{
		if (mdl == null)
			return;

		ModelTexture tex = mdl.hasMesh() ? mdl.getMesh().texture : null;
		if (tex == null) {
			textured.set(false);
			mainImg.bind(TextureManager.glMissingTextureID);
			return;
		}

		textured.set(true);
		tex.setShaderParameters(this, useFiltering, useLOD);

		if (tex.hasAux() && mdl.hasAuxProperties.get()) {
			auxScale.set(calcScaleForShift(mdl.auxShiftS.get()), calcScaleForShift(mdl.auxShiftT.get()));
			auxOffset.set(mdl.auxOffsetS.get(), mdl.auxOffsetT.get());
		}

		if (mdl.pannerID.get() < 0) {
			mainScroll.set(0, 0);
			auxScroll.set(0, 0);
		}
		else
			MapEditor.instance().map.scripts.texPanners.get(mdl.pannerID.get()).setShaderParams();
	}

	private static float calcScaleForShift(int shift)
	{
		if (shift <= 10) {
			return 1.0f / (1 << shift);
		}
		else {
			return 1 << (16 - shift);
		}
	}
}
