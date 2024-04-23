package game.map.editor.render;

public enum PreviewOriginMode
{
	// @formatter:off
	CURSOR		("3D Cursor Position"),
	SELECTION	("Center of Selection"),
	ORIGIN		("World Origin");
	// @formatter:on

	private final String name;

	private PreviewOriginMode(String name)
	{
		this.name = name;
	}

	@Override
	public String toString()
	{
		return name;
	}
}
