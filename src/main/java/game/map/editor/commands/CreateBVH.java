package game.map.editor.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;

import common.Vector3f;
import common.commands.AbstractCommand;
import game.map.BoundingBox;
import game.map.MapObject;
import game.map.MapObject.HitType;
import game.map.editor.MapEditor;
import game.map.hit.Collider;
import game.map.tree.MapObjectNode;

//XXX just bad overall
public class CreateBVH extends AbstractCommand
{
	private final List<Collider> colliders;
	private final IdentityHashMap<Collider, MapObjectNode<Collider>> oldParents;
	private final IdentityHashMap<Collider, MapObjectNode<Collider>> newParents;
	private final Collider bvh;

	public CreateBVH(List<Collider> colliders)
	{
		super("Create BVH");

		this.colliders = colliders;
		oldParents = new IdentityHashMap<>();
		newParents = new IdentityHashMap<>();

		// sort, get child at .... etc
		// save/restore tree would be better!

		for (Collider c : colliders)
			oldParents.put(c, c.getNode().parentNode);

		BoundingHierarchy hierarchy = new BoundingHierarchy(colliders);

		bvh = hierarchy.put(null, newParents);
		bvh.setName("BVH Root");
	}

	@Override
	public void exec()
	{
		super.exec();

		MapEditor editor = MapEditor.instance();

		for (Collider c : colliders)
			newParents.get(c).add(c.getNode());

		editor.map.create(bvh);
		editor.selectionManager.createObject(bvh);
	}

	@Override
	public void undo()
	{
		super.undo();

		MapEditor editor = MapEditor.instance();

		//XXX

		for (Collider c : colliders)
			c.getNode().parentNode = oldParents.get(c);
	}

	private static class BoundingHierarchy
	{
		private BoundingHierarchy[] children;
		private List<Collider> objects;
		private BoundingBox bb;

		public BoundingHierarchy(List<Collider> objects)
		{
			this.objects = objects;
			bb = new BoundingBox();
			for (MapObject obj : objects)
				bb.encompass(obj.AABB);
			Vector3f size = bb.getSize();

			if (objects.size() > 3 && (size.x > 100 || size.z > 100))
				divide();
		}

		public Collider put(MapObjectNode<Collider> parent, IdentityHashMap<Collider, MapObjectNode<Collider>> parentMap)
		{
			Collider bvh = new Collider(HitType.GROUP);
			bvh.setName("BVH");

			if (parent != null)
				parent.add(bvh.getNode());

			if (children != null) {
				children[0].put(bvh.getNode(), parentMap);
				children[1].put(bvh.getNode(), parentMap);
			}
			else {
				for (Collider c : objects)
					parentMap.put(c, bvh.getNode());
			}

			return bvh;
		}

		public void divide()
		{
			children = new BoundingHierarchy[2];
			List<Collider> left = new ArrayList<>();
			List<Collider> right = new ArrayList<>();

			Vector3f size = bb.getSize();
			if (size.x > size.z) {
				// divide along x
				Collections.sort(objects, (a, b) -> (int) a.AABB.getCenter().x - (int) b.AABB.getCenter().x);
			}
			else {
				// divide along z
				Collections.sort(objects, (a, b) -> (int) a.AABB.getCenter().z - (int) b.AABB.getCenter().z);
			}

			int median = objects.size() / 2;
			for (int i = 0; i < median; i++)
				left.add(objects.get(i));
			for (int i = median; i < objects.size(); i++)
				right.add(objects.get(i));

			children[0] = new BoundingHierarchy(left);
			children[1] = new BoundingHierarchy(right);
		}

		public void print()
		{
			print("");
		}

		public void print(String tabs)
		{
			System.out.println(tabs + "@");
			tabs = tabs + "\t";
			if (children != null) {
				children[0].print(tabs);
				children[1].print(tabs);
			}
			else {
				for (Collider c : objects)
					System.out.println(tabs + c);
			}
		}
	}
}
