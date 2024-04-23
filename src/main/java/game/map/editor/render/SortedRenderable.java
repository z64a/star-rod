package game.map.editor.render;

import game.map.editor.geometry.Vector3f;

import game.map.editor.common.BaseCamera;

public interface SortedRenderable
{
	public RenderMode getRenderMode();

	public Vector3f getCenterPoint();

	public void render(RenderingOptions opts, BaseCamera camera);

	public void setDepth(int normalizedDepth);

	public int getDepth();
}
