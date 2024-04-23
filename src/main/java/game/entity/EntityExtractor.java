package game.entity;

import static app.Directories.DUMP_ENTITY_RAW;
import static app.Directories.DUMP_ENTITY_SRC;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Stack;

import org.apache.commons.io.FileUtils;

import app.Environment;
import app.Resource;
import app.Resource.ResourceType;
import app.StarRodException;
import app.input.IOUtils;
import game.entity.EntityInfo.EntityType;
import game.entity.EntityModel.EntityPart;
import game.entity.EntityModel.EntityTexture;
import game.entity.EntityModel.EntityTriangle;
import game.entity.EntityModel.EntityVertex;
import game.f3dex2.BaseF3DEX2;
import game.f3dex2.DisplayList;
import game.f3dex2.DisplayList.CommandType;
import game.f3dex2.commands.LoadVertex;
import game.f3dex2.commands.NewDL;
import game.f3dex2.commands.SetImg;
import game.f3dex2.commands.SetOtherModeL;
import game.f3dex2.commands.SetOtherModeL.OtherModeOption;
import game.f3dex2.commands.SetTile;
import game.f3dex2.commands.SetTileSize;
import game.f3dex2.commands.Texture;
import game.f3dex2.commands.Tri1;
import game.f3dex2.commands.Tri2;
import game.f3dex2.commands.UseMatrix;
import game.map.editor.render.RenderMode;
import game.map.shape.TransformMatrix;
import game.texture.Tile;
import game.texture.TileFormat;
import util.Logger;

public class EntityExtractor
{
	public static class EntityDataRoot
	{
		public final boolean ignoreMatrix;
		public final TransformMatrix transform;
		public final int displayListOffset;
		public final int matrixOffset;
		public final int textureOffset;
		public final int overrideMode;

		public EntityDataRoot(boolean ignoreMatrix, TransformMatrix mat,
			int displayListOffset, int matrixOffset, int textureOffset, int overrideMode)
		{
			this.ignoreMatrix = ignoreMatrix;
			this.transform = mat;
			this.displayListOffset = displayListOffset;
			this.matrixOffset = matrixOffset;
			this.textureOffset = textureOffset;
			this.overrideMode = overrideMode;
		}
	}

	// section of the display list
	private static class ModelNode
	{
		public final int treeDepth;
		public final int displayListOffset;

		public TransformMatrix matrix = null;
		public int overrideMode = -1;
		public int renderMode = DEFAULT_RENDER_MODE;

		public final List<ModelNode> children;
		public final List<EntityTriangle> triangles;

		public boolean enableTex = false;
		public EntityTexture texture = null;
		public boolean ignoreMatrix = false;

		public ModelNode(int treeDepth, int displayListOffset)
		{
			this.treeDepth = treeDepth;
			this.displayListOffset = displayListOffset;
			children = new ArrayList<>();
			triangles = new ArrayList<>();

			matrix = TransformMatrix.identity();
		}
	}

	public static void main(String[] args) throws IOException
	{
		Environment.initialize();
		extractAll();
		Environment.exit();
	}

	public static void extractAll() throws IOException
	{
		dumpBinaryFiles();
		extractModels();
	}

	private static void dumpBinaryFiles() throws IOException
	{
		ByteBuffer fileBuffer = Environment.getBaseRomBuffer();

		for (EntitySet set : EntitySet.values()) {
			if (set == EntitySet.DUMMY)
				continue;

			for (int i = 0; i < 3; i++) {
				byte[] bytes = new byte[set.dmaEnd - set.dmaStart];
				fileBuffer.position(set.dmaStart);
				fileBuffer.get(bytes);

				File rawFile = new File(DUMP_ENTITY_RAW + "_" + set.name() + ".bin");
				FileUtils.writeByteArrayToFile(rawFile, bytes);
			}
		}

		for (EntityType entity : EntityType.values()) {
			if (entity.set == EntitySet.DUMMY)
				continue;

			if (entity.typeData == null)
				Logger.log("Missing data for entity: " + entity.name);

			int dmaStart = entity.typeData.dmaArgs[0][0];
			int dmaEnd = entity.typeData.dmaArgs[0][1];
			byte[] bytes = new byte[dmaEnd - dmaStart];
			fileBuffer.position(dmaStart);
			fileBuffer.get(bytes);

			File rawFile = new File(DUMP_ENTITY_RAW + entity.name() + ".bin");
			FileUtils.writeByteArrayToFile(rawFile, bytes);

			dmaStart = entity.typeData.dmaArgs[1][0];
			dmaEnd = entity.typeData.dmaArgs[1][1];
			if (dmaStart != 0) {
				bytes = new byte[dmaEnd - dmaStart];
				fileBuffer.position(dmaStart);
				fileBuffer.get(bytes);

				rawFile = new File(DUMP_ENTITY_RAW + entity.name() + "_AUX.bin");
				FileUtils.writeByteArrayToFile(rawFile, bytes);
			}
		}
	}

	private static void extractModels()
	{
		for (EntityType entity : EntityType.values()) {
			if (entity.set == EntitySet.DUMMY)
				continue;

			try {
				new EntityExtractor(entity.name);
			}
			catch (IOException e) {
				StarRodException sre = new StarRodException("IOException decompiling %s%n%s", entity.name, e.getMessage());
				sre.setStackTrace(e.getStackTrace());
				throw sre;
			}
		}
	}

	public static boolean PRINT_DISPLAY_LISTS = false;

	private boolean shadowMode = false;
	public final String entityName;
	public final String entityPath;
	private ByteBuffer fileBuffer;
	private HashMap<String, EntityTexture> textureMap;
	private HashMap<EntityVertex, EntityVertex> vertexMap;

	private static final int DEFAULT_RENDER_MODE = 1;

	public EntityExtractor(String name) throws IOException
	{
		entityName = name;
		entityPath = DUMP_ENTITY_SRC + "/" + entityName;

		FileUtils.forceMkdir(new File(entityPath));

		String resName = name + ".txt";

		if (!Resource.hasResource(ResourceType.EntityModelRoots, resName)) {
			Logger.logWarning("No model data for " + name);
			return;
		}
		Logger.log("Creating model for " + name);

		List<EntityDataRoot> roots = new ArrayList<>();
		TransformMatrix currentMat = TransformMatrix.identity();
		boolean ignoreMatrix = false;
		int overrideMode = -1;

		for (String s : Resource.getTextInput(ResourceType.EntityModelRoots, resName, false)) {
			String[] tokens = s.split("\\s+");

			/*
			 * dl addr
			 * dl addr / mat
			 * dl addr // tex
			 * dl addr / mat / texname
			 */
			if (tokens[0].equals("dl")) {
				String[] args = s.trim().substring(2).trim().split("\\s*\\/\\s*");

				int dlOffset = (int) Long.parseLong(args[0], 16);
				int matOffset = -1;
				int texOffset = -1;

				if (args.length > 1 && !args[1].isEmpty())
					matOffset = (int) Long.parseLong(args[1], 16);
				if (args.length > 2 && !args[2].isEmpty())
					texOffset = (int) Long.parseLong(args[2], 16);

				roots.add(new EntityDataRoot(ignoreMatrix, currentMat, dlOffset, matOffset, texOffset, overrideMode));

				// reset for next dl
				ignoreMatrix = false;
				overrideMode = -1;
			}
			else if (tokens[0].equals("ignore")) {
				if (tokens[1].equals("matrix"))
					ignoreMatrix = true;
			}
			else if (tokens[0].equals("identity")) {
				currentMat = TransformMatrix.identity();
			}
			else if (tokens[0].equals("translate")) {
				TransformMatrix translateMat = TransformMatrix.identity();
				translateMat.translate(
					Float.parseFloat(tokens[1]),
					Float.parseFloat(tokens[2]),
					Float.parseFloat(tokens[3]));
				currentMat = TransformMatrix.multiply(currentMat, translateMat);
			}
			else if (tokens[0].equals("scale")) {
				TransformMatrix scaleMat = TransformMatrix.identity();
				if (tokens.length == 2) {
					float f = Float.parseFloat(tokens[1]);
					scaleMat.set(f, 0, 0);
					scaleMat.set(f, 1, 1);
					scaleMat.set(f, 2, 2);
				}
				else if (tokens.length == 4) {
					scaleMat.set(Float.parseFloat(tokens[1]), 0, 0);
					scaleMat.set(Float.parseFloat(tokens[2]), 1, 1);
					scaleMat.set(Float.parseFloat(tokens[3]), 2, 2);
				}
				currentMat = TransformMatrix.multiply(currentMat, scaleMat);
			}
			else if (tokens[0].equals("mode")) {
				overrideMode = Integer.parseInt(tokens[1], 16);
			}
		}

		File rawFile = new File(DUMP_ENTITY_RAW + name + ".bin");
		fileBuffer = IOUtils.getDirectBuffer(rawFile);

		textureMap = new HashMap<>();
		vertexMap = new HashMap<>();

		List<ModelNode> nodes = new ArrayList<>(roots.size());
		for (EntityDataRoot root : roots) {
			ModelNode node = new ModelNode(0, root.displayListOffset);
			node.overrideMode = root.overrideMode;

			if (root.textureOffset >= 0) {
				ModelNode texNode = new ModelNode(0, root.textureOffset);
				readDisplayNode(texNode);
				node.texture = texNode.texture;
			}

			node.ignoreMatrix = root.ignoreMatrix;
			if (root.matrixOffset >= 0) {
				fileBuffer.position(root.matrixOffset);
				node.matrix.readRDP(fileBuffer);
			}
			node.matrix = TransformMatrix.multiply(root.transform, node.matrix);
			nodes.add(node);
		}

		for (ModelNode node : nodes)
			readDisplayNode(node);

		if (PRINT_DISPLAY_LISTS) {
			for (ModelNode node : nodes)
				printNodeInfo(node);
		}

		EntityModel mdl = createModel(nodes);
		mdl.writeToObj(new File(entityPath + "/model.obj"));
	}

	private EntityModel createModel(List<ModelNode> roots)
	{
		EntityModel mdl = new EntityModel(null);

		Stack<ModelNode> stack = new Stack<>();
		for (ModelNode root : roots)
			stack.push(root);

		int partCount = 1;
		while (!stack.isEmpty()) {
			ModelNode node = stack.pop();

			if (node.triangles.size() > 0) {
				EntityPart part = new EntityPart("Part" + partCount++);

				part.triangles.addAll(node.triangles);
				part.texture = node.texture;
				part.renderMode = RenderMode.getModeForID(node.renderMode);

				mdl.parts.add(part);
			}

			for (ModelNode child : node.children)
				stack.push(child);
		}

		return mdl;
	}

	private void printNodeInfo(ModelNode node)
	{
		if (node.texture == null)
			Logger.logf("[%X] Node %X has %d tris", node.treeDepth,
				node.displayListOffset, node.triangles.size());
		else
			Logger.logf("[%X] Node %X has %d tris, tex = %s", node.treeDepth,
				node.displayListOffset, node.triangles.size(), node.texture.name);

		for (ModelNode child : node.children)
			printNodeInfo(child);
	}

	private int toOffset(int addr)
	{
		if (shadowMode)
			return addr - 0x802E0D90;
		else
			return addr & 0xFFFFFF; // A000000 --> 0
	}

	private void readDisplayNode(ModelNode node) throws IOException
	{
		String indent = new String(new char[node.treeDepth]).replace("\0", "    ");
		if (PRINT_DISPLAY_LISTS)
			Logger.logf(indent + "(%d) Display List %X", node.treeDepth, node.displayListOffset);

		List<BaseF3DEX2> commands = DisplayList.readList(fileBuffer, node.displayListOffset);

		// model dumping
		EntityVertex[] vertexTable = new EntityVertex[32];

		// texture reading
		TileFormat fmt = null;
		int ptrLastImage = 0;
		int ptrImg = 0;
		int ptrPal = 0;
		int width = 0;
		int height = 0;
		int cmS = 0;
		int cmT = 0;

		if (node.overrideMode >= 0)
			node.renderMode = node.overrideMode;

		for (BaseF3DEX2 cmd : commands) {
			switch (cmd.type) {
				case G_DL:
					if (node.triangles.size() > 0)
						Logger.logWarning("Triangles found before branch. Invalid texture state possible.");

					if (PRINT_DISPLAY_LISTS)
						Logger.log(indent + cmd.getString());

					NewDL newDL = (NewDL) cmd;
					ModelNode child = new ModelNode(node.treeDepth + 1, toOffset(newDL.addr));

					// propagate state
					if (node.overrideMode >= 0 && child.overrideMode < 0)
						child.overrideMode = node.overrideMode;
					child.enableTex = node.enableTex;
					if (node.matrix != null)
						child.matrix = node.matrix;
					if (node.texture != null)
						child.texture = node.texture;

					readDisplayNode(child);

					if (child.texture != node.texture)
						node.texture = child.texture;

					node.children.add(child);
					break;

				case G_VTX:
					LoadVertex loadVertex = (LoadVertex) cmd;
					if (PRINT_DISPLAY_LISTS)
						Logger.logf(indent + "## Adding " + loadVertex.num + " vertices from " + String.format("%04X", toOffset(loadVertex.addr)));

					for (int i = 0; i < loadVertex.num; i++) {
						int vetexPos = toOffset(loadVertex.addr) + 0x10 * i;
						EntityVertex vertex = new EntityVertex(fileBuffer, vetexPos, node.matrix);
						if (!vertexMap.containsKey(vertex))
							vertexMap.put(vertex, vertex);
						vertexTable[loadVertex.pos + i] = vertexMap.get(vertex);
					}
					break;

				case G_MTX:
					int pos = fileBuffer.position();

					UseMatrix useMtx = (UseMatrix) cmd;
					fileBuffer.position(toOffset(useMtx.addr));
					TransformMatrix loadMatrix = TransformMatrix.identity();
					loadMatrix.readRDP(fileBuffer);

					if (!node.ignoreMatrix)
						node.matrix = TransformMatrix.multiply(node.matrix, loadMatrix);

					fileBuffer.position(pos);
					break;

				case G_SetOtherMode_L:
					SetOtherModeL setModeL = (SetOtherModeL) cmd;
					if (setModeL.opt == OtherModeOption.G_MDSFT_RENDERMODE) {
						int newRenderMode = RenderMode.getFromOtherModelL(setModeL.value);
						if (newRenderMode >= 0 && node.overrideMode < 0)
							node.renderMode = newRenderMode;
					}
					break;

				case G_TRI1:
					Tri1 tri1 = (Tri1) cmd;
					node.triangles.add(buildTriangle(vertexTable, tri1.v1, tri1.v2, tri1.v3));
					break;

				case G_TRI2:
					Tri2 tri2 = (Tri2) cmd;
					node.triangles.add(buildTriangle(vertexTable, tri2.v1, tri2.v2, tri2.v3));
					node.triangles.add(buildTriangle(vertexTable, tri2.v4, tri2.v5, tri2.v6));
					break;

				case G_SETIMG:
					SetImg setImage = (SetImg) cmd;
					ptrLastImage = toOffset(setImage.addr);
					break;

				case G_TEXTURE:
					Texture tex = (Texture) cmd;
					node.enableTex = tex.enable;
					if (!node.enableTex)
						node.texture = null;
					break;

				case G_LOADTLUT:
					assert (ptrLastImage != 0);
					ptrPal = ptrLastImage;
					break;

				case G_SETTILE:
					SetTile setTile = (SetTile) cmd;

					if (setTile.descriptor == 0) // G_TX_RENDERTILE, later pal gets loaded to G_TX_LOADTILE only
					{
						ptrImg = ptrLastImage;
						fmt = setTile.fmt;
						cmS = setTile.cmS;
						cmT = setTile.cmT;
					}
					break;

				case G_SETTILESIZE:
					SetTileSize setSize = (SetTileSize) cmd;
					assert (setSize.startS == 0);
					assert (setSize.startT == 0);
					width = setSize.W;
					height = setSize.H;
				default:
			}
			if (PRINT_DISPLAY_LISTS && cmd.type != CommandType.G_DL)
				Logger.log(indent + cmd.getString());
		}

		if (fmt != null) {
			node.texture = getTexture(fileBuffer, fmt, ptrImg, ptrPal, width, height);
			node.texture.cmS = cmS;
			node.texture.cmT = cmT;
			if (PRINT_DISPLAY_LISTS)
				Logger.logf(indent + "## Dumping %s texture from %X : %X", fmt, ptrImg, ptrPal);
		}

		if (PRINT_DISPLAY_LISTS)
			Logger.log("");
	}

	private static EntityTriangle buildTriangle(EntityVertex[] vertexTable, int i, int j, int k)
	{
		EntityVertex v1, v2, v3;
		v1 = vertexTable[i];
		v2 = vertexTable[j];
		v3 = vertexTable[k];

		return new EntityTriangle(v1, v2, v3);
	}

	private EntityTexture getTexture(ByteBuffer fileBuffer, TileFormat fmt, int imgAddr, int palAddr, int width, int height) throws IOException
	{
		String texName = String.format("%s_%04X", fmt, imgAddr);
		EntityTexture tex = textureMap.get(texName);
		if (tex != null)
			return tex;

		tex = ripTexture(texName, fileBuffer, fmt, imgAddr, palAddr, width, height);
		textureMap.put(texName, tex);

		String filename = entityPath + "/" + texName;
		FileUtils.touch(new File(filename + ".png"));
		tex.tile.savePNG(filename);

		return tex;
	}

	private static EntityTexture ripTexture(String texName, ByteBuffer fileBuffer,
		TileFormat fmt, int imgAddr, int palAddr, int width, int height) throws IOException
	{
		EntityTexture tex = new EntityTexture(texName);
		tex.tile = new Tile(fmt, height, width);

		if (fmt.type == TileFormat.TYPE_CI) {
			fileBuffer.position(imgAddr);
			tex.tile.readImage(fileBuffer, true);

			fileBuffer.position(palAddr);
			tex.tile.readPalette(fileBuffer);
		}
		else {
			fileBuffer.position(imgAddr);
			tex.tile.readImage(fileBuffer, true);
		}

		return tex;
	}
}
