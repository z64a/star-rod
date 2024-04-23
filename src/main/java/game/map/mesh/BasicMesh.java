package game.map.mesh;

import static game.map.MapKey.*;
import static renderer.buffers.BufferedMesh.VBO_COLOR;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.w3c.dom.Element;

import game.map.editor.MapEditor.EditorMode;
import game.map.editor.render.Color4f;
import game.map.editor.render.RenderingOptions;
import game.map.editor.selection.SelectionManager.SelectionMode;
import game.map.hit.HitObject;
import game.map.shape.TriangleBatch;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlSerializable;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class BasicMesh extends AbstractMesh implements Iterable<Triangle>, XmlSerializable
{
	private static final int latestVersion = 0;
	public int instanceVersion = latestVersion;

	// necessary because triangles require a parent batch
	public TriangleBatch batch;

	public static BasicMesh read(XmlReader xmr, Element meshElement)
	{
		BasicMesh mesh = new BasicMesh();
		mesh.fromXML(xmr, meshElement);
		return mesh;
	}

	@Override
	public void fromXML(XmlReader xmr, Element meshElem)
	{
		xmr.requiresAttribute(meshElem, ATTR_VERSION);
		instanceVersion = xmr.readInt(meshElem, ATTR_VERSION);

		Element tribatchElement = xmr.getUniqueRequiredTag(meshElem, TAG_TRIANGLE_BATCH);
		batch = new TriangleBatch(this);
		batch.fromXML(xmr, tribatchElement);

		batch.setParent(this);
		updateHierarchy();
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag meshTag = xmw.createTag(TAG_BASIC_MESH, false);
		xmw.addInt(meshTag, ATTR_VERSION, latestVersion);
		xmw.openTag(meshTag);

		for (Triangle t : batch.triangles)
			for (Vertex v : t.vert) {
				if (!v.useLocal)
					v.copyWorldToLocal();
			}

		batch.toXML(xmw);

		xmw.closeTag(meshTag);
	}

	public BasicMesh()
	{
		super(VBO_COLOR);
		batch = new TriangleBatch(this);
	}

	@Override
	public void prepareVertexBuffers(RenderingOptions opts)
	{
		Color4f[] colors = {
				new Color4f(1.0f, 0.0f, 0.0f, 0.5f),
				new Color4f(0.0f, 1.0f, 0.0f, 0.5f),
				new Color4f(0.0f, 0.0f, 1.0f, 0.5f)
		};

		if (parentObject instanceof HitObject hitObj)
			colors = hitObj.getColors(opts.useColliderColoring);

		validateBuffer();
		buffer.clear();
		boolean selectionEnabled = (opts.editorMode == EditorMode.Modify || opts.editorMode == EditorMode.Scripts);
		for (TriangleBatch batch : getBatches()) {
			batch.bufferStartPos = -1;
			for (Triangle t : batch.triangles) {
				boolean selected = selectionEnabled &&
					(opts.selectionMode == SelectionMode.TRIANGLE && t.selected) ||
					(opts.selectionMode == SelectionMode.OBJECT && parentObject.selected);

				Color4f color = colors[1];
				if (t.doubleSided)
					color = colors[2];
				if (selected)
					color = colors[0];

				int triStart = addTriangle(t, color);
				if (batch.bufferStartPos < 0)
					batch.bufferStartPos = triStart;
			}
		}
		buffer.loadBuffers();
	}

	private int addTriangle(Triangle t, Color4f color)
	{
		int i = addVertex(t.vert[0], color);
		int j = addVertex(t.vert[1], color);
		int k = addVertex(t.vert[2], color);

		buffer.addTriangle(i, j, k);
		return i;
	}

	private int addVertex(Vertex v, Color4f color)
	{
		return buffer.addVertex()
			.setPosition(v.getCurrentX(), v.getCurrentY(), v.getCurrentZ())
			.setColor(color.r, color.g, color.b, color.a)
			.getIndex();
	}

	@Override
	public void updateHierarchy()
	{
		batch.parentMesh = this;
		for (Triangle t : batch.triangles) {
			t.parentBatch = batch;
			t.vert[0].parentMesh = this;
			t.vert[1].parentMesh = this;
			t.vert[2].parentMesh = this;
		}
	}

	@Override
	public List<TriangleBatch> getBatches()
	{
		List<TriangleBatch> batches = new LinkedList<>();
		batches.add(batch);
		return batches;
	}

	@Override
	public BasicMesh deepCopy()
	{
		BasicMesh m = new BasicMesh();
		m.batch = batch.deepCopy();
		m.batch.parentMesh = m;
		m.updateHierarchy();
		return m;
	}

	@Override
	public Iterator<Triangle> iterator()
	{
		return batch.triangles.iterator();
	}
}
