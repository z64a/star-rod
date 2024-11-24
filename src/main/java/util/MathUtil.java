package util;

import common.Vector3f;

public abstract class MathUtil
{
	public static final float SMALL_NUMBER = 1e-4f;
	public static final float VERY_SMALL_NUMBER = 1e-8f;

	public static final boolean nearlyZero(float f)
	{
		return Math.abs(f) < SMALL_NUMBER;
	}

	public static final boolean veryNearlyZero(float f)
	{
		return Math.abs(f) < VERY_SMALL_NUMBER;
	}

	public static boolean nearlyZero(double d)
	{
		return Math.abs(d) < SMALL_NUMBER;
	}

	public static boolean veryNearlyZero(double d)
	{
		return Math.abs(d) < VERY_SMALL_NUMBER;
	}

	public static int roundAwayFromZero(float f)
	{
		int v = (int) f;
		if (v == f)
			return v;
		else
			return (int) ((f > 0) ? Math.ceil(f) : Math.floor(f));
	}

	public static Vector3f getRandomUnitVector()
	{
		double z = 2.0 * Math.random() - 1.0;
		double theta = 2.0 * Math.PI * Math.random();
		double r = Math.sqrt(1 - z * z);

		return new Vector3f((float) (r * Math.cos(theta)), (float) (r * Math.sin(theta)), (float) z);
	}

	public static int clamp(int v, int min, int max)
	{
		assert (max >= min);
		return Math.max(Math.min(v, max), min);
	}

	public static float clamp(float v, float min, float max)
	{
		assert (max >= min);
		return Math.max(Math.min(v, max), min);
	}

	public static double clamp(double v, double min, double max)
	{
		assert (max >= min);
		return Math.max(Math.min(v, max), min);
	}

	public static float lerp(float alpha, float a, float b)
	{
		alpha = clamp(alpha, 0.0f, 1.0f);
		return a + (b - a) * alpha;
	}

	public static float lerp(float v, float min, float max, float a, float b)
	{
		assert (max >= min);
		float alpha = (v - min) / (max - min);
		alpha = clamp(alpha, 0.0f, 1.0f);
		return a + (b - a) * alpha;
	}

	public static float interp(float current, float goal, float fraction, double deltaTime)
	{
		//	return lerp(a, b, 1 - (float)Math.pow(fraction, deltaTime));
		if (Math.abs(goal - current) < SMALL_NUMBER)
			return goal;
		return lerp((float) (fraction * deltaTime), current, goal);
	}

	public static float interpR(float a, float b, float fraction, double deltaTime)
	{
		float ax = (float) Math.cos(Math.toRadians(a));
		float az = (float) Math.sin(Math.toRadians(a));

		float bx = (float) Math.cos(Math.toRadians(b));
		float bz = (float) Math.sin(Math.toRadians(b));

		float dx = interp(ax, bx, fraction, deltaTime);
		float dz = interp(az, bz, fraction, deltaTime);

		return (float) Math.toDegrees(Math.atan2(dz, dx));
	}
}
