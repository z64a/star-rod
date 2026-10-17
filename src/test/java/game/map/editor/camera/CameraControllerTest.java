package game.map.editor.camera;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import common.Vector3f;

import game.map.hit.CameraZoneData;
import game.map.hit.ControlType;

class CameraControllerTest
{
	private static final double TICK = 1.0 / 30;

	@Test
	void cameraHoldsHeightDuringJumpAndFollowsDrops()
	{
		CameraController camera = new CameraController();
		camera.update(null, new Vector3f(), true, 0);
		float baseY = camera.getPosition().y;
		camera.update(null, new Vector3f(10, 60, 0), false, TICK);
		assertEquals(baseY, camera.getPosition().y);
		assertEquals(10, camera.getPosition().x);
		camera.update(null, new Vector3f(10, -30, 0), false, TICK);
		assertEquals(baseY - 30, camera.getPosition().y, 0.001);
		camera.update(null, new Vector3f(10, 0, 0), true, TICK);
		assertTrue(camera.getPosition().y > baseY - 30 && camera.getPosition().y < baseY);
	}

	@Test
	void zoneChangesBlendAndDetectEditsToTheSameZone()
	{
		CameraController camera = new CameraController();
		CameraZoneData data = settings(ControlType.TYPE_0, false);
		camera.update(data, new Vector3f(), true, 0);
		Vector3f original = camera.getPosition();
		data.boomLength.set(900.0f);
		camera.update(data, new Vector3f(), true, TICK);
		assertTrue(camera.getPosition().z > original.z);
		assertTrue(camera.getPosition().z < original.z * 1.05, "Zone edit should blend instead of snapping");
		for (int i = 0; i < 100; i++)
			camera.update(data, new Vector3f(), true, TICK);
		assertEquals(original.z * 2, camera.getPosition().z, 0.01);
	}

	@Test
	void cameraCutDiscardsInterpolationAndOldZoneSettings()
	{
		CameraController camera = new CameraController();
		camera.update(settings(ControlType.TYPE_4, false), new Vector3f(), true, 0);
		Vector3f destination = new Vector3f(1000, 100, -400);
		camera.reset(destination);
		assertFalse(camera.isInitialized());
		camera.update(null, destination, true, 0);
		assertEquals(1000, camera.getPosition(0).x);
		assertEquals(camera.getPosition().y, camera.getPosition(0).y);
		assertEquals(camera.getPosition().z, camera.getPosition(0).z);
		assertEquals(0, camera.getRotation().y);
	}

	@Test
	void everyCameraTypeHandlesCoincidentControlPoints()
	{
		for (ControlType type : ControlType.values()) {
			for (boolean flag : new boolean[] { false, true }) {
				CameraController camera = new CameraController();
				CameraZoneData data = settings(type, flag);
				data.posA.point.setPosition(0, 0, 0);
				data.posB.point.setPosition(0, 0, 0);
				data.posC.point.setPosition(0, 0, 0);
				for (int i = 0; i < 100; i++) {
					camera.update(data, new Vector3f(i, 0, -i), true, TICK);
					assertFinite(camera.getPosition(), type + " flag " + flag);
					assertFinite(camera.getRotation(), type + " flag " + flag);
				}
			}
		}
	}

	@Test
	void lineProjectionAndSegmentConstraintsHaveExpectedTargets()
	{
		CameraZoneData data = settings(ControlType.TYPE_2, false);
		data.posA.point.setPosition(-100, 0, 0);
		data.posB.point.setPosition(0, 0, 0);
		data.posC.point.setPosition(100, 0, 0);
		CameraController camera = new CameraController();
		camera.update(data, new Vector3f(40, 25, 75), true, 0);
		assertEquals(40, camera.getTargetPosition().x, 0.001);
		assertEquals(0, camera.getTargetPosition().z, 0.001);
		int[] encoded = data.getData();
		encoded[0] = ControlType.TYPE_6.index;
		encoded[10] = 1;
		data = new CameraZoneData(null, encoded);
		camera.reset(new Vector3f());
		camera.update(data, new Vector3f(40, 25, 75), true, 0);
		assertEquals(0, camera.getTargetPosition().x, 0.001);
		assertEquals(0, camera.getTargetPosition().z, 0.001);
	}

	@Test
	void yawInterpolationTakesShortestPathAcrossAngleBoundary()
	{
		CameraZoneData data = settings(ControlType.TYPE_0, false);
		data.posA.point.setPosition(0, 0, 0);
		data.posB.point.setPosition(1, 0, 100);
		CameraController camera = new CameraController();
		camera.update(data, new Vector3f(), true, 0);
		data.posB.point.setPosition(-1, 0, 100);
		for (int i = 0; i < 100; i++) {
			camera.update(data, new Vector3f(), true, TICK);
			assertTrue(Math.abs(camera.getRotation(0.5f).y) > 170, "Yaw must cross 180, not swing through zero");
		}
	}

	private static CameraZoneData settings(ControlType type, boolean flag)
	{
		int[] encoded = new CameraZoneData(null).getData();
		encoded[0] = type.index;
		encoded[10] = flag ? 1 : 0;
		return new CameraZoneData(null, encoded);
	}

	private static void assertFinite(Vector3f v, String message)
	{
		assertTrue(Float.isFinite(v.x) && Float.isFinite(v.y) && Float.isFinite(v.z), message);
	}
}
