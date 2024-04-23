package game.entity;

public enum EntityMenuGroup
{
	// @formatter:off
	Block		("Blocks"),
	HammerBlock ("Hammer Blocks"),
	Mechanism	("Mechanisms"),
	Misc		("Misc"),
	JanIwaOnly	("Only Jan/Iwa"),
	SbkOmoOnly	("Only Sbk/Omo"),
	OtherAreas	("Only Other Areas"),
	Hidden		("IGNORE ME");
	// @formatter:on

	public String menuName;

	private EntityMenuGroup(String name)
	{
		menuName = name;
	}

	@Override
	public String toString()
	{
		return menuName;
	}
}
