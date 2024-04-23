package game.map.mesh;

import java.util.List;

import game.map.MapObject;
import game.map.editor.render.RenderingOptions;
import game.map.shape.TriangleBatch;
import renderer.buffers.BufferedMesh;

public abstract class AbstractMesh implements Iterable<Triangle>
{
	private static final int latestVersion = 0;
	public int instanceVersion = latestVersion;

	public MapObject parentObject; // every mesh belongs to a selectable map object
	public int selectedTriangleCount = 0;

	public BufferedMesh buffer = null;
	private final int meshFlags;

	public AbstractMesh(int meshFlags)
	{
		this.meshFlags = meshFlags;
	}

	public void validateBuffer()
	{
		if (buffer == null)
			buffer = new BufferedMesh(0, 0, meshFlags);
	}

	public final void setVAO()
	{
		validateBuffer();
		buffer.setVAO();
	}

	public abstract AbstractMesh deepCopy();

	public abstract void updateHierarchy();

	public abstract List<TriangleBatch> getBatches();

	public abstract void prepareVertexBuffers(RenderingOptions opts);
}
