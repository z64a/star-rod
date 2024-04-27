package game.map.shape;

import static game.map.MapKey.*;

import java.util.List;

import org.w3c.dom.Element;

import game.map.mesh.AbstractMesh;
import game.map.mesh.Triangle;
import game.map.mesh.Vertex;
import game.map.shape.commands.DisplayCommand;
import util.identity.IdentityArrayList;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlSerializable;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class TriangleBatch extends DisplayCommand implements XmlSerializable
{
	private int instanceVersion = latestVersion;
	private static final int latestVersion = 0;

	public IdentityArrayList<Triangle> triangles;
	public int bufferStartPos;

	public void setParent(AbstractMesh parent)
	{
		parentMesh = parent;
		for (Triangle t : triangles) {
			t.parentBatch = this;
			t.vert[0].parentMesh = parent;
			t.vert[1].parentMesh = parent;
			t.vert[2].parentMesh = parent;
		}
	}

	public static TriangleBatch read(XmlReader xmr, Element batchElem, AbstractMesh parent)
	{
		TriangleBatch batch = new TriangleBatch(parent);
		batch.fromXML(xmr, batchElem);
		return batch;
	}

	@Override
	public void fromXML(XmlReader xmr, Element batchElem)
	{
		xmr.requiresAttribute(batchElem, ATTR_VERSION);
		instanceVersion = xmr.readInt(batchElem, ATTR_VERSION);

		Element vertexTableElement = xmr.getUniqueRequiredTag(batchElem, TAG_VERTEX_TABLE);
		List<Element> vertexElements = xmr.getTags(vertexTableElement, TAG_VERTEX);

		Vertex[] vertexTable = new Vertex[vertexElements.size()];

		int i = 0;
		for (Element vertexElement : vertexElements) {
			Vertex v = Vertex.read(xmr, vertexElement);
			vertexTable[i++] = v;
		}

		Element triangleListElement = xmr.getUniqueRequiredTag(batchElem, TAG_TRIANGLE_LIST);
		List<Element> triangleElements = xmr.getTags(triangleListElement, TAG_TRIANGLE);

		for (Element triangleElement : triangleElements) {
			Triangle t = Triangle.read(xmr, triangleElement, vertexTable);
			t.parentBatch = this;
			triangles.add(t);
		}
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		Vertex[] vertexTable = generateVertexTable();

		XmlTag batchTag = xmw.createTag(TAG_TRIANGLE_BATCH, false);
		xmw.addInt(batchTag, ATTR_VERSION, latestVersion);
		xmw.openTag(batchTag);

		XmlTag vertsTag = xmw.createTag(TAG_VERTEX_TABLE, false);
		xmw.openTag(vertsTag);
		for (Vertex v : vertexTable)
			v.toXML(xmw);
		xmw.closeTag(vertsTag);

		XmlTag trisTag = xmw.createTag(TAG_TRIANGLE_LIST, false);
		xmw.openTag(trisTag);
		for (Triangle t : triangles)
			t.toXML(xmw);
		xmw.closeTag(trisTag);

		xmw.closeTag(batchTag);
	}

	public TriangleBatch(AbstractMesh parentMesh)
	{
		super(parentMesh);
		triangles = new IdentityArrayList<>();
	}

	@Override
	public TriangleBatch deepCopy()
	{
		TriangleBatch copyBatch = new TriangleBatch(parentMesh);

		for (Triangle t : triangles) {
			Triangle copyTriangle = t.deepCopy();
			copyTriangle.setParent(copyBatch);
			copyBatch.triangles.add(copyTriangle);
		}

		return copyBatch;
	}

	@Override
	public String toString()
	{
		return "Draw " + triangles.size() + " triangles.";
	}

	@Override
	public CmdType getType()
	{
		return CmdType.DrawTriangleBatch;
	}

	@Override
	public int[] getF3DEX2Command()
	{
		throw new UnsupportedOperationException("Cannot create F3DEX2 command for TriangleBatch objects!");
	}

	public Vertex[] generateVertexTable()
	{
		// clear previous indicies
		int index = 0;
		for (Triangle t : triangles)
			for (Vertex v : t.vert)
				v.index = -1;

		// assign new indicies to unique vertices
		for (Triangle t : triangles) {
			for (int i = 0; i < 3; i++) {
				Vertex v = t.vert[i];
				if (v.index == -1)
					v.index = index++;
				t.ijk[i] = v.index;
			}
		}

		int numVertices = index;
		Vertex[] vertexTable = new Vertex[numVertices];

		for (Triangle t : triangles)
			for (Vertex v : t.vert)
				vertexTable[v.index] = v;

		return vertexTable;
	}
}
