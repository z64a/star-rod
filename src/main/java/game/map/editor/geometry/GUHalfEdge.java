package game.map.editor.geometry;

public class GUHalfEdge
{
	public GUTriangle triangle;
	public GUVertex vertex;

	public GUHalfEdge prev, next, twin;

	public GUHalfEdge(GUVertex v)
	{
		vertex = v;
	}
}
