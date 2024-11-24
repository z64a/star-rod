package game.map.editor.selection;

import common.Vector3f;
import game.map.editor.render.PresetColor;
import game.map.editor.selection.PickRay.PickHit;
import renderer.buffers.LineRenderQueue;
import renderer.buffers.PointRenderQueue;
import renderer.shaders.RenderState;

public final class Trace
{
	private static final Vector3f RED = new Vector3f(1.0f, 0.0f, 0.0f);
	public final PickRay ray;
	public final PickHit hit;
	public final Vector3f hitColor;
	public final float maxLength;

	public Trace(PickRay ray, PickHit hit)
	{
		this(ray, hit, 10000.0f, RED);
	}

	public Trace(PickRay ray, PickHit hit, float maxLength)
	{
		this(ray, hit, maxLength, RED);
	}

	public Trace(PickRay ray, PickHit hit, Vector3f hitColor)
	{
		this(ray, hit, 10000.0f, hitColor);
	}

	public Trace(PickRay ray, PickHit hit, float maxLength, Vector3f hitColor)
	{
		this.ray = ray;
		this.hit = hit;
		this.hitColor = hitColor;
		this.maxLength = maxLength;
	}

	public void render()
	{
		RenderState.setLineWidth(2.0f);
		RenderState.setPointSize(10.0f);
		RenderState.setColor(PresetColor.YELLOW);

		float len = (hit.dist > maxLength) ? maxLength : hit.dist;

		LineRenderQueue.addLine(
			LineRenderQueue.addVertex().setPosition(
				ray.origin.x, ray.origin.y, ray.origin.z).getIndex(),
			LineRenderQueue.addVertex().setPosition(
				ray.origin.x + ray.direction.x * len,
				ray.origin.y + ray.direction.y * len,
				ray.origin.z + ray.direction.z * len).getIndex());

		LineRenderQueue.render(true);

		if (hit.dist <= maxLength) {
			PointRenderQueue.addPoint().setPosition(
				ray.origin.x + ray.direction.x * len,
				ray.origin.y + ray.direction.y * len,
				ray.origin.z + ray.direction.z * len)
				.setColor(hitColor.x, hitColor.y, hitColor.z);
			PointRenderQueue.render(true);
		}
	}
}
