package game.map.editor.camera;

import game.map.Axis;
import game.map.BoundingBox;
import game.map.editor.MapEditor;
import game.map.editor.common.BaseCamera;
import game.map.editor.common.KeyboardInput;
import game.map.editor.common.MouseInput;
import game.map.editor.common.MousePixelRead;
import game.map.editor.geometry.Vector3f;
import game.map.editor.selection.PickRay;
import game.map.editor.selection.PickRay.Channel;

public abstract class MapEditCamera extends BaseCamera
{
	protected final MapEditor editor;
	protected final MapEditViewport view;

	public abstract void recalculateProjectionMatrix();

	// resets view to default
	public abstract void reset();

	public abstract void resize();

	public abstract void centerOn(BoundingBox aabb);

	// respond to user input and move this camera
	public abstract void handleMovementInput(MouseInput mouse, KeyboardInput keyboard, float deltaTime);

	public abstract Vector3f getTranslationVector(int dx, int dy);

	public abstract Axis getRotationAxis();

	public abstract Vector3f getForward(float length);

	public abstract Vector3f getUp(float length);

	public abstract Vector3f getRight(float length);

	// called before any other drawing to the viewport, useful for setting uo
	// the map's parallax background image or drawing the grid
	public abstract void drawBackground();

	protected MapEditCamera(MapEditViewport view)
	{
		super();
		this.view = view;
		this.editor = view.editor;
	}

	public Vector3f getPosition()
	{
		return new Vector3f(pos);
	}

	public void release()
	{}

	public void tick(double deltaTime)
	{}

	/**
	 * Returns a pick ray based on current mouse position.
	 * @return
	 */
	public PickRay getPickRay(int mouseX, int mouseY)
	{
		MousePixelRead pixelRead = getMousePosition(mouseX, mouseY, false, false);
		Vector3f pickPoint = pixelRead.worldPos;

		Vector3f dir = Vector3f.sub(pickPoint, pos).normalize();

		return new PickRay(Channel.SELECTION, new Vector3f(pos), dir, view);
	}
}
