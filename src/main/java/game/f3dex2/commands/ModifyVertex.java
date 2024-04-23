package game.f3dex2.commands;

import app.input.InvalidInputException;
import game.DataUtils;
import game.f3dex2.BaseF3DEX2;
import game.f3dex2.DisplayList.CommandType;

/*
	Modifies a four-byte portion of the vertex specified.

	02 ww nn nn vv vv vv vv

	w 	Enumerated set of values specifying what to change
	n 	Vertex buffer index of vertex to modify (*2)
	v 	New value to insert

	The portion modified is specified by w, and the new value is given in v. Lighting calculations (if enabled) and position transformations are not calculated by the RSP after use of this command, so modifications modify final color and vertices.

	The valid values for w have names as follows:

	    G_MWO_POINT_RGBA = 0x10 Modifies the color of the vertex
	    G_MWO_POINT_ST = 0x14 Modifies the texture coordinates
	    G_MWO_POINT_XYSCREEN = 0x18 Modifies the X and Y position
	    G_MWO_POINT_ZSCREEN = 0x1C Modifies the Z position (lower four nybbles of v should always be zero for this modification)
 */
public class ModifyVertex extends BaseF3DEX2
{
	public static String[] optLocations = {
			"G_MWO_POINT_RGBA",
			"G_MWO_POINT_ST",
			"G_MWO_POINT_XYSCREEN",
			"G_MWO_POINT_ZSCREEN"
	};

	public static int[] valLocations = {
			0x10,
			0x14,
			0x18,
			0x1C
	};

	public int pos;
	public int location;
	public int value;

	public ModifyVertex(CommandType cmd, Integer ... args) throws InvalidInputException
	{
		super(cmd, args, 2);

		location = (args[0] >> 16) & 0xFF;
		pos = (args[0] & 0xFFFF) >> 1;
		value = args[1];
	}

	public ModifyVertex(CommandType cmd, String ... params) throws InvalidInputException
	{
		super(cmd, params, 3);

		pos = DataUtils.parseIntString(params[0]);

		if (pos < 0 || pos >= 32)
			throw new InvalidInputException("%d is not a valid position in the vertex buffer!", pos);

		location = -1;
		for (int i = 0; i < optLocations.length; i++) {
			if (optLocations[i].equalsIgnoreCase(params[1]))
				location = valLocations[i];
		}
		if (location == -1)
			location = DataUtils.parseIntString(params[1]);

		value = DataUtils.parseIntString(params[2]);
	}

	@Override
	public int[] assemble()
	{
		int[] encoded = new int[2];
		encoded[0] = opField;

		encoded[0] |= (location & 0xFF) << 16;
		encoded[0] |= (pos << 1) & 0xFFFF;

		encoded[1] = value;

		return encoded;
	}

	@Override
	public String getString()
	{
		// gsSPModifyVertex(vbidx, where, val)

		String locationName = null;
		for (int i = 0; i < valLocations.length; i++) {
			if (location == valLocations[i])
				locationName = optLocations[i];
		}
		if (locationName == null)
			locationName = String.format("%X", location);

		return String.format("%-16s (%X, %s, %08X)", getName(), pos, locationName, value);
	}
}
