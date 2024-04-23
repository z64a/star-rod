package game.map.editor.commands;

import java.util.ArrayList;
import java.util.List;

import game.map.mesh.Triangle;
import util.MathUtil;

public class CleanupTriangles extends AbstractCommand
{
	private final List<Triangle> targets;

	private final AbstractCommand deselectCommand;

	public CleanupTriangles(List<Triangle> triangles)
	{
		super("Cleanup Triangles");

		this.targets = new ArrayList<>();

		for (Triangle t : triangles) {
			if (MathUtil.veryNearlyZero(t.getArea()))
				targets.add(t);
		}

		deselectCommand = editor.selectionManager.getModifyTriangles(null, targets, true);
		deselectCommand.silence();
	}

	@Override
	public boolean shouldExec()
	{
		return targets.size() > 0;
	}

	@Override
	public void exec()
	{
		super.exec();

		deselectCommand.exec();

		for (Triangle t : targets)
			t.parentBatch.triangles.remove(t);
	}

	@Override
	public void undo()
	{
		super.undo();
		for (Triangle t : targets)
			t.parentBatch.triangles.add(t);

		deselectCommand.undo();
	}
}
