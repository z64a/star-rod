package game.map.editor.render;

import java.util.ArrayList;
import java.util.List;

import common.Vector3f;
import game.map.shape.TriangleBatch;

public class PreviewGeometry
{
	public boolean visible;
	public Vector3f color = new Vector3f(1.0f, 1.0f, 0.0f);
	public PreviewDrawMode drawMode = PreviewDrawMode.ANIM_EDGES;
	public boolean useDepth = false;

	public TriangleBatch batch;
	public List<Vector3f> edges = new ArrayList<>();
	public List<Vector3f> points = new ArrayList<>();

	private Runnable updateFunc;

	public void init()
	{
		batch = null;
		visible = false;
		edges.clear();
		points.clear();
	}

	public void clear()
	{
		batch = null;
		visible = false;
		edges.clear();
		points.clear();
	}

	public final void setUpdate(Runnable runnable)
	{
		updateFunc = runnable;
	}

	public final void update()
	{
		if (visible && updateFunc != null)
			updateFunc.run();
	}
}
