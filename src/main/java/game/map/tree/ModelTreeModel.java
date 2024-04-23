package game.map.tree;

import game.map.shape.Model;

public class ModelTreeModel extends MapObjectTreeModel<Model>
{
	public ModelTreeModel()
	{
		this(Model.createDefaultRoot().getNode());
	}

	public ModelTreeModel(MapObjectNode<Model> root)
	{
		super(root);
	}

	@Override
	public void recalculateIndicies()
	{
		getRoot().reassignIndexDepthFirstPost(0);
	}
}
