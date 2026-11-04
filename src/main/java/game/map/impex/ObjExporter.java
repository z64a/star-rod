package game.map.impex;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import common.Vector3f;

import app.Environment;
import app.input.IOUtils;
import assets.AssetManager;
import assets.AssetSubdir;
import game.map.hit.Collider;
import game.map.hit.Zone;
import game.map.mesh.BasicMesh;
import game.map.mesh.TexturedMesh;
import game.map.mesh.Triangle;
import game.map.mesh.Vertex;
import game.map.shape.Model;
import game.texture.ModelTexture;

public class ObjExporter
{
	private final PrintWriter pw;
	private final IdentityHashMap<Vertex, Integer> vertexMap;
	private final ArrayList<Vertex> vertexTable;

	public ObjExporter(File f) throws FileNotFoundException
	{
		pw = IOUtils.getBufferedPrintWriter(f);
		vertexMap = new IdentityHashMap<>();
		vertexTable = new ArrayList<>();
	}

	public void writeModels(Iterable<Model> models, String texName)
	{
		boolean wroteLibrary = false;
		for (String archiveName : getTextureArchives(models, texName)) {
			File textureFile = AssetManager.get(AssetSubdir.MAP_TEX, archiveName + "/" + archiveName + ".mtl");
			if (textureFile.exists()) {
				pw.println("mtllib " + archiveName);
				wroteLibrary = true;
			}
		}
		if (wroteLibrary)
			pw.println("");

		for (Model mdl : models) {
			if (!mdl.hasMesh())
				continue;
			exportTexturedMesh(mdl.getName(), mdl.getMesh());
		}
	}

	/** The archives the models' textures are from: every one they use with the texture pool, or else the map's. */
	private static Collection<String> getTextureArchives(Iterable<Model> models, String mapArchiveName)
	{
		if (!Environment.usingTexturePool())
			return List.of(mapArchiveName);

		Set<String> archiveNames = new LinkedHashSet<>();
		for (Model mdl : models) {
			if (mdl.hasMesh() && mdl.getMesh().texture != null)
				archiveNames.add(mdl.getMesh().texture.archiveName);
		}
		return archiveNames;
	}

	public void exportTexturedMesh(String name, TexturedMesh mesh)
	{
		int start = vertexTable.size();

		for (Triangle t : mesh) {
			Vector3f faceNormal = t.getNormal();
			if (faceNormal == null)
				faceNormal = new Vector3f(0.0f, 1.0f, 0.0f);

			for (Vertex v : t.vert) {
				if (!vertexMap.containsKey(v)) {
					vertexTable.add(v);
					vertexMap.put(v, vertexTable.size());
					v.normal = new Vector3f();
				}
				v.normal.add(faceNormal);
			}
		}

		for (int i = start; i < vertexTable.size(); i++)
			pw.println("v  "
				+ vertexTable.get(i).getCurrentX() + " "
				+ vertexTable.get(i).getCurrentY() + " "
				+ vertexTable.get(i).getCurrentZ());

		for (int i = start; i < vertexTable.size(); i++)
			pw.println("vn  "
				+ vertexTable.get(i).normal.x + " "
				+ vertexTable.get(i).normal.y + " "
				+ vertexTable.get(i).normal.z);

		float uScale = ModelTexture.getScaleU(mesh.texture);
		float vScale = ModelTexture.getScaleV(mesh.texture);

		for (int i = start; i < vertexTable.size(); i++)
			pw.println("vt  "
				+ vertexTable.get(i).uv.getU() / uScale + " "
				+ vertexTable.get(i).uv.getV() / vScale);

		pw.println("");

		pw.println("o " + name);
		if (mesh.textured)
			pw.println("usemtl m_" + mesh.textureName);

		for (Triangle t : mesh) {
			int i = vertexMap.get(t.vert[0]);
			int j = vertexMap.get(t.vert[1]);
			int k = vertexMap.get(t.vert[2]);

			pw.printf("f  %d/%d/%d %d/%d/%d %d/%d/%d%n",
				i, i, i, j, j, j, k, k, k);
		}

		pw.println("");
	}

	public void writeColliders(Iterable<Collider> colliders)
	{
		for (Collider c : colliders) {
			if (!c.hasMesh())
				continue;
			exportBasicMesh(c.getName(), c.mesh);
		}
	}

	public void writeZones(Iterable<Zone> zones)
	{
		for (Zone z : zones) {
			if (!z.hasMesh())
				continue;
			exportBasicMesh(z.getName(), z.mesh);
		}
	}

	public void exportBasicMesh(String name, BasicMesh mesh)
	{
		int start = vertexTable.size();

		for (Triangle t : mesh) {
			Vector3f faceNormal = t.getNormal();
			if (faceNormal == null)
				faceNormal = new Vector3f(0.0f, 1.0f, 0.0f);
			for (Vertex v : t.vert) {
				if (!vertexMap.containsKey(v)) {
					vertexTable.add(v);
					vertexMap.put(v, vertexTable.size());
					v.normal = new Vector3f();
				}
				v.normal.add(faceNormal);
			}
		}

		for (int i = start; i < vertexTable.size(); i++)
			pw.println("v  "
				+ vertexTable.get(i).getCurrentX() + " "
				+ vertexTable.get(i).getCurrentY() + " "
				+ vertexTable.get(i).getCurrentZ());

		for (int i = start; i < vertexTable.size(); i++)
			pw.println("vn  "
				+ vertexTable.get(i).normal.x + " "
				+ vertexTable.get(i).normal.y + " "
				+ vertexTable.get(i).normal.z);

		pw.println("");

		pw.println("o " + name);
		for (Triangle t : mesh) {
			int i = vertexMap.get(t.vert[0]);
			int j = vertexMap.get(t.vert[1]);
			int k = vertexMap.get(t.vert[2]);

			pw.printf("f  %d//%d %d//%d %d//%d%n",
				i, i, j, j, k, k);
		}

		pw.println("");
	}

	public void close()
	{
		pw.close();
	}
}
