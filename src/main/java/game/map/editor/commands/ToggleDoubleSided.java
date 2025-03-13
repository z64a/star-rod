package game.map.editor.commands;

import common.commands.AbstractCommand;
import game.map.mesh.Triangle;

public class ToggleDoubleSided extends AbstractCommand
{
	private final Iterable<Triangle> triangles;

	public ToggleDoubleSided(Iterable<Triangle> triangles)
	{
		super("Toggle Double Sided");
		this.triangles = triangles;
	}

	@Override
	public void exec()
	{
		super.exec();

		for (Triangle t : triangles) {
			t.doubleSided = !t.doubleSided;
		}
	}

	@Override
	public void undo()
	{
		super.undo();

		for (Triangle t : triangles) {
			t.doubleSided = !t.doubleSided;
		}
	}
}
