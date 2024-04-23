package game.map.tree;

import game.map.hit.Zone;

public class ZoneTreeModel extends MapObjectTreeModel<Zone>
{
	public ZoneTreeModel()
	{
		super(Zone.createDefaultRoot().getNode());
	}

	public ZoneTreeModel(MapObjectNode<Zone> root)
	{
		super(root);
	}

	@Override
	public void recalculateIndicies()
	{
		getRoot().reassignIndexDepthFirstPost(0);
	}
}
