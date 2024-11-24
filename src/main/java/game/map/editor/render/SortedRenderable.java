package game.map.editor.render;

import common.BaseCamera;
import common.Vector3f;

public interface SortedRenderable
{
	public RenderMode getRenderMode();

	public Vector3f getCenterPoint();

	public void render(RenderingOptions opts, BaseCamera camera);

	public void setDepth(int normalizedDepth);

	public int getDepth();
}
