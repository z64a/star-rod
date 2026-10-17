package game.map.editor.camera;

import common.Vector3f;

import game.map.hit.CameraZoneData;
import game.map.hit.ControlType;
import util.MathUtil;

public class CameraController
{
	private static final float ENGINE_TICKS_PER_SECOND = 30.0f;
	private static final float DEFAULT_BOOM_LENGTH = 450.0f;
	private static final float DEFAULT_BOOM_PITCH = 15.0f;
	private static final float DEFAULT_VIEW_PITCH = -6.0f;

	// saved values from last controller
	public ControlType type = ControlType.TYPE_3;
	public float boomLength = DEFAULT_BOOM_LENGTH;
	public float boomPitch = DEFAULT_BOOM_PITCH;
	public float viewPitch = DEFAULT_VIEW_PITCH;

	private Vector3f samplePosition = new Vector3f();
	private Vector3f targetPos = new Vector3f();
	private double targetBoomLength = DEFAULT_BOOM_LENGTH;
	private double targetYaw = 0;

	private Vector3f currentPos = new Vector3f();
	private Vector3f previousPos = new Vector3f();
	private float currentYaw = 0;
	private float currentPitch = 0;
	private float previousYaw = 0;
	private float previousPitch = 0;

	// Zone changes interpolate between complete camera rigs. This mirrors CAM_UPDATE_FROM_ZONE rather than smoothing the final transform.
	private CameraSettings previousSettings;
	private CameraSettings currentSettings;
	private final CameraRig previousRig = new CameraRig();
	private final CameraRig nextRig = new CameraRig();
	private final CameraRig currentRig = new CameraRig();
	private final Vector3f previousTargetOffset = new Vector3f();
	private boolean previousTracksNext;
	private boolean initialized;
	private float linearInterp;
	private float interpAlpha = 1.0f;

	private float yinterpGoal;
	private float yinterpCurrent;
	private float yinterpAlpha = 1.0f;

	// breaks o863 from kzn_20, which erroneously uses -100000 (FFFE7960) for the flag
	public boolean flag;

	float Ax = 0;
	float Az = 0;
	float Bx = 0;
	float By = 0;
	float Bz = 0;
	float Cx = 0;
	float Cz = 0;

	public Vector3f getPosition()
	{
		return new Vector3f(currentPos);
	}

	public Vector3f getPosition(float alpha)
	{
		return new Vector3f(
			MathUtil.lerp(alpha, previousPos.x, currentPos.x),
			MathUtil.lerp(alpha, previousPos.y, currentPos.y),
			MathUtil.lerp(alpha, previousPos.z, currentPos.z));
	}

	public Vector3f getRotation()
	{
		return new Vector3f(currentPitch, currentYaw, 0);
	}

	public Vector3f getRotation(float alpha)
	{
		return new Vector3f(interpolateAngle(previousPitch, currentPitch, alpha), interpolateAngle(previousYaw, currentYaw, alpha), 0);
	}

	public Vector3f getSamplePosition()
	{
		return new Vector3f(samplePosition);
	}

	public Vector3f getTargetPosition()
	{
		return new Vector3f(targetPos);
	}

	public boolean isInitialized()
	{
		return initialized;
	}

	public void reset(Vector3f position)
	{
		initialized = false;
		type = ControlType.TYPE_3;
		flag = false;
		boomLength = DEFAULT_BOOM_LENGTH;
		boomPitch = DEFAULT_BOOM_PITCH;
		viewPitch = DEFAULT_VIEW_PITCH;
		samplePosition.set(position);
		targetPos.set(position);
		previousSettings = null;
		currentSettings = null;
		previousTracksNext = false;
		linearInterp = 0.0f;
		interpAlpha = 1.0f;
		yinterpGoal = position.y;
		yinterpCurrent = position.y;
		yinterpAlpha = 1.0f;

		nextRig.setDefaults(position);
		previousRig.set(nextRig);
		currentRig.set(nextRig);
		updateOutput(currentRig);
		previousPos.set(currentPos);
		previousPitch = currentPitch;
		previousYaw = currentYaw;
	}

	public void update(CameraZoneData data, Vector3f position, boolean allowVertical, double deltaTime)
	{
		update(data, position, allowVertical, 24.0f, deltaTime);
	}

	public void update(CameraZoneData data, Vector3f position, boolean allowVertical, float yinterpRate, double deltaTime)
	{
		samplePosition.set(position);

		float tickScale = Math.max(0.0f, (float) deltaTime * ENGINE_TICKS_PER_SECOND);
		CameraSettings settings = CameraSettings.from(data);

		if (!initialized) {
			initialize(settings, position);
			return;
		}

		previousPos.set(currentPos);
		previousPitch = currentPitch;
		previousYaw = currentYaw;

		updateVerticalTarget(position.y, allowVertical, yinterpRate, tickScale);
		Vector3f cameraTarget = new Vector3f(position.x, yinterpCurrent, position.z);
		boolean changingZone = !CameraSettings.same(currentSettings, settings);

		if (changingZone) {
			boolean interruptedTransition = interpAlpha < 1.0f;
			previousRig.set(currentRig);
			previousSettings = interruptedTransition ? null : currentSettings;
			currentSettings = settings;
			nextRig.set(evaluateRig(currentSettings, cameraTarget, nextRig, false, true));

			previousTracksNext = interruptedTransition || isLineConstraint(previousSettings) || isLineConstraint(currentSettings);
			if (previousTracksNext) {
				previousTargetOffset.set(
					previousRig.targetPos.x - nextRig.targetPos.x,
					previousRig.targetPos.y - nextRig.targetPos.y,
					previousRig.targetPos.z - nextRig.targetPos.z);
			}
			else if (currentSettings != null && currentSettings.type == ControlType.TYPE_4) {
				previousSettings = null;
			}

			linearInterp = 0.0f;
			interpAlpha = 0.0f;
		}

		nextRig.set(evaluateRig(currentSettings, cameraTarget, nextRig, false, false));
		if (interpAlpha < 1.0f) {
			if (previousTracksNext) {
				previousRig.targetPos.set(
					nextRig.targetPos.x + previousTargetOffset.x,
					nextRig.targetPos.y + previousTargetOffset.y,
					nextRig.targetPos.z + previousTargetOffset.z);
			}
			else if (previousSettings != null) {
				previousRig.set(evaluateRig(previousSettings, cameraTarget, previousRig, false, false));
			}

			advanceTransition(tickScale);
			interpolateRig(currentRig, previousRig, nextRig, interpAlpha);
		}
		else {
			currentRig.set(nextRig);
			previousRig.set(nextRig);
		}

		targetPos.set(nextRig.targetPos);
		updateOutput(currentRig);
		if (currentSettings != null)
			loadSettings(currentSettings);
	}

	private void initialize(CameraSettings settings, Vector3f position)
	{
		currentSettings = settings;
		previousSettings = settings;
		yinterpGoal = position.y;
		yinterpCurrent = position.y;
		yinterpAlpha = 1.0f;

		Vector3f cameraTarget = new Vector3f(position);
		nextRig.set(evaluateRig(settings, cameraTarget, nextRig, true, false));
		previousRig.set(nextRig);
		currentRig.set(nextRig);
		targetPos.set(nextRig.targetPos);
		linearInterp = 0.0f;
		interpAlpha = 1.0f;
		previousTracksNext = false;
		updateOutput(currentRig);
		previousPos.set(currentPos);
		previousPitch = currentPitch;
		previousYaw = currentYaw;
		initialized = true;
	}

	private CameraRig evaluateRig(CameraSettings settings, Vector3f position, CameraRig basis, boolean initializing, boolean changingZone)
	{
		CameraRig rig = new CameraRig(basis);
		if (settings == null) {
			rig.targetPos.set(position);
			return rig;
		}

		loadSettings(settings);
		if (type == ControlType.TYPE_2) {
			if (initializing) {
				rig.boomYaw = (float) Math.toDegrees(Math.atan2(Bx - Ax, -(Bz - Az)));
				rig.boomLength = Math.abs(boomLength);
				rig.boomPitch = boomPitch;
				rig.viewPitch = viewPitch;
			}

			if (flag) {
				if (initializing)
					rig.targetPos.set(Bx, position.y, Bz);
				else
					rig.targetPos.y = position.y;
			}
			else {
				targetPos = new Vector3f(rig.targetPos);
				targetBoomLength = rig.boomLength;
				targetYaw = Math.toRadians(rig.boomYaw);
				updateTarget(position.x, position.y, position.z);
				rig.targetPos.set(targetPos);
			}
			return rig;
		}

		if (type == ControlType.TYPE_5 && flag) {
			if (initializing) {
				setFixedLineRig(rig, position.y);
			}
			else if (changingZone) {
				rig.targetPos.set(Bx, position.y, Bz);
			}
			else {
				rig.targetPos.y = position.y;
			}
			return rig;
		}

		if (type == ControlType.TYPE_3) {
			rig.targetPos.set(position);
			return rig;
		}

		targetPos = new Vector3f(rig.targetPos);
		targetBoomLength = rig.boomLength;
		targetYaw = Math.toRadians(rig.boomYaw);
		updateTarget(position.x, position.y, position.z);

		rig.targetPos.set(targetPos);
		rig.boomLength = (float) targetBoomLength;
		rig.boomPitch = boomPitch;
		rig.viewPitch = viewPitch;
		rig.boomYaw = (float) Math.toDegrees(targetYaw);
		return rig;
	}

	private void setFixedLineRig(CameraRig rig, float targetY)
	{
		if (boomLength < 0.0f) {
			rig.boomYaw = (float) Math.toDegrees(Math.atan2(Bx - Ax, -(Bz - Az)));
			rig.boomLength = -boomLength;
		}
		else {
			rig.boomYaw = (float) Math.toDegrees(Math.atan2(Ax - Bx, -(Az - Bz)));
			rig.boomLength = boomLength;
		}
		rig.boomPitch = boomPitch;
		rig.viewPitch = viewPitch;
		rig.targetPos.set(Bx, targetY, Bz);
	}

	private void loadSettings(CameraSettings settings)
	{
		type = settings.type;
		flag = settings.flag;
		boomLength = settings.boomLength;
		boomPitch = settings.boomPitch;
		viewPitch = settings.viewPitch;
		Ax = settings.Ax;
		Az = settings.Az;
		Bx = settings.Bx;
		By = settings.By;
		Bz = settings.Bz;
		Cx = settings.Cx;
		Cz = settings.Cz;
	}

	private void updateVerticalTarget(float targetY, boolean allowVertical, float yinterpRate, float tickScale)
	{
		if (!allowVertical) {
			yinterpAlpha = 0.0f;
		}
		else if (yinterpGoal != targetY) {
			yinterpGoal = targetY;
			yinterpAlpha = 0.0f;
		}

		// The engine always follows downward movement immediately, even while player Y is ignored.
		if (targetY < yinterpGoal && targetY <= yinterpCurrent) {
			yinterpGoal = targetY;
			yinterpAlpha = 1.0f;
		}

		float rate = Math.max(1.0f, yinterpRate);
		yinterpAlpha += ((1.01f - yinterpAlpha) / rate) * tickScale;
		yinterpAlpha = MathUtil.clamp(yinterpAlpha, 0.0f, 1.0f);
		yinterpCurrent += (yinterpGoal - yinterpCurrent) * yinterpAlpha;
	}

	private void advanceTransition(float tickScale)
	{
		float maxDelta = angularDistance(previousRig.boomYaw, nextRig.boomYaw);
		maxDelta = Math.max(maxDelta, angularDistance(previousRig.boomPitch, nextRig.boomPitch));
		maxDelta = Math.max(maxDelta, angularDistance(previousRig.viewPitch, nextRig.viewPitch));
		maxDelta = Math.max(maxDelta, Math.abs(previousRig.boomLength - nextRig.boomLength));

		float dx = previousRig.targetPos.x - nextRig.targetPos.x;
		float dy = previousRig.targetPos.y - nextRig.targetPos.y;
		float dz = previousRig.targetPos.z - nextRig.targetPos.z;
		maxDelta = Math.max(maxDelta, (float) Math.sqrt(dx * dx + dy * dy + dz * dz) * 0.2f);
		maxDelta = MathUtil.clamp(maxDelta, 20.0f, 90.0f);

		linearInterp += tickScale / maxDelta;
		linearInterp = Math.min(linearInterp, 1.0f);
		interpAlpha = (float) ((1.0 - Math.cos(linearInterp * Math.PI)) * 0.5001);
		if (interpAlpha >= 1.0f) {
			interpAlpha = 1.0f;
			linearInterp = 0.0f;
			previousSettings = currentSettings;
			previousTracksNext = false;
		}
	}

	private void updateOutput(CameraRig rig)
	{
		double yaw = Math.toRadians(rig.boomYaw);
		double pitch = Math.toRadians(rig.boomPitch);
		float length = Math.abs(rig.boomLength) < 0.1f ? 0.1f : rig.boomLength;

		currentPos.set(
			rig.targetPos.x - (float) (length * Math.cos(pitch) * Math.sin(yaw)),
			rig.targetPos.y + (float) (length * Math.sin(pitch)),
			rig.targetPos.z + (float) (length * Math.cos(pitch) * Math.cos(yaw)));
		currentYaw = rig.boomYaw;
		currentPitch = rig.boomPitch + rig.viewPitch;
	}

	private static boolean isLineConstraint(CameraSettings settings)
	{
		return settings != null && (settings.type == ControlType.TYPE_2 || settings.type == ControlType.TYPE_5);
	}

	private static float angularDistance(float a, float b)
	{
		float delta = Math.abs(a - b) % 360.0f;
		return delta > 180.0f ? 360.0f - delta : delta;
	}

	private static float interpolateAngle(float a, float b, float alpha)
	{
		float delta = (b - a) % 360.0f;
		if (delta > 180.0f)
			delta -= 360.0f;
		if (delta < -180.0f)
			delta += 360.0f;
		return a + delta * MathUtil.clamp(alpha, 0.0f, 1.0f);
	}

	private static void interpolateRig(CameraRig result, CameraRig a, CameraRig b, float alpha)
	{
		result.targetPos.set(
			MathUtil.lerp(alpha, a.targetPos.x, b.targetPos.x),
			MathUtil.lerp(alpha, a.targetPos.y, b.targetPos.y),
			MathUtil.lerp(alpha, a.targetPos.z, b.targetPos.z));
		result.boomYaw = interpolateAngle(a.boomYaw, b.boomYaw, alpha);
		result.boomLength = MathUtil.lerp(alpha, a.boomLength, b.boomLength);
		result.boomPitch = interpolateAngle(a.boomPitch, b.boomPitch, alpha);
		result.viewPitch = interpolateAngle(a.viewPitch, b.viewPitch, alpha);
	}

	// reverse engineered from func_800304FC, starting with the switch at 800308AC
	private void updateTarget(float X, float Y, float Z)
	{
		switch (type) {
			// Constrain Yaw to Axis -- yaw is defined by the line segment AB
			// flag 0 = free forward movement (follow player)
			// flag 1 = lock forward movement (must intersect B)
			// Uses: A/B as 2D points
			case TYPE_0: // (VERIFIED)
			{
				double BAx = Bx - Ax;
				double BAz = Bz - Az;
				targetYaw = Math.atan2(BAx, -BAz); // note: sign for z reversed from PM64
				targetBoomLength = Math.abs(boomLength);

				if (!flag)
					targetPos.set(X, Y, Z);
				else {
					// only move camera along the line perpendicular to AB passing through B
					double d2 = BAx * BAx + BAz * BAz;
					if (d2 == 0.0) {
						targetPos.y = Y;
						break;
					}
					double perpdot = BAx * (Z - Bz) - BAz * (X - Bx);

					targetPos.y = Y;
					targetPos.x = (float) (Bx - BAz * perpdot / d2);
					targetPos.z = (float) (Bz + BAx * perpdot / d2);
				}
			}
				break;

			// Radial Focal Point -- faces toward or away from a fixed point
			// flag 0 = free forward movement (follow player)
			// flag 1 = lock forward movement (fixed radius)
			// negative boom length reverses direction
			case TYPE_1: // (VERIFIED)
			{
				double dx = X - Ax;
				double dz = Z - Az;
				double D2 = dx * dx + dz * dz;

				if (boomLength < 0) {
					targetBoomLength = -boomLength;
					targetYaw = Math.atan2(dx, -dz); // note: sign for z reversed from PM64
				}
				else {
					targetBoomLength = boomLength;
					targetYaw = Math.atan2(-dx, dz); // note: sign for z reversed from PM64
				}

				if (!flag) {
					targetPos.x = X;
					targetPos.y = Y;
					targetPos.z = Z;
				}
				else if (D2 != 0) {
					double BAx = Bx - Ax;
					double BAz = Bz - Az;
					double R = Math.sqrt((BAx * BAx + BAz * BAz) / D2);

					targetPos.x = (float) (Ax + dx * R);
					targetPos.y = Y;
					targetPos.z = (float) (Az + dz * R);
				}
			}
				break;

			// Uses: A/B/C as 2D points
			case TYPE_2: //VERIFIED
			{
				if (!flag) {
					double Kx = Ax;
					double Kz = Az;

					if (Ax == Bx && Az == Bz) {
						Kx = Cx;
						Kz = Cz;
					}

					double BCx = Bx - Cx;
					double BCz = Bz - Cz;

					double BKx = Bx - Kx;
					double BKz = Bz - Kz;

					double BPx = Bx - X;
					double BPz = Bz - Z;

					// Project along the perpendicular to BK onto BC. Coincident or
					// perpendicular control lines cannot define a unique projection.
					double denominator = BCx * BKx + BCz * BKz;
					targetPos.y = Y;
					if (denominator != 0.0) {
						double correction = (BPz * BCx - BPx * BCz) / denominator;
						targetPos.x = (float) (X - BKz * correction);
						targetPos.z = (float) (Z + BKx * correction);
					}

					targetBoomLength = Math.abs(boomLength);
				}
				else {
					// static camera, do not update
				}
			}
				break;

			// Uses: no control points
			case TYPE_3: // (VERIFIED)
			{
				// Follow Player, Maintain Yaw
				// does not use flag
				targetPos.set(X, Y, Z);
				targetBoomLength = boomLength;
			}
				break;

			// Uses: A as a 2D point and B as a 3D point
			case TYPE_4: // (VERIFIED)
			{
				// Fixed Position and Yaw -- positioned at B facing along AB
				// does not use flag
				targetPos = new Vector3f(Bx, By, Bz);
				double BAx = Bx - Ax;
				double BAz = Bz - Az;
				targetYaw = Math.atan2(BAx, -BAz); // note: sign for z reversed from PM64
				targetBoomLength = Math.abs(boomLength);
			}
				break;

			// Uses: A/B/C as 2D points
			case TYPE_5: //VERIFIED
			{
				// Constrain to Line, Facing Point
				// Camera position is projected onto a line defined by BC, while the yaw
				// is in the direction of A.
				if (!flag) {
					double BCx = Bx - Cx;
					double BCz = Bz - Cz;

					double PCx = X - Cx;
					double PCz = Z - Cz;

					double lengthSquared = BCx * BCx + BCz * BCz;
					if (lengthSquared == 0.0) {
						targetPos.y = Y;
						break;
					}
					double t = (PCx * BCx + PCz * BCz) / lengthSquared;

					targetPos.y = Y;
					targetPos.x = (float) (Cx + t * BCx);
					targetPos.z = (float) (Cz + t * BCz);

					targetBoomLength = Math.abs(boomLength);

					double TAx = targetPos.x - Ax;
					double TAz = targetPos.z - Az;

					if (boomLength < 0)
						targetYaw = Math.atan2(TAx, -TAz);
					else
						targetYaw = Math.atan2(-TAx, TAz);
				}
				else {
					// stops the camera in its tracks, does not update positon or orientation.
					// great for a 'camera stopper' near a map exit that doesn't need to scroll
					// toward and away from the camera.
					targetPos.y = Y;

					//XXX old	targetPos = new Vector3f(Bx, By, Bz);
				}
			}
				break;

			// Uses: A/B as 2D points
			case TYPE_6: // (VERIFIED) 800309CC
			{
				// Constrain to Line Segment
				// Position interpolates between limiting points A and B, following the player.
				// Yaw is set perpendicular to AB.
				// flag 0 = free forward movement (follow player)
				// flag 1 = lock forward movement (constrainted to line)
				double BAx = Bx - Ax;
				double BAz = Bz - Az;
				targetYaw = Math.atan2(BAz, BAx); // note: sign for z reversed from PM64
				targetBoomLength = Math.abs(boomLength);

				// project on to line
				double lengthSquared = BAx * BAx + BAz * BAz;
				if (lengthSquared == 0.0) {
					targetPos.y = Y;
					break;
				}
				double t = (BAx * (X - Ax) + BAz * (Z - Az)) / lengthSquared;
				double Px = t * BAx + Ax;
				double Pz = t * BAz + Az;

				double Rx, Rz;

				/*
				// simpler way? may miss some edge cases
				if(t <= 0) {
					Rx = Ax;
					Rz = Az;
				}
				else if (t >= 1) {
					Rx = Bx;
					Rz = Bz;
				}
				else {
					Rx = Px;
					Rz = Pz;
				}
				*/

				// how PM64 does it
				double qx = BAx * t;
				double qz = BAz * t;

				if (0 <= BAx * qx + BAz * qz) {
					Rx = Bx;
					Rz = Bz;

					if ((qx * qx + qz * qz) <= (BAx * BAx + BAz * BAz)) {
						Rx = Px;
						Rz = Pz;
					}
				}
				else {
					Rx = Ax;
					Rz = Az;
				}

				// save result
				if (!flag) {
					targetPos.x = (float) (Rx + (X - Px));
					targetPos.y = Y;
					targetPos.z = (float) (Rz + (Z - Pz));
				}
				else {
					targetPos.x = (float) Rx;
					targetPos.y = Y;
					targetPos.z = (float) Rz;
				}
			}
				break;

			default:
				// default is to not update the camera (but no such zones exist)
		}
	}

	private static class CameraRig
	{
		public final Vector3f targetPos = new Vector3f();
		public float boomLength = DEFAULT_BOOM_LENGTH;
		public float boomPitch = DEFAULT_BOOM_PITCH;
		public float viewPitch = DEFAULT_VIEW_PITCH;
		public float boomYaw;

		public CameraRig()
		{}

		public CameraRig(CameraRig other)
		{
			set(other);
		}

		public void set(CameraRig other)
		{
			targetPos.set(other.targetPos);
			boomLength = other.boomLength;
			boomPitch = other.boomPitch;
			viewPitch = other.viewPitch;
			boomYaw = other.boomYaw;
		}

		public void setDefaults(Vector3f position)
		{
			targetPos.set(position);
			boomLength = DEFAULT_BOOM_LENGTH;
			boomPitch = DEFAULT_BOOM_PITCH;
			viewPitch = DEFAULT_VIEW_PITCH;
			boomYaw = 0.0f;
		}
	}

	private static class CameraSettings
	{
		public final ControlType type;
		public final boolean flag;
		public final float boomLength;
		public final float boomPitch;
		public final float viewPitch;
		public final float Ax;
		public final float Az;
		public final float Bx;
		public final float By;
		public final float Bz;
		public final float Cx;
		public final float Cz;

		private CameraSettings(CameraZoneData data)
		{
			type = data.getType();
			flag = data.getFlag();
			boomLength = data.boomLength.get();
			boomPitch = data.boomPitch.get();
			viewPitch = data.viewPitch.get();
			Ax = data.posA.getX();
			Az = data.posA.getZ();
			Bx = data.posB.getX();
			By = data.posB.getY();
			Bz = data.posB.getZ();
			Cx = data.posC.getX();
			Cz = data.posC.getZ();
		}

		public static CameraSettings from(CameraZoneData data)
		{
			return data == null ? null : new CameraSettings(data);
		}

		public static boolean same(CameraSettings a, CameraSettings b)
		{
			if (a == null || b == null)
				return a == b;

			return a.type == b.type
				&& a.flag == b.flag
				&& a.boomLength == b.boomLength
				&& a.boomPitch == b.boomPitch
				&& a.viewPitch == b.viewPitch
				&& a.Ax == b.Ax
				&& a.Az == b.Az
				&& a.Bx == b.Bx
				&& a.By == b.By
				&& a.Bz == b.Bz
				&& a.Cx == b.Cx
				&& a.Cz == b.Cz;
		}
	}
}
