package game.map.editor.camera;

import game.map.editor.geometry.Vector3f;
import game.map.hit.CameraZoneData;
import game.map.hit.ControlType;
import util.MathUtil;

public class CameraController
{
	// saved values from last controller
	public ControlType type = ControlType.TYPE_3;
	public float boomLength = 450;
	public float boomPitch = 15;
	public float viewPitch = -6;

	private Vector3f samplePosition = new Vector3f();
	private Vector3f targetPos = new Vector3f();
	private double targetBoomLength = 450;
	private double targetYaw = 0;

	private Vector3f lookAt = new Vector3f();

	private Vector3f goalPos = new Vector3f();
	private float goalYaw = 0;
	private float goalPitch = 0;

	private Vector3f currentPos = new Vector3f();
	private float currentYaw = 0;
	private float currentPitch = 0;

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
	{ return currentPos; }

	public Vector3f getRotation()
	{ return new Vector3f(currentPitch, currentYaw, 0); }

	public Vector3f getSamplePosition()
	{ return new Vector3f(samplePosition); }

	public Vector3f getTargetPosition()
	{ return new Vector3f(targetPos); }

	public void update(CameraZoneData data, Vector3f position, boolean allowVertical, double deltaTime)
	{
		samplePosition.set(position);
		loadData(data);
		updateTarget(samplePosition.x, samplePosition.y, samplePosition.z);
		updateGoal();
		computeAngles();
		blend(allowVertical, deltaTime);
	}

	private void loadData(CameraZoneData data)
	{
		if (data != null) {
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
	}

	private void updateGoal()
	{
		if (type == ControlType.TYPE_2 && flag)
			return; // completely frozen boundary camera

		if (Math.abs(targetBoomLength) < 0.1)
			targetBoomLength = Math.signum(targetBoomLength) * 0.1;

		double thetaRad = Math.toRadians(boomPitch);
		double phiRad = -Math.toRadians(viewPitch);

		// calculation of cam[3C] from [80031FCC, 80032088]
		goalPos.x = targetPos.x - (float) (targetBoomLength * Math.cos(thetaRad) * Math.sin(targetYaw));
		goalPos.y = targetPos.y + (float) (targetBoomLength * Math.sin(thetaRad));
		goalPos.z = targetPos.z + (float) (targetBoomLength * Math.cos(thetaRad) * Math.cos(targetYaw));

		double R = targetBoomLength * Math.cos(thetaRad);
		double H = goalPos.y - targetPos.y;

		// calculations of cam[48]
		lookAt.x = goalPos.x + (float) ((R * Math.cos(phiRad) + H * Math.sin(phiRad)) * Math.sin(targetYaw));
		lookAt.y = goalPos.y + (float) (R * Math.sin(phiRad) - H * Math.cos(phiRad));
		lookAt.z = goalPos.z - (float) ((R * Math.cos(phiRad) + H * Math.sin(phiRad)) * Math.cos(targetYaw));
	}

	private void computeAngles()
	{
		// ---------------------------------------
		// calculate angles

		Vector3f forward = Vector3f.sub(goalPos, lookAt);

		if (forward.length() == 0)
			forward = new Vector3f(1.0f, 0.0f, 0.0f);
		forward.normalize();

		goalPitch = (float) Math.toDegrees(Math.asin(forward.y));
		goalYaw = (float) Math.toDegrees(targetYaw);
	}

	private void blend(boolean allowVerticalCameraMovement, double deltaTime)
	{
		float snapDist = 1.0f;
		float fraction = 5f;

		if (Math.abs(goalPos.x - currentPos.x) < snapDist)
			currentPos.x = goalPos.x;
		else
			currentPos.x = MathUtil.interp(currentPos.x, goalPos.x, fraction, deltaTime);

		if (allowVerticalCameraMovement) {
			if (Math.abs(goalPos.y - currentPos.y) < snapDist)
				currentPos.y = goalPos.y;
			else
				currentPos.y = MathUtil.interp(currentPos.y, goalPos.y, fraction, deltaTime);
		}

		if (Math.abs(goalPos.z - currentPos.z) < snapDist)
			currentPos.z = goalPos.z;
		else
			currentPos.z = MathUtil.interp(currentPos.z, goalPos.z, fraction, deltaTime);

		currentPitch = MathUtil.interpR(currentPitch, goalPitch, 5f, deltaTime);
		currentYaw = MathUtil.interpR(currentYaw, goalYaw, 5f, deltaTime);
	}

	private double getAlpha()
	{
		float delta, amt;

		// actually, should be three of these for boom yaw, boom pitch, and view pitch
		// both pitch are combined here
		delta = Math.abs(currentYaw - goalYaw);
		if (delta > 180)
			delta -= 360.0f;
		amt = delta;
		delta = Math.abs(currentPitch - goalPitch);
		if (delta > 180)
			delta -= 360.0f;
		if (delta > amt)
			amt = delta;
		//XXX also do Math.abs(currentBoomLength - goalBoomLength)
		float dx = goalPos.x - currentPos.x;
		float dy = goalPos.y - currentPos.y;
		float dz = goalPos.z - currentPos.z;
		delta = (float) (Math.sqrt(dx * dx + dy * dy + dz * dz) * 0.3);

		if (delta < 20.0f)
			delta = 20.0f;
		if (delta > 70.0f)
			delta = 70.0f;

		float alpha = 0.0f; // TODO get prev value

		// compute alpha
		alpha += (1.0f / amt) * 3.0f; // 3.0 = scale
		if (alpha > 1.0)
			alpha = 1.0f;

		// final step, add sin blend
		return 0.5 + (Math.sin((alpha - 0.5) * Math.PI) / 2.0);
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

					if (BCx == 0) {
						double Q = (BCx * BKx / BCz) + BKz;
						double V = (BPz * BCx / BCz) - BPx;

						targetPos.y = Y;
						targetPos.x = (float) (X - BKz * V / Q);
						targetPos.z = (float) (Z + BKx * V / Q);
					}
					else {
						double Q = -(BCz * BKz / BCx) - BKx;
						double V = (BPx * BCz / BCx) - BPz;

						targetPos.y = Y;
						targetPos.x = (float) (X - BKz * V / Q);
						targetPos.z = (float) (Z + BKx * V / Q);
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

					double t = (PCx * BCx + PCz * BCz) / (BCx * BCx + BCz * BCz);

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
				double t = (BAx * (X - Ax) + BAz * (Z - Az)) / (BAx * BAx + BAz * BAz);
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
}
