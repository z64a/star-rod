package game.map.shape;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import common.Vector3f;
import game.map.MapObject.ShapeType;
import game.map.editor.selection.PickRay;
import game.map.editor.selection.PickRay.Channel;
import game.map.editor.selection.PickRay.PickHit;

class ModelPickingTest
{
	@Test
	void meshlessModelReturnsMissedHit()
	{
		Model model = new Model(ShapeType.GROUP);
		PickRay ray = new PickRay(Channel.SELECTION, new Vector3f(0, 1, 0), PickRay.DOWN);

		PickHit hit = model.tryPick(ray);

		assertNotNull(hit);
		assertTrue(hit.missed());
	}
}
