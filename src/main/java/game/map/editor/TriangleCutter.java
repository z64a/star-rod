package game.map.editor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;

import common.Vector3f;
import common.commands.AbstractCommand;
import common.commands.CommandBatch;

import game.map.MapObject;
import game.map.editor.commands.CreateObject;
import game.map.editor.selection.SelectionManager.SelectionMode;
import game.map.mesh.AbstractMesh;
import game.map.mesh.TexturedMesh;
import game.map.mesh.Triangle;
import game.map.mesh.Vertex;
import game.map.shape.Model;
import game.map.shape.TriangleBatch;
import game.map.shape.UV;
import game.map.tree.MapObjectNode;
import util.Logger;
import util.identity.IdentityArrayList;

public class TriangleCutter
{
	private static final float PLANE_EPSILON = 1.0e-4f;

	private final Vector3f planeNormal;
	private final Vector3f planePoint;
	private boolean successful;

	TriangleCutter(Vector3f planePoint, Vector3f planeNormal)
	{
		this.planePoint = new Vector3f(planePoint);
		this.planeNormal = new Vector3f(planeNormal);
		if (this.planeNormal.length() >= PLANE_EPSILON)
			this.planeNormal.normalize();
	}

	public TriangleCutter(Vector3f planePoint, Vector3f planeNormal, List<MapObject> selectedObjects)
	{
		this(planePoint, planeNormal);

		if (this.planeNormal.length() < PLANE_EPSILON) {
			Logger.logWarning("Cannot cut with a degenerate plane.");
			return;
		}
		this.planeNormal.normalize();

		MapEditor editor = MapEditor.instance();
		if (editor.selectionManager.getSelectionMode() != SelectionMode.OBJECT) {
			Logger.logWarning("The cut tool requires object selection mode.");
			return;
		}

		if (selectedObjects == null || selectedObjects.isEmpty()) {
			Logger.logWarning("Select at least one object before cutting.");
			return;
		}

		for (MapObject obj : selectedObjects) {
			if (obj instanceof Model && obj.hasMesh() && hasWorldSpaceVertices(obj)) {
				Logger.logWarning("Cannot cut transformed model " + obj.getName() + ". Bake its transforms first.");
				return;
			}
		}

		List<ObjectSplit> objectSplits = new ArrayList<>();
		try {
			for (MapObject obj : selectedObjects) {
				if (obj.hasMesh()) {
					ObjectSplit split = buildObjectSplit(obj);
					if (split != null)
						objectSplits.add(split);
				}
			}
		}
		catch (CutException e) {
			Logger.logWarning("Cut aborted: " + e.getMessage());
			return;
		}

		if (objectSplits.isEmpty()) {
			Logger.logWarning("The cut plane did not split any selected objects.");
			return;
		}

		CommandBatch commands = new CommandBatch("Cut Triangles");
		for (ObjectSplit split : objectSplits) {
			if (!split.selectedTrianglesToRemove.isEmpty())
				commands.addCommand(editor.selectionManager.getModifyTriangles(
					null, split.selectedTrianglesToRemove, false));

			commands.addCommand(new CreateObject(split.cutObject));
			commands.addCommand(new ReplaceBatches(split.replacements));

			if (!split.selectedTrianglesToAdd.isEmpty())
				commands.addCommand(editor.selectionManager.getModifyTriangles(
					split.selectedTrianglesToAdd, null, false));
		}

		MapEditor.execute(commands);
		successful = true;
	}

	public boolean wasSuccessful()
	{
		return successful;
	}

	private boolean hasWorldSpaceVertices(MapObject obj)
	{
		for (Triangle t : obj.getMesh()) {
			for (Vertex v : t.vert) {
				if (!v.useLocal)
					return true;
			}
		}
		return false;
	}

	ObjectSplit buildObjectSplit(MapObject obj) throws CutException
	{
		List<TriangleBatch> sourceBatches = obj.getMesh().getBatches();
		List<BatchSplit> batchSplits = new ArrayList<>(sourceBatches.size());
		HashMap<EdgeKey, Vertex> intersectionCache = new HashMap<>();
		IdentityArrayList<Triangle> selectedTrianglesToRemove = new IdentityArrayList<>();
		IdentityArrayList<Triangle> selectedTrianglesToAdd = new IdentityArrayList<>();

		int positiveCount = 0;
		int negativeCount = 0;

		for (TriangleBatch batch : sourceBatches) {
			BatchSplit batchSplit = new BatchSplit(batch);

			for (Triangle source : batch.triangles) {
				TriangleSplit triangleSplit = splitTriangle(source, intersectionCache);
				batchSplit.positive.addAll(triangleSplit.positive);
				batchSplit.negative.addAll(triangleSplit.negative);

				positiveCount += triangleSplit.positive.size();
				negativeCount += triangleSplit.negative.size();

				boolean remainsUnchanged = triangleSplit.negative.size() == 1
					&& triangleSplit.negative.get(0) == source;
				if (source.selected && !remainsUnchanged) {
					selectedTrianglesToRemove.add(source);
					selectedTrianglesToAdd.addAll(triangleSplit.negative);
				}
			}

			batchSplits.add(batchSplit);
		}

		// Moving an entire object to a duplicate is not a cut. Leave it untouched.
		if (positiveCount == 0 || negativeCount == 0)
			return null;

		MapObject cutObject = obj.deepCopy();
		cutObject.setName(obj.getName() + " Cut");
		preserveTreeParent(obj, cutObject);
		copyPropertiesNotHandledByDeepCopy(obj, cutObject);

		List<TriangleBatch> cutBatches = cutObject.getMesh().getBatches();
		if (cutBatches.size() != batchSplits.size())
			throw new CutException("could not preserve the batch layout for " + obj.getName());

		IdentityHashMap<Vertex, Vertex> vertexCopies = new IdentityHashMap<>();
		List<BatchReplacement> replacements = new ArrayList<>(batchSplits.size());
		for (int i = 0; i < batchSplits.size(); i++) {
			BatchSplit split = batchSplits.get(i);
			TriangleBatch cutBatch = cutBatches.get(i);

			cutBatch.triangles = copyTriangles(split.positive, vertexCopies);
			replacements.add(new BatchReplacement(split.sourceBatch, split.negative));
		}

		cutObject.updateMeshHierarchy();
		cutObject.dirtyAABB = true;

		return new ObjectSplit(
			cutObject,
			replacements,
			selectedTrianglesToRemove,
			selectedTrianglesToAdd);
	}

	private TriangleSplit splitTriangle(Triangle source, HashMap<EdgeKey, Vertex> intersectionCache) throws CutException
	{
		boolean hasPositive = false;
		boolean hasNegative = false;
		for (Vertex v : source.vert) {
			float distance = distFromPlane(v.getCurrentPos());
			hasPositive |= distance > PLANE_EPSILON;
			hasNegative |= distance < -PLANE_EPSILON;
		}

		TriangleSplit result = new TriangleSplit();
		if (!hasPositive) {
			result.negative.add(source);
			return result;
		}
		if (!hasNegative) {
			result.positive.add(source);
			return result;
		}

		List<Vertex> positivePolygon = clipTriangle(source, true, intersectionCache);
		List<Vertex> negativePolygon = clipTriangle(source, false, intersectionCache);
		if (positivePolygon == null || negativePolygon == null)
			throw new CutException("could not intersect a triangle in " + getOwnerName(source));

		triangulate(positivePolygon, source.doubleSided, result.positive);
		triangulate(negativePolygon, source.doubleSided, result.negative);

		if (result.positive.isEmpty() || result.negative.isEmpty())
			throw new CutException("the cut would create zero-area geometry in "
				+ getOwnerName(source));

		return result;
	}

	private static String getOwnerName(Triangle triangle)
	{
		if (triangle.parentBatch != null
			&& triangle.parentBatch.parentMesh != null
			&& triangle.parentBatch.parentMesh.parentObject != null) {
			String name = triangle.parentBatch.parentMesh.parentObject.getName();
			if (name != null && !name.isBlank())
				return name;
		}
		return "selected geometry";
	}

	private List<Vertex> clipTriangle(Triangle source, boolean keepPositive, HashMap<EdgeKey, Vertex> intersectionCache)
	{
		List<Vertex> result = new ArrayList<>(4);

		for (int i = 0; i < 3; i++) {
			Vertex current = source.vert[i];
			Vertex next = source.vert[(i + 1) % 3];
			float currentDistance = distFromPlane(current.getCurrentPos());
			float nextDistance = distFromPlane(next.getCurrentPos());

			if (isInside(currentDistance, keepPositive))
				addUniqueVertex(result, current);

			if (crossesPlane(currentDistance, nextDistance)) {
				Vertex intersection = getIntersection(
					current, next, currentDistance, nextDistance, intersectionCache);
				if (intersection == null)
					return null;
				addUniqueVertex(result, intersection);
			}
		}

		if (result.size() > 1 && samePosition(result.get(0), result.get(result.size() - 1)))
			result.remove(result.size() - 1);

		return result;
	}

	private static boolean isInside(float distance, boolean keepPositive)
	{
		return keepPositive ? distance >= -PLANE_EPSILON : distance <= PLANE_EPSILON;
	}

	private static boolean crossesPlane(float a, float b)
	{
		return (a > PLANE_EPSILON && b < -PLANE_EPSILON)
			|| (a < -PLANE_EPSILON && b > PLANE_EPSILON);
	}

	private Vertex getIntersection(
		Vertex a,
		Vertex b,
		float distanceA,
		float distanceB,
		HashMap<EdgeKey, Vertex> intersectionCache)
	{
		EdgeKey edge = new EdgeKey(a, b);
		Vertex cached = intersectionCache.get(edge);
		if (cached != null)
			return cached;

		float denominator = distanceA - distanceB;
		if (Math.abs(denominator) < PLANE_EPSILON || a.useLocal != b.useLocal)
			return null;

		float alpha = distanceA / denominator;
		if (alpha < -PLANE_EPSILON || alpha > 1.0f + PLANE_EPSILON)
			return null;
		alpha = Math.max(0.0f, Math.min(1.0f, alpha));

		Vector3f posA = a.getCurrentPos();
		Vector3f posB = b.getCurrentPos();
		Vertex intersection = new Vertex(
			posA.x + alpha * (posB.x - posA.x),
			posA.y + alpha * (posB.y - posA.y),
			posA.z + alpha * (posB.z - posA.z));
		intersection.useLocal = a.useLocal;

		intersection.uv = new UV(
			Math.round((1.0f - alpha) * a.uv.getU() + alpha * b.uv.getU()),
			Math.round((1.0f - alpha) * a.uv.getV() + alpha * b.uv.getV()));
		intersection.r = interpolate(a.r, b.r, alpha);
		intersection.g = interpolate(a.g, b.g, alpha);
		intersection.b = interpolate(a.b, b.b, alpha);
		intersection.a = interpolate(a.a, b.a, alpha);

		intersectionCache.put(edge, intersection);
		return intersection;
	}

	private static int interpolate(int a, int b, float alpha)
	{
		return Math.round((1.0f - alpha) * a + alpha * b);
	}

	private static void addUniqueVertex(List<Vertex> vertices, Vertex vertex)
	{
		if (vertices.isEmpty() || !samePosition(vertices.get(vertices.size() - 1), vertex))
			vertices.add(vertex);
	}

	private static boolean samePosition(Vertex a, Vertex b)
	{
		return a.getCurrentX() == b.getCurrentX()
			&& a.getCurrentY() == b.getCurrentY()
			&& a.getCurrentZ() == b.getCurrentZ();
	}

	private static void triangulate(List<Vertex> polygon, boolean doubleSided, List<Triangle> output)
	{
		if (polygon.size() < 3)
			return;

		Vertex first = polygon.get(0);
		for (int i = 1; i < polygon.size() - 1; i++) {
			Triangle triangle = new Triangle(first, polygon.get(i), polygon.get(i + 1));
			triangle.doubleSided = doubleSided;
			if (triangle.getArea() > PLANE_EPSILON)
				output.add(triangle);
		}
	}

	private static IdentityArrayList<Triangle> copyTriangles(
		Iterable<Triangle> triangles,
		IdentityHashMap<Vertex, Vertex> vertexCopies)
	{
		IdentityArrayList<Triangle> copies = new IdentityArrayList<>();
		for (Triangle triangle : triangles) {
			Vertex a = vertexCopies.computeIfAbsent(triangle.vert[0], Vertex::deepCopy);
			Vertex b = vertexCopies.computeIfAbsent(triangle.vert[1], Vertex::deepCopy);
			Vertex c = vertexCopies.computeIfAbsent(triangle.vert[2], Vertex::deepCopy);

			Triangle copy = new Triangle(a, b, c);
			copy.doubleSided = triangle.doubleSided;
			copies.add(copy);
		}
		return copies;
	}

	private float distFromPlane(Vector3f point)
	{
		return Vector3f.dot(Vector3f.sub(point, planePoint), planeNormal);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static void preserveTreeParent(MapObject source, MapObject copy)
	{
		MapObjectNode copyNode = copy.getNode();
		copyNode.parentNode = source.getNode().parentNode;
	}

	private static void copyPropertiesNotHandledByDeepCopy(MapObject source, MapObject copy)
	{
		if (source instanceof Model sourceModel && copy instanceof Model copyModel)
			copyModel.pannerID.copy(sourceModel.pannerID);
	}

	private static void applyTriangles(TriangleBatch batch, IdentityArrayList<Triangle> triangles)
	{
		batch.triangles = triangles;
		batch.setParent(batch.parentMesh);

		AbstractMesh mesh = batch.parentMesh;
		if (mesh instanceof TexturedMesh texturedMesh)
			texturedMesh.setDirty();
		if (mesh != null && mesh.parentObject != null)
			mesh.parentObject.dirtyAABB = true;
	}

	static final class ReplaceBatches extends AbstractCommand
	{
		final List<BatchReplacement> replacements;

		ReplaceBatches(List<BatchReplacement> replacements)
		{
			super("Replace Cut Geometry");
			this.replacements = replacements;
		}

		@Override
		public boolean shouldExec()
		{
			return !replacements.isEmpty();
		}

		@Override
		public void exec()
		{
			super.exec();
			for (BatchReplacement replacement : replacements)
				applyTriangles(replacement.batch, replacement.newTriangles);
		}

		@Override
		public void undo()
		{
			super.undo();
			for (BatchReplacement replacement : replacements)
				applyTriangles(replacement.batch, replacement.oldTriangles);
		}
	}

	static final class ObjectSplit
	{
		final MapObject cutObject;
		final List<BatchReplacement> replacements;
		private final IdentityArrayList<Triangle> selectedTrianglesToRemove;
		private final IdentityArrayList<Triangle> selectedTrianglesToAdd;

		private ObjectSplit(
			MapObject cutObject,
			List<BatchReplacement> replacements,
			IdentityArrayList<Triangle> selectedTrianglesToRemove,
			IdentityArrayList<Triangle> selectedTrianglesToAdd)
		{
			this.cutObject = cutObject;
			this.replacements = replacements;
			this.selectedTrianglesToRemove = selectedTrianglesToRemove;
			this.selectedTrianglesToAdd = selectedTrianglesToAdd;
		}
	}

	private static final class BatchSplit
	{
		private final TriangleBatch sourceBatch;
		private final IdentityArrayList<Triangle> positive = new IdentityArrayList<>();
		private final IdentityArrayList<Triangle> negative = new IdentityArrayList<>();

		private BatchSplit(TriangleBatch sourceBatch)
		{
			this.sourceBatch = sourceBatch;
		}
	}

	static final class BatchReplacement
	{
		final TriangleBatch batch;
		final IdentityArrayList<Triangle> oldTriangles;
		final IdentityArrayList<Triangle> newTriangles;

		private BatchReplacement(TriangleBatch batch, Iterable<Triangle> newTriangles)
		{
			this.batch = batch;
			this.oldTriangles = batch.triangles;
			this.newTriangles = new IdentityArrayList<>(newTriangles);
		}
	}

	private static final class TriangleSplit
	{
		private final IdentityArrayList<Triangle> positive = new IdentityArrayList<>();
		private final IdentityArrayList<Triangle> negative = new IdentityArrayList<>();
	}

	private static final class EdgeKey
	{
		private final Vertex a;
		private final Vertex b;

		private EdgeKey(Vertex a, Vertex b)
		{
			this.a = a;
			this.b = b;
		}

		@Override
		public int hashCode()
		{
			return System.identityHashCode(a) ^ System.identityHashCode(b);
		}

		@Override
		public boolean equals(Object obj)
		{
			if (!(obj instanceof EdgeKey other))
				return false;
			return (a == other.a && b == other.b) || (a == other.b && b == other.a);
		}
	}

	private static final class CutException extends Exception
	{
		private static final long serialVersionUID = 1L;

		private CutException(String message)
		{
			super(message);
		}
	}
}
