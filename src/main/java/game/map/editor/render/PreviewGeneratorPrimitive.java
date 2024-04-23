package game.map.editor.render;

import game.map.editor.geometry.Vector3f;

import game.map.shape.TriangleBatch;
import game.map.tree.MapObjectNode;

public class PreviewGeneratorPrimitive extends PreviewGeometry
{
	public MapObjectNode<?> parentObj;
	public TriangleBatch targetBatch;

	public Vector3f center = new Vector3f();
	public PreviewOriginMode originMode = PreviewOriginMode.CURSOR;

	@Override
	public void init()
	{
		super.init();
		center.set(0, 0, 0);
		parentObj = null;
		targetBatch = null;
	}

	@Override
	public void clear()
	{
		super.clear();
		center.set(0, 0, 0);
		parentObj = null;
		targetBatch = null;
	}
}
