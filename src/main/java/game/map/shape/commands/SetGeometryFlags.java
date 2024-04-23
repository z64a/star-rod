package game.map.shape.commands;

import game.map.mesh.AbstractMesh;
import renderer.shaders.RenderState;

// D9FFFFFF 00XXXXXX (ie, D9FFFFFF 00200000)
public class SetGeometryFlags extends ChangeGeometryFlags
{
	public SetGeometryFlags(AbstractMesh parentMesh)
	{
		this(parentMesh, 0xD9FFFFFF, 0);
	}

	public SetGeometryFlags(AbstractMesh parentMesh, int r, int s)
	{
		super(parentMesh);
		assert (r == 0xD9FFFFFF);
		setFlags(s);
	}

	@Override
	public void doGL()
	{
		//	if(useLighting)		glEnable(GL_LIGHTING);
		//	if(smoothShading)	glShadeModel(GL_SMOOTH);
		if (cullBack)
			RenderState.setEnabledCullFace(true);
	}

	@Override
	public String toString()
	{
		return String.format("Set geometry flags: %08X", getFlags());
	}

	@Override
	public CmdType getType()
	{ return CmdType.SetGeometryFlags; }

	@Override
	public int[] getF3DEX2Command()
	{ return new int[] { 0xD9FFFFFF, getFlags() }; }

	@Override
	public DisplayCommand deepCopy()
	{
		return new SetGeometryFlags(parentMesh, 0xD9FFFFFF, getFlags());
	}
}
