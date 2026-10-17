package game.map.editor;

import static app.Directories.*;
import static java.lang.Math.toRadians;

import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import common.BaseCamera;
import common.KeyboardInput;
import common.Vector3f;
import common.commands.AbstractCommand;

import game.map.Axis;
import game.map.BoundingBox;
import game.map.Map;
import game.map.MapObject;
import game.map.MutableAngle;
import game.map.MutableAngle.AngleBackup;
import game.map.MutablePoint;
import game.map.MutablePoint.PointBackup;
import game.map.ReversibleTransform;
import game.map.editor.camera.MapEditViewport;
import game.map.editor.camera.OrthographicViewport;
import game.map.editor.render.RenderMode;
import game.map.editor.render.Renderer;
import game.map.editor.render.RenderingOptions;
import game.map.editor.render.ShadowRenderer.RenderableShadow;
import game.map.editor.render.SortedRenderable;
import game.map.editor.selection.PickRay;
import game.map.editor.selection.PickRay.Channel;
import game.map.editor.selection.PickRay.PickHit;
import game.map.editor.selection.Trace;
import game.map.hit.Collider;
import game.map.marker.Marker;
import game.map.shape.TransformMatrix;
import game.sprite.Sprite;
import game.sprite.SpriteLoader;
import game.sprite.SpriteLoader.AnimMetadata;
import game.sprite.SpriteLoader.SpriteSet;
import renderer.buffers.LineRenderQueue;
import renderer.buffers.PointRenderQueue;
import renderer.shaders.RenderState;
import renderer.shaders.RenderState.PolygonMode;
import renderer.shaders.ShaderManager;
import renderer.shaders.scene.LineShader;
import util.Logger;
import util.MathUtil;
import util.identity.IdentityHashSet;

public class CursorObject extends EditorObject
{
	private static final Vector3f DEFAULT_SIZE = new Vector3f(10.0f, 50.0f, 5.0f);
	private List<GuideSprite> guides;
	private int listPos = 0;

	private static final double SPRITE_TICK_RATE = 1.0 / 30.0;
	private double spriteTime = 0.0;

	private MutablePoint position;
	private MutableAngle yaw;

	private Vector3f previewPos;
	private Vector3f previousPlayerPos;
	private Vector3f renderPlayerPos;
	private boolean preview = false;
	private boolean dragging = false;

	// play in editor fields

	public static boolean showDebugTraces = false;
	public static List<Trace> debugTraces = new LinkedList<>();

	private static final float COLLISION_HEIGHT = 37;
	private static final float COLLISION_RADIUS = 13;

	private static final double PLAYER_TICK_RATE = 1.0 / 30.0;
	private static final float PLAYER_WALK_SPEED = 2.0f;
	private static final float PLAYER_RUN_SPEED = 4.0f;
	private static final float WALK_INPUT_MAGNITUDE = 50.0f;
	private static final float RUN_INPUT_MAGNITUDE = 70.0f;
	private static final float RUN_THRESHOLD = 55.0f;
	private static final float INPUT_BUMP_SCALE = 0.03125f;
	private static final float GROUNDED_INPUT_SCALE = 0.25f;
	private static final float STEP_UP_HEIGHT = 6.0f;
	private static final float MAX_JUMP_SPEED = 32.0f;
	private static final float MAX_LATERAL_STEP = 4.0f;

	private static final int SPIN_INITIAL_TIME = 25;
	private static final int SPIN_FULL_SPEED_TIME = 15;
	private static final float SPIN_RATE = 40.0f;
	private static final float SPIN_SPEED_SCALE = 2.0f;
	private static final float SPIN_FRICTION_SCALE = 0.5f;
	private static final int SPIN_HISTORY_SIZE = 5;
	private static final float SPIN_BLUR_ALPHA = 64.0f / 255.0f;

	private static final float[] JUMP = { 15.7566f, -7.38624f, 3.44694f, -0.75f };
	private static final float[] FALL = { 0.154343f, -0.35008f, -0.182262f, 0.01152f };
	private static final float[] STEP_UP = { 17.7566f, -11.3862f, 3.5f, -0.75f };
	private float[] gravityIntegrator = new float[4];
	private double playerTime = 0.0;

	private float faceAngleGoal = 0;
	private float faceAngle = 0;

	private float movementInputMagnitude = 0;
	private float movementSpeed = 0;
	private double moveYaw = 0;
	private double targetYaw = 0;
	private float currentSpeed = 0;

	private boolean lastFloorSloped = false;

	private enum PlayerState
	{
		Idle, Walk, Run, Jump, Hop, Falling, StepDown, Land, StepDownLand, Spin, StepUp
	}

	private PlayerState actionState = PlayerState.Idle;
	private PlayerState previousActionState = PlayerState.Idle;
	private PlayerState stepUpReturnState = PlayerState.Idle;
	private boolean actionStateChanged = true;
	private int actionSubstate = 0;
	private int timeInAir = 0;
	private boolean jumping = false;
	private boolean falling = false;
	private boolean hovering = false;

	private boolean jumpInput = false;
	private boolean jumpPressed = false;
	private boolean spinInput = false;
	private boolean spinPressed = false;

	private boolean wallContact = false;
	private int currentStateTime = 0;
	private int spinCountdown = 0;
	private int spinHitWallTime = 0;
	private float spinInputMagnitude = 0;
	private float spinRate = SPIN_RATE;
	private boolean bufferedSpin = false;
	private PlayerState bufferedSpinPreviousState = PlayerState.Idle;
	private float bufferedSpinInputMagnitude = 0;
	private double bufferedSpinYaw = 0;
	private final float[] spinHistoryY = new float[SPIN_HISTORY_SIZE];
	private final float[] spinHistoryAngle = new float[SPIN_HISTORY_SIZE];
	private int spinHistoryPos = 0;

	private boolean useBack = false;
	private PickHit shadowHit = null;

	// void falling prevention
	private Vector3f lastGroundPos = new Vector3f();
	private float fallTime = 0.0f;

	public CursorObject(Vector3f initialPosition)
	{
		this(initialPosition, true);
	}

	// Allows the simulation to run without loading project sprite assets.
	CursorObject(Vector3f initialPosition, boolean loadGuideSprites)
	{
		guides = new LinkedList<>();
		if (loadGuideSprites)
			loadGuides();

		position = new MutablePoint(initialPosition);
		yaw = new MutableAngle(0, Axis.Y, true);

		AABB = new BoundingBox();
		recalculateAABB();

		previewPos = new Vector3f(initialPosition);
		previousPlayerPos = new Vector3f(initialPosition);
		renderPlayerPos = new Vector3f(initialPosition);
	}

	@Override
	public void initialize()
	{}

	private void loadGuides()
	{
		File f = new File(PROJ_CFG + FN_EDITOR_GUIDES);

		if (!f.exists())
			f = new File(DATABASE_EDITOR + FN_EDITOR_GUIDES);

		if (!f.exists())
			Logger.logWarning("Cannot find " + FN_EDITOR_GUIDES);
		else
			guides = readJSON(f);

		Logger.log("Loaded " + guides.size() + " guide sprites for the 3D cursor.");
	}

	public Vector3f getPosition()
	{
		if (preview)
			return renderPlayerPos;
		else
			return position.getVector();
	}

	public Vector3f getSimulationPosition()
	{
		if (preview)
			return previewPos;
		else
			return position.getVector();
	}

	public void setPosition(Vector3f newPos)
	{
		if (preview) {
			position.setTempPosition(newPos.x, newPos.y, newPos.z);
			previewPos.set(newPos.x, newPos.y, newPos.z);
			previousPlayerPos.set(newPos.x, newPos.y, newPos.z);
			renderPlayerPos.set(newPos.x, newPos.y, newPos.z);
			resetPlayerState();
			return;
		}

		if (!dragging)
			MapEditor.execute(new SetPosition(this, newPos));
	}

	public void updateDrag(Vector3f newPos)
	{
		position.setTempPosition(newPos.x, newPos.y, newPos.z);
		previewPos.set(newPos.x, newPos.y, newPos.z);
	}

	public void startPreviewMode()
	{
		if (dragging)
			endDrag();

		previewPos.set(position.getVector());
		preview = true;
		resetPlayerState();
		startTransformation();
	}

	public void endPreviewMode()
	{
		preview = false;
		endTransformation();

		MapEditor.execute(new SetPosition(this, previewPos));
	}

	public void startDrag(Vector3f startPos)
	{
		if (preview)
			return;
		dragging = true;

		startTransformation();
	}

	public void endDrag()
	{
		if (preview || !dragging)
			return;
		dragging = false;

		if (position.isTransforming()) {
			Vector3f endPos = position.getVector();
			endTransformation();
			MapEditor.execute(new SetPosition(this, endPos));
		}
	}

	private void resetPlayerState()
	{
		actionState = PlayerState.Idle;
		previousActionState = PlayerState.Idle;
		stepUpReturnState = PlayerState.Idle;
		actionStateChanged = true;
		actionSubstate = 0;
		timeInAir = 0;
		jumping = false;
		falling = false;
		hovering = false;
		jumpInput = false;
		jumpPressed = false;
		spinInput = false;
		spinPressed = false;
		wallContact = false;
		bufferedSpin = false;
		clearSpinHistory();
		currentSpeed = 0.0f;
		movementInputMagnitude = 0.0f;
		movementSpeed = 0.0f;
		playerTime = 0.0;
		fallTime = 0.0f;
		lastGroundPos.set(previewPos);
		lastFloorSloped = false;
		previousPlayerPos.set(previewPos);
		renderPlayerPos.set(previewPos);
		setGravityParams(FALL);
	}

	private void getMovementInput(KeyboardInput keyboard, float camYaw)
	{
		boolean moveForward = keyboard.isKeyDown(KeyEvent.VK_W);
		boolean moveBackward = keyboard.isKeyDown(KeyEvent.VK_S);
		boolean moveLeft = keyboard.isKeyDown(KeyEvent.VK_A);
		boolean moveRight = keyboard.isKeyDown(KeyEvent.VK_D);

		double dr = 0, df = 0;
		if (moveForward)
			df += 1;
		if (moveBackward)
			df -= 1;
		if (moveLeft)
			dr -= 1;
		if (moveRight)
			dr += 1;

		double norm = Math.sqrt(df * df + dr * dr);
		if (norm <= MathUtil.SMALL_NUMBER) {
			movementInputMagnitude = 0;
			return;
		}
		df /= norm;
		dr /= norm;

		double dx = dr * Math.cos(toRadians(camYaw)) + df * Math.sin(toRadians(camYaw));
		double dz = dr * Math.sin(toRadians(camYaw)) - df * Math.cos(toRadians(camYaw));

		moveYaw = Math.atan2(dz, dx);
		movementInputMagnitude = keyboard.isShiftDown() ? RUN_INPUT_MAGNITUDE : WALK_INPUT_MAGNITUDE;
	}

	public void startInputJump()
	{
		if (!jumpInput)
			jumpPressed = true;
		jumpInput = true;
	}

	public void endInputJump()
	{
		jumpInput = false;
	}

	public void startInputSpin()
	{
		if (!spinInput)
			spinPressed = true;
		spinInput = true;
	}

	public void endInputSpin()
	{
		spinInput = false;
	}

	private boolean checkLateralCollision(List<MapObject> candidates, float moveDist, boolean airborne)
	{
		if (moveDist == 0.0f) {
			checkSurroundingCollision(candidates, (airborne ? 1.0f : 0.286f) * COLLISION_HEIGHT);
			return false;
		}

		boolean pushingAgainstWall = false;
		float remaining = moveDist;
		while (remaining > 0.0f) {
			float step = Math.min(remaining, MAX_LATERAL_STEP);
			pushingAgainstWall |= checkLateralCollisionStep(candidates, step, airborne);
			remaining -= step;
		}
		return pushingAgainstWall;
	}

	private boolean checkLateralCollisionStep(List<MapObject> candidates, double moveDist, boolean airborne)
	{
		boolean pushingAgainstWall = false;

		if (moveDist != 0.0) {
			Vector3f updated = new Vector3f(previewPos.x, previewPos.y, previewPos.z);

			Vector3f lower = new Vector3f(previewPos.x, previewPos.y + (airborne ? 0.0f : 10.01f), previewPos.z);
			Vector3f upper = new Vector3f(previewPos.x, previewPos.y + 0.75f * COLLISION_HEIGHT, previewPos.z);
			Vector3f forward = new Vector3f((float) Math.cos(moveYaw), 0.0f, (float) Math.sin(moveYaw));
			PickRay forwardRay = new PickRay(Channel.COLLISION, lower, forward, false);
			PickHit forwardHit = Map.pickObjectFromSet(forwardRay, candidates, false);

			if (showDebugTraces)
				debugTraces.add(new Trace(forwardRay, forwardHit, COLLISION_RADIUS));

			double traceLength = moveDist + COLLISION_RADIUS;
			if (forwardHit.dist >= traceLength) {
				forwardRay = new PickRay(Channel.COLLISION, upper, forward, false);
				forwardHit = Map.pickObjectFromSet(forwardRay, candidates, false);

				if (showDebugTraces)
					debugTraces.add(new Trace(forwardRay, forwardHit, COLLISION_RADIUS));
			}
			if (forwardHit.dist < traceLength) {
				pushingAgainstWall = true;
				// correct when always adding motion
				//	double offset = forwardHit.dist - traceLength;
				//	updated.x += offset * forward.x;
				//	updated.z += offset * forward.z;

				updated.x += (forwardHit.dist - COLLISION_RADIUS) * forward.x;
				updated.z += (forwardHit.dist - COLLISION_RADIUS) * forward.z;
				addPerp(updated, forward, moveDist, forwardHit.norm);

			}
			else {
				updated.x += moveDist * forward.x;
				updated.z += moveDist * forward.z;
			}

			double whiskerAngle = Math.toRadians(35.0);
			Vector3f leftDir = new Vector3f((float) Math.cos(moveYaw - whiskerAngle), 0.0f, (float) Math.sin(moveYaw - whiskerAngle));
			Vector3f rightDir = new Vector3f((float) Math.cos(moveYaw + whiskerAngle), 0.0f, (float) Math.sin(moveYaw + whiskerAngle));
			Vector3f whiskerStart = new Vector3f(updated.x, updated.y + ((airborne ? 1.0f : 0.286f) * COLLISION_HEIGHT), updated.z);

			PickRay leftRay = new PickRay(Channel.COLLISION, whiskerStart, leftDir, false);
			PickRay rightRay = new PickRay(Channel.COLLISION, whiskerStart, rightDir, false);

			PickHit leftHit = Map.pickObjectFromSet(leftRay, candidates, false);
			PickHit rightHit = Map.pickObjectFromSet(rightRay, candidates, false);

			if (showDebugTraces) {
				debugTraces.add(new Trace(leftRay, leftHit, COLLISION_RADIUS));
				debugTraces.add(new Trace(rightRay, rightHit, COLLISION_RADIUS));
			}

			boolean hitWhiskerLeft = (leftHit.dist < COLLISION_RADIUS);
			boolean hitWhiskerRight = (rightHit.dist < COLLISION_RADIUS);

			Vector3f leftPos = new Vector3f(updated);
			if (hitWhiskerLeft) {
				leftPos.x += (leftHit.dist - COLLISION_RADIUS) * leftDir.x;
				leftPos.z += (leftHit.dist - COLLISION_RADIUS) * leftDir.z;
			}
			Vector3f rightPos = new Vector3f(updated);
			if (hitWhiskerRight) {
				rightPos.x += (rightHit.dist - COLLISION_RADIUS) * rightDir.x;
				rightPos.z += (rightHit.dist - COLLISION_RADIUS) * rightDir.z;
			}

			// this is pretty much broken in-game
			// the ONLY thing it does is stop motion when both whiskers hit
			// because the WRONG positions are used when only one is!
			if (hitWhiskerLeft) {
				if (hitWhiskerRight) {
					// hit both, dont update previewPos
				}
				else {
					// only left whisker, note: right pos
					previewPos.x = rightPos.x;
					previewPos.z = rightPos.z;
				}
			}
			else {
				if (hitWhiskerRight) {
					// only right whisker, note: left pos
					previewPos.x = leftPos.x;
					previewPos.z = leftPos.z;
				}
				else {
					// hit neither, forward motion is fine
					previewPos.x = updated.x;
					previewPos.z = updated.z;
				}
			}

			checkSurroundingCollision(candidates, ((airborne ? 1.0f : 0.286f) * COLLISION_HEIGHT));

			//	only run for entity hit boxes, i think.
			//	if(!hitSomething)
			//		checkSurroundingCollision(map, candidates, ((0.75f + 0.286f) * COLLISION_HEIGHT));
		}
		return pushingAgainstWall;
	}

	private void addPerp(Vector3f pos, Vector3f dir, double length, Vector3f normalDir)
	{
		/*
		// correct way:
		double nx = normalDir.x;
		double nz = normalDir.z;
		double len = Math.sqrt(nx*nx + nz*nz);
		nx = nx/len;
		nz = nz/len;
		double dot = nx*dir.x + nz*dir.z;
		// okay way:
		double dot = Vector3f.dot(dir, normalDir);
		
		pos.x += length * (dir.x - normalDir.x * dot);
		pos.z += length * (dir.z - normalDir.z * dot);
		 */

		// their method does NOT normalize the input move vector!
		double mx = dir.x * length;
		double mz = dir.z * length;

		if (normalDir != null) {
			double dot = mx * normalDir.x + mz * normalDir.z;
			pos.x += (mx - dot * normalDir.x) * 0.5;
			pos.z += (mz - dot * normalDir.z) * 0.5;
		}
	}

	private boolean checkSurroundingCollision(List<MapObject> candidates, float offsetY)
	{
		boolean hitSomething = false;

		Vector3f[] traceDirs = new Vector3f[4];
		traceDirs[0] = PickRay.NegZ;
		traceDirs[1] = PickRay.PosX;
		traceDirs[2] = PickRay.PosZ;
		traceDirs[3] = PickRay.NegX;

		for (Vector3f traceDir : traceDirs) {
			Vector3f origin = new Vector3f(previewPos.x, previewPos.y + offsetY, previewPos.z);
			PickRay ray = new PickRay(Channel.COLLISION, origin, traceDir, false);
			PickHit hit = Map.pickObjectFromSet(ray, candidates, false);

			if (hit.dist < COLLISION_RADIUS) {
				previewPos.x += (hit.dist - COLLISION_RADIUS) * traceDir.x;
				previewPos.z += (hit.dist - COLLISION_RADIUS) * traceDir.z;
				hitSomething = true;
			}

			if (showDebugTraces)
				debugTraces.add(new Trace(ray, hit, COLLISION_RADIUS));
		}

		return hitSomething;
	}

	private GroundHit checkForGround(List<MapObject> candidates, float camYaw)
	{
		double angle = Math.toRadians(camYaw + faceAngle - 90.0);
		float dx = (float) Math.cos(angle) * 2.0f * COLLISION_RADIUS * 0.28f;
		float dy = (COLLISION_HEIGHT * 0.5f);
		float dz = (float) Math.sin(angle) * 2.0f * COLLISION_RADIUS * 0.28f;

		Vector3f start = previewPos;
		PickRay[] floorTraces = new PickRay[5];
		floorTraces[0] = new PickRay(Channel.COLLISION, new Vector3f(start.x + dx, start.y + dy, start.z + dz), PickRay.DOWN, false);
		floorTraces[1] = new PickRay(Channel.COLLISION, new Vector3f(start.x - dx, start.y + dy, start.z - dz), PickRay.DOWN, false);
		// these two traces are bugged! they should be (+dz, -dx) and (-dz, +dx)
		floorTraces[2] = new PickRay(Channel.COLLISION, new Vector3f(start.x + dz, start.y + dy, start.z + dx), PickRay.DOWN, false);
		floorTraces[3] = new PickRay(Channel.COLLISION, new Vector3f(start.x - dz, start.y + dy, start.z - dx), PickRay.DOWN, false);
		// central trace is last
		floorTraces[4] = new PickRay(Channel.COLLISION, new Vector3f(start.x, start.y + dy, start.z), PickRay.DOWN, false);

		float minDist = Float.MAX_VALUE;
		PickHit minHit = null;
		int minIndex = -1;

		PickHit[] hits = new PickHit[floorTraces.length];
		for (int i = 0; i < floorTraces.length; i++) {
			hits[i] = Map.pickObjectFromSet(floorTraces[i], candidates, false);
			if (hits[i].dist <= minDist) // note: operator includes ==, we pick LAST match
			{
				minDist = hits[i].dist;
				minHit = hits[i];
				minIndex = i;
			}
		}

		if (showDebugTraces) {
			for (int i = 0; i < floorTraces.length; i++) {
				if (minHit != null && hits[i] == minHit)
					debugTraces.add(new Trace(floorTraces[i], hits[i], dy, new Vector3f(0.0f, 1.0f, 0.0f)));
				else
					debugTraces.add(new Trace(floorTraces[i], hits[i], dy));
			}
		}

		if (minHit == null || minHit.missed())
			return null;

		return new GroundHit(minHit, start.y - minHit.point.y, minIndex == 4);
	}

	private float checkForCeiling(List<MapObject> candidates, float camYaw)
	{
		double angle = Math.toRadians(camYaw + faceAngle - 90.0);
		float dx = (float) Math.cos(angle) * 2.0f * COLLISION_RADIUS * 0.30f;
		float dy = (COLLISION_HEIGHT * 0.5f);
		float dz = (float) Math.sin(angle) * 2.0f * COLLISION_RADIUS * 0.30f;

		Vector3f start = previewPos;
		PickRay[] ceilingTraces = new PickRay[4];
		ceilingTraces[0] = new PickRay(Channel.COLLISION, new Vector3f(start.x + dx, start.y + dy, start.z + dz), PickRay.UP, false);
		ceilingTraces[1] = new PickRay(Channel.COLLISION, new Vector3f(start.x - dx, start.y + dy, start.z - dz), PickRay.UP, false);
		// these two traces are bugged! they should be (+dz, -dx) and (-dz, +dx)
		ceilingTraces[2] = new PickRay(Channel.COLLISION, new Vector3f(start.x + dz, start.y + dy, start.z + dx), PickRay.UP, false);
		ceilingTraces[3] = new PickRay(Channel.COLLISION, new Vector3f(start.x - dz, start.y + dy, start.z - dx), PickRay.UP, false);

		float minDist = Float.MAX_VALUE;
		PickHit minHit = null;

		PickHit[] hits = new PickHit[ceilingTraces.length];
		for (int i = 0; i < ceilingTraces.length; i++) {
			hits[i] = Map.pickObjectFromSet(ceilingTraces[i], candidates, false);
			if (hits[i].dist <= minDist) // note: operator includes ==, we pick LAST match
			{
				minDist = hits[i].dist;
				minHit = hits[i];
			}
		}

		if (showDebugTraces) {
			for (int i = 0; i < ceilingTraces.length; i++) {
				if (hits[i] == minHit)
					debugTraces.add(new Trace(ceilingTraces[i], hits[i], dy, new Vector3f(0.0f, 1.0f, 0.0f)));
				else
					debugTraces.add(new Trace(ceilingTraces[i], hits[i], dy));
			}
		}

		return (minHit == null || minHit.missed()) ? Float.MAX_VALUE : minHit.dist;
	}

	private void setGravityParams(float[] params)
	{
		for (int i = 0; i < 4; i++)
			gravityIntegrator[i] = params[i];
	}

	private float integrateGravity()
	{
		gravityIntegrator[2] += gravityIntegrator[3];
		gravityIntegrator[1] += gravityIntegrator[2];
		gravityIntegrator[0] += gravityIntegrator[1];
		return gravityIntegrator[0];
	}

	public boolean allowVerticalCameraMovement()
	{
		return preview && ((!jumping && !falling) || hovering);
	}

	public float getCameraYInterpRate()
	{
		if (hovering)
			return 7.2f;

		if (lastFloorSloped) {
			switch (actionState) {
				case Jump:
				case Falling:
					return 32.0f;
				default:
					return 3.0f;
			}
		}

		switch (actionState) {
			case Walk:
			case Run:
			case Jump:
				return 7.2f;
			default:
				return 24.0f;
		}
	}

	public float getSimulationInterpolation()
	{
		return hovering ? 1.0f : (float) (playerTime / PLAYER_TICK_RATE);
	}

	private void transitionTo(PlayerState state)
	{
		previousActionState = actionState;
		actionState = state;
		actionStateChanged = true;
	}

	private boolean checkInputJump()
	{
		if (!jumpPressed)
			return false;

		transitionTo(PlayerState.Jump);
		return true;
	}

	private boolean checkInputSpin()
	{
		if (!spinPressed && !bufferedSpin)
			return false;

		boolean wasBuffered = bufferedSpin;
		PlayerState bufferedPreviousState = bufferedSpinPreviousState;
		boolean bufferedMovement = bufferedSpinInputMagnitude != 0.0f;
		transitionTo(PlayerState.Spin);
		if (wasBuffered) {
			previousActionState = bufferedMovement ? bufferedPreviousState : PlayerState.Idle;
			movementInputMagnitude = bufferedSpinInputMagnitude;
			moveYaw = bufferedSpinYaw;
		}
		return true;
	}

	private void updateActionState(List<MapObject> candidates, float cameraYaw, boolean pushedAgainstWallLastTick)
	{
		for (int iteration = 0; iteration < 32; iteration++) {
			boolean entered = actionStateChanged;
			actionStateChanged = false;

			switch (actionState) {
				case Idle:
					actionUpdateIdle(entered);
					break;
				case Walk:
					actionUpdateWalk(entered);
					break;
				case Run:
					actionUpdateRun(entered);
					break;
				case Jump:
				case Hop:
					actionUpdateJump(entered);
					break;
				case Falling:
					actionUpdateFalling(entered);
					break;
				case StepDown:
					actionUpdateStepDown(entered);
					break;
				case Land:
					actionUpdateLand(entered);
					break;
				case StepDownLand:
					actionUpdateStepDownLand(entered);
					break;
				case Spin:
					actionUpdateSpin(entered, pushedAgainstWallLastTick);
					break;
				case StepUp:
					actionUpdateStepUp(entered, candidates, cameraYaw);
					break;
			}

			if (!actionStateChanged)
				return;
		}

		throw new IllegalStateException("Player action dispatch did not settle at " + actionState);
	}

	private void actionUpdateIdle(boolean entered)
	{
		if (checkInputSpin())
			return;

		if (entered) {
			actionSubstate = 0;
			timeInAir = 0;
			currentSpeed = 0.0f;
			jumping = false;
			falling = false;
		}

		if (checkInputJump()) {
			if (movementInputMagnitude != 0.0f)
				targetYaw = moveYaw;
			return;
		}

		if (movementInputMagnitude != 0.0f) {
			targetYaw = moveYaw;
			transitionTo(PlayerState.Walk);
		}
	}

	private void actionUpdateWalk(boolean entered)
	{
		if (checkInputSpin())
			return;

		if (entered)
			currentSpeed = PLAYER_WALK_SPEED;
		if (checkInputJump())
			return;
		if (movementInputMagnitude == 0.0f) {
			transitionTo(PlayerState.Idle);
			return;
		}

		targetYaw = moveYaw;
		if (movementInputMagnitude > RUN_THRESHOLD)
			transitionTo(PlayerState.Run);
	}

	private void actionUpdateRun(boolean entered)
	{
		if (checkInputSpin())
			return;

		currentSpeed = PLAYER_RUN_SPEED;
		if (checkInputJump())
			return;
		if (movementInputMagnitude == 0.0f) {
			transitionTo(PlayerState.Idle);
			return;
		}

		targetYaw = moveYaw;
		if (movementInputMagnitude <= RUN_THRESHOLD)
			transitionTo(PlayerState.Walk);
	}

	private void actionUpdateJump(boolean entered)
	{
		if (entered) {
			actionSubstate = 0;
			timeInAir = 0;
			jumping = true;
			falling = false;
			if (actionState == PlayerState.Jump)
				setGravityParams(JUMP);
		}
		timeInAir++;
	}

	private void actionUpdateFalling(boolean entered)
	{
		if (entered) {
			jumping = false;
			falling = true;
		}
		timeInAir++;
	}

	private void actionUpdateStepDown(boolean entered)
	{
		if (entered) {
			jumping = false;
			falling = true;
		}
		timeInAir++;
		checkInputJump();
	}

	private void actionUpdateLand(boolean entered)
	{
		initializeLanding(entered);
		currentSpeed *= 0.6f;
		if (checkInputJump())
			return;
		transitionToLocomotion();
	}

	private void actionUpdateStepDownLand(boolean entered)
	{
		initializeLanding(entered);
		currentSpeed *= 0.6f;
		if (checkInputJump())
			return;
		if (movementInputMagnitude != 0.0f)
			targetYaw = moveYaw;
		transitionTo(movementInputMagnitude > RUN_THRESHOLD ? PlayerState.Run : PlayerState.Walk);
	}

	private void initializeLanding(boolean entered)
	{
		if (entered) {
			actionSubstate = 0;
			timeInAir = 0;
			jumping = false;
			falling = false;
		}
		actionSubstate++;
	}

	private void transitionToLocomotion()
	{
		if (movementInputMagnitude == 0.0f) {
			transitionTo(PlayerState.Idle);
			return;
		}

		targetYaw = moveYaw;
		transitionTo(movementInputMagnitude > RUN_THRESHOLD ? PlayerState.Run : PlayerState.Walk);
	}

	private void actionUpdateSpin(boolean entered, boolean pushedAgainstWallLastTick)
	{
		boolean firstCall = entered;
		if (entered) {
			currentStateTime = 0;
			actionSubstate = 0;
			bufferedSpin = false;
			spinHitWallTime = 0;
			spinCountdown = SPIN_INITIAL_TIME;
			spinInputMagnitude = movementInputMagnitude;
			if (movementInputMagnitude != 0.0f)
				targetYaw = moveYaw;
			spinRate = faceAngleGoal < 90.0f ? -SPIN_RATE : SPIN_RATE;
			clearSpinHistory();
		}
		recordSpinHistory();

		if (!firstCall && checkInputJump()) {
			if (movementInputMagnitude != 0.0f)
				targetYaw = moveYaw;
			return;
		}

		if (spinCountdown < 11 && spinPressed) {
			bufferedSpin = true;
			bufferedSpinPreviousState = previousActionState;
			bufferedSpinInputMagnitude = movementInputMagnitude;
			bufferedSpinYaw = moveYaw;
		}

		if (actionSubstate >= 2) {
			currentStateTime--;
			if (currentStateTime == 0)
				transitionTo(PlayerState.Idle);
			currentSpeed = 0.0f;
			return;
		}

		if (actionSubstate == 0 && pushedAgainstWallLastTick) {
			spinHitWallTime++;
			if (spinHitWallTime >= 10)
				actionSubstate = 1;
		}

		float speedModifier;
		if (currentStateTime <= SPIN_FULL_SPEED_TIME) {
			speedModifier = spinInputMagnitude != 0.0f ? SPIN_SPEED_SCALE : 0.0f;
		}
		else {
			speedModifier = SPIN_SPEED_SCALE - (currentStateTime - SPIN_FULL_SPEED_TIME - 1) * SPIN_FRICTION_SCALE;
			if (speedModifier < 0.1f)
				speedModifier = 0.1f;
			if (spinInputMagnitude == 0.0f)
				speedModifier = 0.0f;
		}

		currentStateTime++;
		switch (previousActionState) {
			case Idle:
				currentSpeed = movementInputMagnitude != 0.0f ? PLAYER_RUN_SPEED * speedModifier : 0.0f;
				break;
			case Walk:
			case Run:
				currentSpeed = PLAYER_RUN_SPEED * speedModifier;
				break;
			default:
				break;
		}

		if (actionSubstate == 0) {
			spinCountdown--;
			if (spinCountdown > 0) {
				if (currentStateTime >= 2)
					faceAngle = wrapDegrees(faceAngle + spinRate);
				return;
			}
			actionSubstate = 1;
		}

		if (actionSubstate == 1) {
			float previousFacing = faceAngle;
			faceAngle += spinRate;
			if (bufferedSpin) {
				finishSpinRotation();
			}
			else if (previousFacing < faceAngle) {
				if (faceAngle >= 180.0f && previousFacing < 180.0f) {
					faceAngle = 180.0f;
					finishSpinRotation();
				}
			}
			else if (faceAngle <= 0.0f && previousFacing < 90.0f) {
				faceAngle = 0.0f;
				finishSpinRotation();
			}
			faceAngle = wrapDegrees(faceAngle);
		}
	}

	private void finishSpinRotation()
	{
		currentStateTime = 2;
		actionSubstate = 2;
	}

	private void clearSpinHistory()
	{
		spinHistoryPos = 0;
		for (int i = 0; i < SPIN_HISTORY_SIZE; i++) {
			spinHistoryY[i] = Float.NaN;
			spinHistoryAngle[i] = 180.0f;
		}
	}

	private void recordSpinHistory()
	{
		spinHistoryY[spinHistoryPos] = previewPos.y;
		spinHistoryAngle[spinHistoryPos] = faceAngle;
		spinHistoryPos = (spinHistoryPos + 1) % SPIN_HISTORY_SIZE;
	}

	private int getSpinHistoryIndex(int lag)
	{
		int index = spinHistoryPos - lag;
		while (index < 0)
			index += SPIN_HISTORY_SIZE;
		return index % SPIN_HISTORY_SIZE;
	}

	private boolean isSpinBlurActive()
	{
		return preview && actionState == PlayerState.Spin && actionSubstate < 2;
	}

	private static float wrapDegrees(float angle)
	{
		while (angle < 0.0f)
			angle += 360.0f;
		while (angle >= 360.0f)
			angle -= 360.0f;
		return angle;
	}

	private void startFalling(PlayerState state)
	{
		transitionTo(state);
		setGravityParams(FALL);
	}

	private void physUpdateJump()
	{
		if (timeInAir != 0 && actionState == PlayerState.Hop) {
			gravityIntegrator[0] -= 4.5f;
			previewPos.y += gravityIntegrator[0];
			if (gravityIntegrator[0] <= 0.0f) {
				setGravityParams(FALL);
				integrateGravity();
				transitionTo(PlayerState.Falling);
			}
			return;
		}

		if (timeInAir != 0 && !jumpInput) {
			transitionTo(PlayerState.Hop);
			integrateGravity();
		}

		integrateGravity();
		if (gravityIntegrator[0] <= 0.0f) {
			setGravityParams(FALL);
			integrateGravity();
			transitionTo(PlayerState.Falling);
		}
		if (gravityIntegrator[0] > MAX_JUMP_SPEED)
			gravityIntegrator[0] = MAX_JUMP_SPEED;
		previewPos.y += gravityIntegrator[0];
	}

	private void physUpdateFalling(List<MapObject> candidates, float cameraYaw)
	{
		float velocity = integrateGravity();
		GroundHit floor = checkForGround(candidates, cameraYaw);
		if (floor == null || floor.distance > Math.abs(velocity)) {
			previewPos.y += velocity;
			return;
		}

		previewPos.y = floor.hit.point.y;
		updateFloorSlope(floor);
		physPlayerLand();
	}

	private void physPlayerLand()
	{
		boolean steppedDown = actionState == PlayerState.StepDown;
		timeInAir = 0;
		jumping = false;
		falling = false;
		if (movementInputMagnitude != 0.0f) {
			targetYaw = moveYaw;
			transitionTo(movementInputMagnitude > RUN_THRESHOLD ? PlayerState.Run : PlayerState.Walk);
		}
		else {
			transitionTo(steppedDown ? PlayerState.StepDownLand : PlayerState.Land);
		}
	}

	private void actionUpdateStepUp(boolean entered, List<MapObject> candidates, float cameraYaw)
	{
		if (entered) {
			stepUpReturnState = previousActionState;
			actionSubstate = 0;
			timeInAir = 0;
			jumping = false;
			falling = false;
			setGravityParams(STEP_UP);
		}

		integrateGravity();
		previewPos.x += 3.0f * (float) Math.cos(targetYaw);
		previewPos.z += 3.0f * (float) Math.sin(targetYaw);
		movementSpeed = 90.0f;

		if (gravityIntegrator[0] < 0.0f) {
			GroundHit floor = checkForGround(candidates, cameraYaw);
			if (floor != null && floor.distance <= Math.abs(gravityIntegrator[0])) {
				previewPos.y = floor.hit.point.y;
				updateFloorSlope(floor);
				if (stepUpReturnState == PlayerState.Spin)
					transitionToLocomotion();
				else
					transitionTo(stepUpReturnState);
				return;
			}
			startFalling(PlayerState.Falling);
			return;
		}
		previewPos.y += gravityIntegrator[0];
	}

	private void collisionMainLateral(List<MapObject> candidates)
	{
		if (actionState == PlayerState.StepUp) {
			checkSurroundingCollision(candidates, 0.286f * COLLISION_HEIGHT);
			wallContact = false;
			return;
		}
		if (actionState == PlayerState.Land || actionState == PlayerState.StepDownLand) {
			movementSpeed = 0.0f;
			return;
		}

		float moveDist;
		float bump = actionState == PlayerState.Spin ? 0.0f : movementInputMagnitude * INPUT_BUMP_SCALE;
		if (!jumping && !falling)
			bump *= GROUNDED_INPUT_SCALE;
		float mx = bump * (float) Math.cos(moveYaw) + currentSpeed * (float) Math.cos(targetYaw);
		float mz = bump * (float) Math.sin(moveYaw) + currentSpeed * (float) Math.sin(targetYaw);
		moveDist = (float) Math.sqrt(mx * mx + mz * mz);
		if ((jumping || falling) && moveDist > PLAYER_RUN_SPEED) {
			float scale = PLAYER_RUN_SPEED / moveDist;
			mx *= scale;
			mz *= scale;
			moveDist = PLAYER_RUN_SPEED;
		}
		if (moveDist != 0.0f)
			moveYaw = Math.atan2(mz, mx);

		movementSpeed = moveDist * 30.0f;
		wallContact = checkLateralCollision(candidates, moveDist, jumping || falling);
	}

	private void collisionMainAbove(List<MapObject> candidates, float cameraYaw)
	{
		if (!jumping || actionState == PlayerState.Falling || actionState == PlayerState.StepDown)
			return;

		float hitDist = checkForCeiling(candidates, cameraYaw);
		if (hitDist <= Math.abs((COLLISION_HEIGHT * 0.5f) + gravityIntegrator[0])) {
			previewPos.y -= COLLISION_HEIGHT / 10.0f;
			for (int i = 0; i < gravityIntegrator.length; i++)
				gravityIntegrator[i] = 0.0f;
		}
	}

	private void physMainCollisionBelow(List<MapObject> candidates, float cameraYaw)
	{
		if (jumping || falling)
			return;

		GroundHit floor = checkForGround(candidates, cameraYaw);
		if (floor == null) {
			startFalling(PlayerState.Falling);
			return;
		}

		float validFloorDrop = COLLISION_HEIGHT / 7.0f;
		float stepDownDrop = COLLISION_HEIGHT * 2.0f / 7.0f;
		if (floor.distance > validFloorDrop) {
			if (floor.distance <= stepDownDrop && floor.central)
				startFalling(PlayerState.StepDown);
			else
				startFalling(PlayerState.Falling);
			return;
		}

		float floorDelta = -floor.distance;
		if (floorDelta < STEP_UP_HEIGHT) {
			previewPos.y = floor.hit.point.y;
			lastGroundPos.set(previewPos);
			updateFloorSlope(floor);
		}
		else {
			transitionTo(PlayerState.StepUp);
		}
	}

	private void tickPlayer(List<MapObject> candidates, float cameraYaw)
	{
		boolean pushedAgainstWallLastTick = wallContact;
		wallContact = false;
		updateActionState(candidates, cameraYaw, pushedAgainstWallLastTick);
		if (jumping)
			physUpdateJump();
		if (falling)
			physUpdateFalling(candidates, cameraYaw);
		collisionMainLateral(candidates);
		collisionMainAbove(candidates, cameraYaw);
		if (actionState != PlayerState.StepUp)
			physMainCollisionBelow(candidates, cameraYaw);

		jumpPressed = false;
		spinPressed = false;
	}

	private void updateFacing(float cameraYaw, double deltaTime)
	{
		if (actionState != PlayerState.Spin && movementInputMagnitude != 0.0f) {
			float deltaAngle = wrapDegrees((float) Math.toDegrees(moveYaw) - cameraYaw);
			useBack = deltaAngle > 180.0f;
			deltaAngle = wrapDegrees(deltaAngle + 270.0f);
			if (deltaAngle != 0.0f && deltaAngle != 180.0f)
				faceAngleGoal = deltaAngle > 180.0f ? 180.0f : 0.0f;
		}
		if (actionState != PlayerState.Spin)
			faceAngle = MathUtil.interp(faceAngle, faceAngleGoal, 10f, deltaTime);
	}

	private void updateRenderPlayerPosition()
	{
		float alpha = (float) (playerTime / PLAYER_TICK_RATE);
		renderPlayerPos.set(
			MathUtil.lerp(alpha, previousPlayerPos.x, previewPos.x),
			MathUtil.lerp(alpha, previousPlayerPos.y, previewPos.y),
			MathUtil.lerp(alpha, previousPlayerPos.z, previewPos.z));
	}

	private void updateFloorSlope(GroundHit floor)
	{
		Vector3f normal = floor.hit.norm;
		lastFloorSloped = normal != null && (Math.abs(normal.x) > 0.001f || Math.abs(normal.z) > 0.001f);
	}

	public void tickSimulation(KeyboardInput keyboard, Map collisionMap, Map entityMap, MapEditViewport viewport, double deltaTime, boolean hasFocus,
		boolean checkInput, boolean ignoreHiddenColliders, DoubleConsumer simulationStep)
	{
		List<MapObject> candidates = new ArrayList<>();
		for (Collider c : collisionMap.colliderTree) {
			if (c.hasMesh() && (!ignoreHiddenColliders || !c.hidden)
				&& (c.flags.get() & Collider.IGNORE_PLAYER_BIT) == 0)
				candidates.add(c);
		}
		for (Marker m : entityMap.markerTree) {
			if (m.hasCollision())
				candidates.add(m);
		}

		tickSimulation(keyboard, candidates, () -> viewport.camera.getYaw(), deltaTime, hasFocus, checkInput, simulationStep);
	}

	void tickSimulation(KeyboardInput keyboard, List<MapObject> candidates, DoubleSupplier cameraYaw, double deltaTime, boolean hasFocus,
		boolean checkInput, DoubleConsumer simulationStep)
	{
		if (!Double.isFinite(deltaTime) || deltaTime <= 0.0)
			return;
		deltaTime = Math.min(deltaTime, 0.25);
		hovering = false;

		if (checkInput) {
			if (!hasFocus) {
				movementInputMagnitude = 0.0f;
				jumpInput = jumpPressed = spinInput = spinPressed = false;
				bufferedSpin = false;
			}
			else
				getMovementInput(keyboard, (float) cameraYaw.getAsDouble());
		}

		if (checkInput && hasFocus && keyboard.isKeyDown(EditorShortcut.PLAY_IN_EDITOR_HOVER.key)) {
			updateFacing((float) cameraYaw.getAsDouble(), deltaTime);
			float hoverMoveDist = movementInputMagnitude > RUN_THRESHOLD ? PLAYER_RUN_SPEED : PLAYER_WALK_SPEED;
			if (movementInputMagnitude == 0.0f)
				hoverMoveDist = 0.0f;
			movementSpeed = hoverMoveDist * 30.0f;
			checkLateralCollision(candidates, hoverMoveDist * (float) (deltaTime / PLAYER_TICK_RATE), true);
			previewPos.y += (float) (120.0 * deltaTime);
			setGravityParams(FALL);
			if (actionState != PlayerState.Falling)
				transitionTo(PlayerState.Falling);
			jumping = false;
			falling = true;
			fallTime = 0.0f;
			hovering = true;
			playerTime = 0.0;
			previousPlayerPos.set(previewPos);
			renderPlayerPos.set(previewPos);
			simulationStep.accept(deltaTime);
			return;
		}

		playerTime += deltaTime;
		// Absorb floating-point roundoff at exact tick boundaries (for example, 144 Hz).
		while (playerTime + 1e-9 >= PLAYER_TICK_RATE) {
			previousPlayerPos.set(previewPos);
			float yaw = (float) cameraYaw.getAsDouble();
			if (checkInput && hasFocus)
				getMovementInput(keyboard, yaw);
			updateFacing(yaw, PLAYER_TICK_RATE);
			tickPlayer(candidates, yaw);
			playerTime = Math.max(0.0, playerTime - PLAYER_TICK_RATE);

			if (falling) {
				fallTime += PLAYER_TICK_RATE;
				if (fallTime >= 2.0f) {
					previewPos.set(lastGroundPos);
					resetPlayerState();
				}
			}
			else {
				fallTime = 0.0f;
			}

			simulationStep.accept(PLAYER_TICK_RATE);
		}
		updateRenderPlayerPosition();

	}

	public void updateShadow(Map collisionMap, Map entityMap, double deltaTime)
	{
		if (guides.isEmpty()) {
			shadowHit = null;
			return;
		}

		boolean ignoreHiddenColliders = MapEditor.instance().pieIgnoreHiddenColliders;

		List<MapObject> candidates = new ArrayList<>();
		for (Collider c : collisionMap.colliderTree) {
			if (c.hasMesh() && (!ignoreHiddenColliders || !c.hidden)
				&& (c.flags.get() & Collider.IGNORE_PLAYER_BIT) == 0)
				candidates.add(c);
		}
		for (Marker m : entityMap.markerTree) {
			if (m.hasCollision())
				candidates.add(m);
		}

		Vector3f shadowOrigin = new Vector3f(getPosition().x, getPosition().y + COLLISION_HEIGHT / 2, getPosition().z);
		PickRay shadowRay = new PickRay(Channel.COLLISION, shadowOrigin, PickRay.DOWN, false);
		shadowHit = Map.pickObjectFromSet(shadowRay, candidates, false);
	}

	private Vector3f getSize()
	{
		if (guides.size() > 0) {
			GuideSprite guide = guides.get(listPos);
			return new Vector3f(guide.width, guide.height, DEFAULT_SIZE.z);
		}

		return DEFAULT_SIZE;
	}

	public void changeGuide(int dw)
	{
		if (dw == 0)
			return;

		if (guides.size() == 0)
			return;

		GuideSprite current = guides.get(listPos);

		if (current.sprite != null)
			current.sprite.resetAnimation(current.animID);

		if (dw > 0)
			listPos++;
		if (dw < 0)
			listPos--;

		if (listPos >= guides.size())
			listPos = 0;

		if (listPos < 0)
			listPos = guides.size() - 1;

		recalculateAABB();
		MapEditor.instance().selectionManager.currentSelection.updateAABB();
	}

	public void updateAnimation(double deltaTime)
	{
		if (guides.size() == 0 || guides.get(listPos).sprite == null)
			return;

		if (!Double.isFinite(deltaTime) || deltaTime <= 0.0)
			return;
		spriteTime += Math.min(deltaTime, 0.25);
		while (spriteTime >= SPRITE_TICK_RATE) {
			GuideSprite guide = guides.get(listPos);
			guide.sprite.updateAnimation(guide.animID);
			spriteTime -= SPRITE_TICK_RATE;
		}

		GuideSprite primary = guides.get(0);
		if (primary.sprite != null && primary.sprite.metadata.isPlayer && primary.sprite.name.equals("Mario1")) {
			primary.animID = 2;
			if (preview) {
				switch (actionState) {
					case Jump:
						primary.animID = 7;
						break;
					case Hop:
					case Falling:
					case StepDown:
						primary.animID = 8;
						break;
					case Walk:
						primary.animID = 4;
						break;
					case Run:
					case StepUp:
						if (movementSpeed > 0.0)
							primary.animID = 5; // 4 = walk
						break;
					case Spin:
						if (actionSubstate < 2)
							primary.animID = 0x10;
						else
							primary.animID = 2;
						break;
					default:
						break;
				}
			}
		}
	}

	public void render(MapEditViewport view, RenderingOptions opts, Vector3f cameraPos)
	{
		if (view instanceof OrthographicViewport || guides.isEmpty())
			drawBasicCursor();
	}

	public void addRenderables(Collection<SortedRenderable> renderables, MapEditViewport view)
	{
		if (view instanceof OrthographicViewport || guides.isEmpty())
			return;

		if (shadowHit != null && !shadowHit.missed())
			renderables.add(new RenderableShadow(shadowHit.point, shadowHit.norm, shadowHit.dist, false, true, 100.0f));
		renderables.add(new RenderablePlayer(this));
		if (isSpinBlurActive())
			renderables.add(new RenderableSpinBlur(this));
	}

	private void renderPlayer(RenderingOptions opts, BaseCamera camera)
	{
		GuideSprite guide = guides.get(listPos);
		TransformMatrix mtx;

		float x, y, z;

		if (preview) {
			x = renderPlayerPos.x;
			y = renderPlayerPos.y;
			z = renderPlayerPos.z;
		}
		else {
			x = position.getX();
			y = position.getY();
			z = position.getZ();
		}
		y -= Sprite.WORLD_SCALE;

		if (guide.sprite != null) {
			RenderMode.ALPHA_TEST_AA_ZB_2SIDE.setState(opts.worldFogEnabled ? 2 : 0);

			renderGuideSprite(guide, opts, camera, x, y, z, faceAngle, 1.0f, 1.0f);

			if (preview && opts.showBoundingBoxes)
				renderCollision();

			RenderMode.resetState();

			Vector3f size = Vector3f.sub(guide.sprite.aabb.getMax(), guide.sprite.aabb.getMin());
			float w = 0.75f * 0.5f * Math.max(size.x, size.z);
			float h = 0.75f * 0.75f * size.y;

			AABB.clear();
			AABB.encompass(new Vector3f(x - w / 2, y, z - w / 2));
			AABB.encompass(new Vector3f(x + w / 2, y + h, z + w / 2));

			RenderState.setColor(1.0f, 1.0f, Renderer.interpColor(0.0f, 1.0f));
			RenderState.setLineWidth(2.0f);
		}

		// reset scaling/rotation for visualizations
		mtx = TransformMatrix.identity();
		mtx.translate(x, y, z);
		RenderState.setModelMatrix(mtx);

		for (Visualization vis : guide.visualizations)
			vis.render();

		// reset model matrix for world-space visualizations
		RenderState.setModelMatrix(null);

		if (!preview && opts.showBoundingBoxes)
			AABB.render();

		for (Trace t : debugTraces)
			t.render();
		debugTraces.clear();

		// ensure model matrix is reset before returning
		RenderState.setModelMatrix(null);
	}

	private void renderSpinBlur(RenderingOptions opts, BaseCamera camera)
	{
		if (!isSpinBlurActive())
			return;

		GuideSprite guide = guides.get(listPos);
		if (guide.sprite == null)
			return;

		float x = preview ? renderPlayerPos.x : position.getX();
		float y = preview ? renderPlayerPos.y : position.getY();
		float z = preview ? renderPlayerPos.z : position.getZ();
		int historyIndex = getSpinHistoryIndex(1);
		if (!Float.isNaN(spinHistoryY[historyIndex]))
			y = spinHistoryY[historyIndex];
		y -= Sprite.WORLD_SCALE;

		RenderMode.SURF_XLU_AA_ZB_L1.setState(opts.worldFogEnabled ? 2 : 0);
		renderGuideSprite(guide, opts, camera, x, y, z, spinHistoryAngle[historyIndex], 0.0f, SPIN_BLUR_ALPHA);
		RenderMode.resetState();
		RenderState.setModelMatrix(null);
	}

	private void renderGuideSprite(GuideSprite guide, RenderingOptions opts, BaseCamera camera, float x, float y, float z, float angle, float tint,
		float alpha)
	{
		float renderYaw = camera.getYaw() + angle;

		if (opts.spriteShading.enabled)
			opts.spriteShading.setSpriteRenderingPos(camera, x, y, z, -renderYaw);

		TransformMatrix mtx = TransformMatrix.identity();
		mtx.scale(Sprite.WORLD_SCALE);
		mtx.rotate(Axis.Y, -renderYaw);
		mtx.translate(x, y, z);

		RenderState.setModelMatrix(mtx);
		RenderState.setPolygonMode(PolygonMode.FILL);
		guide.sprite.render(opts.spriteShading, guide.animID, guide.palID, preview && useBack, false, opts.useFiltering, tint, tint, tint, alpha);
	}

	public static class RenderablePlayer implements SortedRenderable
	{
		private final CursorObject obj;
		private int depth;

		public RenderablePlayer(CursorObject obj)
		{
			this.obj = obj;
		}

		@Override
		public RenderMode getRenderMode()
		{
			return RenderMode.ALPHA_TEST_AA_ZB_2SIDE;
		}

		@Override
		public Vector3f getCenterPoint()
		{
			if (obj.preview)
				return obj.renderPlayerPos;
			else
				return obj.position.getVector();
		}

		@Override
		public void render(RenderingOptions opts, BaseCamera camera)
		{
			obj.renderPlayer(opts, camera);
		}

		@Override
		public void setDepth(int normalizedDepth)
		{
			depth = normalizedDepth;
		}

		@Override
		public int getDepth()
		{
			return depth;
		}
	}

	public static class RenderableSpinBlur implements SortedRenderable
	{
		private final CursorObject obj;
		private int depth;

		public RenderableSpinBlur(CursorObject obj)
		{
			this.obj = obj;
		}

		@Override
		public RenderMode getRenderMode()
		{
			return RenderMode.SURF_XLU_AA_ZB_L1;
		}

		@Override
		public Vector3f getCenterPoint()
		{
			if (obj.preview)
				return obj.renderPlayerPos;
			else
				return obj.position.getVector();
		}

		@Override
		public void render(RenderingOptions opts, BaseCamera camera)
		{
			obj.renderSpinBlur(opts, camera);
		}

		@Override
		public void setDepth(int normalizedDepth)
		{
			depth = normalizedDepth;
		}

		@Override
		public int getDepth()
		{
			return depth;
		}
	}

	private void renderCollision()
	{
		int N = 2 * Math.round(1.0f + (float) (COLLISION_RADIUS / Math.sqrt(COLLISION_RADIUS)));
		int[][] indices = new int[2][N + 1];

		for (int i = 0; i < N; i++) {
			float x = COLLISION_RADIUS * (float) Math.cos(2 * i * Math.PI / N);
			float z = COLLISION_RADIUS * (float) Math.sin(2 * i * Math.PI / N);
			indices[0][i] = LineRenderQueue.addVertex().setPosition(x, 0, z).getIndex();
			indices[1][i] = LineRenderQueue.addVertex().setPosition(x, COLLISION_HEIGHT, z).getIndex();
			LineRenderQueue.addLine(indices[0][i], indices[1][i]);
		}
		indices[0][N] = indices[0][0];
		indices[1][N] = indices[1][0];

		LineRenderQueue.addLine(indices[0]);
		LineRenderQueue.addLine(indices[1]);

		LineRenderQueue.render(true);
	}

	public void drawBasicCursor()
	{
		float color = Renderer.interpColor(0.0f, 1.0f);

		RenderState.setLineWidth(1.0f);
		RenderState.setPointSize(10.0f);

		TransformMatrix mtx;
		mtx = TransformMatrix.identity();
		mtx.scale(25);
		Vector3f drawPos = getPosition();
		mtx.translate(drawPos.x, drawPos.y, drawPos.z);

		LineShader shader = ShaderManager.use(LineShader.class);

		// sphere vertex color is set to (255,255,255) at construction, apply new color with shader uniform now
		shader.useVertexColor.set(false);
		shader.color.set(1.0f, 1.0f, color, 1.0f);

		RenderState.setDepthWrite(false);
		Renderer.instance().renderLineSphere36(mtx);

		RenderState.enableDepthTest(false);
		PointRenderQueue.addPoint().setPosition(0, 0, 0).setColor(1.0f, 1.0f, color);
		PointRenderQueue.renderWithTransform(mtx, true);
		RenderState.enableDepthTest(true);

		RenderState.setDepthWrite(true);
		RenderState.setModelMatrix(null);
	}

	public static final class SetPosition extends AbstractCommand
	{
		private CursorObject obj;
		private final Vector3f oldPos;
		private final Vector3f newPos;

		public SetPosition(CursorObject obj, Vector3f pos)
		{
			super("Set Position");
			this.obj = obj;
			oldPos = obj.position.getVector();
			newPos = new Vector3f(pos);
		}

		@Override
		public boolean modifiesData()
		{
			return false;
		}

		@Override
		public boolean shouldExec()
		{
			return !newPos.equals(oldPos);
		}

		@Override
		public void exec()
		{
			super.exec();
			obj.previewPos.set(newPos.x, newPos.y, newPos.z);
			obj.position.setX((int) newPos.x);
			obj.position.setY((int) newPos.y);
			obj.position.setZ((int) newPos.z);
			obj.recalculateAABB();
			MapEditor.instance().selectionManager.currentSelection.updateAABB();
		}

		@Override
		public void undo()
		{
			super.undo();
			obj.previewPos.set(oldPos.x, oldPos.y, oldPos.z);
			obj.position.setX((int) oldPos.x);
			obj.position.setY((int) oldPos.y);
			obj.position.setZ((int) oldPos.z);
			obj.recalculateAABB();
			MapEditor.instance().selectionManager.currentSelection.updateAABB();
		}
	}

	@Override
	public void addTo(BoundingBox aabb)
	{
		aabb.encompass(position.getX(), position.getY(), position.getZ());
	}

	@Override
	public boolean isTransforming()
	{
		return position.isTransforming();
	}

	@Override
	public void startTransformation()
	{
		position.startTransform();
		yaw.startTransform();
	}

	@Override
	public void endTransformation()
	{
		position.endTransform();
		yaw.endTransform();

		recalculateAABB();
	}

	@Override
	public void recalculateAABB()
	{
		AABB.clear();

		AABB.encompass(
			position.getX() - (int) getSize().x,
			position.getY(),
			position.getZ() - (int) getSize().z);

		AABB.encompass(
			position.getX() + (int) getSize().x,
			position.getY() + (int) getSize().y,
			position.getZ() + (int) getSize().z);
	}

	@Override
	public boolean allowRotation(Axis axis)
	{
		return axis == Axis.Y;
	}

	@Override
	public ReversibleTransform createTransformer(TransformMatrix m)
	{
		final IdentityHashSet<PointBackup> backupList = new IdentityHashSet<>();
		backupList.add(position.getBackup());
		final AngleBackup backupYaw = yaw.getBackup();

		return new ReversibleTransform() {
			@Override
			public void transform()
			{
				for (PointBackup b : backupList)
					b.pos.setPosition(b.newx, b.newy, b.newz);
				yaw.setAngle(backupYaw.newAngle);

				recalculateAABB();
			}

			@Override
			public void revert()
			{
				for (PointBackup b : backupList)
					b.pos.setPosition(b.oldx, b.oldy, b.oldz);
				yaw.setAngle(backupYaw.oldAngle);

				recalculateAABB();
			}
		};
	}

	@Override
	public void addPoints(IdentityHashSet<MutablePoint> positions)
	{
		positions.add(position);
	}

	@Override
	public void addAngles(IdentityHashSet<MutableAngle> angles)
	{
		angles.add(yaw);
	}

	// ==================================================
	// picking
	// --------------------------------------------------

	@Override
	public PickHit tryPick(PickRay ray)
	{
		PickHit hit = PickRay.getIntersection(ray, AABB);
		hit.obj = this;
		return hit;
	}

	// ==================================================
	// internal classes and XML loading
	// --------------------------------------------------

	private static class GroundHit
	{
		public final PickHit hit;
		public final float distance;
		public final boolean central;

		public GroundHit(PickHit hit, float distance, boolean central)
		{
			this.hit = hit;
			this.distance = distance;
			this.central = central;
		}
	}

	private static class GuideSprite
	{
		public Sprite sprite;
		public int palID;
		public int animID;
		public float width;
		public float height;

		public List<Visualization> visualizations = new LinkedList<>();
	}

	private static abstract class Visualization
	{
		public abstract void render();
	}

	private static class GuideCylinder extends Visualization
	{
		public float radius;
		public float height;

		@Override
		public void render()
		{
			if (height > 0 && radius > 0) {
				int N = 2 * Math.round(1.0f + (float) (radius / Math.sqrt(radius)));
				int[][] indices = new int[2][N + 1];

				for (int i = 0; i < N; i++) {
					float x = radius * (float) Math.cos(2 * i * Math.PI / N);
					float z = radius * (float) Math.sin(2 * i * Math.PI / N);
					indices[0][i] = LineRenderQueue.addVertex().setPosition(x, 0, z).getIndex();
					indices[1][i] = LineRenderQueue.addVertex().setPosition(x, height, z).getIndex();
					LineRenderQueue.addLine(indices[0][i], indices[1][i]);
				}
				indices[0][N] = indices[0][0];
				indices[1][N] = indices[1][0];

				LineRenderQueue.addLine(indices[0]);
				LineRenderQueue.addLine(indices[1]);

				LineRenderQueue.render(true);
			}
		}
	}

	public static class JsonCursorGuide
	{
		String name;
		int player;
		int npc;
		int anim;

		int height;
		int width;

		String shape;
		int radius;
	}

	private static List<GuideSprite> readJSON(File xmlFile)
	{
		ArrayList<GuideSprite> guides = new ArrayList<>(255);
		SpriteLoader loader = MapEditor.instance().spriteLoader;

		try {
			Gson gson = new Gson();
			JsonReader jsonReader = new JsonReader(new FileReader(xmlFile));
			JsonCursorGuide[] jsonGuides = gson.fromJson(jsonReader, JsonCursorGuide[].class);

			for (JsonCursorGuide jsonGuide : jsonGuides) {
				GuideSprite guide = new GuideSprite();

				if (jsonGuide.name != null) {
					AnimMetadata animData = SpriteLoader.getAnimMetadata(jsonGuide.name);
					if (animData != null) {
						guide.palID = animData.palIndex;
						guide.animID = animData.animIndex;

						if (animData.isPlayer)
							guide.sprite = loader.getSprite(SpriteSet.Player, animData.spriteIndex);
						else
							guide.sprite = loader.getSprite(SpriteSet.Npc, animData.spriteIndex);
					}
				}

				guide.width = jsonGuide.width;
				guide.height = jsonGuide.height;

				if (jsonGuide.shape != null) {
					switch (jsonGuide.shape.toLowerCase()) {
						case "cylinder":
							GuideCylinder cylinder = new GuideCylinder();
							cylinder.height = jsonGuide.height;
							cylinder.radius = jsonGuide.radius;
							guide.visualizations.add(cylinder);
							break;
					}
				}

				if (guide.sprite != null) {
					guide.sprite.prepareForEditor();
					guide.sprite.loadTextures();
				}

				guides.add(guide);
			}

		}
		catch (Throwable t) {
			// nothing that goes wrong loading a guide should stop the editor from opening
			Logger.printStackTrace(t);
			Logger.logWarning(t.getClass() + " while loading 3D cursor sprites: " + t.getMessage());
		}

		return guides;
	}
}
