package game.map.editor.commands;

import java.util.List;

import common.commands.AbstractCommand;
import game.map.mesh.Triangle;

public class InvertNormals extends AbstractCommand
{
	private final List<Triangle> triangles;

	public InvertNormals(List<Triangle> triangles)
	{
		super("Invert Normals");
		this.triangles = triangles;
	}

	@Override
	public void exec()
	{
		super.exec();

		for (Triangle t : triangles)
			t.flipNormal();
	}

	@Override
	public void undo()
	{
		super.undo();

		for (Triangle t : triangles)
			t.flipNormal();
	}
}
