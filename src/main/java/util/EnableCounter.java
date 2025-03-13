package util;

public class EnableCounter
{
	private int count = 0;

	public void increment()
	{
		count++;
		assert (count != 0);
	}

	public void decrement()
	{
		assert (count > 0);
		count--;
	}

	public boolean enabled()
	{
		return count > 0;
	}

	public boolean disabled()
	{
		return count == 0;
	}
}
