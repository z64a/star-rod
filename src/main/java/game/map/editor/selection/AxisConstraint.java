package game.map.editor.selection;

public class AxisConstraint
{
	public final boolean allowX, allowY, allowZ;

	public AxisConstraint(boolean x, boolean y, boolean z)
	{
		allowX = x;
		allowY = y;
		allowZ = z;
	}
}
