package game.map.marker;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import common.Vector3f;
import common.commands.AbstractCommand;

import game.map.editor.MapEditor;
import game.map.editor.geometry.GeometryUtils;
import game.map.editor.selection.SelectablePoint;
import game.map.editor.selection.SelectionManager;
import util.IterableListModel;

public class PathData
{
	private final Marker parent;
	private final String updateTag;
	public final int maxLength;

	public IterableListModel<PathPoint> points = new IterableListModel<>();

	public PathData(Marker m, String updateTag)
	{
		this(m, updateTag, Integer.MAX_VALUE);
	}

	public PathData(Marker m, String updateTag, int maxLength)
	{
		this.parent = m;
		this.updateTag = updateTag;
		this.maxLength = maxLength;
	}

	/** The points `point` leads to: those it's been connected to, or else the point after it, if any. */
	public List<PathPoint> successors(PathPoint point)
	{
		if (point.next != null)
			return point.next;
		int index = point.getListIndex();
		return (index + 1 < points.size()) ? List.of(points.get(index + 1)) : List.of();
	}

	/** Whether each point leads only to the point after it. */
	public boolean isLinear()
	{
		for (PathPoint point : points) {
			if (point.next != null)
				return false;
		}
		return true;
	}

	/** Whether every point has a name. */
	public boolean isNamed()
	{
		for (PathPoint point : points) {
			if (point.name == null)
				return false;
		}
		return true;
	}

	/** Makes `point` lead to `successors`, which it then remembers only if they differ from the point after it. */
	void setSuccessors(PathPoint point, List<PathPoint> successors)
	{
		point.next = null;
		if (!same(successors, successors(point)))
			point.next = new ArrayList<>(successors);
	}

	/** Whether two lists hold the same points, which are compared by identity, since points in one place are equal. */
	private static boolean same(List<PathPoint> a, List<PathPoint> b)
	{
		if (a.size() != b.size())
			return false;
		for (int i = 0; i < a.size(); i++) {
			if (a.get(i) != b.get(i))
				return false;
		}
		return true;
	}

	/** Each point's name, or null for one without, or null if none has one. */
	public String[] getNames()
	{
		String[] names = new String[points.size()];
		boolean any = false;
		for (int i = 0; i < names.length; i++) {
			names[i] = points.get(i).name;
			any |= names[i] != null;
		}
		return any ? names : null;
	}

	/** The indices of the points each point leads to, or null if each leads only to the point after it. */
	public int[][] getEdges()
	{
		if (isLinear())
			return null;
		int[][] edges = new int[points.size()][];
		for (int i = 0; i < points.size(); i++)
			edges[i] = successors(points.get(i)).stream().mapToInt(PathPoint::getListIndex).toArray();
		return edges;
	}

	/** Sets the points' names and what they lead to, as getNames and getEdges give them, either of which can be null. */
	public void setGraph(String[] names, int[][] edges)
	{
		for (int i = 0; i < points.size(); i++) {
			PathPoint point = points.get(i);
			point.name = (names != null && i < names.length && canName(point, names[i])) ? names[i] : null;
		}
		for (int i = 0; i < points.size(); i++) {
			PathPoint point = points.get(i);
			point.next = null;
			if (edges == null || i >= edges.length || edges[i] == null)
				continue;
			List<PathPoint> successors = new ArrayList<>();
			for (int index : edges[i]) {
				if (index >= 0 && index < points.size())
					successors.add(points.get(index));
			}
			setSuccessors(point, successors);
		}
	}

	/**
	 * Removes `point`, and makes the points that led to it lead on to where it led instead. Returns what each point led
	 * to before, for restore.
	 */
	Map<PathPoint, List<PathPoint>> remove(PathPoint point)
	{
		List<PathPoint> onward = new ArrayList<>(successors(point));
		onward.removeIf(successor -> successor == point);

		Map<PathPoint, List<PathPoint>> nextBefore = new IdentityHashMap<>();
		Map<PathPoint, List<PathPoint>> rerouted = new IdentityHashMap<>();
		for (PathPoint other : points) {
			nextBefore.put(other, other.next);
			List<PathPoint> successors = successors(other);
			if (other == point || successors.stream().noneMatch(successor -> successor == point))
				continue;
			List<PathPoint> through = new ArrayList<>();
			for (PathPoint successor : successors) {
				for (PathPoint target : (successor == point) ? onward : List.of(successor)) {
					if (target != other && through.stream().noneMatch(added -> added == target))
						through.add(target);
				}
			}
			rerouted.put(other, through);
		}

		points.remove(point.getListIndex());
		rerouted.forEach(this::setSuccessors);
		parent.updateListeners(updateTag);
		return nextBefore;
	}

	public Marker getMarker()
	{
		return parent;
	}

	/** Whether its points can be named and connected, which only a Path marker's can, rather than an NPC's patrol. */
	public boolean isConnectable()
	{
		return parent.pathComponent.path == this;
	}

	/** Adds `added` to the end of the path, leading from `from`. Returns what each point led to before, for retract. */
	Map<PathPoint, List<PathPoint>> extend(PathPoint from, PathPoint added)
	{
		List<PathPoint> fromSuccessors = new ArrayList<>(successors(from));
		fromSuccessors.add(added);
		Map<PathPoint, List<PathPoint>> successorsAfter = new IdentityHashMap<>();
		successorsAfter.put(from, fromSuccessors);
		return append(added, successorsAfter);
	}

	/**
	 * Adds `added` to the end of the path, in the middle of the connection between `a` and `b`, in whichever directions
	 * they're connected. Returns what each point led to before, for retract.
	 */
	Map<PathPoint, List<PathPoint>> insert(PathPoint a, PathPoint b, PathPoint added)
	{
		Map<PathPoint, List<PathPoint>> successorsAfter = new IdentityHashMap<>();
		List<PathPoint> addedSuccessors = new ArrayList<>();
		for (PathPoint[] edge : new PathPoint[][] { { a, b }, { b, a } }) {
			List<PathPoint> successors = new ArrayList<>(successors(edge[0]));
			if (successors.stream().noneMatch(point -> point == edge[1]))
				continue;
			successors.replaceAll(point -> (point == edge[1]) ? added : point);
			successorsAfter.put(edge[0], successors);
			addedSuccessors.add(edge[1]);
		}
		successorsAfter.put(added, addedSuccessors);
		return append(added, successorsAfter);
	}

	/** Whether `a` leads to `b` or `b` leads to `a`. */
	public boolean adjacent(PathPoint a, PathPoint b)
	{
		return successors(a).stream().anyMatch(point -> point == b) || successors(b).stream().anyMatch(point -> point == a);
	}

	/**
	 * Adds `added` to the end of the path, without the point that was last leading to it, then sets what the points in
	 * `successorsAfter` lead to. Returns what each point led to before, for retract.
	 */
	private Map<PathPoint, List<PathPoint>> append(PathPoint added, Map<PathPoint, List<PathPoint>> successorsAfter)
	{
		Map<PathPoint, List<PathPoint>> nextBefore = new IdentityHashMap<>();
		for (PathPoint point : points)
			nextBefore.put(point, point.next);

		PathPoint last = points.isEmpty() ? null : points.get(points.size() - 1);
		List<PathPoint> lastSuccessors = (last == null) ? null : successors(last);

		points.addElement(added);
		if (last != null)
			setSuccessors(last, lastSuccessors);
		successorsAfter.forEach(this::setSuccessors);
		parent.updateListeners(updateTag);
		return nextBefore;
	}

	/** Removes a point that extend added, and puts back what each point led to before. */
	void retract(PathPoint added, Map<PathPoint, List<PathPoint>> nextBefore)
	{
		points.remove(added.getListIndex());
		nextBefore.forEach((point, next) -> point.next = next);
		parent.updateListeners(updateTag);
	}

	/** Puts back a point that remove took out, at `index`, and what each point led to before. */
	void restore(PathPoint point, int index, Map<PathPoint, List<PathPoint>> nextBefore)
	{
		points.add(index, point);
		nextBefore.forEach((other, next) -> other.next = next);
		parent.updateListeners(updateTag);
	}

	/** Whether `point` can be named `name`: a C identifier no other point of the path has, or null for no name. */
	public boolean canName(PathPoint point, String name)
	{
		if (name == null)
			return true;
		if (!name.matches("[A-Za-z_][A-Za-z0-9_]*"))
			return false;
		for (PathPoint other : points) {
			if (other != point && name.equals(other.name))
				return false;
		}
		return true;
	}

	/** Makes `from` lead to `via` in place of `to`, and `via` lead on to `to`. */
	public static final class RouteThroughPoint extends AbstractCommand
	{
		private final PathPoint from;
		private final PathPoint via;
		private final List<PathPoint> fromBefore;
		private final List<PathPoint> viaBefore;
		private final List<PathPoint> fromAfter;
		private final List<PathPoint> viaAfter;

		public RouteThroughPoint(PathPoint from, PathPoint to, PathPoint via)
		{
			super("Route Path Through Point");
			this.from = from;
			this.via = via;
			fromBefore = from.next;
			viaBefore = via.next;

			PathData path = from.path;
			fromAfter = new ArrayList<>();
			for (PathPoint successor : path.successors(from)) {
				PathPoint next = (successor == to) ? via : successor;
				if (fromAfter.stream().noneMatch(point -> point == next))
					fromAfter.add(next);
			}
			viaAfter = new ArrayList<>(path.successors(via));
			if (viaAfter.stream().noneMatch(point -> point == to))
				viaAfter.add(to);
		}

		@Override
		public void exec()
		{
			super.exec();
			from.path.setSuccessors(from, fromAfter);
			from.path.setSuccessors(via, viaAfter);
			from.path.parent.updateListeners(from.path.updateTag);
		}

		@Override
		public void undo()
		{
			super.undo();
			from.next = fromBefore;
			via.next = viaBefore;
			from.path.parent.updateListeners(from.path.updateTag);
		}
	}

	/** Connects `from` to `to`, or removes the connection, which changes what `from` leads to. */
	public static final class SetConnected extends AbstractCommand
	{
		private final PathPoint from;
		private final List<PathPoint> before;
		private final List<PathPoint> after;

		public SetConnected(PathPoint from, PathPoint to, boolean connected)
		{
			super(connected ? "Connect Path Points" : "Disconnect Path Points");
			this.from = from;
			before = from.next;
			List<PathPoint> successors = new ArrayList<>(from.path.successors(from));
			if (!connected)
				successors.removeIf(point -> point == to);
			else if (successors.stream().noneMatch(point -> point == to))
				successors.add(to);
			after = successors;
		}

		@Override
		public boolean shouldExec()
		{
			return !same(after, from.path.successors(from));
		}

		@Override
		public void exec()
		{
			super.exec();
			from.path.setSuccessors(from, after);
			from.path.parent.updateListeners(from.path.updateTag);
		}

		@Override
		public void undo()
		{
			super.undo();
			from.next = before;
			from.path.parent.updateListeners(from.path.updateTag);
		}
	}

	/**
	 * Adds a point leading from `from`, as far on as `from` is from a point leading to it, and selects it in place of
	 * the selected points.
	 */
	/** Adds a point connected to others, and selects it in place of the selected points. */
	private static abstract class AddConnectedPathPoint extends AbstractCommand
	{
		protected final PathPoint added;
		private final AbstractCommand selectAdded;
		private Map<PathPoint, List<PathPoint>> nextBefore;

		protected AddConnectedPathPoint(String name, PathPoint added)
		{
			super(name);
			this.added = added;

			SelectionManager selection = MapEditor.instance().selectionManager;
			selectAdded = selection.getModifyPoints(List.<SelectablePoint> of(added),
				new ArrayList<>(selection.pointSelection.selectableList));
		}

		/** Adds `added` to its path and connects it. Returns what each point led to before, for retract. */
		protected abstract Map<PathPoint, List<PathPoint>> connect();

		@Override
		public boolean shouldExec()
		{
			return added.path.points.size() < added.path.maxLength;
		}

		@Override
		public void exec()
		{
			super.exec();
			nextBefore = connect();
			selectAdded.exec();
		}

		@Override
		public void undo()
		{
			super.undo();
			selectAdded.undo();
			added.path.retract(added, nextBefore);
		}
	}

	/** Adds a point at a position, that `from` leads to. */
	public static final class AddNextPathPoint extends AddConnectedPathPoint
	{
		private final PathPoint from;

		public AddNextPathPoint(PathPoint from, int x, int y, int z)
		{
			super("Add Path Point", new PathPoint(from.path, x, y, z));
			this.from = from;
		}

		@Override
		protected Map<PathPoint, List<PathPoint>> connect()
		{
			return from.path.extend(from, added);
		}
	}

	/** Adds a point at a position, in the middle of the connection between two points. */
	public static final class InsertPathPoint extends AddConnectedPathPoint
	{
		private final PathPoint a;
		private final PathPoint b;

		public InsertPathPoint(PathPoint a, PathPoint b, int x, int y, int z)
		{
			super("Insert Path Point", new PathPoint(a.path, x, y, z));
			this.a = a;
			this.b = b;
		}

		@Override
		public boolean shouldExec()
		{
			return super.shouldExec() && a.path.adjacent(a, b);
		}

		@Override
		protected Map<PathPoint, List<PathPoint>> connect()
		{
			return a.path.insert(a, b, added);
		}
	}

	public static final class SetPathPointName extends AbstractCommand
	{
		private final PathPoint point;
		private final String before;
		private final String after;

		public SetPathPointName(PathPoint point, String name)
		{
			super("Rename Path Point");
			this.point = point;
			before = point.name;
			after = (name == null || name.isEmpty()) ? null : name;
		}

		@Override
		public boolean shouldExec()
		{
			return !Objects.equals(before, after) && point.path.canName(point, after);
		}

		@Override
		public void exec()
		{
			super.exec();
			point.name = after;
			point.path.parent.updateListeners(point.path.updateTag);
		}

		@Override
		public void undo()
		{
			super.undo();
			point.name = before;
			point.path.parent.updateListeners(point.path.updateTag);
		}
	}

	public static final class AddPathPoint extends AbstractCommand
	{
		private final PathData path;
		private final PathPoint wp;

		public AddPathPoint(PathData path)
		{
			super("Add Path Point");
			this.path = path;
			int x, y, z;

			Vector3f cursorPosition = MapEditor.instance().getCursorPosition();

			if (path.points.isEmpty()) {
				x = Math.round(cursorPosition.x);
				y = Math.round(cursorPosition.y);
				z = Math.round(cursorPosition.z);
			}
			else {
				Vector3f extrapolation = new Vector3f();

				if (path.points.size() == 1) {
					PathPoint last = path.points.get(path.points.size() - 1);
					Vector3f prev = last.getPosition();
					extrapolation.x = prev.x + 100;
					extrapolation.y = prev.y;
					extrapolation.z = prev.z;
				}
				else {
					PathPoint prev1 = path.points.get(path.points.size() - 1);
					PathPoint prev2 = path.points.get(path.points.size() - 2);
					Vector3f posPrev1 = prev1.getPosition();
					Vector3f posPrev2 = prev2.getPosition();

					if (GeometryUtils.dist3D(posPrev2, posPrev1) < 1.0f) {
						// too close to determine unit vector, extrapolate along +x
						extrapolation.x = prev1.getX() + 100;
						extrapolation.y = prev1.getY();
						extrapolation.z = prev1.getZ();
					}
					else {
						Vector3f u = GeometryUtils.getUnitVector(posPrev2, posPrev1);
						extrapolation.x = prev1.getX() + (int) (100 * u.x);
						extrapolation.y = prev1.getY() + (int) (100 * u.y);
						extrapolation.z = prev1.getZ() + (int) (100 * u.z);
					}
				}

				boolean useExtrapolation = true;

				for (PathPoint wp : path.points) {
					// don't extrapolate if the 3D cursor is on top one of the waypoints
					if (GeometryUtils.dist3D(wp.getPosition(), cursorPosition) < 1.0f)
						useExtrapolation = false;
				}

				if (useExtrapolation) {
					x = Math.round(extrapolation.x);
					y = Math.round(extrapolation.y);
					z = Math.round(extrapolation.z);
				}
				else {
					x = Math.round(cursorPosition.x);
					y = Math.round(cursorPosition.y);
					z = Math.round(cursorPosition.z);
				}
			}

			wp = new PathPoint(path, x, y, z);
		}

		@Override
		public boolean shouldExec()
		{
			return path.points.size() < path.maxLength;
		}

		@Override
		public void exec()
		{
			super.exec();
			path.points.addElement(wp);
			path.parent.updateListeners(path.updateTag);
		}

		@Override
		public void undo()
		{
			super.undo();
			path.points.remove(path.points.size() - 1);
			path.parent.updateListeners(path.updateTag);
		}
	}

	public static final class DeletePathPoint extends AbstractCommand
	{
		private final PathPoint wp;
		private final int initialPos;

		private AbstractCommand selectionModCommand;
		private Map<PathPoint, List<PathPoint>> nextBefore;

		public DeletePathPoint(PathPoint wp)
		{
			super("Remove Path Point");
			this.wp = wp;
			initialPos = wp.getListIndex();

			List<SelectablePoint> deselectList = new LinkedList<>();
			if (wp.isSelected())
				deselectList.add(wp);

			selectionModCommand = MapEditor.instance().selectionManager.getModifyPoints(null, deselectList);
		}

		@Override
		public void exec()
		{
			selectionModCommand.exec();

			super.exec();
			nextBefore = wp.path.remove(wp);
		}

		@Override
		public void undo()
		{
			super.undo();
			wp.path.restore(wp, initialPos, nextBefore);

			selectionModCommand.undo();
		}
	}

	public void markDegenerates()
	{
		for (PathPoint p : points)
			p.degenerate = false;

		for (int i = 1; i < points.size(); i++) {
			PathPoint prev = points.get(i - 1);
			PathPoint cur = points.get(i);

			if (prev.getX() == cur.getX() && prev.getY() == cur.getY() && prev.getZ() == cur.getZ()) {
				prev.degenerate = true;
				cur.degenerate = true;
			}
		}
	}
}
