package game.map.compiler;

import static game.map.MapObject.ShapeType.MODEL;
import static game.map.MapObject.ShapeType.ROOT;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.TreeMap;

import app.StarRodException;
import app.input.IOUtils;
import game.map.Map;
import game.map.MapObject.ShapeType;
import game.map.mesh.TexturedMesh;
import game.map.mesh.Triangle;
import game.map.mesh.Vertex;
import game.map.shape.LightSet;
import game.map.shape.Model;
import game.map.shape.TransformMatrix;
import game.map.shape.TriangleBatch;
import game.map.shape.UV;
import game.map.shape.commands.ChangeGeometryFlags;
import game.map.shape.commands.FlushPipeline;
import game.map.tree.MapObjectNode;
import game.map.tree.ModelTreeModel;
import util.Logger;
import util.Priority;

/**
 * Flexible enough to follow pointers and decompile maps compiled by the editor
 * with sections rearranged.
 */
public class GeometryDecompiler
{
	// @formatter:off
	public static final int F3DEX2_LOAD_VTX		= 0x01000000;
	public static final int F3DEX2_DRAW_TRI		= 0x05000000;
	public static final int F3DEX2_DRAW_TRIS	= 0x06000000;
	public static final int RDP_PIPE_SYNC 		= 0xE7000000; // see http://level42.ca/projects/ultra64/Documentation/man/pro-man/pro03/index3.4.html
	public static final int F3DEX2_POP_MATRIX	= 0xD8000000;
	public static final int F3DEX2_GEOMETRYMODE	= 0xD9000000;
	public static final int F3DEX2_LOAD_MATRIX	= 0xDA000000;
	public static final int F3DEX2_START_DL 	= 0xDE000000;
	public static final int F3DEX2_END_DL 		= 0xDF000000;
	// @formatter:on

	private HashMap<Integer, Vertex> vertexMap;
	private Vertex[] vertexTable;

	private HashSet<String> textureList = new HashSet<>();
	private LinkedList<Model> meshList;

	private TreeMap<Integer, LightSet> lightSets;
	private HashMap<Integer, Integer> lightCounts;

	private final Map map;
	private final ByteBuffer bb;

	private boolean foundRoot = false;
	private int[] rootAABB = new int[6];

	private static final int BASE_ADDRESS = 0x80210000;

	public GeometryDecompiler(Map m, File source) throws IOException
	{
		map = m;
		bb = IOUtils.getDirectBuffer(source);

		meshList = new LinkedList<>();
		vertexTable = new Vertex[64];

		lightSets = new TreeMap<>();
		lightCounts = new HashMap<>();

		readObjectNames();

		bb.position(0);
		bb.position(bb.getInt() - BASE_ADDRESS);

		MapObjectNode<Model> root = readNode(null).getNode();
		map.modelTree = new ModelTreeModel(root);

		Model rootModel = (root.getUserObject());
		rootModel.cumulativeTransformMatrix = new TransformMatrix();
		rootModel.cumulativeTransformMatrix.setIdentity();
		rootModel.updateTransformHierarchy();

		readLightSets();

		assignNames(map.modelTree.getRoot());
		assignLights(map.modelTree.getRoot());
		map.modelTree.recalculateIndicies();

		for (LightSet lights : lightSets.values())
			map.lightSets.addElement(lights);
	}

	private void assignNames(MapObjectNode<Model> node)
	{
		Model mdl = node.getUserObject();

		for (int i = 0; i < node.getChildCount(); i++)
			assignNames(node.getChildAt(i));

		if (mdl.modelType.get() != ShapeType.ROOT)
			mdl.setName(map.d_modelNames.poll());
	}

	private void readLightSets()
	{
		int i = 0;
		for (Integer addr : lightSets.keySet()) {
			LightSet lights = new LightSet();
			lights.io_listIndex = i++;

			lightSets.put(addr, lights);

			bb.position(addr - BASE_ADDRESS);
			lights.get(bb, lightCounts.get(addr));
			lights.name = String.format("Lights_%08X", addr);
		}
	}

	private void assignLights(MapObjectNode<Model> node)
	{
		Model mdl = node.getUserObject();

		LightSet lights = lightSets.get(mdl.d_ptrLightSet);
		if (lights == null)
			throw new StarRodException("Model %s has invalid lights pointer: %08X", mdl.getName(), mdl.d_ptrLightSet);
		mdl.lights.set(lights);
	}

	private void readObjectNames()
	{
		String name;
		int addr;
		map.d_modelNames = new LinkedList<>();
		map.d_colliderNames = new LinkedList<>();
		map.d_zoneNames = new LinkedList<>();

		bb.position(8);
		addr = bb.getInt();
		if (addr != 0) {
			bb.position(addr - BASE_ADDRESS);
			while (!((name = getName(bb.getInt())).equals("db"))) {
				assert (!map.d_modelNames.contains(name));
				map.d_modelNames.add(name);
			}
		}

		bb.position(12);
		addr = bb.getInt();
		if (addr != 0) {
			bb.position(addr - BASE_ADDRESS);
			while (!((name = getName(bb.getInt())).equals("db"))) {
				assert (!map.d_colliderNames.contains(name));
				map.d_colliderNames.add(name);
			}
		}

		bb.position(16);
		addr = bb.getInt();
		if (addr != 0) {
			bb.position(addr - BASE_ADDRESS);
			while (!((name = getName(bb.getInt())).equals("db"))) {
				assert (!map.d_zoneNames.contains(name));
				map.d_zoneNames.add(name);
			}
		}
	}

	private String getName(int addr)
	{
		int oldPos = bb.position();
		bb.position(addr - BASE_ADDRESS);

		StringBuilder sb = new StringBuilder();
		byte b;
		while ((b = bb.get()) != (byte) 0)
			sb.append((char) b);

		bb.position(oldPos);
		return sb.toString();
	}

	/**
	 * Nodes of the model tree can either be ModelGroups or Models.
	 * We must (1) determine which they are, (2) construct the object,
	 * (3) put it in a new ModelNode, (4) add the new node
	 * to its parent ModelGroup.
	 *
	 * @param parentNode
	 * @throws IOException
	 */
	private Model readNode(MapObjectNode<Model> parent)
	{
		ShapeType type = Model.getTypeFromID(bb.getInt());

		if (type == ROOT) {
			assert (!foundRoot) : "Model tree contains duplicate root.";
			foundRoot = true;
		}

		Model mdl = new Model(type);
		mdl.fileOffset = bb.position() - 4;
		mdl.dumped = true;

		switch (type) {
			case ROOT:
				mdl.setName(String.format("Root", mdl.fileOffset));
				break;
			case SPECIAL:
			case GROUP:
				mdl.setName(String.format("Group %08X", mdl.fileOffset));
				break;
			case MODEL:
				mdl.setName(String.format("Model %08X", mdl.fileOffset));
				break;
		}

		int ptrDisplayData = bb.getInt();
		int numProperties = bb.getInt();
		int ptrPropertyList = bb.getInt();
		int ptrGroupData = bb.getInt();

		assert (ptrDisplayData != 0);
		assert (numProperties >= 6);
		assert (ptrPropertyList != 0);
		if (type == MODEL) {
			assert (ptrGroupData == 0) : "Model nodes should not have group data.";
		}
		else {
			assert (ptrGroupData != 0) : "Group nodes must have have group data.";
		}

		if (type == ROOT) {
			rootAABB = new int[] {
					bb.getInt(), bb.getInt(), bb.getInt(),
					bb.getInt(), bb.getInt(), bb.getInt() };
		}

		bb.position(ptrDisplayData - BASE_ADDRESS);
		int ptrDisplayList = bb.getInt();
		int zero = bb.getInt();
		assert (zero == 0);

		if (type == MODEL)
			readModelDisplayList(mdl, ptrDisplayList);
		else
			readGroupDisplayList(mdl, ptrDisplayList);

		if (type == MODEL)
			readModelProperties(mdl, ptrPropertyList, numProperties);
		else
			readGroupProperties(mdl, ptrPropertyList, numProperties);

		if (type != MODEL)
			readGroupData(mdl, ptrGroupData);

		if (type != ROOT) {
			mdl.getNode().parentNode = parent;
			mdl.getNode().childIndex = parent.getChildCount();
			parent.add(mdl.getNode());
		}

		return mdl;
	}

	private void readModelProperties(Model mdl, int ptrPropertyList, int num)
	{
		bb.position(ptrPropertyList - BASE_ADDRESS);
		int[] property;

		// skip bounding box, we can recalculate it later
		for (int i = 0; i < 6; i++) {
			property = new int[] { bb.getInt(), bb.getInt(), bb.getInt() };
			assert (property[0] == 0x61);
			assert (property[1] == 1);
		}

		// texture name (0 = none)
		property = new int[] { bb.getInt(), bb.getInt(), bb.getInt() };
		assert (property[0] == 0x5E);
		assert (property[1] == 2);
		int ptrTextureName = property[2];

		int[][] properties = new int[num - 7][3];

		for (int i = 0; i < properties.length; i++) {
			properties[i][0] = bb.getInt();
			properties[i][1] = bb.getInt();
			properties[i][2] = bb.getInt();
		}

		mdl.setProperties(properties);

		/*
		for(int i = 0; i < properties.length; i++)
		{
			if(properties[i][0] == 0x5F)
				assert((properties[i][1] & 0xFF000000) == 0) : String.format("%08X", properties[i][1]);
		}
		*/

		/*
		int[][] propCheck = mdl.getProperties();
		
		assert(propCheck.length == properties.length);
		for(int i = 0; i < properties.length; i++)
		{
			for(int j = 0; j < 3; j++)
			{
				System.out.printf("%08X vs %08X\r\n", properties[i][j], propCheck[i][j]);
				assert(propCheck[i][j] == properties[i][j]);
			}
			System.out.println();
		}
		 */

		if (ptrTextureName != 0) {
			bb.position(ptrTextureName - BASE_ADDRESS);
			String textureName = IOUtils.readString(bb, 0x30);
			mdl.getMesh().textureName = textureName;
			if (!textureList.contains(textureName))
				textureList.add(textureName);
		}
	}

	private void readGroupProperties(Model mdl, int ptrPropertyList, int num)
	{
		//	bb.position(ptrPropertyList - BASE_ADDRESS);
		//	pwPL.printf("%s %-5s %08X ", map.name, mdl.getType(), mdl.fileOffset);
		//	for(int i = 0; i < num; i++)
		//		pwPL.printf(" %02X %08X %08X", bb.getInt(), bb.getInt(), bb.getInt());
		//	pwPL.println();

		bb.position(ptrPropertyList - BASE_ADDRESS);
		int[] property;

		// skip bounding box, we can recalculate it later
		for (int i = 0; i < 6; i++) {
			property = new int[] { bb.getInt(), bb.getInt(), bb.getInt() };
			assert (property[0] == 0x61);
			assert (property[1] == 1);
		}

		if (num > 6) {
			assert (num == 8) : num;

			int[][] properties = new int[2][3];
			properties[0] = new int[] { bb.getInt(), bb.getInt(), bb.getInt() };
			properties[1] = new int[] { bb.getInt(), bb.getInt(), bb.getInt() };

			assert (properties[0][0] == 0x60);
			assert (properties[0][1] == 0);
			assert (properties[1][0] == 0x60);
			assert (properties[1][1] == 0);

			mdl.setProperties(properties);
		}
	}

	private void readGroupData(Model mdl, int ptrGroupData)
	{
		bb.position(ptrGroupData - BASE_ADDRESS);
		int ptrTransformMatrix = bb.getInt();
		mdl.d_ptrLightSet = bb.getInt();
		int numLights = bb.getInt();
		int numChildren = bb.getInt();
		int ptrChildList = bb.getInt();

		assert (numChildren > 0);
		assert (ptrChildList != 0);

		bb.position(ptrChildList - BASE_ADDRESS);

		int[] ptrChildren = new int[numChildren];
		for (int i = 0; i < numChildren; i++)
			ptrChildren[i] = bb.getInt();

		for (int i = 0; i < numChildren; i++) {
			bb.position(ptrChildren[i] - BASE_ADDRESS);
			readNode(mdl.getNode());
		}

		if (ptrTransformMatrix != 0) {
			bb.position(ptrTransformMatrix - BASE_ADDRESS);
			mdl.localTransformMatrix.readRDP(bb);
			mdl.hasTransformMatrix.set(true);
		}

		if (mdl.d_ptrLightSet == 0)
			throw new RuntimeException(String.format("Model group %s does not have a light set.\r\n", mdl.getName()));

		lightSets.put(mdl.d_ptrLightSet, null); // read lightsets later
		Integer savedLightCount = lightCounts.get(mdl.d_ptrLightSet);
		if (savedLightCount == null)
			lightCounts.put(mdl.d_ptrLightSet, numLights);
		else
			assert (numLights == savedLightCount);
	}

	private void readModelDisplayList(Model mdl, int ptrDisplayList)
	{
		mdl.setMesh(new TexturedMesh());

		bb.position(ptrDisplayList - BASE_ADDRESS);
		readMeshFromDisplayList(mdl.getMesh());

		//	bb.position(ptrDisplayList - BASE_ADDRESS);
		//	checkDisplayList(mdl.getMesh());

		meshList.add(mdl);
	}

	private void readGroupDisplayList(Model mdl, int ptrDisplayList)
	{
		bb.position(ptrDisplayList - BASE_ADDRESS);

		int cmd = bb.getInt();
		while ((cmd & 0xFF000000) != F3DEX2_END_DL) {
			if ((cmd & 0xFF000000) == RDP_PIPE_SYNC) {
				mdl.setMesh(new TexturedMesh());

				bb.position(bb.position() - 4);
				readMeshFromDisplayList(mdl.getMesh());
				meshList.add(mdl);
				break;
			}
			cmd = bb.getInt();
		}
	}

	// of course, found documentation AFTER i've figured this all out.
	private void readMeshFromDisplayList(TexturedMesh mesh)
	{
		vertexMap = new HashMap<>(); // enforce unique triangles for every mesh
		boolean readingTriangles = false;
		TriangleBatch currentBatch = new TriangleBatch(mesh);

		boolean done = false;
		int code = bb.getInt();
		int arg = bb.getInt();

		while (!done) {
			switch ((code & 0xFF000000)) {
				case RDP_PIPE_SYNC:
					if (readingTriangles) {
						mesh.displayListModel.addElement(currentBatch);
						readingTriangles = false;
						currentBatch = new TriangleBatch(mesh);
					}
					mesh.displayListModel.addElement(new FlushPipeline(mesh));
					assert (arg == 0);
					break;

				case F3DEX2_GEOMETRYMODE:
					if (readingTriangles) {
						mesh.displayListModel.addElement(currentBatch);
						readingTriangles = false;
						currentBatch = new TriangleBatch(mesh);
					}

					mesh.displayListModel.addElement(ChangeGeometryFlags.getCommand(mesh, code, arg));
					break;

				// it is possible to rebuild the vertex table at any point
				case F3DEX2_LOAD_VTX:
					bb.position(bb.position() - 8);
					buildVertexTable(bb);
					break;

				case F3DEX2_DRAW_TRIS:
					Triangle t1 = buildTriangle(code);
					currentBatch.triangles.add(t1);

					Triangle t2 = buildTriangle(arg);
					currentBatch.triangles.add(t2);

					readingTriangles = true;
					break;

				case F3DEX2_DRAW_TRI:
					Triangle t = buildTriangle(code);
					currentBatch.triangles.add(t);
					assert (arg == 0);

					readingTriangles = true;
					break;

				case F3DEX2_END_DL:
					mesh.displayListModel.addElement(currentBatch);
					done = true;
					assert (arg == 0);
					break;

				default:
					Logger.log("Unsupported F3DEX2 command: " + String.format("%02X", (byte) (code >> 6)), Priority.IMPORTANT);
					Logger.log("File pointer: 0x" + String.format("%08X", bb.position()), Priority.IMPORTANT);
					break;
			}

			if (!done) {
				code = bb.getInt();
				arg = bb.getInt();
			}
		}

		mesh.updateHierarchy();
	}

	/**
	 * Builds a triangle object and adds it to the triangle list.
	 * Assumes a properly constructed vertex table and meaningful indicies
	 * into that table in 'word' argument.
	 * @param parentMesh - mesh this triangle will belong to
	 * @param command - display list command specifying this triangle
	 * @return
	 * @throws IOException
	 */
	private Triangle buildTriangle(int command)
	{
		int index1 = (command >> 16) & 0x000000FF;
		int index2 = (command >> 8) & 0x000000FF;
		int index3 = command & 0x000000FF;

		Vertex v1, v2, v3;
		v1 = vertexTable[index1 / 2];
		v2 = vertexTable[index2 / 2];
		v3 = vertexTable[index3 / 2];

		return new Triangle(v1, v2, v3);
	}

	/*
	 * Reads a vertex specification part of a mesh and builds a new vertex list for it.
	 * Returns the list at the end of the vertex specification and resets the file reader
	 * pointer to the beginning of the next word.
	 *
	 * Precondition: RandomAccessFile pointing to the the beginning of a vertex
	 * specification list.
	 */
	private void buildVertexTable(ByteBuffer bb)
	{
		int cmd = bb.getInt();

		while ((cmd & 0xFF000000) == 0x01000000) {
			int num = ((cmd >>> 12) & 0x00000FFF);
			int end = (cmd & 0x00000FFF) / 2;
			int addr = bb.getInt();
			Logger.log("Adding " + num + " vertices from " + String.format("%08X", addr), Priority.DETAIL);

			int start = end - num;
			for (int i = start; i < end; i++) {
				int vertexAddress = makeAddress(addr, i - start);
				if (!vertexMap.containsKey(vertexAddress))
					readVertexData(vertexAddress);
				vertexTable[i] = vertexMap.get(vertexAddress);
			}

			cmd = bb.getInt();
		}

		bb.position(bb.position() - 4);
	}

	/**
	 *
	 * @param addr - memory address of vertex
	 * @throws IOException
	 */
	private void readVertexData(int addr)
	{
		int previousPosition = bb.position();
		bb.position(addr - BASE_ADDRESS);

		Vertex v = new Vertex(bb.getShort(), bb.getShort(), bb.getShort());

		short unknown = bb.getShort();
		if (unknown != (short) 0)
			Logger.log("Vertex with flag " + unknown + " at " + String.format("%08X", bb.position()), Priority.IMPORTANT);

		short ucoord = bb.getShort();
		short vcoord = bb.getShort();
		v.uv = new UV(ucoord, vcoord);
		v.r = bb.get() & 0xFF;
		v.g = bb.get() & 0xFF;
		v.b = bb.get() & 0xFF;
		v.a = bb.get() & 0xFF;

		vertexMap.put(BASE_ADDRESS + bb.position() - 0x10, v);

		bb.position(previousPosition);
	}

	/*
	 * Finds the RAM address of a vertex given the base and offset code.
	 * Offset takes values 0,1,2,3,... indicating the vertex# after base.
	 */
	private static final int makeAddress(int base, int offset)
	{
		return base + ((offset & 0x000000FF) << 4);
	}
}
