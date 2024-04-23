package game.map.editor.render;

import java.util.ArrayList;
import java.util.List;

import game.map.mesh.Triangle;
import game.map.tree.MapObjectNode;

public class PreviewGeneratorFromTriangles extends PreviewGeometry
{
	public MapObjectNode<?> parentObj;
	public List<Triangle> triangles = new ArrayList<>();

	@Override
	public void init()
	{
		super.init();
		parentObj = null;
		triangles.clear();
	}

	@Override
	public void clear()
	{
		super.clear();
		parentObj = null;
		triangles.clear();
	}
}
