package game.map.editor.camera;

import common.KeyboardInput;
import common.MouseInput;
import common.Vector3f;

import game.map.BoundingBox;
import game.map.editor.CursorObject;
import game.map.editor.MapEditor;
import game.map.hit.CameraZoneData;

public class PerspZoneCamera extends PerspBaseCamera
{
	public CameraController controller;
	public CameraZoneData controlData;

	public PerspZoneCamera(MapEditViewport view)
	{
		super(view);
		controller = new CameraController();
		controller.reset(new Vector3f());
	}

	@Override
	public void reset()
	{
		Vector3f initialPosition = new Vector3f(50.0f, 100.0f, 50.0f);
		setPosition(initialPosition);
		setRotation(new Vector3f(45.0f, -45.0f, 0.0f));
		controlData = null;
		if (controller != null)
			controller.reset(initialPosition);

		recalculateProjectionMatrix();
	}

	public void startPreview(Vector3f playerPosition)
	{
		requestCameraCut(playerPosition);
	}

	public void requestCameraCut(Vector3f playerPosition)
	{
		controlData = null;
		controller.reset(playerPosition);
	}

	public void prepareSimulation()
	{
		if (!controller.isInitialized())
			return;

		setPosition(controller.getPosition());
		setRotation(controller.getRotation());
	}

	public void updateSimulation(CameraZoneData data, Vector3f playerPosition, boolean allowVertical, float yinterpRate, double deltaTime)
	{
		controlData = data;
		controller.update(data, playerPosition, allowVertical, yinterpRate, deltaTime);
		setPosition(controller.getPosition());
		setRotation(controller.getRotation());
	}

	@Override
	public void centerOn(BoundingBox aabb)
	{}

	@Override
	public void tick(double deltaTime)
	{
		CursorObject player = MapEditor.instance().cursor3D;
		if (controller.isInitialized()) {
			float alpha = player.getSimulationInterpolation();
			setPosition(controller.getPosition(alpha));
			setRotation(controller.getRotation(alpha));
		}

		recalculateProjectionMatrix();
	}

	@Override
	public void handleMovementInput(MouseInput mouse, KeyboardInput keyboard, float deltaTime)
	{}
}
