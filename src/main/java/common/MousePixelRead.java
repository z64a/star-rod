package common;

public class MousePixelRead
{
	public final Vector3f worldPos;
	public final int stencilValue;

	public MousePixelRead(float x, float y, float z, int stencilValue)
	{
		worldPos = new Vector3f(x, y, z);
		this.stencilValue = stencilValue;
	}
}
