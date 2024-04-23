package game.map.shape.commands;

import game.map.mesh.AbstractMesh;

public abstract class DisplayCommand
{
	/*
	These are the only display commands that are used:

	 * DrawTriangleBatch
	01	F3DEX2_LOAD_VTX
	05	F3DEX2_DRAW_TRI
	06	F3DEX2_DRAW_TRIS

	 * RDP Pipe Sync
	E7	RDP_PIPE_SYNC

	 * Set Geometry Flags
	 * Clear Geometry Flags
	D9	F3DEX2_GEOMETRYMODE

	 * Matrix Commands (handled by model tree)
	D8	F3DEX2_POP_MATRIX
	DA	F3DEX2_LOAD_MATRIX

	 * No Associated Command
	DE	F3DEX2_START_DL
	DF	F3DEX2_END_DL
	 */

	public static enum CmdType
	{
		// @formatter:off
		DrawTriangleBatch	("Draw Triangles"),
		PipeSync			("RDP Pipe Sync"),
		SetGeometryFlags	("Set Geometry Flags"),
		ClrGeometryFlags	("Clear Geometry Flags"),
		Custom				("Custom Command");
		// @formatter:on

		private CmdType(String name)
		{
			this.name = name;
		}

		private final String name;

		@Override
		public String toString()
		{
			return name;
		}
	}

	public transient boolean selected = false;
	public transient AbstractMesh parentMesh;

	public DisplayCommand(AbstractMesh parentMesh)
	{
		this.parentMesh = parentMesh;
	}

	public static DisplayCommand resolveCommand(AbstractMesh parentMesh, int r, int s)
	{
		switch (r >>> 24) {
			case 0xE7:
				return new FlushPipeline(parentMesh);
			case 0xD9:
				return ChangeGeometryFlags.getCommand(parentMesh, r, s);
			default:
				return new CustomCommand(parentMesh, r, s);
		}
	}

	public void doGL()
	{}

	public abstract CmdType getType();

	public abstract int[] getF3DEX2Command();

	public abstract DisplayCommand deepCopy();
}
