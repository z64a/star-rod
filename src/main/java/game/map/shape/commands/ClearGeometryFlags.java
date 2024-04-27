package game.map.shape.commands;

import game.map.mesh.AbstractMesh;
import renderer.shaders.RenderState;

// D9FFFFFF 00XXXXXX (ie, D9FFFFFF 00200000)
public class ClearGeometryFlags extends ChangeGeometryFlags
{
	public ClearGeometryFlags(AbstractMesh parentMesh)
	{
		this(parentMesh, 0xD9FFFFFF, 0);
	}

	public ClearGeometryFlags(AbstractMesh parentMesh, int r, int s)
	{
		super(parentMesh);
		assert (s == 0);
		setFlags(~(r | 0xFF000000));
	}

	@Override
	public void doGL()
	{
		//	if(useLighting)		glEnable(GL_LIGHTING);
		//	if(smoothShading)	glShadeModel(GL_FLAT);
		if (cullBack)
			RenderState.setEnabledCullFace(false);
	}

	@Override
	public String toString()
	{
		return String.format("Clear geometry flags: %08X", getFlags());
	}

	@Override
	public CmdType getType()
	{
		return CmdType.SetGeometryFlags;
	}

	@Override
	public int[] getF3DEX2Command()
	{
		int flags = ~getFlags() & 0x00FFFFFF;
		return new int[] { 0xD9000000 | flags, 0 };
	}

	@Override
	public DisplayCommand deepCopy()
	{
		return new ClearGeometryFlags(parentMesh, 0xD9000000 | ~getFlags(), 0);
	}
}
