package game.map.editor.camera;

import game.map.editor.geometry.Vector3f;

import game.map.BoundingBox;
import game.map.editor.CursorObject;
import game.map.editor.MapEditor;
import game.map.editor.common.KeyboardInput;
import game.map.editor.common.MouseInput;
import game.map.hit.CameraZoneData;

public class PerspZoneCamera extends PerspBaseCamera
{
	public CameraController controller;
	public CameraZoneData controlData;

	public PerspZoneCamera(MapEditViewport view)
	{
		super(view);
		controller = new CameraController();
	}

	@Override
	public void reset()
	{
		setPosition(new Vector3f(50.0f, 100.0f, 50.0f));
		setRotation(new Vector3f(45.0f, -45.0f, 0.0f));

		recalculateProjectionMatrix();
	}

	@Override
	public void centerOn(BoundingBox aabb)
	{}

	@Override
	public void tick(double deltaTime)
	{
		CursorObject player = MapEditor.instance().cursor3D;
		controller.update(controlData, player.getPosition(), player.allowVerticalCameraMovement(), deltaTime);

		setPosition(controller.getPosition());
		setRotation(controller.getRotation());

		recalculateProjectionMatrix();
	}

	@Override
	public void handleMovementInput(MouseInput mouse, KeyboardInput keyboard, float deltaTime)
	{}
}
