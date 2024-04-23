package game.map.editor;

public class Grid
{
	public boolean binary;
	public int power;

	private static final int MAX_BIN_PWR = 9; // 512
	private static final int MAX_DEC_PWR = 5; // 500

	public Grid(boolean binary, int power)
	{
		this.binary = binary;
		this.power = power;
	}

	public void toggleType()
	{
		if (binary) {
			switch (power) {
				// @formatter:off
				case 0: power = 0; break; // 1 -> 1
				case 1: power = 0; break; // 2 -> 1
				case 2: power = 1; break; // 4 -> 5
				case 3: power = 2; break; // 8 -> 10
				case 4: power = 2; break; // 16 -> 10
				case 5: power = 3; break; // 32 -> 50
				case 6: power = 3; break; // 64 -> 50
				case 7: power = 4; break; // 128 -> 100
				case 8: power = 4; break; // 256 -> 100
				case 9: power = 5; break; // 512 -> 500
				// @formatter:on
			}
		}
		else {
			switch (power) {
				// @formatter:off
				case 0: power = 0; break; // 1 -> 1
				case 1: power = 2; break; // 5 -> 4
				case 2: power = 3; break; // 10 -> 8
				case 3: power = 6; break; // 50 -> 64
				case 4: power = 7; break; // 100 -> 128
				case 5: power = 9; break; // 500 -> 512
				// @formatter:on
			}
		}

		binary = !binary;
	}

	public void increasePower()
	{
		int max = getMaxPower();
		power++;
		if (power > max)
			power = max;
	}

	public void decreasePower()
	{
		power--;
		if (power < 0)
			power = 0;
	}

	public int getPower()
	{ return power; }

	private int getMaxPower()
	{ return binary ? MAX_BIN_PWR : MAX_DEC_PWR; }

	public int getSpacing()
	{ return getGridSpacing(power); }

	public int getSpacing(int width)
	{
		int pwr = power;
		int spacing = getGridSpacing(pwr);

		// prevent too many lines from drawing
		while (pwr < getMaxPower() && (width / (double) spacing) > 200.0)
			spacing = getGridSpacing(++pwr);

		return spacing;
	}

	private int getGridSpacing(int pwr)
	{
		return binary ? getBinaryGridSpacing(pwr) : getDecimalGridSpacing(pwr);
	}

	protected int getBinaryGridSpacing(int pwr)
	{
		if (pwr < 0)
			pwr = 0;
		if (pwr > MAX_BIN_PWR)
			pwr = MAX_BIN_PWR;
		return 1 << pwr;
	}

	protected int getDecimalGridSpacing(int pwr)
	{
		if (pwr < 0)
			pwr = 0;
		if (pwr > MAX_DEC_PWR)
			pwr = MAX_DEC_PWR;

		switch (pwr) {
			// @formatter:off
			case 0: return 1;
			case 1: return 5;
			case 2: return 10;
			case 3: return 50;
			case 4: return 100;
			case 5: return 500;
			case 6: return 1000;
			case 7: return 5000;
			case 8: return 10000;
			// @formatter:on
		}

		return Short.MAX_VALUE; // 32767
	}
}
