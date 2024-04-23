package game.yay0;

public class EncodeLink implements Encode
{
	public final int length;
	public final int distance;

	public EncodeLink(int length, int distance)
	{
		this.length = length;
		this.distance = distance;
	}

	@Override
	public void exec(Yay0Encoder encoder)
	{
		encoder.addLink(length, distance);
	}

	@Override
	public int getEncodeLength()
	{ return length; }

	@Override
	public int getBudgetCost()
	{ return (length > 17) ? 3 : 2; }

	@Override
	public String toString()
	{
		return String.format("<L:%d D:%d>", length, distance);
	}
}
