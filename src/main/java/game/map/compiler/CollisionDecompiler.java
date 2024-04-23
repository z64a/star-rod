package game.map.compiler;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import app.input.IOUtils;
import game.map.Map;
import game.map.MapObject.HitType;
import game.map.hit.CameraZoneData;
import game.map.hit.Collider;
import game.map.hit.Zone;
import game.map.mesh.Triangle;
import game.map.mesh.Vertex;
import game.map.tree.ColliderTreeModel;
import game.map.tree.MapObjectNode;
import game.map.tree.ZoneTreeModel;
import util.Logger;

public class CollisionDecompiler
{
	private Map map;
	private ByteBuffer bb;
	private List<Vertex> vertexList;

	public CollisionDecompiler(Map map, File source) throws IOException
	{
		this.map = map;
		bb = IOUtils.getDirectBuffer(source);

		int[] fileHeader = new int[4];
		for (int i = 0; i < fileHeader.length; i++)
			fileHeader[i] = bb.getInt();

		if (fileHeader[0] != 0)
			readColliders(fileHeader[0]);

		if (fileHeader[1] != 0)
			readZones(fileHeader[1]);
	}

	private void readColliders(int offset)
	{
		bb.position(offset);

		int numColliders = bb.getInt() >> 16;
		int colliderTableOffset = bb.getInt();

		int numVertices = bb.getInt() >> 16;
		int vertexTableOffset = bb.getInt();

		bb.getInt(); // number of bounding boxes ALWAYS equals number of colliders
		int boxTableOffset = bb.getInt();

		bb.position(vertexTableOffset);
		vertexList = new ArrayList<>();
		for (int i = 0; i < numVertices; i++) {
			Vertex v = new Vertex(bb.getShort(), bb.getShort(), bb.getShort());
			vertexList.add(v);
		}

		Collider rootCollider = Collider.createDefaultRoot();
		map.colliderTree = new ColliderTreeModel(rootCollider.getNode());

		List<MapObjectNode<Collider>> nodes = new ArrayList<>(numColliders);
		int[] children = new int[numColliders]; // keep track of children for each node
		int[] next = new int[numColliders]; // keep track of siblings for each node
		int[] parent = new int[numColliders]; // keep track of parents for each node

		// read all child/sibiling relationships
		for (int i = 0; i < numColliders; i++) {
			bb.position(colliderTableOffset + i * 0xC);
			int fileOffset = bb.position();

			int boxEntryOffset = 4 * bb.getShort();
			next[i] = bb.getShort();
			children[i] = bb.getShort();
			int numTriangles = bb.getShort();
			int triangleOffset = bb.getInt();

			Collider c = null;

			// group
			if (numTriangles == 0) {
				c = new Collider(HitType.GROUP);
			}
			// collider
			else {
				c = new Collider(HitType.HIT);

				bb.position(triangleOffset);
				for (int j = 0; j < numTriangles; j++) {
					Triangle t = makeTriangle(vertexList, bb.getInt());
					c.mesh.batch.triangles.add(t);
				}
			}

			bb.position(boxTableOffset + boxEntryOffset);
			Vertex v1 = new Vertex((int) bb.getFloat(), (int) bb.getFloat(), (int) bb.getFloat());
			Vertex v2 = new Vertex((int) bb.getFloat(), (int) bb.getFloat(), (int) bb.getFloat());

			c.AABB.encompass(v1);
			c.AABB.encompass(v2);
			int flagBits = bb.getInt();
			c.flags.set(flagBits & ~0xFF);
			c.surface.set(flagBits & 0xFF);

			c.dumped = true;
			c.fileOffset = fileOffset;

			// don't be confused -- this handles parent field of collider's mesh
			c.updateMeshHierarchy();

			nodes.add(c.getNode());
			parent[i] = -1;
			c.setName(map.d_colliderNames.poll());

			switch (c.flags.get()) {
				case 0x00000000: // most common (default?) case
				case 0x00008000: // often used on deilitw, deilite, etc
				case 0x00018000: // often used on ttw, tte, etc triggers
					break;
				default:
					Logger.logfWarning("Unknown collider flags: %08X %6s %s\n", c.flags.get(), map.getName(), c.getName());
			}
		}

		MapObjectNode<Collider> parentNode = null;
		MapObjectNode<Collider> childNode = null;

		// add children to their parents
		for (int i = 0; i < numColliders; i++) {
			int child = children[i];

			while (child != -1) {
				parentNode = nodes.get(i);
				childNode = nodes.get(child);
				parent[child] = i;

				childNode.parentNode = parentNode;
				childNode.childIndex = parentNode.getChildCount();
				parentNode.add(childNode);

				if (next[child] != -1)
					child = next[child];
				else
					child = -1;
			}
		}

		// remaining orphans are children of the root
		for (int i = 0; i < numColliders; i++) {
			if (parent[i] == -1) {
				childNode = nodes.get(i);
				childNode.parentNode = rootCollider.getNode();
				childNode.childIndex = rootCollider.getNode().getChildCount();
				rootCollider.getNode().add(childNode);
			}
		}

		map.colliderTree.recalculateIndicies();
	}

	private void readZones(int offset)
	{
		bb.position(offset);

		int numZones = bb.getInt() >> 16;
		int zoneTableOffset = bb.getInt();

		int numVertices = bb.getInt() >> 16;
		int vertexTableOffset = bb.getInt();

		bb.getInt(); // we don't need to know the number of cameras
		int camTableOffset = bb.getInt();

		bb.position(vertexTableOffset);
		vertexList = new ArrayList<>();
		for (int i = 0; i < numVertices; i++) {
			Vertex v = new Vertex(bb.getShort(), bb.getShort(), bb.getShort());
			vertexList.add(v);
		}

		Zone rootZone = Zone.createDefaultRoot();
		map.zoneTree = new ZoneTreeModel(rootZone.getNode());

		List<MapObjectNode<Zone>> nodes = new ArrayList<>(numZones);
		int[] children = new int[numZones]; // keep track of children for each node
		int[] next = new int[numZones]; // keep track of siblings for each node
		int[] parent = new int[numZones]; // keep track of parents for each node

		// read all child/sibiling relationships
		for (int i = 0; i < numZones; i++) {
			bb.position(zoneTableOffset + i * 0xC);
			int fileOffset = bb.position();

			int camEntryOffset = bb.getShort();
			next[i] = bb.getShort();
			children[i] = bb.getShort();
			int numTriangles = bb.getShort();

			Zone z = null;

			// group
			if (numTriangles == 0) {
				z = new Zone(HitType.GROUP);
			}
			// zone
			else {
				z = new Zone(HitType.HIT);

				bb.position(bb.getInt());
				for (int j = 0; j < numTriangles; j++) {
					Triangle t = makeTriangle(vertexList, bb.getInt());
					z.mesh.batch.triangles.add(t);
				}
			}

			z.dumped = true;
			z.fileOffset = fileOffset;
			z.setName(String.format("%04X", nodes.size()));

			z.updateMeshHierarchy();

			if (camEntryOffset != -1) {
				z.hasCameraData.set(true);
				bb.position(camTableOffset + 4 * camEntryOffset);
				int[] cameraData = new int[11];
				for (int j = 0; j < cameraData.length; j++)
					cameraData[j] = bb.getInt();
				z.camData = new CameraZoneData(z, cameraData);
			}

			nodes.add(z.getNode());
			parent[i] = -1;
			z.setName(map.d_zoneNames.poll());
		}

		MapObjectNode<Zone> parentNode = null;
		MapObjectNode<Zone> childNode = null;

		// add children to their parents
		for (int i = 0; i < numZones; i++) {
			int child = children[i];

			while (child != -1) {
				parentNode = nodes.get(i);
				childNode = nodes.get(child);
				parent[child] = i;

				childNode.parentNode = parentNode;
				childNode.childIndex = parentNode.getChildCount();
				parentNode.add(childNode);

				if (next[child] != -1)
					child = next[child];
				else
					child = -1;
			}
		}

		// remaining orphans are children of the root
		for (int i = 0; i < numZones; i++) {
			if (parent[i] == -1) {
				childNode = nodes.get(i);
				childNode.parentNode = rootZone.getNode();
				childNode.childIndex = rootZone.getNode().getChildCount();
				rootZone.getNode().add(childNode);
			}
		}

		map.zoneTree.recalculateIndicies();
	}

	private static Triangle makeTriangle(List<Vertex> vertexList, int word)
	{
		assert ((word & 0x80000000) == 0); // MSB is unused

		Vertex v1 = vertexList.get(word & 0x3FF);
		Vertex v2 = vertexList.get((word >>> 10) & 0x3FF);
		Vertex v3 = vertexList.get((word >>> 20) & 0x3FF);

		Triangle t = new Triangle(v1, v2, v3);
		t.doubleSided = (word & 0x40000000) == 0;
		return t;
	}
}
