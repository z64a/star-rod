package game.map.impex;

public enum ImpexFormat
{
	PREFAB("prefab"),
	OBJ("obj"),
	FBX("fbx");

	private final String name;

	private ImpexFormat(String name)
	{
		this.name = name;
	}

	@Override
	public String toString()
	{
		return name;
	}
}
