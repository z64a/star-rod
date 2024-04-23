package game.map.editor.render;

public enum PresetColor
{
	// @formatter:off
	WHITE		(1.0f, 1.0f, 1.0f),
	RED			(1.0f, 0.0f, 0.0f),
	GREEN		(0.0f, 1.0f, 0.0f),
	BLUE		(0.0f, 0.0f, 1.0f),
	YELLOW		(1.0f, 1.0f, 0.0f),
	TEAL		(0.0f, 1.0f, 1.0f),
	PINK		(1.0f, 0.4f, 0.8f), // more pleasing than magenta
	DARK_BLUE	(0.0f, 0.4f, 0.8f);
	// @formatter:on

	public final float r;
	public final float g;
	public final float b;

	private PresetColor(float r, float g, float b)
	{
		this.r = r;
		this.g = g;
		this.b = b;
	}
}
