package game.map.editor.commands;

import java.util.ArrayList;

import common.commands.AbstractCommand;
import game.map.editor.selection.Selection;
import game.map.mesh.Triangle;

public class DeleteTriangles extends AbstractCommand
{
	private final ArrayList<Triangle> targets;
	private final Selection<Triangle> selection;

	@SuppressWarnings("unchecked")
	public DeleteTriangles(Selection<Triangle> selection)
	{
		super("Delete Triangles");
		this.selection = selection;
		this.targets = (ArrayList<Triangle>) selection.selectableList.clone();
	}

	@Override
	public void exec()
	{
		super.exec();

		for (Triangle t : targets)
			t.parentBatch.triangles.remove(t);

		selection.removeAndDeselect(targets);
		selection.updateAABB();
	}

	@Override
	public void undo()
	{
		super.undo();
		for (Triangle t : targets)
			t.parentBatch.triangles.add(t);

		selection.addAndSelect(targets);
		selection.updateAABB();
	}
}
