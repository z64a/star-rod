package game.map.compiler;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import javax.swing.ListModel;

import org.apache.commons.io.FileUtils;

import app.Directories;
import app.Environment;
import app.config.Options;
import app.input.IOUtils;
import assets.AssetManager;
import game.map.BoundingBox;
import game.map.Map;
import game.map.MapObject.ShapeType;
import game.map.mesh.TexturedMesh;
import game.map.mesh.Triangle;
import game.map.mesh.Vertex;
import game.map.shape.LightSet;
import game.map.shape.Model;
import game.map.shape.TransformMatrix;
import game.map.shape.TriangleBatch;
import game.map.shape.commands.DisplayCommand;
import game.map.tree.MapObjectNode;
import util.Logger;
import util.Priority;

public class GeometryCompiler
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

	private static final int RAM_BASE = 0x80210000;
	private static final int MAX_MAP_SIZE = 0x30000;
	private static final int MAX_STAGE_SIZE = 0x8000;

	private RandomAccessFile raf;

	private int vertexTableBase;
	private ArrayList<Vertex> vertexTable;
	private HashMap<Vertex, Integer> vertexMap;

	private ArrayList<TransformMatrix> matrixTable;
	private HashMap<TransformMatrix, Integer> matrixMap;

	private HashMap<String, Integer> textureNameMap;

	/*
	 * Plan:
	 * (0) create canonical version of all model and vertex data
	 * (1) write vertex data into one massive table
	 * (2) write display lists and vertex table
	 * (3) go back and add offset of vertex table to all vertex references
	 * (4) write properties and lists
	 *  -  skip extra strings section, it's not needed for the map to work.
	 * (5) write texture names and update previous section
	 * */

	public GeometryCompiler(Map map) throws IOException
	{
		File build_dec = new File(AssetManager.getMapBuildDir(), map.getName() + "_shape.bin");

		Logger.log("Compiling map geometry to " + build_dec.getPath());

		if (build_dec.exists())
			build_dec.delete();

		MapObjectNode<Model> rootNode = map.modelTree.getRoot();
		finalizeBoundingBoxes(rootNode);

		raf = new RandomAccessFile(build_dec, "rw");
		raf.seek(0x20);

		// texture list
		textureNameMap = new HashMap<>();
		for (Model mdl : map.modelTree) {
			TexturedMesh m = mdl.getMesh();
			if (!textureNameMap.containsKey(m.textureName) && !m.textureName.isEmpty()) {
				textureNameMap.put(m.textureName, (int) raf.getFilePointer());

				String textureName = m.textureName;
				if (Environment.projectConfig.getBoolean(Options.WriteLegacyTexNames)) {
					if (!textureName.endsWith("tif")) {
						textureName = map.texName.substring(0, 4) + textureName + "tif";
					}
				}

				raf.write(textureName.getBytes());
				raf.write((byte) 0);
				raf.seek(((int) raf.getFilePointer() + 3) & 0xFFFFFFFC);
			}
		}
		raf.seek(((int) raf.getFilePointer() + 0xF) & 0xFFFFFFF0);

		// create vertex table
		vertexTable = new ArrayList<>();
		vertexMap = new HashMap<>();
		buildVertexTable(rootNode);

		// write vertex table
		vertexTableBase = (int) raf.getFilePointer();
		for (Vertex v : vertexTable)
			raf.write(v.getCompiledRepresentation());
		raf.seek(((int) raf.getFilePointer() + 0xF) & 0xFFFFFFF0);

		// write light sets
		for (LightSet lightSet : map.lightSets) {
			lightSet.c_address = RAM_BASE + (int) raf.getFilePointer();
			lightSet.write(raf);
		}

		// create matrix list
		matrixTable = new ArrayList<>();
		addUniqueMatricies(rootNode, new HashSet<>());

		// write matrix list
		matrixMap = new HashMap<>();
		for (TransformMatrix m : matrixTable) {
			matrixMap.put(m, (int) raf.getFilePointer());
			m.writeRDP(raf);
		}

		// display list
		writeDisplayList(rootNode);
		raf.seek(((int) raf.getFilePointer() + 0xF) & 0xFFFFFFF0);

		// model tree
		int modelTreeRoot = writeModelTree(rootNode, map.lightSets);

		// header
		raf.seek(0);
		raf.writeInt(RAM_BASE + modelTreeRoot);
		raf.writeInt(RAM_BASE + vertexTableBase);

		// padding
		raf.seek(raf.length());
		int nextAlignedOffset = ((int) raf.length() + 0xF) & 0xFFFFFFF0;
		for (int i = 0; i < nextAlignedOffset - raf.length(); i += 4)
			raf.writeInt(0);

		raf.close();

		byte[] complete = FileUtils.readFileToByteArray(build_dec);

		// check size
		boolean battleMap = map.getName().contains("_bt");
		int limit = battleMap ? MAX_STAGE_SIZE : MAX_MAP_SIZE;

		if (complete.length > limit) {
			String mapType = battleMap ? "battle map" : "map";
			String breakdown = String.format("0x%X of 0x%X bytes (%4.2f%%)",
				complete.length, limit, 100.0 * complete.length / limit);
			Logger.log("Build failed: " + mapType + " size exceeds engine limit " + breakdown, Priority.ERROR);

			throw new BuildException("Build failed: " + mapType + " size exceeds engine limit.\n" + breakdown);
		}

		File headerFile = Directories.PROJ_INCLUDE_MAPFS.file(map.getName() + "_shape.h");
		try (PrintWriter pw = IOUtils.getBufferedPrintWriter(headerFile)) {
			for (Model mdl : map.modelTree.getList()) {
				pw.printf("#define %-23s 0x%X%n", "MODEL_" + mdl.getName(), mdl.getNode().getTreeIndex());
			}
			pw.println();
		}
	}

	private void finalizeBoundingBoxes(MapObjectNode<Model> node)
	{
		// depth first, ensure child bounding boxes are correct
		for (int i = 0; i < node.getChildCount(); i++)
			finalizeBoundingBoxes(node.getChildAt(i));

		Model mdl = node.getUserObject();
		mdl.localAABB = new BoundingBox();

		if (mdl.hasMesh())
			mdl.calculateLocalAABB();

		for (int i = 0; i < node.getChildCount(); i++) {
			Model child = node.getChildAt(i).getUserObject();
			mdl.localAABB.encompass(child.localAABB);
		}
	}

	/**
	 * Creates a list of unique Matrix objects from all ModelGroups.
	 * NOTE: Uses the matrix map as a side effect. Be sure to clear it afterward.
	 * @param node
	 * @param matrixList
	 */
	private void addUniqueMatricies(MapObjectNode<Model> node, HashSet<TransformMatrix> matrixSet)
	{
		Model mdl = node.getUserObject();

		if (mdl.modelType.get() != ShapeType.MODEL) {
			Model group = node.getUserObject();
			if (group.hasTransformMatrix.get() && !matrixSet.contains(group.localTransformMatrix)) {
				matrixTable.add(group.localTransformMatrix);
				matrixSet.add(group.localTransformMatrix);
			}
		}

		for (int i = 0; i < node.getChildCount(); i++) {
			addUniqueMatricies(node.getChildAt(i), matrixSet);
		}
	}

	/**
	 * Take a list of models and construct a list of vertices, removing duplicates.
	 * @param node - pass in root to build the whole map
	 */
	private void buildVertexTable(MapObjectNode<Model> node)
	{
		Model mdl = node.getUserObject();

		for (int i = 0; i < node.getChildCount(); i++)
			buildVertexTable(node.getChildAt(i));

		if (mdl.hasMesh()) {
			for (Triangle t : mdl.getMesh())
				for (Vertex v : t.vert)
					addVertexToTable(v);
		}
	}

	private void addVertexToTable(Vertex v)
	{
		if (!vertexMap.containsKey(v)) {
			vertexMap.put(v, vertexTable.size());
			vertexTable.add(v);
		}
	}

	/**
	 * Recursively writes the display list to the .shape file. Pass the
	 * root as a parameter to write the whole tree.
	 * @param node
	 * @throws IOException
	 */
	private void writeDisplayList(MapObjectNode<Model> node) throws IOException
	{
		for (int i = 0; i < node.getChildCount(); i++) {
			MapObjectNode<Model> child = node.getChildAt(i);
			writeDisplayList(child);
		}

		Model mdl = node.getUserObject();
		mdl.c_DisplayListOffset = (int) raf.getFilePointer(); //XXX changed!

		// F3DEX2_LOAD_MATRIX
		if (mdl.hasTransformMatrix.get()) {
			raf.writeInt(0xDA380000);
			raf.writeInt(RAM_BASE + matrixMap.get(mdl.localTransformMatrix));
		}

		// write DL for this group
		for (int i = 0; i < node.getChildCount(); i++) {
			MapObjectNode<Model> child = node.getChildAt(i);

			raf.writeInt(F3DEX2_START_DL);
			raf.writeInt(RAM_BASE + child.getUserObject().c_DisplayListOffset);
		}

		if (mdl.hasMesh()) {
			//	System.out.println(mdl + " " + mdl.mesh.batchList.size());

			//	mdl.c_DisplayListOffset = (int)raf.getFilePointer();
			writeMeshDisplayList(mdl.getMesh());
		}

		// F3DEX2_POP_MATRIX
		if (mdl.hasTransformMatrix.get()) {
			raf.writeInt(0xD8380002);
			raf.writeInt(0x00000040);
		}

		raf.writeInt(F3DEX2_END_DL);
		raf.writeInt(0);
	}

	/**
	 * Start with a pipeline sync, then separate the triangles into groups
	 * according to geometry mode settings. Write the geometry mode commands
	 * and the triangle lists. Finish with an end list command.
	 *
	 * @param mesh
	 * @throws IOException
	 */
	private void writeMeshDisplayList(TexturedMesh mesh) throws IOException
	{
		for (int i = 0; i < mesh.displayListModel.size(); i++) {
			DisplayCommand cmd = mesh.displayListModel.getElementAt(i);
			if (cmd instanceof TriangleBatch batch) {
				writeTriangleList(batch.triangles);
			}
			else {
				int[] v = cmd.getF3DEX2Command();
				raf.writeInt(v[0]);
				raf.writeInt(v[1]);
			}

		}

		raf.writeInt(F3DEX2_END_DL);
		raf.writeInt(0);
	}

	/**
	 * Takes a list of triangles, generates draw commands, and writes them to
	 * the filestream. The list is assumed to use the same geometry mode throughout.
	 *
	 * Since the size of this list is unknown, it is possible
	 *
	 * @param triangleList
	 * @throws IOException
	 */
	private void writeTriangleList(List<Triangle> triangleList) throws IOException
	{
		if (triangleList == null || triangleList.size() == 0)
			return;

		ArrayList<VertexBatch> batchList = getTriangleBatches(triangleList);
		for (VertexBatch batch : batchList)
			writeTriangleBatch(batch);
	}

	/**
	 * Separates an arraylist of triangles into batches with <= VERTEX_BUFFER_SIZE vertices.
	 * @param triangleList
	 * @return
	 */
	private ArrayList<VertexBatch> getTriangleBatches(List<Triangle> triangleList)
	{
		ArrayList<VertexBatch> batchList = new ArrayList<>();
		VertexBatch batch = new VertexBatch();
		batchList.add(batch);

		// add a triangle to the batch, rejecting it if it breaks the buffer size
		for (int i = 0; i < triangleList.size(); i++) {
			Triangle t = triangleList.get(i);
			if (!batch.addTriangle(t)) {
				batch = new VertexBatch();
				batchList.add(batch);
				i--;
			}
		}

		return batchList;
	}

	private void writeTriangleBatch(VertexBatch batch) throws IOException
	{
		ArrayList<Integer> indexList = new ArrayList<>();
		for (Vertex v : batch.vertexSet)
			indexList.add(vertexMap.get(v));

		Collections.sort(indexList);

		ArrayList<IntegerRange> indexGroups = new ArrayList<>();
		IntegerRange currentRange = new IntegerRange(indexList.get(0));
		indexGroups.add(currentRange);

		for (Integer i : indexList) {
			if (i == currentRange.end)
				continue;

			if (i == currentRange.end + 1) {
				currentRange.end++;
			}
			else {
				currentRange = new IntegerRange(i);
				indexGroups.add(currentRange);
			}
		}

		ArrayList<Vertex> vertexBuffer = new ArrayList<>();
		HashMap<Vertex, Integer> vertexBufferMap = new HashMap<>();

		// write load vertex commands and load the buffer
		for (IntegerRange range : indexGroups) {
			for (int i = range.start; i <= range.end; i++) {
				Vertex v = vertexTable.get(i);
				vertexBufferMap.put(v, vertexBuffer.size());
				vertexBuffer.add(v);
			}

			int cmd = F3DEX2_LOAD_VTX;
			cmd |= range.length() << 12;
			cmd |= 2 * vertexBuffer.size();
			raf.writeInt(cmd);
			raf.writeInt(RAM_BASE + vertexTableBase + range.start * 0x10);
		}

		// write draw triangle commands
		for (int draws = 0; draws < batch.triangleList.size();) {
			Triangle t;

			// only one left, do a single draw call
			if (batch.triangleList.size() - draws == 1) {
				t = batch.triangleList.get(draws);
				int code = F3DEX2_DRAW_TRI;
				code |= (2 * vertexBufferMap.get(t.vert[0])) << 16;
				code |= (2 * vertexBufferMap.get(t.vert[1])) << 8;
				code |= (2 * vertexBufferMap.get(t.vert[2]));
				raf.writeInt(code);
				raf.writeInt(0);
				draws++;

				// draw two triangles
			}
			else {
				t = batch.triangleList.get(draws);
				int code = F3DEX2_DRAW_TRIS;
				code |= (2 * vertexBufferMap.get(t.vert[0])) << 16;
				code |= (2 * vertexBufferMap.get(t.vert[1])) << 8;
				code |= (2 * vertexBufferMap.get(t.vert[2]));
				raf.writeInt(code);
				draws++;

				t = batch.triangleList.get(draws);
				code = 0;
				code |= (2 * vertexBufferMap.get(t.vert[0])) << 16;
				code |= (2 * vertexBufferMap.get(t.vert[1])) << 8;
				code |= (2 * vertexBufferMap.get(t.vert[2]));
				raf.writeInt(code);
				draws++;
			}
		}
	}

	/**
	 * Represents a range of integers, from start to finish (inclusive).
	 */
	private static class IntegerRange
	{
		public IntegerRange(int start)
		{
			this(start, start);
		}

		public IntegerRange(int start, int end)
		{
			this.start = start;
			this.end = end;
			if (end < start)
				throw new RuntimeException("Invalid range generated: " + start + " to " + end);
		}

		public int length()
		{
			return 1 + end - start;
		}

		public int start;
		public int end;
	}

	/**
	 * Represents a single batch of vertices that get loaded into the
	 * vertex buffer during draw calls for a subset of triangles.
	 */
	private static class VertexBatch
	{
		public ArrayList<Triangle> triangleList;
		public HashSet<Vertex> vertexSet;

		public static final int VERTEX_BUFFER_SIZE = 32;

		public VertexBatch()
		{
			triangleList = new ArrayList<>(VERTEX_BUFFER_SIZE / 2);
			vertexSet = new HashSet<>(VERTEX_BUFFER_SIZE * 2);
		}

		public boolean addTriangle(Triangle t)
		{
			ArrayList<Vertex> added = new ArrayList<>(5);
			for (Vertex v : t.vert)
				addVertex(added, v);

			if (vertexSet.size() > VertexBatch.VERTEX_BUFFER_SIZE) {
				for (Vertex v : added)
					vertexSet.remove(v);
				return false;
			}
			else {
				triangleList.add(t);
				return true;
			}
		}

		private void addVertex(ArrayList<Vertex> added, Vertex v)
		{
			if (!vertexSet.contains(v)) {
				added.add(v);
				if (!vertexSet.add(v))
					throw new RuntimeException("Collision detected when creating vertex batch!");
			}
		}
	}

	/**
	 * Write the model tree. Call this method with the root as a parameter
	 * to write the whole tree.
	 * @param node
	 * @return Offset of the root
	 * @throws IOException
	 */
	private int writeModelTree(MapObjectNode<Model> node, ListModel<LightSet> lightSets) throws IOException
	{
		Model mdl = node.getUserObject();
		int nodePosition = -1;

		if (mdl.modelType.get() == ShapeType.MODEL) {
			int propertiesAddress = (int) raf.getFilePointer();
			int numProperties = writeModelProperties(mdl);

			raf.writeInt(RAM_BASE + mdl.c_DisplayListOffset);
			raf.writeInt(0);
			nodePosition = (int) raf.getFilePointer();
			raf.writeInt(2); // type 2 = model
			raf.writeInt(RAM_BASE + (int) raf.getFilePointer() - 0xC);
			raf.writeInt(numProperties);
			raf.writeInt(RAM_BASE + propertiesAddress);
			raf.writeInt(0);
		}
		else {
			ArrayList<Integer> childOffsets = new ArrayList<>();

			// write children, skipping empty groups
			for (int i = 0; i < node.getChildCount(); i++) {
				MapObjectNode<Model> child = node.getChildAt(i);
				childOffsets.add(writeModelTree(child, lightSets));
			}

			int propertiesOffset = (int) raf.getFilePointer();
			int numProperties = writeGroupProperties(mdl);

			int childListOffset = (int) raf.getFilePointer();
			for (Integer i : childOffsets)
				raf.writeInt(RAM_BASE + i);

			if (mdl.hasTransformMatrix.get())
				raf.writeInt(RAM_BASE + matrixMap.get(mdl.localTransformMatrix));
			else
				raf.writeInt(0);

			raf.writeInt(mdl.lights.get().c_address);
			raf.writeInt(mdl.lights.get().getLightCount());

			raf.writeInt(childOffsets.size());
			raf.writeInt(RAM_BASE + childListOffset);

			raf.writeInt(RAM_BASE + mdl.c_DisplayListOffset);
			raf.writeInt(0);

			nodePosition = (int) raf.getFilePointer();
			raf.writeInt(Model.getIDFromType(mdl.modelType.get()));
			raf.writeInt(RAM_BASE + (int) raf.getFilePointer() - 0xC);

			raf.writeInt(numProperties);
			raf.writeInt(RAM_BASE + propertiesOffset);
			raf.writeInt(RAM_BASE + (int) raf.getFilePointer() - 0x2C);
		}

		Logger.log(String.format("Wrote %s to %08X", mdl.toString(), nodePosition), Priority.DETAIL);
		return nodePosition;
	}

	private int writeGroupProperties(Model mdl) throws IOException
	{
		writeBoundingBox(mdl.AABB);

		int[][] properties = mdl.getProperties();
		for (int[] element : properties) {
			raf.writeInt(element[0]);
			raf.writeInt(element[1]);
			raf.writeInt(element[2]);
		}

		return 6 + properties.length;
	}

	private int writeModelProperties(Model mdl) throws IOException
	{
		writeBoundingBox(mdl.localAABB);

		// write texture pointer
		raf.writeInt(0x5E);
		raf.writeInt(2);
		if (mdl.getMesh().textureName.isEmpty())
			raf.writeInt(0);
		else
			raf.writeInt(RAM_BASE + textureNameMap.get(mdl.getMesh().textureName));

		int[][] properties = mdl.getProperties();
		for (int[] element : properties) {
			raf.writeInt(element[0]);
			raf.writeInt(element[1]);
			raf.writeInt(element[2]);
		}

		return 7 + properties.length;
	}

	private void writeBoundingBox(BoundingBox AABB) throws IOException
	{
		raf.writeInt(0x61);
		raf.writeInt(1);
		raf.writeFloat(AABB.min.getX());
		raf.writeInt(0x61);
		raf.writeInt(1);
		raf.writeFloat(AABB.min.getY());
		raf.writeInt(0x61);
		raf.writeInt(1);
		raf.writeFloat(AABB.min.getZ());

		raf.writeInt(0x61);
		raf.writeInt(1);
		raf.writeFloat(AABB.max.getX());
		raf.writeInt(0x61);
		raf.writeInt(1);
		raf.writeFloat(AABB.max.getY());
		raf.writeInt(0x61);
		raf.writeInt(1);
		raf.writeFloat(AABB.max.getZ());
	}
}
