package game.map.editor.camera;

import static java.lang.Math.*;

import java.awt.event.KeyEvent;

import common.KeyboardInput;
import common.MouseInput;
import common.Vector3f;
import game.map.BoundingBox;

public class PerspFreeCamera extends PerspBaseCamera
{
	private static final float MAX_PITCH = 80;
	private static final float SPEED = 320.0f;

	public PerspFreeCamera(MapEditViewport view)
	{
		this(new Vector3f(50.0f, 100.0f, 50.0f), new Vector3f(45.0f, -45.0f, 0.0f), view);
	}

	public PerspFreeCamera(Vector3f pos, Vector3f rotation, MapEditViewport view)
	{
		super(view);
		setPosition(pos);
		setRotation(rotation);
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
	{
		// get size of the selection, clamp to [100.0, 500.0]
		Vector3f min = aabb.getMin();
		Vector3f max = aabb.getMax();
		float sizeX = max.x - min.x;
		float sizeY = max.y - min.y;
		float sizeZ = max.z - min.z;
		float size = Math.max(Math.max(sizeX, sizeY), sizeZ);
		size = Math.max(size, 100.0f);
		size = Math.min(size, 500.0f);

		// move to distance equal to clamped selection size
		Vector3f center = aabb.getCenter();
		float dX = pos.x - center.x;
		float dY = pos.y - center.y;
		float dZ = pos.z - center.z;
		float dist = (float) Math.sqrt(dX * dX + dY * dY + dZ * dZ);
		pos.x = center.x + (size / dist) * dX;
		pos.y = center.y + (size / dist) * dY;
		pos.z = center.z + (size / dist) * dZ;

		// set angles and clamp pitch
		float r = (float) Math.sqrt(dX * dX + dZ * dZ);
		yaw = (int) Math.toDegrees(Math.atan2(-dX, dZ));
		pitch = (int) Math.toDegrees(Math.atan2(dY, r));
		if (pitch < -MAX_PITCH)
			pitch = -MAX_PITCH;
		if (pitch > MAX_PITCH)
			pitch = MAX_PITCH;
	}

	public void lookAt(Vector3f target)
	{
		float dX = pos.x - target.x;
		float dY = pos.y - target.y;
		float dZ = pos.z - target.z;

		float r = (float) Math.sqrt(dX * dX + dZ * dZ);
		yaw = (int) Math.toDegrees(Math.atan2(-dX, dZ));
		pitch = (int) Math.toDegrees(Math.atan2(dY, r));
		if (pitch < -MAX_PITCH)
			pitch = -MAX_PITCH;
		if (pitch > MAX_PITCH)
			pitch = MAX_PITCH;
	}

	@Override
	public void tick(double deltaTime)
	{
		recalculateProjectionMatrix();
	}

	@Override
	public void handleMovementInput(MouseInput mouse, KeyboardInput keyboard, float deltaTime)
	{
		boolean moveEnabled = keyboard.isKeyDown(KeyEvent.VK_SHIFT);
		mouse.setGrabbed(moveEnabled);

		if (moveEnabled) {
			yaw += mouse.getFrameDX() * 0.16f;
			if (yaw > 360)
				yaw = yaw - 360;
			if (yaw < 0)
				yaw = yaw + 360;

			pitch -= mouse.getFrameDY() * 0.16f;
			if (pitch < -MAX_PITCH)
				pitch = -MAX_PITCH;
			if (pitch > MAX_PITCH)
				pitch = MAX_PITCH;

			int vx = 0, vz = 0;

			boolean moveForward = keyboard.isKeyDown(KeyEvent.VK_W);
			boolean moveBackward = keyboard.isKeyDown(KeyEvent.VK_S);
			boolean moveLeft = keyboard.isKeyDown(KeyEvent.VK_A);
			boolean moveRight = keyboard.isKeyDown(KeyEvent.VK_D);

			float mod = 1.0f;
			if (mouse.isHoldingLMB())
				mod *= 2.5f;
			if (mouse.isHoldingRMB())
				mod *= 0.4f;

			if (moveForward)
				vz -= SPEED * deltaTime * mod;
			if (moveBackward)
				vz += SPEED * deltaTime * mod;

			if (moveLeft)
				vx -= SPEED * deltaTime * mod;
			if (moveRight)
				vx += SPEED * deltaTime * mod;

			moveFromLook(vx, 0, vz);
		}
	}

	private void moveFromLook(float dx, float dy, float dz)
	{
		this.pos.z += dx * (float) cos(toRadians(yaw - 90)) + dz * cos(toRadians(yaw));
		this.pos.x -= dx * (float) sin(toRadians(yaw - 90)) + dz * sin(toRadians(yaw));
		this.pos.y += dy * (float) sin(toRadians(pitch - 90)) + dz * sin(toRadians(pitch));
	}
}
