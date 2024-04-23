package game.map.shape;

public enum ModelReplaceType
{
	// @formatter:off
	None		("None"),
	RedFire		("Red Flame"),
	BlueFire1	("Blue Flame"),
	BlueFire2	("Small Blue Flame"),
	PinkFire	("Pink Flame");
	// @formatter:on

	public final String name;

	private ModelReplaceType(String name)
	{
		this.name = name;
	}

	@Override
	public String toString()
	{
		return name;
	}

	public static ModelReplaceType getType(int id)
	{
		switch (id) {
			case 2:
				return RedFire;
			case 1:
				return BlueFire1;
			case 3:
				return BlueFire2;
			case 4:
				return PinkFire;
			default:
				return None;
		}
	}

	public static int getID(ModelReplaceType type)
	{
		switch (type) {
			case RedFire:
				return 2;
			case BlueFire1:
				return 1;
			case BlueFire2:
				return 3;
			case PinkFire:
				return 4;
			default:
				return 0;
		}
	}
}
