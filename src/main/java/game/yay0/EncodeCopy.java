package game.yay0;

public class EncodeCopy implements Encode
{
	public final byte value;

	public EncodeCopy(byte b)
	{
		value = b;
	}

	@Override
	public void exec(Yay0Encoder encoder)
	{
		encoder.addCopy(value);
	}

	@Override
	public int getEncodeLength()
	{ return 1; }

	@Override
	public int getBudgetCost()
	{ return 1; }

	@Override
	public String toString()
	{
		return String.format("<%02X>", value);
	}
}
