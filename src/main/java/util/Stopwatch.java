package util;

public class Stopwatch
{
	private long t0;

	public Stopwatch()
	{
		reset();
	}

	public void reset()
	{
		t0 = System.nanoTime();
	}

	public double ms()
	{
		long t1 = System.nanoTime();
		return (t1 - t0) / 1e6;
	}

	public double us()
	{
		long t1 = System.nanoTime();
		return (t1 - t0) / 1e3;
	}
}
