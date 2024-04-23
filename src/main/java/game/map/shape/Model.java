package game.map.shape;

import static game.map.MapKey.*;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.w3c.dom.Element;

import app.SwingUtils;
import game.map.BoundingBox;
import game.map.MapObject;
import game.map.MutablePoint;
import game.map.ReversibleTransform;
import game.map.editor.MapEditor;
import game.map.editor.camera.MapEditViewport;
import game.map.editor.commands.AbstractCommand;
import game.map.editor.commands.fields.EditableField;
import game.map.editor.commands.fields.EditableField.EditableFieldFactory;
import game.map.editor.commands.fields.EditableField.StandardBoolName;
import game.map.editor.render.RenderMode;
import game.map.editor.render.Renderer;
import game.map.editor.render.RenderingOptions;
import game.map.editor.selection.PickRay;
import game.map.editor.selection.PickRay.PickHit;
import game.map.editor.ui.info.ModelInfoPanel;
import game.map.hit.CameraZoneData;
import game.map.mesh.TexturedMesh;
import game.map.mesh.Triangle;
import game.map.mesh.Vertex;
import game.map.tree.MapObjectNode;
import util.Logger;
import util.Priority;
import util.identity.IdentityHashSet;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class Model extends MapObject
{
	private int instanceVersion = latestVersion;
	private static final int latestVersion = 2;

	private MapObjectNode<Model> node;

	private final Consumer<Object> notifyCallback = (o) -> {
		notifyListeners();
	};

	public EditableField<ShapeType> modelType = EditableFieldFactory.create(ShapeType.MODEL)
		.setCallback(notifyCallback).setName("Set Model Type").build();

	public EditableField<Boolean> hasMesh = EditableFieldFactory.create(false)
		.setCallback((obj) -> {
			dirtyAABB = true;
			notifyListeners();
		}).setName(new StandardBoolName("Transform Matrix")).build();

	private TexturedMesh mesh = null;

	public transient boolean recievesTransform = false;
	public transient TransformMatrix cumulativeTransformMatrix;

	/*
	 * Handling the transform matricies involves a subtle design decision:
	 * Typically, every group with a distinct transformation has its own matrix.
	 * Groups may share matricies if they use the same transformation, and some
	 * matricies are reused extensively. The best solution is to let every model
	 * have its own TransformMatrix and reduce them to a set of distinct matricies
	 * during map compilation. However, there are three matricies in the game that
	 * are identical to another in the same shape file:
	 * 	kpa_80 80211F70
	 * 	mac_01 80214EB0
	 * 	mac_04 80213D90
	 * Whether they hold any special significance is unknown (and untested!).
	 */
	public EditableField<Boolean> hasTransformMatrix = EditableFieldFactory.create(false)
		.setCallback((obj) -> {
			updateTransformHierarchy();
			notifyListeners();
		}).setName(new StandardBoolName("Transform Matrix")).build();

	public TransformMatrix localTransformMatrix;

	public EditableField<LightSet> lights = EditableFieldFactory.create((LightSet) null)
		.setCallback(notifyCallback).setName("Set Grid X Divisions").build();

	public transient int lightsIndex; // used for serialization
	public transient int d_ptrLightSet; // used by decompiler

	public EditableField<Boolean> hasProp60 = EditableFieldFactory.create(false)
		.setCallback(notifyCallback).setName(new StandardBoolName("Property 60")).build();

	public EditableField<Integer> prop60a = EditableFieldFactory.create(0)
		.setCallback(notifyCallback).setName("Set Property 60").build();
	public EditableField<Integer> prop60b = EditableFieldFactory.create(0)
		.setCallback(notifyCallback).setName("Set Property 60").build();

	public EditableField<RenderMode> renderMode = EditableFieldFactory.create(RenderMode.SURF_SOLID_AA_ZB)
		.setCallback(notifyCallback).setName("Set Render Mode").build();

	public EditableField<Boolean> hasAuxProperties = EditableFieldFactory.create(false)
		.setCallback(notifyCallback).setName(new StandardBoolName("Special Properties")).build();

	public EditableField<Integer> auxOffsetT = EditableFieldFactory.create(0)
		.setCallback(notifyCallback).setName("Set Aux Offset").build();
	public EditableField<Integer> auxOffsetS = EditableFieldFactory.create(0)
		.setCallback(notifyCallback).setName("Set Aux Offset").build();

	public EditableField<Integer> auxShiftT = EditableFieldFactory.create(0)
		.setCallback(notifyCallback).setName("Set Aux Scale").build();
	public EditableField<Integer> auxShiftS = EditableFieldFactory.create(0)
		.setCallback(notifyCallback).setName("Set Aux Scale").build();

	// this one is packed into the properties list, does nothing on its own
	public EditableField<Integer> defaultPannerID = EditableFieldFactory.create(0)
		.setCallback(notifyCallback).setName("Set Default Panner").build();

	// the one used by the script generator
	public EditableField<Integer> pannerID = EditableFieldFactory.create(-1)
		.setCallback(notifyCallback).setName("Set Texture Panner").build();

	public EditableField<ModelReplaceType> replaceWith = EditableFieldFactory.create(ModelReplaceType.None)
		.setCallback(notifyCallback).setName("Set Replacement").build();

	public EditableField<Boolean> hasProp62 = EditableFieldFactory.create(false)
		.setCallback(notifyCallback).setName(new StandardBoolName("Property 62")).build();

	public EditableField<Boolean> hasCameraData = EditableFieldFactory.create(false)
		.setCallback(notifyCallback).setName(new StandardBoolName("Camera Data")).build();

	public transient CameraZoneData camData = null;

	public transient int c_DisplayListOffset; // used by compiler
	public transient BoundingBox localAABB; // used by compiler

	public static final int[][] basicModelProperties = {
			{ 0x5C, 0, 1 },
			{ 0x5F, 0, 0 } };

	public static final int[][] basicGroupProperties = {
			{ 0x60, 0, 0 },
			{ 0x61, 0, 1 } };

	/**
	 * For serialization purposes only!
	 */
	public Model()
	{
		super(MapObjectType.MODEL);

		setDefaultMesh();
		updateMeshHierarchy();

		localTransformMatrix = new TransformMatrix();
		localTransformMatrix.setIdentity();
	}

	// factory method for XML deserialization
	public static Model read(XmlReader xmr, Element mdlElem)
	{
		Model mdl = new Model();
		mdl.fromXML(xmr, mdlElem);
		return mdl;
	}

	@Override
	public void fromXML(XmlReader xmr, Element mdlElem)
	{
		super.fromXML(xmr, mdlElem);

		node = new MapObjectNode<>(this);

		xmr.requiresAttribute(mdlElem, ATTR_VERSION);
		instanceVersion = xmr.readInt(mdlElem, ATTR_VERSION);

		xmr.requiresAttribute(mdlElem, ATTR_SHAPE_TYPE);
		modelType.set(xmr.readEnum(mdlElem, ATTR_SHAPE_TYPE, ShapeType.class));

		xmr.requiresAttribute(mdlElem, ATTR_LIGHT_SET);
		lightsIndex = xmr.readInt(mdlElem, ATTR_LIGHT_SET);

		Element meshElement = xmr.getUniqueTag(mdlElem, TAG_TEXTURED_MESH);
		if (meshElement != null) {
			hasMesh.set(true);
			mesh = TexturedMesh.read(xmr, meshElement);
			mesh.parentObject = this;
			AABB = new BoundingBox();
		}
		else {
			hasMesh.set(false);
			Element aabbElement = xmr.getUniqueTag(mdlElem, TAG_BOUNDING_BOX);
			if (aabbElement != null)
				AABB = BoundingBox.read(xmr, aabbElement);
			else
				xmr.complain("Model " + getName() + " requires either " + TAG_TEXTURED_MESH + " or " + TAG_BOUNDING_BOX);
		}

		Element matrixElement = xmr.getUniqueTag(mdlElem, TAG_TX_MAT);
		if (matrixElement != null) {
			hasTransformMatrix.set(true);
			localTransformMatrix = TransformMatrix.read(xmr, matrixElement);
		}

		if (modelType.get() != ShapeType.ROOT) {
			Element propertyListElement = xmr.getUniqueRequiredTag(mdlElem, TAG_PROPERTY_LIST);
			List<Element> propertyElements = xmr.getTags(propertyListElement, TAG_PROPERTY);

			int numProperties = propertyElements.size();
			int[][] properties = new int[numProperties][3];

			int i = 0;
			for (Element property : propertyElements)
				properties[i++] = xmr.readHexArray(property, ATTR_PROPERTY_V, 3);
			setProperties(properties);
		}

		// override if available
		if (xmr.hasAttribute(mdlElem, ATTR_SCROLL_UNIT))
			pannerID.set(xmr.readInt(mdlElem, ATTR_SCROLL_UNIT));
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag mdlTag = xmw.createTag(TAG_MODEL, false);
		xmw.addInt(mdlTag, ATTR_VERSION, latestVersion);
		xmw.addEnum(mdlTag, ATTR_SHAPE_TYPE, modelType.get());
		xmw.addInt(mdlTag, ATTR_LIGHT_SET, lights.get() == null ? 0 : lights.get().io_listIndex);

		if (pannerID.get() >= 0)
			xmw.addInt(mdlTag, ATTR_SCROLL_UNIT, pannerID.get());

		xmw.openTag(mdlTag);
		super.toXML(xmw);

		if (hasMesh.get())
			mesh.toXML(xmw);
		else
			AABB.toXML(xmw);

		if (hasTransformMatrix.get())
			localTransformMatrix.toXML(xmw);

		XmlTag propertyListTag = xmw.createTag(TAG_PROPERTY_LIST, false);
		xmw.openTag(propertyListTag);
		int[][] properties = getProperties();
		for (int[] element : properties) {
			XmlTag propertyTag = xmw.createTag(TAG_PROPERTY, true);
			xmw.addHexArray(propertyTag, ATTR_PROPERTY_V, element[0], element[1], element[2]);
			xmw.printTag(propertyTag);
		}
		xmw.closeTag(propertyListTag);

		xmw.closeTag(mdlTag);
	}

	@Override
	public void initialize()
	{
		recalculateAABB();
	}

	public void setProperties(int[][] properties)
	{
		if (modelType.get() == ShapeType.MODEL)
			setModelProperties(properties);
		else
			setGroupProperties(properties);
	}

	private void setModelProperties(int[][] properties)
	{
		if (modelType.get() != ShapeType.MODEL)
			throw new IllegalStateException("Tried to invoke setModelProperties on " + modelType);

		int lastPropertyType = 0;

		int camDataPos = 0;
		int[] camDataWords = new int[11];
		renderMode.set(RenderMode.NONE);

		for (int[] property : properties) {
			assert (property[0] >= lastPropertyType);

			switch (property[0]) {
				case 0x5C:
					assert (property[1] == 0);
					RenderMode mode = RenderMode.getModeForID(property[2]);
					if (mode == null) {
						mode = RenderMode.getModeForID(1);
						Logger.logfError("Invalid render mode %02X, setting to %s", property[2], mode.name);
					}
					renderMode.set(mode);
					break;

				case 0x5D:
					// 1 indiciates float, so the middle 9 use
					if (camDataPos == 0 || camDataPos == 10)
						assert (property[1] == 0);
					else
						assert (property[1] == 1);

					hasCameraData.set(true);
					camDataWords[camDataPos++] = property[2];
					break;

				case 0x5F:
					hasAuxProperties.set(true);
					int a = property[1];
					int b = property[2];
					assert ((a & 0xFF000000) == 0);
					assert ((b & 0xFFF00F00) == 0);

					auxOffsetT.set((a >> 12) & 0xFFF);
					auxOffsetS.set(a & 0xFFF);

					auxShiftT.set((b >> 16) & 0xF);
					auxShiftS.set((b >> 12) & 0xF);

					replaceWith.set(ModelReplaceType.getType((b >> 4) & 0xF));
					defaultPannerID.set(b & 0xF);
					break;

				case 0x62:
					hasProp62.set(true);
					break;
			}

			lastPropertyType = property[0];
		}

		if (camDataPos > 0)
			camData = new CameraZoneData(this, camDataWords);
		else
			camData = new CameraZoneData(this);

	}

	private void setGroupProperties(int[][] properties)
	{
		if (modelType.get() == ShapeType.MODEL)
			throw new IllegalStateException("Tried to invoke setGroupProperties on " + modelType);

		if (properties.length != 0) {
			if (properties.length != 2)
				throw new IllegalStateException("Too many properties for group: " + properties.length);

			hasProp60.set(true);
			prop60a.set(properties[0][2]);
			prop60b.set(properties[1][2]);
		}
	}

	public int[][] getProperties()
	{
		int[][] properties;

		if (modelType.get() == ShapeType.MODEL) {
			ArrayList<int[]> propertyList = new ArrayList<>();

			if (renderMode.get() != RenderMode.NONE)
				propertyList.add(new int[] { 0x5C, 0, renderMode.get().id });

			if (hasCameraData.get()) {
				int[] camDataWords = camData.getData();
				propertyList.add(new int[] { 0x5D, 0, camDataWords[0] });
				for (int i = 1; i < 10; i++)
					propertyList.add(new int[] { 0x5D, 1, camDataWords[i] });
				propertyList.add(new int[] { 0x5D, 0, camDataWords[10] });
			}

			if (hasAuxProperties.get()) {
				int a = 0;
				a |= (auxOffsetT.get() & 0xFFF) << 12;
				a |= (auxOffsetS.get() & 0xFFF);

				int b = 0;
				b |= (auxShiftT.get() & 0xF) << 16;
				b |= (auxShiftS.get() & 0xF) << 12;
				b |= (ModelReplaceType.getID(replaceWith.get()) & 0xF) << 4;
				b |= (defaultPannerID.get() & 0xF);

				propertyList.add(new int[] { 0x5F, a, b });
			}

			if (hasProp62.get())
				propertyList.add(new int[] { 0x62, 0, 0x00008000 });

			properties = new int[propertyList.size()][3];
			propertyList.toArray(properties);
		}
		else {
			if (hasProp60.get()) {
				properties = new int[2][];
				properties[0] = new int[] { 0x60, 0, prop60a.get() };
				properties[1] = new int[] { 0x60, 0, prop60b.get() };
			}
			else {
				properties = new int[0][3];
			}
		}
		return properties;
	}

	public Model(ShapeType type)
	{
		super(MapObjectType.MODEL);

		this.instanceVersion = latestVersion;
		this.modelType.set(type);

		node = new MapObjectNode<>(this);
		AABB = new BoundingBox();

		localTransformMatrix = new TransformMatrix();
		localTransformMatrix.setIdentity();
	}

	public static Model createDefaultRoot()
	{
		Model rootModel = new Model(ShapeType.ROOT);
		rootModel.setName("Root");
		rootModel.cumulativeTransformMatrix = new TransformMatrix();
		rootModel.cumulativeTransformMatrix.setIdentity();
		rootModel.updateTransformHierarchy();

		return rootModel;
	}

	public static Model createBareModel()
	{
		Model mdl = new Model(ShapeType.MODEL);
		mdl.setDefaultMesh();
		mdl.mesh.setTexture("");

		mdl.setProperties(basicModelProperties);
		return mdl;
	}

	public static Model create(TriangleBatch batch, String name)
	{
		Model mdl = new Model(ShapeType.MODEL);
		mdl.setName(name);

		mdl.setDefaultMesh();
		mdl.mesh.setTexture("");

		mdl.setProperties(basicModelProperties);
		mdl.getMesh().displayListModel.addElement(batch);

		mdl.updateMeshHierarchy();
		mdl.dirtyAABB = true;
		mdl.updateTransformHierarchy();

		return mdl;
	}

	@Override
	public Model deepCopy()
	{
		Model m = new Model(modelType.get());
		m.AABB = AABB.deepCopy();
		m.dirtyAABB = dirtyAABB;

		if (hasMesh.get())
			m.setMesh(mesh.deepCopy());
		else
			m.setMesh(null);

		m.setName(getName());

		m.localTransformMatrix = localTransformMatrix.deepCopy();
		m.hasTransformMatrix.copy(hasTransformMatrix);

		m.node.parentNode = node.parentNode;
		m.updateTransformHierarchy();

		m.lights = lights;

		int[][] properties = getProperties();

		if (modelType.get() == ShapeType.MODEL)
			m.setModelProperties(properties);

		return m;
	}

	public static final ShapeType getTypeFromID(int id)
	{
		switch (id) {
			case 2:
				return ShapeType.MODEL;
			case 5:
				return ShapeType.GROUP;
			case 7:
				return ShapeType.ROOT;
			case 10:
				return ShapeType.SPECIAL;
			default:
				throw new RuntimeException("Invalid model type ID =" + id);
		}
	}

	public static final int getIDFromType(ShapeType type)
	{
		switch (type) {
			case MODEL:
				return 2;
			case GROUP:
				return 5;
			case ROOT:
				return 7;
			case SPECIAL:
				return 10;
			default:
				throw new RuntimeException("Invalid model type =" + type);
		}
	}

	@Override
	public MapObjectType getObjectType()
	{ return MapObjectType.MODEL; }

	/**
	 * Camera Controller
	 */

	@Override
	public boolean hasCameraControlData()
	{
		return hasCameraData.get();
	}

	@Override
	public CameraZoneData getCameraControlData()
	{ return camData; }

	@Override
	public void addPoints(IdentityHashSet<MutablePoint> positionSet)
	{
		super.addPoints(positionSet);

		if (hasCameraData.get()) {
			camData.posA.addPoints(positionSet);
			camData.posB.addPoints(positionSet);
			camData.posC.addPoints(positionSet);
		}
	}

	@Override
	public void renderPoints(RenderingOptions opts, Renderer renderer, MapEditViewport view)
	{
		if (hasCameraData.get()) {
			MapEditor editor = MapEditor.instance();
			editor.dummyCameraController.update(camData, editor.cursor3D.getPosition(),
				editor.cursor3D.allowVerticalCameraMovement(), editor.getDeltaTime());
			camData.drawHelpers(renderer, editor.dummyCameraController, true);
		}
	}

	/**
	 * Mesh
	 */

	@Override
	public TexturedMesh getMesh()
	{ return mesh; }

	public void setMesh(TexturedMesh newMesh)
	{
		mesh = newMesh;
		hasMesh.set((newMesh != null));

		dirtyAABB = true;
		if (hasMesh.get())
			updateMeshHierarchy();
	}

	public void setDefaultMesh()
	{
		setMesh(TexturedMesh.createDefaultMesh());
	}

	@Override
	public void updateMeshHierarchy()
	{
		mesh.parentObject = this;
		mesh.updateHierarchy();
	}

	@Override
	public boolean transforms()
	{
		return hasMesh.get();
	}

	@Override
	public boolean shouldTryPick(PickRay ray)
	{
		return hasMesh.get() && PickRay.intersects(ray, AABB);
	}

	@Override
	public PickHit tryPick(PickRay ray)
	{
		PickHit nearestHit = new PickHit(ray, Float.MAX_VALUE);
		if (!hasMesh.get())
			return null;

		if (!PickRay.intersects(ray, AABB))
			return nearestHit;

		for (Triangle t : mesh) {
			PickHit hit = PickRay.getIntersection(ray, t);
			if (hit.dist < nearestHit.dist)
				nearestHit = hit;
		}
		nearestHit.obj = this;
		return nearestHit;
	}

	@Override
	public boolean shouldTryTrianglePick(PickRay ray)
	{
		return hasMesh.get() && PickRay.intersects(ray, AABB);
	}

	@Override
	public String toString()
	{
		return getName() + (hasTransformMatrix.get() ? " *" : "");
	}

	public Color getTreeTextColor()
	{ return (hasTransformMatrix.get() && !localTransformMatrix.baked) ? SwingUtils.getBlueTextColor() : null; }

	public void calculateLocalAABB()
	{
		if (!hasMesh.get())
			return;

		for (Triangle t : mesh)
			for (Vertex v : t.vert) {
				MutablePoint point = v.getLocalPosition();
				localAABB.encompass(point.getX(), point.getY(), point.getZ());
			}
	}

	public void updateTransformHierarchy()
	{
		if (node.parentNode != null) {
			Model parentModel = node.parentNode.getUserObject();
			recievesTransform = parentModel.recievesTransform || parentModel.hasTransformMatrix.get();

			if (hasTransformMatrix.get()) {
				TransformMatrix left = localTransformMatrix.copyWithPreview();
				TransformMatrix right = parentModel.cumulativeTransformMatrix.copyWithPreview();
				cumulativeTransformMatrix = TransformMatrix.multiply(left, right);
			}
			else
				cumulativeTransformMatrix = parentModel.cumulativeTransformMatrix.deepCopy();
		}
		else {
			// root
			if (hasTransformMatrix.get())
				cumulativeTransformMatrix = localTransformMatrix.copyWithPreview();
			else
				cumulativeTransformMatrix = TransformMatrix.identity();
		}

		if (hasMesh.get()) {
			boolean hasTransformation = recievesTransform || hasTransformMatrix.get();

			if (hasTransformation)
				for (Triangle t : mesh)
					for (Vertex v : t.vert)
						v.forceTransform(cumulativeTransformMatrix);

			for (Triangle t : mesh)
				for (Vertex v : t.vert)
					v.useLocal = !hasTransformation;

			dirtyAABB = true;
		}

		for (int i = 0; i < node.getChildCount(); i++) {
			MapObjectNode<Model> childNode = node.getChildAt(i);
			childNode.getUserObject().updateTransformHierarchy();
		}
	}

	@Override
	public ReversibleTransform createTransformer(TransformMatrix m)
	{
		if (hasMesh.get()) {
			if (!recievesTransform) {
				return super.createTransformer(m);

			}
			else {
				Model parentModel = node.parentNode.getUserObject();
				if (!parentModel.hasTransformMatrix.get()) {
					Logger.log("Could not apply matrix transformation to group!", Priority.WARNING);
					return null;
				}

				MatrixTransformer backup = new MatrixTransformer();
				backup.mdl = this;
				backup.parent = parentModel;
				backup.oldMatrix = parentModel.localTransformMatrix;
				backup.newMatrix = TransformMatrix.multiply(m, parentModel.localTransformMatrix);

				return backup;
			}
		}

		return null;
	}

	public static class MatrixTransformer extends ReversibleTransform
	{
		private Model mdl;
		private Model parent;
		private TransformMatrix oldMatrix, newMatrix;

		@Override
		public void transform()
		{
			if (mdl.recievesTransform) {
				parent.localTransformMatrix = newMatrix;
				parent.updateTransformHierarchy();
			}
		}

		@Override
		public void revert()
		{
			if (mdl.recievesTransform) {
				parent.localTransformMatrix = oldMatrix;
				parent.updateTransformHierarchy();
			}
		}
	}

	@Override
	public boolean allowsPopup()
	{
		return modelType.get() != ShapeType.MODEL;
	}

	@Override
	public boolean allowsChildren()
	{
		return modelType.get() != ShapeType.MODEL;
	}

	@Override
	public MapObjectNode<Model> getNode()
	{ return node; }

	public static final class SetMatrixElement extends AbstractCommand
	{
		private final Model mdl;
		private final double oldValue;
		private final double newValue;
		private final int row, col;

		public SetMatrixElement(Model mdl, int row, int col, double val)
		{
			super("Set Transform Matrix Element");
			this.mdl = mdl;
			this.row = row;
			this.col = col;
			oldValue = mdl.localTransformMatrix.get(row, col);
			newValue = val;
		}

		@Override
		public boolean shouldExec()
		{
			return oldValue != newValue;
		}

		@Override
		public void exec()
		{
			super.exec();
			mdl.localTransformMatrix.set(newValue, row, col);
			mdl.updateTransformHierarchy();
			mdl.notifyListeners();
		}

		@Override
		public void undo()
		{
			super.undo();
			mdl.localTransformMatrix.set(oldValue, row, col);
			mdl.updateTransformHierarchy();
			mdl.notifyListeners();
		}
	}

	public static final class SetMatrix extends AbstractCommand
	{
		private final Model mdl;
		private final double[][] oldMat;
		private final double[][] newMat;
		private final boolean oldBaked;

		public SetMatrix(Model mdl, double[][] mat, boolean baked)
		{
			super("Set Transform Matrix");
			this.mdl = mdl;

			oldMat = new double[4][4];
			newMat = new double[4][4];
			oldBaked = baked;

			for (int i = 0; i < 4; i++)
				for (int j = 0; j < 4; j++) {
					oldMat[i][j] = mdl.localTransformMatrix.get(i, j);
					newMat[i][j] = mat[i][j];
				}
		}

		@Override
		public boolean shouldExec()
		{
			for (int i = 0; i < 4; i++)
				for (int j = 0; j < 4; j++) {
					if (oldMat[i][j] != newMat[i][j])
						return true;
				}

			return false;
		}

		@Override
		public void exec()
		{
			super.exec();

			for (int i = 0; i < 4; i++)
				for (int j = 0; j < 4; j++)
					mdl.localTransformMatrix.set(newMat[i][j], i, j);

			mdl.localTransformMatrix.baked = true;
			mdl.updateTransformHierarchy();
			mdl.notifyListeners();
			editor.gui.repaintVisibleTree();
		}

		@Override
		public void undo()
		{
			super.undo();

			for (int i = 0; i < 4; i++)
				for (int j = 0; j < 4; j++)
					mdl.localTransformMatrix.set(oldMat[i][j], i, j);

			mdl.localTransformMatrix.baked = oldBaked;
			mdl.updateTransformHierarchy();
			mdl.notifyListeners();
			editor.gui.repaintVisibleTree();
		}
	}

	@Override
	public boolean hasMesh()
	{
		return hasMesh.get();
	}

	public void displayListChanged()
	{
		notifyListeners(ModelInfoPanel.tag_DisplayListTab);
	}
}
