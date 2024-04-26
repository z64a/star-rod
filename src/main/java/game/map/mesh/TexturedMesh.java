package game.map.mesh;

import static game.map.MapKey.*;
import static renderer.buffers.BufferedMesh.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;

import javax.swing.DefaultListModel;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import game.map.editor.MapEditor.EditorMode;
import game.map.editor.render.RenderingOptions;
import game.map.editor.render.TextureManager;
import game.map.editor.selection.SelectionManager.SelectionMode;
import game.map.shape.TriangleBatch;
import game.map.shape.commands.ChangeGeometryFlags;
import game.map.shape.commands.DisplayCommand;
import game.map.shape.commands.FlushPipeline;
import game.texture.ModelTexture;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlSerializable;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class TexturedMesh extends AbstractMesh implements XmlSerializable
{
	private int instanceVersion = latestVersion;
	private static final int latestVersion = 3;

	public transient DisplayListModel displayListModel;

	private transient boolean dirtyBuffer = true;

	public String textureName = "";

	// members for the map editor at runtime
	public transient boolean textured = false;
	public transient ModelTexture texture = null;

	public static TexturedMesh read(XmlReader xmr, Element meshElement)
	{
		TexturedMesh m = new TexturedMesh();
		m.fromXML(xmr, meshElement);
		return m;
	}

	@Override
	public void fromXML(XmlReader xmr, Element meshElem)
	{
		xmr.requiresAttribute(meshElem, ATTR_VERSION);
		instanceVersion = xmr.readInt(meshElem, ATTR_VERSION);

		xmr.hasAttribute(meshElem, ATTR_TEXTURE_NAME);
		textureName = xmr.getAttribute(meshElem, ATTR_TEXTURE_NAME);

		Element displayListElement = xmr.getUniqueRequiredTag(meshElem, TAG_DISPLAY_LIST);
		NodeList children = displayListElement.getChildNodes();

		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
			if (child instanceof Element elem) {
				if (elem.getTagName().equals(TAG_TRIANGLE_BATCH.toString())) {
					TriangleBatch batch = TriangleBatch.read(xmr, elem, this);
					displayListModel.addElement(batch);
				}
				else {
					int[] v = xmr.readHexArray(elem, ATTR_DISPLAY_CMD_V, 2);
					displayListModel.addElement(DisplayCommand.resolveCommand(this, v[0], v[1]));
				}
			}
		}

		updateHierarchy();
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag meshTag = xmw.createTag(TAG_TEXTURED_MESH, false);
		xmw.addInt(meshTag, ATTR_VERSION, latestVersion);
		xmw.addAttribute(meshTag, ATTR_TEXTURE_NAME, textureName);

		xmw.openTag(meshTag);

		XmlTag dlTag = xmw.createTag(TAG_DISPLAY_LIST, false);
		xmw.openTag(dlTag);
		for (DisplayCommand cmd : displayListModel) {
			if (cmd instanceof TriangleBatch batch) {
				batch.toXML(xmw);
			}
			else {
				int[] binaryCmd = cmd.getF3DEX2Command();
				XmlTag cmdTag = xmw.createTag(TAG_DISPLAY_CMD, true);
				xmw.addHexArray(cmdTag, ATTR_DISPLAY_CMD_V, binaryCmd);
				xmw.printTag(cmdTag);
			}
		}
		xmw.closeTag(dlTag);

		xmw.closeTag(meshTag);
	}

	public static TexturedMesh createDefaultMesh()
	{
		TexturedMesh mesh = new TexturedMesh();
		mesh.addDefaultCommands();
		return mesh;
	}

	private void addDefaultCommands()
	{
		displayListModel.addElement(new FlushPipeline(this));
		displayListModel.addElement(ChangeGeometryFlags.getCommand(this, 0xD9FDFFFF, 0x00000000));
		displayListModel.addElement(ChangeGeometryFlags.getCommand(this, 0xD9FFFFFF, 0x00200400));
	}

	public TexturedMesh()
	{
		super(VBO_UV | VBO_COLOR | VBO_AUX);
		displayListModel = new DisplayListModel(this);
	}

	@Override
	public void prepareVertexBuffers(RenderingOptions opts)
	{
		// when profiling this is nearly INSTANT -- so its perfectly fine to just reload the buffers every frame
		//	if(!dirtyBuffer)
		//		return;

		validateBuffer();
		buffer.clear();
		boolean selectionEnabled = (opts.editorMode == EditorMode.Modify || opts.editorMode == EditorMode.Scripts);
		for (TriangleBatch batch : getBatches()) {
			batch.bufferStartPos = -1;
			for (Triangle t : batch.triangles) {
				int triStart = addTexturedTriangle(t, selectionEnabled &&
					(opts.selectionMode == SelectionMode.TRIANGLE && t.selected) ||
					(opts.selectionMode == SelectionMode.OBJECT && parentObject.selected));
				if (batch.bufferStartPos < 0)
					batch.bufferStartPos = triStart;
			}
		}
		buffer.loadBuffers();
		//	dirtyBuffer = false;
	}

	public void setDirty()
	{
		dirtyBuffer = true;
	}

	private int addTexturedTriangle(Triangle t, boolean selected)
	{
		int i = addTexturedVertex(t.vert[0], selected);
		int j = addTexturedVertex(t.vert[1], selected);
		int k = addTexturedVertex(t.vert[2], selected);
		buffer.addTriangle(i, j, k);
		return i;
	}

	private int addTexturedVertex(Vertex v, boolean selected)
	{
		float r = (v.r & 0xFF) / 255.0f;
		float g = (v.g & 0xFF) / 255.0f;
		float b = (v.b & 0xFF) / 255.0f;
		float a = (v.a & 0xFF) / 255.0f;

		return buffer.addVertex()
			.setPosition(v.getCurrentX(), v.getCurrentY(), v.getCurrentZ())
			.setColor(r, g, b, a)
			.setUV(v.uv.getU(), v.uv.getV())
			.setAux(0, selected ? 1.0f : 0.0f)
			.getIndex();
	}

	public void setTexture(String texName)
	{
		if (texName.isEmpty())
			setTexture((ModelTexture) null);
		else
			setTexture(TextureManager.get(texName));
	}

	public void setTexture(ModelTexture newTexture)
	{
		texture = newTexture;
		textured = (newTexture != null);
		textureName = textured ? newTexture.getName() : "";
	}

	public void changeTexture(ModelTexture newTexture)
	{
		TextureManager.decrement(texture);
		setTexture(newTexture);
		TextureManager.increment(texture);
	}

	@Override
	public void updateHierarchy()
	{
		for (int i = 0; i < displayListModel.size(); i++) {
			DisplayCommand cmd = displayListModel.getElementAt(i);
			if (cmd instanceof TriangleBatch batch)
				batch.setParent(this);
		}
	}

	@Override
	public List<TriangleBatch> getBatches()
	{
		List<TriangleBatch> batches = new LinkedList<>();

		for (int i = 0; i < displayListModel.size(); i++) {
			DisplayCommand cmd = displayListModel.getElementAt(i);
			if (cmd instanceof TriangleBatch batch)
				batches.add(batch);
		}

		return batches;
	}

	@Override
	public TexturedMesh deepCopy()
	{
		TexturedMesh m = new TexturedMesh();

		for (int i = 0; i < displayListModel.size(); i++) {
			DisplayCommand cmd = displayListModel.getElementAt(i);
			m.displayListModel.addElement(cmd.deepCopy());
		}
		m.updateHierarchy();
		m.setTexture(texture);

		return m;
	}

	@Override
	public Iterator<Triangle> iterator()
	{
		return new TexturedMeshIterator(displayListModel);
	}

	private static class TexturedMeshIterator implements Iterator<Triangle>
	{
		private ArrayList<Iterator<Triangle>> iterators = new ArrayList<>(3);
		private int i = 0;

		private TexturedMeshIterator(DisplayListModel displayList)
		{
			for (int i = 0; i < displayList.size(); i++) {
				DisplayCommand cmd = displayList.getElementAt(i);
				if (cmd instanceof TriangleBatch batch) {
					iterators.add(batch.triangles.iterator());
				}
			}
		}

		@Override
		public boolean hasNext()
		{
			if (iterators.isEmpty())
				return false;

			return batchHasNext(i);
		}

		private boolean batchHasNext(int i)
		{
			if (iterators.get(i).hasNext())
				return true;

			if ((i + 1) < iterators.size())
				return batchHasNext(i + 1);

			return false;
		}

		@Override
		public Triangle next()
		{
			if (!hasNext())
				throw new NoSuchElementException();

			Iterator<Triangle> iter = iterators.get(i);
			while (!iter.hasNext()) {
				i++;
				iter = iterators.get(i);
			}

			return iter.next();
		}

		@Override
		public void remove()
		{
			throw new UnsupportedOperationException();
		}
	}

	/**
	 * This extended list model updates the displayListDirty flag whenever a TriangleBatch
	 * is added to or removed from the list model.
	 */
	public static class DisplayListModel extends DefaultListModel<DisplayCommand> implements Iterable<DisplayCommand>
	{
		private final TexturedMesh mesh;

		private DisplayListModel(TexturedMesh mesh)
		{
			this.mesh = mesh;
		}

		public void setDirty()
		{
			mesh.dirtyBuffer = true;
		}

		@Override
		public void add(int index, DisplayCommand cmd)
		{
			if (cmd instanceof TriangleBatch batch) {
				batch.parentMesh = mesh;
				mesh.dirtyBuffer = true;
			}
			super.add(index, cmd);
		}

		@Override
		public void addElement(DisplayCommand cmd)
		{
			if (cmd instanceof TriangleBatch batch) {
				batch.parentMesh = mesh;
				mesh.dirtyBuffer = true;
			}
			super.addElement(cmd);
		}

		@Override
		public void insertElementAt(DisplayCommand cmd, int index)
		{
			if (cmd instanceof TriangleBatch batch) {
				batch.parentMesh = mesh;
				mesh.dirtyBuffer = true;
			}
			super.insertElementAt(cmd, index);
		}

		@Override
		public DisplayCommand remove(int index)
		{
			DisplayCommand cmd = super.remove(index);
			if (cmd instanceof TriangleBatch)
				mesh.dirtyBuffer = true;
			return cmd;
		}

		@Override
		public boolean removeElement(Object obj)
		{
			boolean removed = super.removeElement(obj);
			if (removed && (obj instanceof TriangleBatch))
				mesh.dirtyBuffer = true;
			return removed;
		}

		@Override
		public void removeElementAt(int index)
		{
			if (get(index) instanceof TriangleBatch)
				mesh.dirtyBuffer = true;
			super.removeElementAt(index);
		}

		@Override
		public void removeAllElements()
		{
			mesh.dirtyBuffer = true;
			super.removeAllElements();
		}

		@Override
		public void clear()
		{
			mesh.dirtyBuffer = true;
			super.clear();
		}

		@Override
		public DisplayCommand set(int index, DisplayCommand cmd)
		{
			DisplayCommand old = super.set(index, cmd);
			if (old instanceof TriangleBatch || cmd instanceof TriangleBatch)
				mesh.dirtyBuffer = true;
			return old;
		}

		@Override
		public void setElementAt(DisplayCommand cmd, int index)
		{
			if (get(index) instanceof TriangleBatch || cmd instanceof TriangleBatch)
				mesh.dirtyBuffer = true;
			super.setElementAt(cmd, index);
		}

		@Override
		public Iterator<DisplayCommand> iterator()
		{
			return new DisplayListIterator(this);
		}

		private static class DisplayListIterator implements Iterator<DisplayCommand>
		{
			private final DisplayListModel listModel;
			private int pos = 0;

			private DisplayListIterator(DisplayListModel listModel)
			{
				this.listModel = listModel;
			}

			@Override
			public boolean hasNext()
			{
				return pos < listModel.getSize();
			}

			@Override
			public DisplayCommand next()
			{
				return listModel.get(pos++);
			}
		}
	}
}
