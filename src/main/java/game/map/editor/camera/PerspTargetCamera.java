package game.map.editor.camera;

import java.awt.event.KeyEvent;

import common.KeyboardInput;
import common.MouseInput;
import game.map.BoundingBox;
import game.map.editor.MapEditor;
import game.map.marker.Marker;

public class PerspTargetCamera extends PerspBaseCamera
{
	public CameraController controller;
	public Marker targetMarker;

	public PerspTargetCamera(MapEditViewport view)
	{
		super(view);
		controller = new CameraController();
	}

	@Override
	public void reset()
	{
		recalculateProjectionMatrix();
	}

	@Override
	public void centerOn(BoundingBox aabb)
	{}

	@Override
	public void tick(double deltaTime)
	{
		if (targetMarker != null)
			controller.update(targetMarker.cameraComponent.getCurrentData(), targetMarker.position.getVector(), true, deltaTime);

		setPosition(controller.getPosition());
		setRotation(controller.getRotation());

		recalculateProjectionMatrix();
	}

	@Override
	public void handleMovementInput(MouseInput mouse, KeyboardInput keyboard, float deltaTime)
	{
		// if we start moving, exit this camera mode
		if (keyboard.isKeyDown(KeyEvent.VK_SHIFT)) {
			MapEditor.instance().clearTargetCamera();
		}
	}
}
