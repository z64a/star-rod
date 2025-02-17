package game.map.editor.commands;

import java.util.ArrayList;

import common.commands.AbstractCommand;
import game.map.editor.selection.Selection;
import game.map.mesh.Triangle;

public class CloneTriangles extends AbstractCommand
{
	private ArrayList<Triangle> copies;
	private ArrayList<Triangle> originals;
	private final Selection<Triangle> selection;

	public CloneTriangles(Selection<Triangle> selection)
	{
		super("Clone Selection");
		this.selection = selection;
		copies = new ArrayList<>();
		originals = new ArrayList<>();

		for (Triangle t : selection.selectableList) {
			originals.add(t);

			Triangle copy = t.deepCopy();
			copy.parentBatch = t.parentBatch;

			copy.vert[0].parentMesh = t.vert[0].parentMesh;
			copy.vert[1].parentMesh = t.vert[1].parentMesh;
			copy.vert[2].parentMesh = t.vert[2].parentMesh;

			copies.add(copy);
		}
	}

	@Override
	public void exec()
	{
		super.exec();
		selection.clear();

		for (Triangle t : copies)
			t.parentBatch.triangles.add(t);

		selection.addAndSelect(copies);
	}

	@Override
	public void undo()
	{
		super.undo();
		selection.clear();

		for (Triangle t : copies)
			t.parentBatch.triangles.remove(t);

		selection.addAndSelect(originals);
	}
}
