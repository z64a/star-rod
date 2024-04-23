package game.map.tree;

import game.map.marker.Marker;

public class MarkerTreeModel extends MapObjectTreeModel<Marker>
{
	public MarkerTreeModel()
	{
		super(Marker.createDefaultRoot().getNode());
	}

	public MarkerTreeModel(MapObjectNode<Marker> root)
	{
		super(root);
	}

	@Override
	public void recalculateIndicies()
	{
		getRoot().reassignIndexDepthFirstPost(-1);
	}
}
