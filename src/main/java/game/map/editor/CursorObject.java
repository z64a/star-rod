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
	private boolean preview = false;
	private boolean dragging = false;

	// play in editor fields

	public static boolean showDebugTraces = false;
	public static List<Trace> debugTraces = new LinkedList<>();

	private static final float COLLISION_HEIGHT = 37;
	private static final float COLLISION_RADIUS = 13;

	public static final float WALK_SPEED = 120.0f;

	private static final float[] JUMP = { 15.7566f, -7.38624f, 3.44694f, -0.75f };
	private static final float[] FALL = { 0.154343f, -0.35008f, -0.182262f, 0.01152f };
	private float[] gravityIntegrator = new float[4];
	private long frameCount = 0;

	private float faceAngleGoal = 0;
	private float faceAngle = 0;

	private float moveSpeed = 0;
	private double moveYaw = 0;

	private float lastValidCamHeight = 0.0f;

	private enum FallState
	{
		OnGround, Jump, JumpAbort, Fall
	}

	private FallState fallState = FallState.OnGround;
	private boolean touchingGround = true;
	private boolean hovering = false;
	private float fallSpeed = 0;

	private boolean canJump = false;
	private boolean jumpInput = false;

	private boolean useBack = false;
	private PickHit shadowHit = null;

	// void falling prevention
	private Vector3f lastGroundPos = new Vector3f();
	private float fallTime = 0.0f;

	public CursorObject(Vector3f initialPosition)
	{
		guides = new LinkedList<>();
		loadGuides();

		position = new MutablePoint(initialPosition);
		yaw = new MutableAngle(0, Axis.Y, true);

		AABB = new BoundingBox();
		recalculateAABB();

		previewPos = new Vector3f(initialPosition);
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
			return previewPos;
		else
			return position.getVector();
	}

	public void setPosition(Vector3f newPos)
	{
		if (preview) {
			position.setTempPosition(newPos.x, newPos.y, newPos.z);
			previewPos.set(newPos.x, newPos.y, newPos.z);
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

		preview = true;
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

	private void getMovementInput(KeyboardInput keyboard, float camYaw, double deltaTime)
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
			moveSpeed = 0;
			//TODO moveYaw = facingYaw
			return;
		}
		df /= norm;
		dr /= norm;

		double dx = dr * Math.cos(toRadians(camYaw)) + df * Math.sin(toRadians(camYaw));
		double dz = dr * Math.sin(toRadians(camYaw)) - df * Math.cos(toRadians(camYaw));

		moveYaw = Math.atan2(dz, dx);
		moveSpeed = keyboard.isShiftDown() ? 300.0f : WALK_SPEED;
	}

	public void startInputJump()
	{
		jumpInput = true;
	}

	public void endInputJump()
	{
		jumpInput = false;
		canJump = true;
	}

	private void checkLateralCollision(List<MapObject> candidates, double deltaTime)
	{
		double moveDist = moveSpeed * deltaTime;
		//	float mx = (float)(moveDist * Math.cos(moveYaw));
		//	float mz = (float)(moveDist * Math.sin(moveYaw));

		if (moveDist != 0.0) {
			Vector3f updated = new Vector3f(previewPos.x, previewPos.y, previewPos.z);

			Vector3f lower = new Vector3f(previewPos.x, previewPos.y + 10.01f, previewPos.z);
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
			Vector3f whiskerStart = new Vector3f(updated.x, updated.y + (0.286f * COLLISION_HEIGHT), updated.z);

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

			checkSurroundingCollision(candidates, (0.286f * COLLISION_HEIGHT));

			//	only run for entity hit boxes, i think.
			//	if(!hitSomething)
			//		checkSurroundingCollision(map, candidates, ((0.75f + 0.286f) * COLLISION_HEIGHT));
		}
		else {
			checkSurroundingCollision(candidates, (0.286f * COLLISION_HEIGHT));
		}
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

	private float checkForGround(List<MapObject> candidates, float camYaw)
	{
		double angle = Math.toRadians(camYaw + faceAngleGoal - 90.0);
		float dx = (float) Math.cos(angle) * 2.0f * COLLISION_RADIUS * 0.28f;
		float dy = (COLLISION_HEIGHT * 0.5f);
		float dz = (float) Math.sin(angle) * 2.0f * COLLISION_RADIUS * 0.28f;

		Vector3f start = getPosition();
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

		PickHit[] hits = new PickHit[floorTraces.length];
		for (int i = 0; i < floorTraces.length; i++) {
			hits[i] = Map.pickObjectFromSet(floorTraces[i], candidates, false);
			if (hits[i].dist <= minDist) // note: operator includes ==, we pick LAST match
			{
				minDist = hits[i].dist;
				minHit = hits[i];
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

		if (minHit == null)
			return Float.MAX_VALUE;

		return (minHit == null || minHit.missed()) ? Float.MAX_VALUE : (start.y - minHit.point.y);
	}

	private float checkForCeiling(List<MapObject> candidates, float camYaw)
	{
		double angle = Math.toRadians(camYaw + faceAngleGoal - 90.0);
		float dx = (float) Math.cos(angle) * 2.0f * COLLISION_RADIUS * 0.30f;
		float dy = (COLLISION_HEIGHT * 0.5f);
		float dz = (float) Math.sin(angle) * 2.0f * COLLISION_RADIUS * 0.30f;

		Vector3f start = getPosition();
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
		// assumes frame rate of 30fps
		gravityIntegrator[2] += gravityIntegrator[3];
		gravityIntegrator[1] += gravityIntegrator[2];
		gravityIntegrator[0] += gravityIntegrator[1];
		return -gravityIntegrator[0];
	}

	public boolean allowVerticalCameraMovement()
	{
		return (preview && (fallState == FallState.OnGround || hovering || previewPos.y < lastValidCamHeight));
	}

	public void setMoveHeading(float moveSpeed, float moveYaw)
	{
		this.moveSpeed = moveSpeed;
		this.moveYaw = moveYaw;
	}

	public void tickSimulation(KeyboardInput keyboard, Map collisionMap, Map entityMap, MapEditViewport viewport, double deltaTime, boolean hasFocus,
		boolean checkInput, boolean showDebugTraces)
	{
		// process input

		touchingGround = false;
		hovering = false;

		if (checkInput) {
			if (!hasFocus)
				moveSpeed = 0.0f;
			getMovementInput(keyboard, viewport.camera.getYaw(), deltaTime);
		}

		// update animation state

		float deltaAngle = (float) Math.toDegrees(moveYaw) - viewport.camera.getYaw();
		while (deltaAngle < 0.0f)
			deltaAngle += 360.0f;
		while (deltaAngle >= 360.0f)
			deltaAngle -= 360.0f;

		useBack = (deltaAngle > 180.0f);

		deltaAngle += 270;
		while (deltaAngle >= 360.0f)
			deltaAngle -= 360.0f;

		if (deltaAngle != 0.0f && deltaAngle != 180.0f)
			faceAngleGoal = (deltaAngle > 180.0f) ? 180.0f : 0.0f;

		faceAngle = MathUtil.interp(faceAngle, faceAngleGoal, 10f, deltaTime);

		// check collision

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

		checkLateralCollision(candidates, deltaTime);

		// hover
		if (checkInput && keyboard.isKeyDown(EditorShortcut.PLAY_IN_EDITOR_HOVER.key)) {
			previewPos.y += (float) (120.0 * deltaTime);
			setGravityParams(FALL);
			fallState = FallState.Fall;
			fallTime = 0.0f;

			hovering = true;
			lastValidCamHeight = previewPos.y;

			return;
		}

		// do physics

		if (fallState == FallState.OnGround) {
			float hitDist = checkForGround(candidates, viewport.camera.getYaw());

			if (hitDist > COLLISION_HEIGHT * 2.0 / 7.0) {
				setGravityParams(FALL);
				fallState = FallState.Fall;
			}
			else //if(hitDist < 6)
			{
				previewPos.y -= hitDist;
				touchingGround = true;
			}

			if (jumpInput && canJump) {
				canJump = false;
				setGravityParams(JUMP);
				fallState = FallState.Jump;
			}
		}

		// do gravity at 30 FPS
		if (++frameCount % 2 == 0) {
			float fallDist = 0;

			switch (fallState) {
				case OnGround:
					fallSpeed = 0;
					/*
					hitDist = checkForGround(map, candidates, viewport);
					if(hitDist < Float.MAX_VALUE)
					{
						if(hitDist > 10)
						{
							setGravityParams(FALL);
							fallDist = integrateGravity();
							fallState = FallState.Fall;
						}
						else
						{
							previewPos.y -= hitDist;
						}
					}
					 */
					break;
				case Jump:
					if (!jumpInput)
						fallState = FallState.JumpAbort;
					fallDist = integrateGravity();
					if (gravityIntegrator[0] < 0) {
						setGravityParams(FALL);
						fallDist = integrateGravity();
						fallState = FallState.Fall;
					}
					break;
				case JumpAbort:
					fallDist = 4.5f;
					gravityIntegrator[0] -= fallDist;
					if (gravityIntegrator[0] < 0) {
						setGravityParams(FALL);
						fallDist = integrateGravity();
						fallState = FallState.Fall;
					}
					break;
				case Fall:
					fallDist = integrateGravity();
					break;
			}

			fallSpeed = fallDist / 2;
		}

		// check collision above
		if (fallState == FallState.Jump) {
			float hitDist = checkForCeiling(candidates, viewport.camera.getYaw());
			if (!touchingGround && hitDist < (COLLISION_HEIGHT / 2.0) + gravityIntegrator[0]) {
				setGravityParams(FALL);
				fallState = FallState.Fall;
				previewPos.y -= (COLLISION_HEIGHT / 10.0f);
			}
		}

		if (fallSpeed > 0) {
			float hitDist = checkForGround(candidates, viewport.camera.getYaw());
			if (hitDist < fallSpeed) {
				//		fallSpeed = hitDist;
				fallState = FallState.OnGround;
			}
			else
				previewPos.y -= fallSpeed;
		}
		else if (fallSpeed < 0) {
			// jump
			previewPos.y -= fallSpeed;
		}

		// limit falling off
		if (fallState == FallState.Fall) {
			fallTime += deltaTime;

			if (fallTime > 2.0f) {
				previewPos.set(lastGroundPos);
				fallState = FallState.OnGround;
			}
		}
		else {
			fallTime = 0.0f;
			if (fallState == FallState.OnGround) {
				lastGroundPos.set(previewPos);
				lastValidCamHeight = previewPos.y;
			}
		}
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

		Vector3f shadowOrigin = new Vector3f(previewPos.x, previewPos.y + COLLISION_HEIGHT / 2, previewPos.z);
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

		spriteTime += deltaTime;
		if (spriteTime >= SPRITE_TICK_RATE) {
			GuideSprite guide = guides.get(listPos);
			guide.sprite.updateAnimation(guide.animID);
			spriteTime -= SPRITE_TICK_RATE;
		}

		GuideSprite primary = guides.get(0);
		if (primary.sprite.metadata.isPlayer && primary.sprite.name.equals("Mario1")) {
			primary.animID = 2;
			if (preview) {
				switch (fallState) {
					case Jump:
						primary.animID = 7;
						break;
					case JumpAbort:
					case Fall:
						primary.animID = 8;
						break;
					case OnGround:
						if (moveSpeed > 0.0)
							primary.animID = 5; // 4 = walk
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
	}

	private void renderPlayer(RenderingOptions opts, BaseCamera camera)
	{
		GuideSprite guide = guides.get(listPos);
		TransformMatrix mtx;

		float x, y, z;
		boolean renderBackFace;

		if (preview) {
			x = previewPos.x;
			y = previewPos.y;
			z = previewPos.z;
			renderBackFace = useBack;
		}
		else {
			x = position.getX();
			y = position.getY();
			z = position.getZ();
			renderBackFace = false;
		}
		y -= Sprite.WORLD_SCALE;

		if (guide.sprite != null) {
			float renderYaw = camera.getYaw() + faceAngle;

			if (opts.spriteShading != null)
				opts.spriteShading.setSpriteRenderingPos(camera, x, y, z, -renderYaw);

			mtx = TransformMatrix.identity();
			mtx.scale(Sprite.WORLD_SCALE);
			mtx.rotate(Axis.Y, -renderYaw);
			mtx.translate(x, y, z);

			RenderState.setModelMatrix(mtx);
			RenderState.setPolygonMode(PolygonMode.FILL);

			guide.sprite.render(opts.spriteShading, guide.animID, guide.palID, renderBackFace, opts.useFiltering, false);

			if (preview && opts.showBoundingBoxes)
				renderCollision();

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
				return obj.previewPos;
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
		mtx.translate(position.getX(), position.getY(), position.getZ());

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
			newPos = pos;
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
