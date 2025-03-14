package game.map.impex;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import common.Vector3f;
import game.map.MapObject;
import game.map.MapObject.HitType;
import game.map.hit.Collider;
import game.map.hit.Zone;
import game.map.mesh.Triangle;
import game.map.mesh.Vertex;
import game.map.shape.Model;
import game.map.shape.TriangleBatch;
import game.map.shape.UV;
import game.texture.ModelTexture;
import util.Logger;
import util.Priority;

//TODO support using \ to continue line on next line
public class ObjImporter
{
	private List<Vertex> vertexTable;
	private List<Vector3f> normalTable;
	private List<float[]> uvTable;

	public ObjImporter()
	{}

	public List<Collider> readColliders(File f) throws IOException
	{
		BufferedReader in = new BufferedReader(new FileReader(f));
		String line;

		vertexTable = new ArrayList<>();
		normalTable = new ArrayList<>();
		uvTable = new ArrayList<>();

		List<Collider> colliderList = new ArrayList<>();
		Collider currentCollider = null;
		TriangleBatch currentBatch = null;

		while ((line = in.readLine()) != null) {
			line = cleanLine(line);
			if (line.isEmpty())
				continue;

			String[] tokens = line.split(" ");

			if (tokens[0].equals("o")) {
				if (currentCollider != null)
					currentCollider.updateMeshHierarchy();

				Collider c = new Collider(HitType.HIT);
				c.setName(tokens[1]);

				colliderList.add(c);
				currentCollider = c;
				currentBatch = c.mesh.batch;

			}
			else if (tokens[0].equals("v"))
				readVertex(tokens);
			else if (tokens[0].equals("vn"))
				readNormal(tokens);
			else if (tokens[0].equals("vt"))
				readUV(tokens);
			else if (tokens[0].equals("f") && tokens.length == 4)
				currentBatch.triangles.add(readTriangle(tokens));
			else
				Logger.log("Unsupported OBJ keyword: " + line);
		}
		in.close();

		if (currentCollider != null)
			currentCollider.updateMeshHierarchy();

		// fix normals if necessary
		fixNormals(colliderList);

		return colliderList;
	}

	public List<Zone> readZones(File f) throws IOException
	{
		BufferedReader in = new BufferedReader(new FileReader(f));
		String line;

		vertexTable = new ArrayList<>();
		normalTable = new ArrayList<>();
		uvTable = new ArrayList<>();

		List<Zone> zoneList = new ArrayList<>();
		Zone currentZone = null;
		TriangleBatch currentBatch = null;

		while ((line = in.readLine()) != null) {
			line = cleanLine(line);
			if (line.isEmpty())
				continue;

			String[] tokens = line.split(" ");

			if (tokens[0].equals("o")) {
				if (currentZone != null)
					currentZone.updateMeshHierarchy();

				Zone z = new Zone(HitType.HIT);
				z.setName(tokens[1]);

				zoneList.add(z);
				currentZone = z;
				currentBatch = z.mesh.batch;

			}
			else if (tokens[0].equals("v"))
				readVertex(tokens);
			else if (tokens[0].equals("vn"))
				readNormal(tokens);
			else if (tokens[0].equals("vt"))
				readUV(tokens);
			else if (tokens[0].equals("f") && tokens.length == 4)
				currentBatch.triangles.add(readTriangle(tokens));
			else
				Logger.log("Unsupported OBJ keyword: " + line);
		}
		in.close();

		if (currentZone != null)
			currentZone.updateMeshHierarchy();

		// fix normals if necessary
		fixNormals(zoneList);

		return zoneList;
	}

	public List<Model> readModels(File f) throws IOException
	{
		BufferedReader in = new BufferedReader(new FileReader(f));
		String line;

		vertexTable = new ArrayList<>();
		normalTable = new ArrayList<>();
		uvTable = new ArrayList<>();

		List<Model> modelList = new ArrayList<>();
		Model currentModel = null;
		TriangleBatch currentBatch = null;
		ModelTexture currentTexture = null;

		while ((line = in.readLine()) != null) {
			line = cleanLine(line);
			if (line.isEmpty())
				continue;

			String[] tokens = line.split(" ");

			if (tokens[0].equals("o")) {
				if (currentModel != null)
					currentModel.updateMeshHierarchy();

				Model mdl = Model.createBareModel();
				modelList.add(mdl);

				TriangleBatch batch = new TriangleBatch(mdl.getMesh());
				mdl.getMesh().displayListModel.addElement(batch);

				mdl.setName(tokens[1]);

				currentModel = mdl;
				currentBatch = batch;
				currentTexture = null;
			}
			else if (tokens[0].equals("usemtl")) {
				String texName = tokens[1].substring(2);
				currentModel.getMesh().setTexture(texName);
				currentTexture = currentModel.getMesh().texture;
			}
			else if (tokens[0].equals("v"))
				readVertex(tokens);
			else if (tokens[0].equals("vn"))
				readNormal(tokens);
			else if (tokens[0].equals("vt"))
				readUV(tokens);
			else if (tokens[0].equals("f") && tokens.length == 4) {
				float uScale = ModelTexture.getScaleU(currentTexture);
				float vScale = ModelTexture.getScaleV(currentTexture);
				currentBatch.triangles.add(readTriangle(tokens, uScale, vScale));
			}
			else if (tokens[0].equals("f") && tokens.length == 5) {
				Logger.logWarning("Unsupported OBJ face quads: " + line);
			}
			else
				Logger.logWarning("Unsupported OBJ keyword: " + line);
		}
		in.close();

		if (currentModel != null)
			currentModel.updateMeshHierarchy();

		// fix normals if necessary
		fixNormals(modelList);

		return modelList;
	}

	private String cleanLine(String line)
	{
		line = line.replaceAll(" +|\t+", " ").trim(); // remove whitespace
		if (line.contains("#")) // ignore comments, trim end of line comments
		{
			if (line.startsWith("#"))
				return "";

			line = line.substring(0, line.indexOf("#")).trim();
		}

		return line;
	}

	private void readVertex(String[] tokens)
	{
		int x = (int) Math.round(Double.parseDouble(tokens[1]));
		int y = (int) Math.round(Double.parseDouble(tokens[2]));
		int z = (int) Math.round(Double.parseDouble(tokens[3]));
		Vertex v = new Vertex(x, y, z);
		v.normal = new Vector3f();
		vertexTable.add(v);
	}

	private void readNormal(String[] tokens)
	{
		float nx = (float) Double.parseDouble(tokens[1]);
		float ny = (float) Double.parseDouble(tokens[2]);
		float nz = (float) Double.parseDouble(tokens[3]);
		normalTable.add(new Vector3f(nx, ny, nz));
	}

	private void readUV(String[] tokens)
	{
		float u = (float) Double.parseDouble(tokens[1]);
		float v = (float) Double.parseDouble(tokens[2]);
		uvTable.add(new float[] { u, v });
	}

	private Triangle readTriangle(String[] tokens)
	{
		return readTriangle(tokens, 1.0f, 1.0f);
	}

	private Triangle readTriangle(String[] tokens, float uScale, float vScale)
	{
		Vertex[] verts = new Vertex[3];

		for (int i = 0; i < 3; i++) {
			String[] vertTokens = tokens[i + 1].split("/");
			int vertIndex, uvIndex, normIndex;
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
					verts[i].uv = new UV(currentUV[0] * uScale, currentUV[1] * vScale);
					break;
				case 3: //	v  i/t/n  j/t/n  k/t/n    ---OR--     v  i//n  j//n  k//n
					vertIndex = Integer.parseInt(vertTokens[0]);
					vertIndex = (vertIndex < 0) ? vertexTable.size() + vertIndex : vertIndex - 1;
					verts[i] = vertexTable.get(vertIndex);

					normIndex = Integer.parseInt(vertTokens[2]);
					normIndex = (normIndex < 0) ? normalTable.size() + normIndex : normIndex - 1;
					verts[i].normal.set(normalTable.get(normIndex));

					if (!vertTokens[1].isEmpty()) {
						uvIndex = Integer.parseInt(vertTokens[1]);
						uvIndex = (uvIndex < 0) ? uvTable.size() + uvIndex : uvIndex - 1;
						if (uvTable.size() > uvIndex)
							currentUV = uvTable.get(uvIndex);
						else
							currentUV = new float[] { 0, 0 };
						verts[i].uv = new UV(currentUV[0] * uScale, currentUV[1] * vScale);
					}
			}
		}

		return new Triangle(verts[0], verts[1], verts[2]);
	}

	private void fixNormals(Iterable<? extends MapObject> objs)
	{
		int fixedNormals = 0;
		for (MapObject obj : objs)
			for (Triangle t : obj.getMesh()) {
				Vector3f currentNormal = t.getNormal();
				if (currentNormal == null)
					currentNormal = new Vector3f(0.0f, 1.0f, 0.0f);

				// face normal = vector sum of vertex normals
				Vector3f importedNormal = new Vector3f();
				for (Vertex v : t.vert) {
					importedNormal.add(v.normal);
				}

				// only care about sign, no need to normalize
				if (Vector3f.dot(currentNormal, importedNormal) < 0) {
					Vertex temp = t.vert[1];
					t.vert[1] = t.vert[2];
					t.vert[2] = temp;

					fixedNormals++;
				}
			}

		if (fixedNormals > 0)
			Logger.log("Fixed normals for " + fixedNormals + " triangles.", Priority.STANDARD);
	}
}
