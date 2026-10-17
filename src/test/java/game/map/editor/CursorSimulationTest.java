package game.map.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.event.KeyEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JPanel;

import org.junit.jupiter.api.Test;

import common.KeyboardInput;
import common.Vector3f;

import game.map.MapObject;
import game.map.editor.camera.CameraController;
import game.map.hit.Collider;
import game.map.mesh.BasicMesh;
import game.map.mesh.Triangle;
import game.map.mesh.Vertex;

class CursorSimulationTest
{
	private static final double TICK = 1.0 / 30;
	private final JPanel inputSource = new JPanel();
	private final KeyboardInput keyboard = new KeyboardInput(inputSource);

	@Test
	void walkingAndRunningAreIndependentOfRenderFrameRate()
	{
		for (boolean run : new boolean[] { false, true }) {
			keyboard.reset();
			press(KeyEvent.VK_D);
			if (run)
				press(KeyEvent.VK_SHIFT);
			Float expected = null;
			for (int fps : new int[] { 15, 30, 60, 120, 144 }) {
				CursorObject player = player(0, 0, 0);
				AtomicInteger ticks = new AtomicInteger();
				for (int frame = 0; frame < fps; frame++)
					player.tickSimulation(keyboard, floor(), () -> 0, 1.0 / fps, true, true, dt -> ticks.incrementAndGet());
				assertEquals(30, ticks.get(), "Physics ticks at " + fps + " FPS");
				Vector3f pos = player.getSimulationPosition();
				assertEquals(0, pos.y, 0.001);
				assertEquals(0, pos.z, 0.001);
				if (expected == null)
					expected = pos.x;
				assertEquals(expected, pos.x, 0.001, "Movement at " + fps + " FPS");
			}
			assertTrue(expected > (run ? 130 : 65) && expected < (run ? 145 : 80));
		}
	}

	@Test
	void renderPositionInterpolatesWithoutChangingPhysics()
	{
		CursorObject player = player(0, 0, 0);
		press(KeyEvent.VK_D);
		advance(player, floor(), 1, TICK);
		float physicalX = player.getSimulationPosition().x;
		assertEquals(0, player.getPosition().x, 0.001);
		advance(player, floor(), 1, TICK / 2);
		assertEquals(physicalX, player.getSimulationPosition().x, 0.001);
		assertEquals(physicalX / 2, player.getPosition().x, 0.001);
	}

	@Test
	void heldJumpIsHigherThanTapAndBothLandAtEveryFrameRate()
	{
		for (int fps : new int[] { 15, 30, 60, 120 }) {
			float tapHeight = jumpHeight(fps, false);
			float heldHeight = jumpHeight(fps, true);
			assertTrue(tapHeight > 0, "Tap must survive between physics ticks");
			assertTrue(heldHeight > tapHeight + 10, "Holding jump produces a higher jump");
		}
	}

	private float jumpHeight(int fps, boolean held)
	{
		CursorObject player = player(0, 0, 0);
		player.startInputJump();
		if (!held)
			player.endInputJump();
		float maxY = 0;
		for (int i = 0; i < fps * 2; i++) {
			advance(player, floor(), 1, 1.0 / fps);
			float y = player.getSimulationPosition().y;
			assertTrue(y >= -0.001, "Player must not fall through the floor");
			maxY = Math.max(maxY, y);
		}
		assertEquals(0, player.getSimulationPosition().y, 0.001, "Holding jump must not cause automatic repeat jumps");
		assertTrue(player.allowVerticalCameraMovement());
		return maxY;
	}

	@Test
	void jumpingIntoLowCeilingStopsTheAscent()
	{
		CursorObject player = player(0, 0, 0);
		Collider ceiling = quad(new Vertex(-500, 55, -500), new Vertex(500, 55, -500),
			new Vertex(500, 55, 500), new Vertex(-500, 55, 500));
		List<MapObject> scene = List.of(floor().get(0), ceiling);
		player.startInputJump();
		float maxY = 0;
		for (int i = 0; i < 60; i++) {
			advance(player, scene, 1, TICK);
			maxY = Math.max(maxY, player.getSimulationPosition().y);
		}
		assertTrue(maxY <= 55 - 37 + 1, "Head must remain below the ceiling: " + maxY);
		assertEquals(0, player.getSimulationPosition().y, 0.001);
	}

	@Test
	void spinMovesFasterButCannotPassThroughThinWalls()
	{
		Collider wall = quad(new Vertex(80, 0, -500), new Vertex(80, 0, 500),
			new Vertex(80, 200, 500), new Vertex(80, 200, -500));
		List<MapObject> scene = List.of(floor().get(0), wall);
		press(KeyEvent.VK_D);
		press(KeyEvent.VK_SHIFT);
		CursorObject runner = player(0, 0, 0);
		CursorObject spinner = player(0, 0, 0);
		spinner.startInputSpin();
		spinner.endInputSpin();
		advance(runner, floor(), 8, TICK);
		advance(spinner, floor(), 8, TICK);
		assertTrue(spinner.getSimulationPosition().x > runner.getSimulationPosition().x * 1.5);
		spinner.setPosition(new Vector3f());
		spinner.startInputSpin();
		for (int i = 0; i < 60; i++) {
			advance(spinner, scene, 1, TICK);
			assertTrue(spinner.getSimulationPosition().x <= 80 - 13 + 0.01, "Spin must stop at the wall");
		}
		assertTrue(spinner.getSimulationPosition().x > 60);
	}

	@Test
	void canStepUpAndDownLowPlatforms()
	{
		List<MapObject> scene = List.of(ground(-500, 0, 0), ground(0, 80, 8), ground(80, 500, 0));
		CursorObject player = player(-40, 0, 0);
		press(KeyEvent.VK_D);
		boolean reachedPlatform = false;
		for (int i = 0; i < 90; i++) {
			advance(player, scene, 1, TICK);
			Vector3f pos = player.getSimulationPosition();
			assertTrue(pos.y >= -0.001);
			if (pos.x > 20 && pos.x < 60 && Math.abs(pos.y - 8) < 0.001)
				reachedPlatform = true;
		}
		assertTrue(reachedPlatform, "Player should settle on the raised platform");
		assertTrue(player.getSimulationPosition().x > 100);
		assertEquals(0, player.getSimulationPosition().y, 0.001);
	}

	@Test
	void fallingOffAnIsolatedStepReturnsToSafeGround()
	{
		List<MapObject> scene = List.of(ground(-100, 0, 0), ground(0, 2, 8));
		CursorObject player = player(-25, 0, 0);
		press(KeyEvent.VK_D);
		boolean fell = false;
		boolean recovered = false;
		for (int i = 0; i < 110; i++) {
			advance(player, scene, 1, TICK);
			Vector3f pos = player.getSimulationPosition();
			fell |= pos.y < -100;
			if (fell && pos.y >= 0)
				recovered = true;
		}
		assertTrue(fell);
		assertTrue(recovered, "Stepping off a narrow platform must not disable void recovery");
	}

	@Test
	void teleportResetsMomentumAndRenderedPosition()
	{
		CursorObject player = player(0, 0, 0);
		press(KeyEvent.VK_D);
		player.startInputSpin();
		advance(player, floor(), 4, TICK);
		keyboard.reset();
		player.setPosition(new Vector3f(100, 0, 20));
		assertEquals(100, player.getPosition().x);
		assertEquals(20, player.getPosition().z);
		advance(player, floor(), 4, TICK);
		assertEquals(100, player.getSimulationPosition().x);
		assertEquals(0, player.getSimulationPosition().y);
	}

	@Test
	void focusLossStopsWalkingAndClearsQueuedJumpAndHover()
	{
		CursorObject player = player(0, 0, 0);
		press(KeyEvent.VK_D);
		advance(player, floor(), 4, TICK);
		float x = player.getSimulationPosition().x;
		player.startInputJump();
		press(KeyEvent.VK_K);
		player.tickSimulation(keyboard, floor(), () -> 0, TICK, false, true, dt -> {});
		assertEquals(x, player.getSimulationPosition().x);
		assertEquals(0, player.getSimulationPosition().y);
		keyboard.reset();
		advance(player, floor(), 4, TICK);
		assertEquals(0, player.getSimulationPosition().y);
	}

	@Test
	void cameraRelativeMovementUsesEachPhysicsTickEvenDuringFrameDrops()
	{
		press(KeyEvent.VK_W);
		Vector3f expected = null;
		for (int fps : new int[] { 15, 30, 60, 120 }) {
			CursorObject player = player(0, 0, 0);
			AtomicInteger tick = new AtomicInteger();
			for (int i = 0; i < fps; i++)
				player.tickSimulation(keyboard, floor(), () -> tick.get() * 3, 1.0 / fps, true, true, dt -> tick.incrementAndGet());
			if (expected == null)
				expected = new Vector3f(player.getSimulationPosition());
			assertEquals(expected.x, player.getSimulationPosition().x, 0.001);
			assertEquals(expected.z, player.getSimulationPosition().z, 0.001);
		}
	}

	@Test
	void hoverUpdatesCameraAndPlayerTogether()
	{
		CursorObject player = player(0, 0, 0);
		CameraController camera = new CameraController();
		camera.reset(new Vector3f());
		camera.update(null, player.getSimulationPosition(), true, 0);
		press(KeyEvent.VK_K);
		player.tickSimulation(keyboard, floor(), () -> 0, TICK / 2, true, true,
			dt -> camera.update(null, player.getSimulationPosition(), player.allowVerticalCameraMovement(), player.getCameraYInterpRate(), dt));
		assertEquals(2, player.getPosition().y, 0.001);
		assertEquals(1, player.getSimulationInterpolation());
		assertEquals(camera.getPosition().y, camera.getPosition(player.getSimulationInterpolation()).y);
	}

	private CursorObject player(float x, float y, float z)
	{
		CursorObject player = new CursorObject(new Vector3f(x, y, z), false);
		player.startPreviewMode();
		return player;
	}

	private void advance(CursorObject player, List<MapObject> scene, int frames, double deltaTime)
	{
		for (int i = 0; i < frames; i++)
			player.tickSimulation(keyboard, scene, () -> 0, deltaTime, true, true, dt -> {});
	}

	private void press(int key)
	{
		keyboard.keyPressed(new KeyEvent(inputSource, KeyEvent.KEY_PRESSED, 0, 0, key, KeyEvent.CHAR_UNDEFINED));
	}

	private static List<MapObject> floor()
	{
		return List.of(ground(-1000, 1000, 0));
	}

	private static Collider ground(int minX, int maxX, int y)
	{
		return quad(new Vertex(minX, y, -1000), new Vertex(minX, y, 1000),
			new Vertex(maxX, y, 1000), new Vertex(maxX, y, -1000));
	}

	private static Collider quad(Vertex a, Vertex b, Vertex c, Vertex d)
	{
		BasicMesh mesh = new BasicMesh();
		mesh.batch.triangles.add(new Triangle(a, b, c));
		mesh.batch.triangles.add(new Triangle(a, c, d));
		Collider collider = Collider.create(mesh, "test collision");
		collider.recalculateAABB();
		return collider;
	}
}
