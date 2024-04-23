package game.entity;

import static org.lwjgl.opengl.GL11.glTexCoord2f;
import static org.lwjgl.opengl.GL11.glVertex3f;
import static renderer.buffers.BufferedMesh.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

import org.apache.commons.io.FileUtils;
import game.map.editor.geometry.Vector3f;

import app.StarRodException;
import app.StarRodMain;
import app.input.IOUtils;
import game.entity.EntityInfo.EntityType;
import game.map.Axis;
import game.map.BoundingBox;
import game.map.editor.common.BaseCamera;
import game.map.editor.render.RenderMode;
import game.map.editor.render.RenderingOptions;
import game.map.editor.render.SortedRenderable;
import game.map.editor.render.TextureManager;
import game.map.editor.selection.PickRay;
import game.map.editor.selection.PickRay.PickHit;
import game.map.shape.TransformMatrix;
import game.texture.Tile;
import game.texture.TileFormat;
import renderer.buffers.BufferedMesh;
import renderer.shaders.RenderState;
import renderer.shaders.RenderState.PolygonMode;
import renderer.shaders.ShaderManager;
import renderer.shaders.scene.EntityShader;
import util.Logger;
import util.MathUtil;
import util.Priority;

public class EntityModel
{
	public final EntityType type;
	public ArrayList<EntityPart> parts = new ArrayList<>();
	public ArrayList<EntityTexture> textures = new ArrayList<>();
	private BoundingBox aabb = new BoundingBox();

	public EntityModel(EntityType type)
	{
		this.type = type;
	}

	public static final class EntityPart
	{
		public final String name;
		public List<EntityTriangle> triangles;
		public BufferedMesh mesh;

		public EntityTexture texture = null;
		public RenderMode renderMode = RenderMode.SURF_SOLID_AA_ZB;

		public EntityPart(String name)
		{
			this.name = name;
			triangles = new ArrayList<>();
		}

		public void loadGL()
		{
			mesh = new BufferedMesh(VBO_INDEX | VBO_UV | VBO_COLOR);
			for (EntityTriangle tri : triangles) {
				int i = loadVertex(tri.vert[0]);
				int j = loadVertex(tri.vert[1]);
				int k = loadVertex(tri.vert[2]);
				mesh.addTriangle(i, j, k);
			}
			mesh.loadBuffers();
		}

		private int loadVertex(EntityVertex bv)
		{
			return mesh.addVertex()
				.setPosition(bv.fx, bv.fy, bv.fz)
				.setColor(bv.r, bv.g, bv.b, bv.a)
				.setUV(bv.u, bv.v).getIndex();
		}

		public void render(
			RenderingOptions opts, EntityShader shader, TransformMatrix mtx,
			boolean selected, boolean transformSign, float modifier)
		{
			RenderMode mode = renderMode;
			if (opts.entityFogEnabled)
				mode.setState(2);
			else if (texture != null)
				mode.setState(1);
			else
				mode.setState(0);

			// ugly hack to support special-case of adjustable sign orientations
			if (transformSign) {
				RenderState.pushModelMatrix();
				TransformMatrix signMtx = TransformMatrix.identity();
				signMtx.rotate(Axis.Z, modifier);
				signMtx.translate(-0.180847f, 20.0f, 1.0f);
				mtx = mtx.concat(signMtx);
			}

			if (texture != null) {
				shader.textured.set(true);
				texture.setShaderParameters(shader);
			}
			else {
				// sometimes these wont draw. why???
				shader.textured.set(false);
			}

			mesh.renderWithTransform(mtx);

			if (transformSign)
				RenderState.popModelMatrix();
		}
	}

	public static final class EntityTriangle
	{
		public EntityVertex[] vert;

		public EntityTriangle(EntityVertex v1, EntityVertex v2, EntityVertex v3)
		{
			vert = new EntityVertex[3];
			vert[0] = v1;
			vert[1] = v2;
			vert[2] = v3;
		}

		public void render()
		{
			vert[0].render();
			vert[1].render();
			vert[2].render();
		}
	}

	public static final class EntityVertex
	{
		public float fx;
		public float fy;
		public float fz;

		public int u;
		public int v;

		public float r = 1.0f;
		public float g = 1.0f;
		public float b = 1.0f;
		public float a = 1.0f;

		// for serialization
		private EntityTexture tex;

		private EntityVertex()
		{
			// for deserialization
		}

		public EntityVertex(ByteBuffer bb, int offset, TransformMatrix matrix)
		{
			bb.position(offset);

			int x = bb.getShort();
			int y = bb.getShort();
			int z = bb.getShort();

			int zero = bb.getShort();
			if (zero != 0)
				Logger.log("Vertex with unk value " + zero + " at " + String.format("%08X", offset), Priority.IMPORTANT);

			u = bb.getShort();
			v = bb.getShort();
			r = (bb.get() & 0xFF) / 255.0f;
			g = (bb.get() & 0xFF) / 255.0f;

			b = (bb.get() & 0xFF) / 255.0f;

			a = (bb.get() & 0xFF) / 255.0f;

			Vector3f vec = new Vector3f(x, y, z);
			if (matrix != null)
				vec = matrix.applyTransform(vec);
			fx = vec.x;
			fy = vec.y;
			fz = vec.z;
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(a, b, fx, fy, fz, g, r, u, v);
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
			EntityVertex other = (EntityVertex) obj;
			if (a != other.a)
				return false;
			if (b != other.b)
				return false;
			if (Float.floatToIntBits(fx) != Float.floatToIntBits(other.fx))
				return false;
			if (Float.floatToIntBits(fy) != Float.floatToIntBits(other.fy))
				return false;
			if (Float.floatToIntBits(fz) != Float.floatToIntBits(other.fz))
				return false;
			if (g != other.g)
				return false;
			if (r != other.r)
				return false;
			if (u != other.u)
				return false;
			if (v != other.v)
				return false;
			return true;
		}

		public void render()
		{
			RenderState.setColor(r, g, b, a);
			glTexCoord2f(u, v);
			glVertex3f(fx, fy, fz);
		}

		public Vector3f getVector()
		{ return new Vector3f(fx, fy, fz); }

		/*
		public Vector3f getWorldVector(float yaw, float dx, float dy, float dz)
		{
			double yawRad = Math.toRadians(yaw);
			float sinYaw = (float)Math.sin(yawRad);
			float cosYaw = (float)Math.cos(yawRad);

			return new Vector3f(
					dx + cosYaw * fx + sinYaw * fz,
					dy + fy,
					dz + -sinYaw * fx + cosYaw * fz);
		}
		 */
	}

	public static final class EntityTexture
	{
		public final String name;
		public Tile tile;
		public int cmS;
		public int cmT;

		public EntityTexture(String name)
		{
			this.name = name;
		}

		private static float getScaleU(EntityTexture tex)
		{
			return (tex != null) ? 32.0f * tex.tile.width : 1024.0f;
		}

		private static float getScaleV(EntityTexture tex)
		{
			return (tex != null) ? 32.0f * tex.tile.height : 1024.0f;
		}

		public void load(String directory)
		{
			try {
				tile = Tile.load(
					new File(directory + "/" + name + ".png"),
					TileFormat.getFormat(name.substring(0, name.indexOf('_'))));
			}
			catch (IOException e) {
				StarRodMain.displayStackTrace(e);
			}
		}

		public void glLoad()
		{
			tile.glLoad(cmS, cmT, false);

			if (tile.palette != null)
				tile.palette.glLoad();
		}

		public void glDelete()
		{
			tile.glDelete();

			if (tile.palette != null)
				tile.palette.glDelete();
		}

		public void setShaderParameters(EntityShader shader)
		{
			int type = tile.format.type;
			shader.format.set(type);

			tile.glBind(shader.texture);

			if (type == TileFormat.TYPE_CI)
				tile.palette.glBind(shader.palette);
			else
				TextureManager.missingPalette.glBind(shader.palette);
		}
	}

	public void writeToObj(File f)
	{
		ArrayList<EntityVertex> vertexTable = new ArrayList<>(256);
		HashMap<EntityVertex, Integer> vertexMap = new LinkedHashMap<>();

		for (EntityPart part : parts) {
			for (EntityTriangle tri : part.triangles) {
				if (!vertexMap.containsKey(tri.vert[0])) {
					int vertIndex = vertexTable.size();
					vertexMap.put(tri.vert[0], vertIndex);
					vertexTable.add(tri.vert[0]);
				}
				tri.vert[0].tex = part.texture;

				if (!vertexMap.containsKey(tri.vert[1])) {
					int vertIndex = vertexTable.size();
					vertexMap.put(tri.vert[1], vertIndex);
					vertexTable.add(tri.vert[1]);
				}
				tri.vert[1].tex = part.texture;

				if (!vertexMap.containsKey(tri.vert[2])) {
					int vertIndex = vertexTable.size();
					vertexMap.put(tri.vert[2], vertIndex);
					vertexTable.add(tri.vert[2]);
				}
				tri.vert[2].tex = part.texture;
			}
		}

		try {
			FileUtils.touch(f);
			try (PrintWriter pw = IOUtils.getBufferedPrintWriter(f)) {
				for (EntityVertex v : vertexTable) {
					if (v.r != 255 || v.g != 255 || v.b != 255 || v.a != 255)
						pw.printf("v  %f %f %f %f %f %f %f%n", v.fx, v.fy, v.fz, v.r, v.g, v.b, v.a);
					else
						pw.printf("v  %f %f %f%n", v.fx, v.fy, v.fz);
				}
				for (EntityVertex v : vertexTable) {
					float uScale = EntityTexture.getScaleU(v.tex);
					float vScale = EntityTexture.getScaleV(v.tex);
					pw.printf("vt %f %f%n", v.u / uScale, v.v / vScale);
				}
				for (EntityPart part : parts) {
					pw.printf("o %s%n", part.name);
					pw.printf("mode %X%n", part.renderMode.id);
					if (part.texture != null)
						pw.printf("tex %s %X %X%n", part.texture.name, part.texture.cmS, part.texture.cmT);
					for (EntityTriangle tri : part.triangles) {
						int i = vertexMap.get(tri.vert[0]) + 1;
						int j = vertexMap.get(tri.vert[1]) + 1;
						int k = vertexMap.get(tri.vert[2]) + 1;

						pw.printf("f  %d/%d %d/%d %d/%d%n", i, i, j, j, k, k);
					}
				}
			}
		}
		catch (IOException e) {
			Logger.logError("Failed to write entity model to " + f.getName());
			Logger.printStackTrace(e);
		}
	}

	public void readFromObj(String directory, boolean loadGL)
	{
		HashMap<String, EntityTexture> textureSet = new HashMap<>();

		try (BufferedReader reader = new BufferedReader(new FileReader(new File(directory + "/model.obj")))) {
			ArrayList<EntityVertex> vertexTable = new ArrayList<>(512);
			ArrayList<float[]> uvTable = new ArrayList<>(512);

			EntityPart currentPart = null;

			String line;
			int lineNum = 0;
			while ((line = reader.readLine()) != null) {
				lineNum++;

				String[] tokens = line.trim().split("\\s+");
				if (tokens[0].equals("o")) {
					currentPart = new EntityPart(tokens[1]);
					parts.add(currentPart);
				}
				else if (tokens[0].equals("mode")) {
					currentPart.renderMode = RenderMode.getModeForID(Integer.parseInt(tokens[1], 16));
				}
				else if (tokens[0].equals("tex")) {
					String texName = tokens[1];
					if (!textureSet.containsKey(texName)) {
						EntityTexture tex = new EntityTexture(texName);
						tex.load(directory);
						textureSet.put(texName, tex);
					}
					currentPart.texture = textureSet.get(texName);
					currentPart.texture.cmS = Integer.parseInt(tokens[2], 16);
					currentPart.texture.cmT = Integer.parseInt(tokens[3], 16);
				}
				else if (tokens[0].equals("v")) {
					if (tokens.length != 4 && tokens.length != 8)
						throw new IOException("Invalid vertex specifier on line " + lineNum);

					EntityVertex v = new EntityVertex();
					v.fx = Float.parseFloat(tokens[1]);
					v.fy = Float.parseFloat(tokens[2]);
					v.fz = Float.parseFloat(tokens[3]);

					if (tokens.length == 8) {
						v.r = Float.parseFloat(tokens[4]);
						v.g = Float.parseFloat(tokens[5]);
						v.b = Float.parseFloat(tokens[6]);
						v.a = Float.parseFloat(tokens[7]);
					}

					vertexTable.add(v);
				}
				else if (tokens[0].equals("vt")) {
					float u = (float) Double.parseDouble(tokens[1]);
					float v = (float) Double.parseDouble(tokens[2]);

					uvTable.add(new float[] { u, v });
				}
				else if (tokens[0].equals("f") && tokens.length == 4) {
					float uScale = EntityTexture.getScaleU(currentPart.texture);
					float vScale = EntityTexture.getScaleV(currentPart.texture);

					EntityTriangle tri = buildTriangle(vertexTable, uvTable, tokens, uScale, vScale);
					currentPart.triangles.add(tri);
				}
				else
					Logger.logWarning("Unsupported OBJ keyword: " + line);
			}

			textures.addAll(textureSet.values());

			for (EntityVertex v : vertexTable) {
				aabb.encompass(
					MathUtil.roundAwayFromZero(v.fx),
					MathUtil.roundAwayFromZero(v.fy),
					MathUtil.roundAwayFromZero(v.fz));
			}

			if (type == EntityType.BlueWarpPipe) {
				Vector3f min = aabb.getMin();
				aabb.encompass((int) min.x, (int) min.y - 55, (int) min.z);
			}

			if (loadGL) {
				for (EntityPart part : parts)
					part.loadGL();
			}
		}
		catch (IOException e) {
			throw new StarRodException("Failed to read entity model from " + directory);
		}
	}

	private static EntityTriangle buildTriangle(ArrayList<EntityVertex> vertexTable, ArrayList<float[]> uvTable, String[] tokens, float uScale,
		float vScale)
	{
		EntityVertex[] verts = new EntityVertex[3];

		for (int i = 0; i < 3; i++) {
			String[] vertTokens = tokens[i + 1].split("/");
			int vertIndex, uvIndex;
			float[] currentUV;

			switch (vertTokens.length) {
				case 1: //	v	i  j  k
					vertIndex = Integer.parseInt(vertTokens[0]);
					vertIndex = (vertIndex < 0) ? vertexTable.size() + vertIndex : vertIndex - 1;
					verts[i] = vertexTable.get(vertIndex);
					break;
				case 2: //	v  i/t  j/t  k/t
					vertIndex = Integer.parseInt(vertTokens[0]);
					vertIndex = (vertIndex < 0) ? vertexTable.size() + vertIndex : vertIndex - 1;
					uvIndex = Integer.parseInt(vertTokens[1]);
					uvIndex = (uvIndex < 0) ? uvTable.size() + uvIndex : uvIndex - 1;
					verts[i] = vertexTable.get(vertIndex);
					if (uvTable.size() > uvIndex)
						currentUV = uvTable.get(uvIndex);
					else
						currentUV = new float[] { 0, 0 };
					verts[i].u = Math.round(currentUV[0] * uScale);
					verts[i].v = Math.round(currentUV[1] * vScale);
					break;
				case 3: //	v  i/t/n  j/t/n  k/t/n    ---OR--     v  i//n  j//n  k//n
					vertIndex = Integer.parseInt(vertTokens[0]);
					vertIndex = (vertIndex < 0) ? vertexTable.size() + vertIndex : vertIndex - 1;
					verts[i] = vertexTable.get(vertIndex);

					if (!vertTokens[1].isEmpty()) {
						uvIndex = Integer.parseInt(vertTokens[1]);
						uvIndex = (uvIndex < 0) ? uvTable.size() + uvIndex : uvIndex - 1;
						if (uvTable.size() > uvIndex)
							currentUV = uvTable.get(uvIndex);
						else
							currentUV = new float[] { 0, 0 };
						verts[i].u = Math.round(currentUV[0] * uScale);
						verts[i].v = Math.round(currentUV[1] * vScale);
					}
			}
		}

		return new EntityTriangle(verts[0], verts[1], verts[2]);
	}

	public void loadTextures()
	{
		for (EntityTexture tex : textures)
			tex.glLoad();
	}

	public void freeTextures()
	{
		for (EntityTexture tex : textures)
			tex.glDelete();
	}

	public PickHit tryPick(PickRay ray, EntityType entityType, float modifier)
	{
		PickHit nearest = new PickHit(ray);

		PickHit aabbHit = PickRay.getIntersection(ray, aabb);
		if (aabbHit.missed() && entityType != EntityType.ArrowSign)
			return nearest;

		for (int i = 0; i < parts.size(); i++) {
			EntityPart part = parts.get(i);

			// ugly hack to support special-case of adjustable sign orientations
			boolean transformSign = (i == 0 && entityType == EntityType.ArrowSign);

			for (EntityTriangle triangle : part.triangles) {
				PickHit hit;
				if (transformSign) {
					hit = PickRay.getIntersection(ray,
						getRedSignTransfrom(triangle.vert[0].getVector(), modifier),
						getRedSignTransfrom(triangle.vert[1].getVector(), modifier),
						getRedSignTransfrom(triangle.vert[2].getVector(), modifier));
				}
				else {
					hit = PickRay.getIntersection(ray,
						triangle.vert[0].getVector(),
						triangle.vert[1].getVector(),
						triangle.vert[2].getVector());
				}
				if (!hit.missed() && hit.dist < nearest.dist)
					nearest = hit;
			}
		}

		return nearest;
	}

	// matrix transform
	private static Vector3f getRedSignTransfrom(Vector3f in, float angle)
	{
		float sinA = (float) Math.sin(Math.toRadians(angle));
		float cosA = (float) Math.cos(Math.toRadians(angle));

		Vector3f out = new Vector3f();
		out.x = (in.x * cosA - in.y * sinA) - 0.180847f;
		out.y = (in.x * sinA + in.y * cosA) + 20.0f;
		out.z = in.z + 1.0f;

		return out;
	}

	public static class RenderablePart implements SortedRenderable
	{
		private final EntityPart part;
		private final boolean transformSign;
		private final boolean selected;
		private final float x, y, z, yaw;
		private final float modifier;
		private int depth;

		public RenderablePart(EntityModel mdl, int partIndex, boolean selected, float x, float y, float z, float yaw, float modifier)
		{
			part = mdl.parts.get(partIndex);
			transformSign = (partIndex == 0 && mdl.type == EntityType.ArrowSign);
			this.modifier = modifier;
			this.selected = selected;
			this.x = x;
			this.y = y;
			this.z = z;
			this.yaw = yaw;
		}

		@Override
		public RenderMode getRenderMode()
		{ return part.renderMode; }

		@Override
		public Vector3f getCenterPoint()
		{ return new Vector3f(x, y, z); }

		@Override
		public void render(RenderingOptions opts, BaseCamera camera)
		{
			EntityShader shader = ShaderManager.use(EntityShader.class);
			shader.useFiltering.set(opts.useFiltering);
			shader.selected.set(selected);

			RenderState.setPolygonMode(PolygonMode.FILL);
			RenderState.setColor(1.0f, 1.0f, 1.0f, 1.0f);

			RenderState.pushModelMatrix();
			TransformMatrix mtx = TransformMatrix.identity();

			mtx.rotate(Axis.Y, yaw);
			mtx.translate(x, y, z);

			part.render(opts, shader, mtx, selected, transformSign, modifier);

			RenderState.popModelMatrix();

			// reset state a bit
			RenderMode.resetState();
		}

		@Override
		public void setDepth(int normalizedDepth)
		{ depth = normalizedDepth; }

		@Override
		public int getDepth()
		{ return depth; }
	}
}
