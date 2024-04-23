package game.map.editor;

public class UVGrid extends Grid
{
	private static final int MAX_PWR = 11; // 2048

	public UVGrid(int power)
	{
		super(true, power);
	}

	@Override
	public void toggleType()
	{
		throw new UnsupportedOperationException("UV grid is binary only.");
	}

	@Override
	public void increasePower()
	{
		power++;
		if (power > MAX_PWR)
			power = MAX_PWR;
	}

	@Override
	protected int getBinaryGridSpacing(int pwr)
	{
		if (pwr < 0)
			pwr = 0;
		if (pwr > MAX_PWR)
			pwr = MAX_PWR;
		return 1 << pwr;
	}

	@Override
	protected int getDecimalGridSpacing(int pwr)
	{
		throw new UnsupportedOperationException("UV grid is binary only.");
	}
}
