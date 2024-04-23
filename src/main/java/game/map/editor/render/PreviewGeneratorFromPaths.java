package game.map.editor.render;

import java.util.ArrayList;
import java.util.List;

import game.map.marker.Marker;
import game.map.tree.MapObjectNode;

public class PreviewGeneratorFromPaths extends PreviewGeometry
{
	public MapObjectNode<?> parentObj;
	public List<Marker> paths = new ArrayList<>();

	@Override
	public void init()
	{
		super.init();
		parentObj = null;
		paths.clear();
	}

	@Override
	public void clear()
	{
		super.clear();
		parentObj = null;
		paths.clear();
	}
}
