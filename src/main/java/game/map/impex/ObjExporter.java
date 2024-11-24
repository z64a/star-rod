package game.map.impex;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.IdentityHashMap;

import app.input.IOUtils;
import common.Vector3f;
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

	public void writeModels(Iterable<Model> models, String mtlFilename)
	{
		if (!mtlFilename.isEmpty()) {
			pw.println("mtllib " + mtlFilename);
			pw.println("");
		}

		for (Model mdl : models) {
			if (!mdl.hasMesh())
				continue;
			exportTexturedMesh(mdl.getName(), mdl.getMesh());
		}
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
