package game.map.compiler;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

import game.map.editor.geometry.Vector3f;

import app.Directories;
import app.input.IOUtils;
import assets.AssetManager;
import game.map.BoundingBox;
import game.map.Map;
import game.map.hit.Collider;
import game.map.hit.HitObject;
import game.map.hit.Zone;
import game.map.mesh.Triangle;
import game.map.mesh.Vertex;
import game.map.tree.MapObjectNode;
import util.Logger;
import util.Priority;

public class CollisionCompiler
{
	public CollisionCompiler(Map map) throws IOException
	{
		File build_dec = new File(AssetManager.getMapBuildDir(), map.getName() + "_hit.bin");

		Logger.log("Compiling map collision to " + build_dec.getPath());

		if (build_dec.exists())
			build_dec.delete();

		RandomAccessFile raf = new RandomAccessFile(build_dec, "rw");

		int colliderHeaderOffset = compileColliders(raf, map);
		int zoneHeaderOffset = compileZones(raf, map);

		raf.seek(0);
		raf.writeInt(colliderHeaderOffset);
		raf.writeInt(zoneHeaderOffset);
		raf.close();

		File headerFile = Directories.PROJ_INCLUDE_MAPFS.file(map.getName() + "_hit.h");
		try (PrintWriter pw = IOUtils.getBufferedPrintWriter(headerFile)) {
			for (Collider c : map.colliderTree.getList()) {
				pw.printf("#define %-23s 0x%X%n", "COLLIDER_" + c.getName(), c.getNode().getTreeIndex());
			}
			pw.println();
			for (Zone z : map.zoneTree.getList()) {
				pw.printf("#define %-23s 0x%X%n", "ZONE_" + z.getName(), z.getNode().getTreeIndex());
			}
			pw.println();
		}
	}

	private int compileColliders(RandomAccessFile raf, Map map) throws IOException
	{
		MapObjectNode<Collider> colliderRoot = map.colliderTree.getRoot();
		int num = colliderRoot.countDescendents();
		ArrayList<Collider> colliderList = new ArrayList<>(num);

		constructArray(colliderList, colliderRoot);
		finalizeBoundingBoxes(colliderRoot);

		/* construct vertex list */

		ArrayList<SimpleVertex> uniqueVertexList = new ArrayList<>();
		HashMap<SimpleVertex, Integer> uniqueVertexMap = new HashMap<>();

		HashMap<Vertex, SimpleVertex> simpleVertexMap = new HashMap<>();

		for (Collider c : colliderList) {
			if (!c.hasMesh())
				continue;

			for (Triangle t : c.getMesh())
				for (Vertex v : t.vert) {
					SimpleVertex sv = new SimpleVertex(v);
					simpleVertexMap.put(v, sv);

					if (!uniqueVertexMap.containsKey(sv)) {
						uniqueVertexMap.put(sv, uniqueVertexList.size());
						uniqueVertexList.add(sv);
					}
				}
		}

		if (uniqueVertexList.size() > 1024) {
			String err = "Maximum number of vertices exceeded: (" + uniqueVertexList.size() + " / 1024).";
			Logger.log("Collision Compile Error: " + err, Priority.ERROR);
			throw new BuildException(err);
		}

		/* write vertices */

		int colliderVertexOffset = 0x10;
		raf.seek(colliderVertexOffset);

		for (SimpleVertex v : uniqueVertexList) {
			raf.writeShort((short) v.x);
			raf.writeShort((short) v.y);
			raf.writeShort((short) v.z);
		}

		// pad to alignment
		if ((uniqueVertexList.size() % 2) == 1)
			raf.writeShort(0);

		int endVertOffset = (int) raf.getFilePointer();
		Logger.logf("Wrote %d vertices (%X to %X).", uniqueVertexList.size(), 0x10, endVertOffset);

		/* write triangles */

		int triCount = 0;
		for (int i = colliderList.size() - 1; i >= 0; i--) {
			Collider c = colliderList.get(i);
			if (!c.hasMesh())
				continue;

			c.c_TriangleOffset = (int) raf.getFilePointer();
			for (Triangle t : c.getMesh()) {
				int index1 = uniqueVertexMap.get(simpleVertexMap.get(t.vert[0])) & 0x3FF;
				int index2 = uniqueVertexMap.get(simpleVertexMap.get(t.vert[1])) & 0x3FF;
				int index3 = uniqueVertexMap.get(simpleVertexMap.get(t.vert[2])) & 0x3FF;

				int triangle = t.doubleSided ? 0 : 0x40000000;
				triangle = triangle | index1;
				triangle = triangle | (index2 << 10);
				triangle = triangle | (index3 << 20);

				raf.writeInt(triangle);
				triCount++;
			}
		}

		int endTriOffset = (int) raf.getFilePointer();
		Logger.logf("Wrote %d triangles (%X to %X).", triCount, endVertOffset, endTriOffset);

		/* write meshes */

		int colliderMeshOffset = (int) raf.getFilePointer();
		short aabbOffset = 0; // not file offset, its the word offset in the aabb table
		for (Collider c : colliderList) {
			raf.writeShort(aabbOffset);
			raf.writeShort(c.c_NextIndex);
			raf.writeShort(c.c_ChildIndex);

			if (c.hasMesh()) {
				int triangleCount = c.mesh.batch.triangles.size();
				raf.writeShort(triangleCount);
				raf.writeInt(c.c_TriangleOffset);
			}
			else {
				raf.writeShort(0);
				raf.writeInt(0);
			}

			aabbOffset += 7;
		}

		int endColliderOffset = (int) raf.getFilePointer();
		Logger.logf("Wrote %d colliders (%X to %X).", colliderList.size(), endTriOffset, endColliderOffset);

		/* write bounding boxes */

		int colliderBoundingOffset = (int) raf.getFilePointer();
		for (Collider c : colliderList) {
			Vector3f min = c.AABB.getMin();
			Vector3f max = c.AABB.getMax();
			raf.writeFloat(min.x);
			raf.writeFloat(min.y);
			raf.writeFloat(min.z);
			raf.writeFloat(max.x);
			raf.writeFloat(max.y);
			raf.writeFloat(max.z);

			int flagBits = 0;
			flagBits |= c.flags.get() & ~0xFF;
			flagBits |= c.surface.get() & 0xFF;
			raf.writeInt(flagBits);
		}

		int endBoxesOffset = (int) raf.getFilePointer();
		Logger.logf("Wrote %d bounding boxes (%X to %X).", colliderList.size(), endColliderOffset, endBoxesOffset);

		/* write header */

		int colliderHeaderOffset = (int) raf.getFilePointer();

		raf.writeShort(colliderList.size());
		raf.writeShort(0);
		raf.writeInt(colliderMeshOffset);

		raf.writeShort(uniqueVertexList.size());
		raf.writeShort(0);
		raf.writeInt(colliderVertexOffset);

		raf.writeShort(colliderList.size() * 7);
		raf.writeShort(0);
		raf.writeInt(colliderBoundingOffset);

		while ((raf.length() & 0x0F) != 0)
			raf.write(0);

		return colliderHeaderOffset;
	}

	private int compileZones(RandomAccessFile raf, Map map) throws IOException
	{
		MapObjectNode<Zone> zoneRoot = map.zoneTree.getRoot();
		int num = zoneRoot.countDescendents();
		ArrayList<Zone> zoneList = new ArrayList<>(num);

		constructArray(zoneList, zoneRoot);
		finalizeBoundingBoxes(zoneRoot);

		/* construct vertex list */

		ArrayList<Vertex> vertexList = new ArrayList<>();
		HashMap<Vertex, Integer> vertexMap = new HashMap<>();

		for (Zone z : zoneList) {
			if (!z.hasMesh())
				continue;

			for (Triangle t : z.getMesh())
				for (Vertex v : t.vert)
					if (!vertexMap.containsKey(v)) {
						vertexMap.put(v, vertexList.size());
						vertexList.add(v);
					}
		}

		/* write vertices */

		int areaVertexOffset = (int) raf.getFilePointer();

		for (Vertex v : vertexList) {
			raf.writeShort((short) v.getCurrentX());
			raf.writeShort((short) v.getCurrentY());
			raf.writeShort((short) v.getCurrentZ());
		}

		// pad to alignment
		if ((vertexList.size() % 2) == 1)
			raf.writeShort(0);

		/* write triangles */

		for (int i = zoneList.size() - 1; i >= 0; i--) {
			Zone z = zoneList.get(i);
			if (!z.hasMesh())
				continue;

			z.c_TriangleOffset = (int) raf.getFilePointer();
			for (Triangle t : z.getMesh()) {
				int index1 = vertexMap.get(t.vert[0]) & 0x3FF;
				int index2 = vertexMap.get(t.vert[1]) & 0x3FF;
				int index3 = vertexMap.get(t.vert[2]) & 0x3FF;

				int triangle = t.doubleSided ? 0 : 0x40000000;
				triangle = triangle | index1;
				triangle = triangle | (index2 << 10);
				triangle = triangle | (index3 << 20);

				raf.writeInt(triangle);
			}
		}

		/* write meshes */

		int areaMeshOffset = (int) raf.getFilePointer();
		int cameraOffset = 0;
		for (Zone z : zoneList) {
			if (z.hasCameraData.get()) {
				raf.writeShort(cameraOffset);
				z.c_CameraOffset = cameraOffset * 4;
				cameraOffset += 11; // 11 words
			}
			else {
				raf.writeShort(-1);
			}

			raf.writeShort(z.c_NextIndex);
			raf.writeShort(z.c_ChildIndex);

			if (z.hasMesh()) {
				int triangleCount = z.mesh.batch.triangles.size();
				raf.writeShort(triangleCount);
				raf.writeInt(z.c_TriangleOffset);
			}
			else {
				raf.writeShort(0);
				raf.writeInt(0);
			}
		}

		/* write camera data -- could be out of order or duplicated etc. */
		int zoneDataOffset = (int) raf.getFilePointer();
		int zoneDataSize = 0;
		for (Zone z : zoneList) {
			if (z.c_CameraOffset >= 0) {
				raf.seek(zoneDataOffset + z.c_CameraOffset);
				for (int i : z.camData.getData())
					raf.writeInt(i);
				zoneDataSize += 0x2C;
			}
		}

		raf.seek(zoneDataOffset + zoneDataSize);

		/* write header */

		int zoneHeaderOffset = (int) raf.getFilePointer();

		raf.writeShort(zoneList.size());
		raf.writeShort(0);
		raf.writeInt(areaMeshOffset);

		raf.writeShort(vertexList.size());
		raf.writeShort(0);
		raf.writeInt(areaVertexOffset);

		raf.writeShort(zoneDataSize / 4);
		raf.writeShort(0);
		raf.writeInt(zoneDataOffset);

		while ((raf.length() & 0x0F) != 0)
			raf.write(0);

		return zoneHeaderOffset;
	}

	private <T extends HitObject> void constructArray(ArrayList<T> hitObjects, MapObjectNode<T> node)
	{
		for (int i = 0; i < node.getChildCount(); i++) {
			MapObjectNode<T> child = node.getChildAt(i);

			if (i == 0)
				node.getUserObject().c_ChildIndex = child.getTreeIndex();

			if (i != node.getChildCount() - 1)
				child.getUserObject().c_NextIndex = node.getChildAt(i + 1).getTreeIndex();

			constructArray(hitObjects, node.getChildAt(i));
		}

		if (!node.isRoot())
			hitObjects.add(node.getUserObject());
	}

	private <T extends HitObject> void finalizeBoundingBoxes(MapObjectNode<T> node)
	{
		HitObject obj = node.getUserObject();
		obj.AABB = new BoundingBox();

		for (int i = 0; i < node.getChildCount(); i++) {
			MapObjectNode<T> childNode = node.getChildAt(i);
			HitObject childObj = childNode.getUserObject();

			switch (childObj.getType()) {
				case HIT:
					obj.AABB.encompass(childObj.AABB);
					break;
				case ROOT:
				case GROUP:
					finalizeBoundingBoxes(childNode);
					obj.AABB.encompass(childObj.AABB);
					break;
			}
		}
	}

	private static class SimpleVertex
	{
		private final Vertex v;
		private final int x, y, z;

		public SimpleVertex(Vertex v)
		{
			this.v = v;
			this.x = v.getCurrentX();
			this.y = v.getCurrentY();
			this.z = v.getCurrentZ();
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(x, y, z);
		}

		@Override
		public boolean equals(Object obj)
		{
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			SimpleVertex other = (SimpleVertex) obj;
			if (x != other.x)
				return false;
			if (y != other.y)
				return false;
			if (z != other.z)
				return false;
			return true;
		}
	}
}
