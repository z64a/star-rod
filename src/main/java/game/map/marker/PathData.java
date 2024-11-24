package game.map.marker;

import java.util.LinkedList;
import java.util.List;

import common.Vector3f;
import game.map.editor.MapEditor;
import game.map.editor.commands.AbstractCommand;
import game.map.editor.geometry.GeometryUtils;
import game.map.editor.selection.SelectablePoint;
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

		public DeletePathPoint(PathPoint wp)
		{
			super("Remove Path Point");
			this.wp = wp;
			initialPos = wp.getListIndex();

			List<SelectablePoint> deselectList = new LinkedList<>();
			if (wp.isSelected())
				deselectList.add(wp);

			selectionModCommand = editor.selectionManager.getModifyPoints(null, deselectList);
		}

		@Override
		public void exec()
		{
			selectionModCommand.exec();

			super.exec();
			wp.path.points.remove(initialPos);
			wp.path.parent.updateListeners(wp.path.updateTag);
		}

		@Override
		public void undo()
		{
			super.undo();
			wp.path.points.add(initialPos, wp);
			wp.path.parent.updateListeners(wp.path.updateTag);

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
