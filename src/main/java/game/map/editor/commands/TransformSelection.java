package game.map.editor.commands;

import java.util.LinkedList;

import common.commands.AbstractCommand;
import game.map.ReversibleTransform;
import game.map.editor.MapEditor;
import game.map.editor.selection.Selectable;
import game.map.editor.selection.Selection;
import game.map.shape.TransformMatrix;

public class TransformSelection<T extends Selectable> extends AbstractCommand
{
	private LinkedList<ReversibleTransform> transformerList;
	private Selection<T> selection;

	public TransformSelection(Selection<T> selection, TransformMatrix m)
	{
		super("Transform Selection");
		this.selection = selection;

		transformerList = new LinkedList<>();
		for (T item : selection.selectableList) {
			if (!item.transforms())
				continue;

			ReversibleTransform t = item.createTransformer(m);

			if (t != null)
				transformerList.add(t);

			item.endTransformation();
		}
	}

	@Override
	public void exec()
	{
		super.exec();

		for (ReversibleTransform t : transformerList)
			t.transform();

		selection.updateAABB();

		MapEditor editor = MapEditor.instance();
		editor.forceUpdateInfoPanels();
	}

	@Override
	public void undo()
	{
		super.undo();

		for (ReversibleTransform t : transformerList)
			t.revert();

		selection.updateAABB();

		MapEditor editor = MapEditor.instance();
		editor.forceUpdateInfoPanels();
	}
}
