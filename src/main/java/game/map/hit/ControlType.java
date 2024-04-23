package game.map.hit;

public enum ControlType
{
	TYPE_0(0, true, "0 Fixed Orientation", "Disable Forward Motion",
		"Camera follows the player, using a fixed yaw position."),
	TYPE_1(1, true, "1 Look Toward Point", "Constrain to Fixed Radius",
		"Camera faces toward or away from a point with the player in the center of the frame. Use a negative boom length to look away from a point."),
	TYPE_2(2, true, "2 Boundary Camera", "Freeze Camera Position",
		"Camera is prevented from moving past a fixed line. Use these near exits to stop camera movement."),
	TYPE_3(3, false, "3 Follow Player", "Unused",
		"Follows the player using whatever yaw value the camera initially possessed."),
	TYPE_4(4, false, "4 Fixed Position and Orientation", "Unused",
		"Both position and yaw are fixed."),
	TYPE_5(5, true, "5 Look Toward Point, Constrain to Line", "Freeze Target at Point",
		""),
	TYPE_6(6, true, "6 Constrain between Points", "Disable Forward Motion",
		"Camera position is contrained to a line segement, with yaw perpendicular to the line segment.");

	public final int index;
	public final String name;
	public final String desc;
	public final String flagName;
	public final boolean usesFlag; // not REALLY true, all types use the flag but the purpose for 3/4 is not known

	private ControlType(int value, boolean usesFlag, String name, String flagName, String desc)
	{
		this.index = value;
		this.name = name;
		this.desc = desc;
		this.flagName = flagName;
		this.usesFlag = usesFlag;
	}

	@Override
	public String toString()
	{
		return name;
	}

	public static ControlType getType(int index)
	{
		switch (index) {
			case 0:
				return TYPE_0;
			case 1:
				return TYPE_1;
			case 2:
				return TYPE_2;
			case 3:
				return TYPE_3;
			case 4:
				return TYPE_4;
			case 5:
				return TYPE_5;
			case 6:
				return TYPE_6;
			default:
				throw new IllegalArgumentException("No camera type for index " + index);
		}
	}
}
