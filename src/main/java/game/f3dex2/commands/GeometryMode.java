package game.f3dex2.commands;

import app.input.InvalidInputException;
import game.f3dex2.BaseF3DEX2;
import game.f3dex2.DisplayList.CommandType;

/*
	Enables and disables certain geometry parameters (ex. lighting, front-/backface culling, Z-buffer).

	D9 [CC CC CC] [SS SS SS SS]
 */

public class GeometryMode extends BaseF3DEX2
{
	public boolean clear = false;
	public int flags = 0;

	private static enum Flag
	{
		// @formatter:off
		G_ZBUFFER				(0),
		G_SHADE					(2),
		G_CULL_FRONT			(9),
		G_CULL_BACK				(10),
		G_FOG					(16),
		G_LIGHTING				(17),
		G_TEXTURE_GEN			(18),
		G_TEXTURE_GEN_LINEAR	(19),
		G_LOD					(20),
		G_SHADING_SMOOTH		(21),
		G_CLIPPING				(23);
		// @formatter:on

		private final int mask;

		private Flag(int shift)
		{
			mask = 1 << shift;
		}

		@Override
		public String toString()
		{
			return name();
		}
	}

	public GeometryMode(CommandType cmd, Integer ... args) throws InvalidInputException
	{
		super(cmd, args, 2);

		clear = (args[1] == 0);
		if (!clear && (args[0] != 0xD9FFFFFF))
			throw new InvalidInputException("%s mixes set and clear bits: %08X %08X", getName(), args[0], args[1]);

		flags = clear ? ~(args[0] | 0xFF000000) : args[1];

		/*
		int bits = flags;
		for(Flag flag : Flag.values())
		{
			if((flag.mask & bits) != 0)
				bits ^= flag.mask;
		}
		*/
	}

	public GeometryMode(CommandType cmd, String ... params) throws InvalidInputException
	{
		super(cmd, params, -1);

		if (params.length < 2)
			throw new InvalidInputException("%s requires at least two parameters, read %d", getName(), params.length);

		if (params[0].equalsIgnoreCase("CLEAR"))
			clear = true;
		else if (params[0].equalsIgnoreCase("SET"))
			clear = false;
		else
			throw new InvalidInputException("%s mode must be 'Set' or 'Clear', read %d", getName(), params[0]);

		if (clear && params[1].equalsIgnoreCase("ALL")) {
			flags = 0xFFFFFF;
			return;
		}

		flags = 0;
		args:
		for (int i = 1; i < params.length; i++) {
			for (Flag flag : Flag.values()) {
				if (flag.toString().equalsIgnoreCase(params[i])) {
					flags |= flag.mask;
					continue args;
				}
			}

			throw new InvalidInputException("%s includes unknown flag: %s", getName(), params[i]);
		}
	}

	@Override
	public int[] assemble()
	{
		int[] encoded = new int[2];

		if (clear) {
			encoded[0] = opField;
			encoded[1] = 0;

			if (flags != 0xFFFFFF)
				encoded[0] = opField | (~flags & 0xFFFFFF);
		}
		else {
			encoded[0] = 0xD9FFFFFF;
			encoded[1] = flags;
		}

		return encoded;
	}

	@Override
	public String getString()
	{
		StringBuilder sb = new StringBuilder(String.format("%-16s (%s", getName(), clear ? "Clear" : "Set"));

		if (clear && flags == 0xFFFFFF) {
			sb.append(", ALL");
		}
		else {
			int bits = flags;
			for (Flag flag : Flag.values()) {
				if ((flag.mask & bits) != 0) {
					sb.append(", ");
					sb.append(flag.toString());
					bits ^= flag.mask;
				}
			}
		}

		sb.append(")");

		return sb.toString();
	}
}
