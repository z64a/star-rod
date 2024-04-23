package game.map.editor.render;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

import app.Resource;
import app.Resource.ResourceType;
import game.map.editor.geometry.Vector3f;
import renderer.buffers.TriangleRenderQueue;
import util.Logger;
import util.Priority;

public class UtilityModel
{
	public static class UtilityTriangle
	{
		public final Vector3f a, b, c;

		public UtilityTriangle(Vector3f a, Vector3f b, Vector3f c)
		{
			this.a = a;
			this.b = b;
			this.c = c;
		}

		public void queueForRendering(boolean inverted)
		{
			if (inverted) {
				TriangleRenderQueue.addTriangle(
					TriangleRenderQueue.addVertex().setPosition(a.x, a.y, a.z).getIndex(),
					TriangleRenderQueue.addVertex().setPosition(c.x, c.y, c.z).getIndex(),
					TriangleRenderQueue.addVertex().setPosition(b.x, b.y, b.z).getIndex());
			}
			else {
				TriangleRenderQueue.addTriangle(
					TriangleRenderQueue.addVertex().setPosition(a.x, a.y, a.z).getIndex(),
					TriangleRenderQueue.addVertex().setPosition(b.x, b.y, b.z).getIndex(),
					TriangleRenderQueue.addVertex().setPosition(c.x, c.y, c.z).getIndex());
			}
		}
	}

	public ArrayList<UtilityTriangle> triangles;

	public UtilityModel(File f)
	{
		triangles = new ArrayList<>();

		try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
			readFromObj(reader);
		}
		catch (IOException e) {
			Logger.log("Failed to load resource " + f.getName(), Priority.ERROR);
		}
	}

	public UtilityModel(String resourceName)
	{
		triangles = new ArrayList<>();

		InputStream is = Resource.getStream(ResourceType.EditorAsset, resourceName);
		if (is == null) {
			Logger.log("Unable to locate resource " + resourceName, Priority.ERROR);
			return;
		}

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
			readFromObj(reader);
		}
		catch (IOException e) {
			Logger.log("Failed to load resource " + resourceName, Priority.ERROR);
		}
	}

	private void readFromObj(BufferedReader reader) throws IOException
	{
		ArrayList<Vector3f> vertexTable = new ArrayList<>(512);
		vertexTable.add(new Vector3f()); // index zero

		String line;
		while ((line = reader.readLine()) != null) {
			if (line.startsWith("v")) {
				String[] tokens = line.split("\\s+");

				Vector3f v = new Vector3f();
				v.x = Float.parseFloat(tokens[1]);
				v.y = -Float.parseFloat(tokens[2]);
				v.z = Float.parseFloat(tokens[3]);
				vertexTable.add(v);
			}

			if (line.startsWith("f")) {
				String[] tokens = line.split("\\s+");

				Vector3f a = vertexTable.get(Integer.parseInt(tokens[1]));
				Vector3f b = vertexTable.get(Integer.parseInt(tokens[2]));
				Vector3f c = vertexTable.get(Integer.parseInt(tokens[3]));
				UtilityTriangle t = new UtilityTriangle(b, a, c);

				/*
				Vector3f diff21 = new Vector3f();
				Vector3f diff31 = new Vector3f();;
				Vector3f.sub(t.v2.position, t.v1.position, diff21);
				Vector3f.sub(t.v3.position, t.v1.position, diff31);
				t.norm = Vector3f.cross(diff21, diff31);
				 */

				triangles.add(t);
			}
		}
	}

	public void render(boolean inverted)
	{
		for (UtilityTriangle t : triangles)
			t.queueForRendering(inverted);
		TriangleRenderQueue.render(true);
	}
}
