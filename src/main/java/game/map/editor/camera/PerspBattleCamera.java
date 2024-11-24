package game.map.editor.camera;

import common.KeyboardInput;
import common.MouseInput;
import common.Vector3f;
import game.map.BoundingBox;

public class PerspBattleCamera extends PerspBaseCamera
{
	public PerspBattleCamera(MapEditViewport view)
	{
		super(view);
		reset();
	}

	@Override
	public void reset()
	{
		float boomLength = 500.0f;
		float boomPitch = 8.0f;
		float posX = 0.0f;
		float posY = 60.0f;
		float posZ = 0.0f;

		posZ += (float) (boomLength * Math.cos(Math.toRadians(boomPitch)));
		posY += (float) (boomLength * Math.sin(Math.toRadians(boomPitch)));

		setPosition(new Vector3f(posX, posY, posZ));
		setRotation(new Vector3f(boomPitch, 0.0f, 0.0f));

		recalculateProjectionMatrix();
	}

	@Override
	public void centerOn(BoundingBox aabb)
	{}

	@Override
	public void tick(double deltaTime)
	{
		recalculateProjectionMatrix();
	}

	@Override
	public void handleMovementInput(MouseInput mouse, KeyboardInput keyboard, float deltaTime)
	{}
}
