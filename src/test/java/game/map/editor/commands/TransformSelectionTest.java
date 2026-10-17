package game.map.editor.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import game.map.MapObject.ShapeType;
import game.map.editor.EditorObject;
import game.map.editor.selection.Selection;
import game.map.shape.Model;
import game.map.shape.TransformMatrix;

class TransformSelectionTest
{
	@Test
	void editorOnlyObjectTransformDoesNotModifyMapData()
	{
		Selection<EditorObject> selection = new Selection<>(EditorObject.class, null);
		selection.selectableList.add(new EditorObject() {
			@Override
			public void initialize()
			{}
		});

		TransformSelection<EditorObject> command = new TransformSelection<>(selection, TransformMatrix.identity());

		assertFalse(command.modifiesData());
	}

	@Test
	void regularMapObjectTransformModifiesMapData()
	{
		Selection<Model> selection = new Selection<>(Model.class, null);
		selection.selectableList.add(new Model(ShapeType.GROUP));

		TransformSelection<Model> command = new TransformSelection<>(selection, TransformMatrix.identity());

		assertTrue(command.modifiesData());
	}
}
