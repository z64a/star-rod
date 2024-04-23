package game.map.editor.render;

import game.map.editor.MapEditor.EditorMode;
import game.map.editor.selection.SelectionManager.SelectionMode;
import game.map.shading.ShadingProfile;
import renderer.shaders.postprocess.PostProcessFX;

public class RenderingOptions
{
	public static enum SurfaceMode
	{
		TEXTURED, SHADED, WIREFRAME
	}

	public EditorMode editorMode = EditorMode.Modify;
	public SelectionMode selectionMode = SelectionMode.OBJECT;
	public SurfaceMode modelSurfaceMode = SurfaceMode.TEXTURED;
	public PostProcessFX postProcessFX = PostProcessFX.NONE;
	public boolean useFiltering = false;
	public boolean useTextureLOD = false;
	public boolean useGeometryFlags = false;
	public boolean worldFogEnabled = false;
	public boolean entityFogEnabled = false;
	public boolean isStage = false;

	public boolean showBoundingBoxes = false;
	public boolean showNormals = false;
	public boolean edgeHighlights = false;
	public boolean showEntityCollision = false;

	public boolean useColliderColoring = false;

	public ShadingProfile spriteShading = null;

	public boolean thumbnailMode = false;

	public int canvasSizeX;
	public int canvasSizeY;
	public float screenFade = 0.0f;
	public float time = 0.0f;
}
