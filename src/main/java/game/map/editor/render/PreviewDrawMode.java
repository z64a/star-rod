package game.map.editor.render;

public enum PreviewDrawMode
{
	// @formatter:off
	ANIM_EDGES	("Dashed Edges"),
	EDGES		("Solid Edges"),
	FILLED		("Surface");
	// @formatter:on

	private final String name;

	private PreviewDrawMode(String name)
	{
		this.name = name;
	}

	@Override
	public String toString()
	{
		return name;
	}
}
