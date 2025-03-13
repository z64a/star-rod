package game.map.editor.commands;

import java.util.IdentityHashMap;

import common.commands.AbstractCommand;
import game.map.editor.render.Color4d;
import game.map.mesh.Vertex;

public class PaintVertices extends AbstractCommand
{
	private final IdentityHashMap<Vertex, Color4d> oldColorMap;
	private final IdentityHashMap<Vertex, Color4d> newColorMap;

	public PaintVertices(IdentityHashMap<Vertex, Color4d> backupVertexColorMap, IdentityHashMap<Vertex, Color4d> newVertexColorMap)
	{
		super("Painting " + newVertexColorMap.size() + " Vertices");
		this.oldColorMap = backupVertexColorMap;
		this.newColorMap = newVertexColorMap;
	}

	@Override
	public void exec()
	{
		super.exec();

		for (Vertex v : newColorMap.keySet()) {
			Color4d newColor = newColorMap.get(v);
			v.r = newColor.r;
			v.g = newColor.g;
			v.b = newColor.b;
			v.a = newColor.a;
		}
	}

	@Override
	public void undo()
	{
		super.undo();

		for (Vertex v : newColorMap.keySet()) {
			Color4d oldColor = oldColorMap.get(v);
			v.r = oldColor.r;
			v.g = oldColor.g;
			v.b = oldColor.b;
			v.a = oldColor.a;
		}
	}
}
