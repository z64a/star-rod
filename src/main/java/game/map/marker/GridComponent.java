package game.map.marker;

import static game.map.MapKey.*;

import java.util.Collection;
import java.util.Collections;
import java.util.function.Consumer;

import org.w3c.dom.Element;

import app.input.InputFileException;
import game.entity.EntityInfo.EntityType;
import game.map.BoundingBox;
import game.map.editor.MapEditor;
import game.map.editor.camera.MapEditViewport;
import game.map.editor.commands.AbstractCommand;
import game.map.editor.commands.fields.EditableField;
import game.map.editor.commands.fields.EditableField.EditableFieldFactory;
import game.map.editor.commands.fields.EditableField.StandardBoolName;
import game.map.editor.render.PresetColor;
import game.map.editor.render.Renderer;
import game.map.editor.render.RenderingOptions;
import game.map.editor.render.SortedRenderable;
import game.map.editor.render.TextureManager;
import game.map.editor.selection.PickRay;
import game.map.editor.selection.PickRay.Channel;
import game.map.editor.selection.PickRay.PickHit;
import game.map.editor.ui.info.MarkerInfoPanel;
import game.map.marker.GridOccupant.OccupantType;
import game.map.mesh.Triangle;
import game.map.mesh.Vertex;
import game.map.scripts.extract.HeaderEntry;
import game.map.shape.TransformMatrix;
import renderer.buffers.LineRenderQueue;
import renderer.buffers.TriangleRenderQueue;
import renderer.shaders.RenderState;
import renderer.shaders.RenderState.PolygonMode;
import renderer.shaders.ShaderManager;
import renderer.shaders.scene.MarkerShader;
import util.identity.IdentityArrayList;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class GridComponent extends BaseMarkerComponent
{
	private final Consumer<Object> notifyCallback = (o) -> {
		parentMarker.updateListeners(MarkerInfoPanel.tag_GeneralTab);
	};

	public EditableField<Integer> gridIndex = EditableFieldFactory.create(0)
		.setCallback(notifyCallback).setName("Set Grid System ID").build();

	public EditableField<Integer> gridSizeX = EditableFieldFactory.create(1)
		.setCallback(notifyCallback).setName("Set Grid X Divisions").build();

	public EditableField<Integer> gridSizeZ = EditableFieldFactory.create(1)
		.setCallback(notifyCallback).setName("Set Grid Z Divisions").build();

	public EditableField<Integer> gridSpacing = EditableFieldFactory.create(25)
		.setCallback(notifyCallback).setName("Set Grid Spacing").build();

	public EditableField<Boolean> gridUseGravity = EditableFieldFactory.create(false)
		.setCallback(notifyCallback).setName(new StandardBoolName("Block Gravity")).build();

	public EditableField<Boolean> showEditHandles = EditableFieldFactory.create(false)
		.setCallback(notifyCallback).setName(new StandardBoolName("Enable Grid Editing")).build();

	public IdentityArrayList<GridOccupant> gridOccupants = new IdentityArrayList<>();

	public GridComponent(Marker parent)
	{
		super(parent);
	}

	@Override
	public GridComponent deepCopy(Marker copyParent)
	{
		GridComponent copy = new GridComponent(copyParent);
		copy.gridIndex.copy(gridIndex);
		copy.gridSizeX.copy(gridSizeX);
		copy.gridSizeZ.copy(gridSizeZ);
		copy.gridSpacing.copy(gridSpacing);
		copy.gridUseGravity.copy(gridUseGravity);
		copy.showEditHandles.copy(showEditHandles);
		for (GridOccupant occ : gridOccupants)
			copy.gridOccupants.add(occ.deepCopy(copy.gridOccupants));
		return copy;
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag compTag = xmw.createTag(TAG_MARKER_GRID, true);
		xmw.addIntArray(compTag, ATTR_MARKER_GRID_SIZE, gridSizeX.get(), gridSizeZ.get(), gridSpacing.get());

		int[] gridContent = new int[gridOccupants.size() * 3];
		int i = 0;
		for (GridOccupant occ : gridOccupants) {
			if (occ.posX < gridSizeX.get() && occ.posZ < gridSizeZ.get()) {
				gridContent[i++] = occ.posX;
				gridContent[i++] = occ.posZ;
				gridContent[i++] = occ.type.get().id;
			}
		}
		xmw.addIntArray(compTag, ATTR_MARKER_GRID_OCC, gridContent);

		xmw.addBoolean(compTag, ATTR_MARKER_GRID_GRAV, gridUseGravity.get());

		xmw.printTag(compTag);
	}

	@Override
	public void fromXML(XmlReader xmr, Element markerElem)
	{
		Element gridElem = xmr.getUniqueRequiredTag(markerElem, TAG_MARKER_GRID);

		int[] grid = xmr.readIntArray(gridElem, ATTR_MARKER_GRID_SIZE, 3);
		gridSizeX.set(grid[0]);
		gridSizeZ.set(grid[1]);
		gridSpacing.set(grid[2]);

		if (xmr.hasAttribute(gridElem, ATTR_MARKER_GRID_OCC)) {
			int[] gridContent = xmr.readIntArray(gridElem, ATTR_MARKER_GRID_OCC, -1);
			if (gridContent.length % 3 != 0)
				throw new InputFileException(xmr.getSourceFile(), "Invalid grid contents for " + parentMarker.getName());
			for (int i = 0; i < gridContent.length; i += 3)
				gridOccupants.add(new GridOccupant(gridOccupants, gridContent[i], gridContent[i + 1], gridContent[i + 2]));
		}

		if (xmr.hasAttribute(gridElem, ATTR_MARKER_GRID_GRAV))
			gridUseGravity.set(xmr.readBoolean(gridElem, ATTR_MARKER_GRID_GRAV));
	}

	@Override
	public void addRenderables(RenderingOptions opts, Collection<SortedRenderable> renderables, PickHit shadowHit)
	{
		for (GridOccupant occ : gridOccupants) {
			if (occ.type.get() != OccupantType.Block)
				continue;

			EntityType.PushBlock.addRenderables(renderables, parentMarker.selected,
				parentMarker.position.getX() + (int) (gridSpacing.get() * (occ.posX + 0.5)),
				parentMarker.position.getY(),
				parentMarker.position.getZ() + (int) (gridSpacing.get() * (occ.posZ + 0.5)),
				(float) parentMarker.yaw.getAngle(), 0);
		}
	}

	@Override
	public void render(RenderingOptions opts, MapEditViewport view, Renderer renderer)
	{
		super.render(opts, view, renderer);

		if (parentMarker.selected)
			renderGrid(opts, view, renderer);

		if (showEditHandles.get())
			renderEditHandles(opts, view, renderer);

		if (opts.showEntityCollision) {
			for (GridOccupant occ : gridOccupants) {
				if (occ.type.get() == OccupantType.Block) {
					EntityType.PushBlock.renderCollision(parentMarker.selected, 0,
						parentMarker.position.getX() + (int) (gridSpacing.get() * (occ.posX + 0.5)),
						parentMarker.position.getY(),
						parentMarker.position.getZ() + (int) (gridSpacing.get() * (occ.posZ + 0.5)));
				}
			}
		}
	}

	private GridOccupant getOccupantAt(int x, int z)
	{
		for (GridOccupant occ : gridOccupants) {
			if (occ.posX == x && occ.posZ == z)
				return occ;
		}
		return null;
	}

	private void setOccupantAt(int x, int z, OccupantType type)
	{
		GridOccupant occ = getOccupantAt(x, z);
		if (occ != null) {
			if (type == null)
				MapEditor.execute(new RemoveGridOccupant(occ));
			else
				MapEditor.execute(occ.type.mutator(type));
		}
		else {
			if (type != null) {
				MapEditor.execute(new AddGridOccupant(parentMarker, x, z, type));
			}
		}
	}

	private void renderEditHandles(RenderingOptions opts, MapEditViewport view, Renderer renderer)
	{
		int posX = parentMarker.position.getX();
		int posY = parentMarker.position.getY() + 1;
		int posZ = parentMarker.position.getZ();
		int gridSize = gridSpacing.get();

		MarkerShader shader = ShaderManager.use(MarkerShader.class);
		shader.texture.bind(TextureManager.glMarkerTexID);

		RenderState.setPolygonMode(PolygonMode.FILL);

		GridOccupant[][] content = new GridOccupant[gridSizeX.get()][gridSizeZ.get()];
		for (GridOccupant occ : gridOccupants) {
			int i = occ.posX;
			int j = occ.posZ;
			if (i < gridSizeX.get() && j < gridSizeZ.get())
				content[i][j] = occ;
		}

		for (int i = 0; i < gridSizeX.get(); i++) {
			for (int j = 0; j < gridSizeZ.get(); j++) {
				GridOccupant occ = content[i][j];
				int dx = (gridSize / 2) + i * gridSize;
				int dz = (gridSize / 2) + j * gridSize;

				TransformMatrix mtx = TransformMatrix.identity();
				PresetColor color = PresetColor.WHITE;

				if (occ == null) {
					color = PresetColor.GREEN;
					mtx.scale(gridSize * 0.4, gridSize * 0.25, gridSize * 0.4);
					mtx.translate(dx, gridSize * 0.25, dz);
				}
				else if (occ.type.get() == OccupantType.Obstruction) {
					color = PresetColor.RED;
					mtx.scale(gridSize * 0.4, gridSize * 0.25, gridSize * 0.4);
					mtx.translate(dx, gridSize * 0.25, dz);
				}
				else if (occ.type.get() == OccupantType.Block) {
					color = PresetColor.DARK_BLUE;
					mtx.scale(gridSize * 0.5, gridSize * 0.5, gridSize * 0.5);
					mtx.translate(dx, gridSize * 0.25, dz);
				}

				mtx.translate(posX, posY, posZ);

				shader.color.set(color.r, color.g, color.b, 1.0f);
				Renderer.instance().renderTexturedCube(mtx);
			}
		}
	}

	private PickHit pickEditHandles(PickRay ray)
	{
		int posX = parentMarker.position.getX();
		int posY = parentMarker.position.getY() + 1;
		int posZ = parentMarker.position.getZ();
		int gridSize = gridSpacing.get();

		PickHit bestHit = new PickHit(ray);
		int bestX = -1;
		int bestZ = -1;

		for (int i = 0; i < gridSizeX.get(); i++) {
			for (int j = 0; j < gridSizeZ.get(); j++) {
				int dx = (gridSize / 2) + i * gridSize;
				int dz = (gridSize / 2) + j * gridSize;

				TransformMatrix mtx = TransformMatrix.identity();
				mtx.scale(gridSize * 0.4, gridSize * 0.25, gridSize * 0.4);
				mtx.translate(posX + dx, posY, posZ + dz);

				int sizeXZ = Math.round(gridSize * 0.4f);
				int sizeY = Math.round(gridSize * 0.5f);

				BoundingBox bb = new BoundingBox();
				bb.encompass(posX + dx - sizeXZ, posY, posZ + dz - sizeXZ);
				bb.encompass(posX + dx + sizeXZ, posY + sizeY, posZ + dz + sizeXZ);

				PickHit attempt = PickRay.getIntersection(ray, bb);

				if (attempt.dist < bestHit.dist) {
					bestHit = attempt;
					bestX = i;
					bestZ = j;
				}
			}
		}

		if (!bestHit.missed()) {
			GridOccupant occ = getOccupantAt(bestX, bestZ);
			if (occ == null) {
				setOccupantAt(bestX, bestZ, OccupantType.Obstruction);
			}
			else if (occ.type.get() == OccupantType.Obstruction) {
				setOccupantAt(bestX, bestZ, OccupantType.Block);
			}
			else if (occ.type.get() == OccupantType.Block) {
				setOccupantAt(bestX, bestZ, null);
			}

			if (parentMarker.isSelected())
				ray.preventSelectionChange = true;
		}

		return bestHit;
	}

	private void renderGrid(RenderingOptions opts, MapEditViewport view, Renderer renderer)
	{
		int posX = parentMarker.position.getX();
		int posY = parentMarker.position.getY() + 1;
		int posZ = parentMarker.position.getZ();
		int gridSize = gridSpacing.get();

		RenderState.setModelMatrix(null);

		for (GridOccupant occ : gridOccupants) {
			if (occ.type.get() == OccupantType.Block)
				RenderState.setColor(0.0f, 0.5f, 1.0f, 1.0f);
			else
				RenderState.setColor(PresetColor.RED);

			int x = occ.posX;
			int z = occ.posZ;

			TriangleRenderQueue.addQuad(
				TriangleRenderQueue.addVertex().setPosition(posX + gridSize * (x + 0), posY, posZ + gridSize * (z + 0)).getIndex(),
				TriangleRenderQueue.addVertex().setPosition(posX + gridSize * (x + 1), posY, posZ + gridSize * (z + 0)).getIndex(),
				TriangleRenderQueue.addVertex().setPosition(posX + gridSize * (x + 1), posY, posZ + gridSize * (z + 1)).getIndex(),
				TriangleRenderQueue.addVertex().setPosition(posX + gridSize * (x + 0), posY, posZ + gridSize * (z + 1)).getIndex());
		}

		RenderState.setPolygonMode(PolygonMode.FILL);
		TriangleRenderQueue.render(true);

		RenderState.setLineWidth(2.0f);
		RenderState.setColor(PresetColor.WHITE);

		for (int i = 0; i <= gridSizeX.get(); i++) {
			LineRenderQueue.addLine(
				LineRenderQueue.addVertex().setPosition(posX + gridSize * i, posY, posZ).getIndex(),
				LineRenderQueue.addVertex().setPosition(posX + gridSize * i, posY, posZ + gridSize * gridSizeZ.get()).getIndex());
		}
		for (int i = 0; i <= gridSizeZ.get(); i++) {
			LineRenderQueue.addLine(
				LineRenderQueue.addVertex().setPosition(posX, posY, posZ + gridSize * i).getIndex(),
				LineRenderQueue.addVertex().setPosition(posX + gridSize * gridSizeX.get(), posY, posZ + gridSize * i).getIndex());
		}

		RenderState.setDepthWrite(false);
		LineRenderQueue.render(true);
		RenderState.setDepthWrite(true);
	}

	@Override
	public void tick(double deltaTime)
	{
		buildCollisionMesh();
	}

	@Override
	public boolean hasCollision()
	{
		return true;
	}

	private void buildCollisionMesh()
	{
		IdentityArrayList<Triangle> triangles = parentMarker.collisionMesh.batch.triangles;
		triangles.clear();
		parentMarker.collisionAABB.clear();

		if (!EntityType.PushBlock.hasModel())
			return;

		for (GridOccupant occ : gridOccupants) {
			if (occ.type.get() == OccupantType.Block)
				addBlockGridCollisionMesh(triangles, occ.posX, occ.posZ);
		}
	}

	private void addBlockGridCollisionMesh(IdentityArrayList<Triangle> triangles, int n, int m)
	{
		int[] size = EntityType.PushBlock.typeData.collisionSize;
		int halfX = size[0] / 2;
		int halfZ = size[2] / 2;

		Vertex[][] box = new Vertex[2][4];
		box[0][0] = new Vertex(-halfX, 0, -halfZ);
		box[0][1] = new Vertex(halfX, 0, -halfZ);
		box[0][2] = new Vertex(halfX, 0, halfZ);
		box[0][3] = new Vertex(-halfX, 0, halfZ);

		box[1][0] = new Vertex(-halfX, size[1], -halfZ);
		box[1][1] = new Vertex(halfX, size[1], -halfZ);
		box[1][2] = new Vertex(halfX, size[1], halfZ);
		box[1][3] = new Vertex(-halfX, size[1], halfZ);

		triangles.add(new Triangle(box[0][0], box[0][1], box[0][2]));
		triangles.add(new Triangle(box[0][2], box[0][3], box[0][0]));

		triangles.add(new Triangle(box[1][1], box[1][0], box[1][2]));
		triangles.add(new Triangle(box[1][3], box[1][2], box[1][0]));

		triangles.add(new Triangle(box[0][1], box[0][0], box[1][1]));
		triangles.add(new Triangle(box[0][2], box[0][1], box[1][2]));
		triangles.add(new Triangle(box[0][3], box[0][2], box[1][3]));
		triangles.add(new Triangle(box[0][0], box[0][3], box[1][0]));

		triangles.add(new Triangle(box[0][0], box[1][0], box[1][1]));
		triangles.add(new Triangle(box[0][1], box[1][1], box[1][2]));
		triangles.add(new Triangle(box[0][2], box[1][2], box[1][3]));
		triangles.add(new Triangle(box[0][3], box[1][3], box[1][0]));

		float yawAngle = -(float) parentMarker.yaw.getAngle();
		int posX = parentMarker.position.getX() + (int) (gridSpacing.get() * (n + 0.5));
		int posY = parentMarker.position.getY();
		int posZ = parentMarker.position.getZ() + (int) (gridSpacing.get() * (m + 0.5));

		for (int i = 0; i < 2; i++)
			for (int j = 0; j < 4; j++)
				box[i][j].setPositon(Marker.transformLocalToWorld(box[i][j].getCurrentPos(), yawAngle, posX, posY, posZ));

		parentMarker.collisionAABB.encompass(box[0][0]);
		parentMarker.collisionAABB.encompass(box[1][2]);
	}

	@Override
	public PickHit trySelectionPick(PickRay ray)
	{
		assert (ray.channel == Channel.SELECTION);

		if (showEditHandles.get()) {
			PickHit editHit = pickEditHandles(ray);
			if (!editHit.missed())
				return editHit;
		}

		PickHit hit = new PickHit(ray);

		for (GridOccupant occ : gridOccupants) {
			if (occ.type.get() == OccupantType.Block) {
				PickHit attempt = EntityType.PushBlock.tryPick(ray, 0, 0,
					parentMarker.position.getX() + (int) (gridSpacing.get() * (occ.posX + 0.5)),
					parentMarker.position.getY(),
					parentMarker.position.getZ() + (int) (gridSpacing.get() * (occ.posZ + 0.5)));

				if (attempt.dist < hit.dist)
					hit = attempt;
			}
		}
		if (hit.missed())
			hit = PickRay.getIntersection(ray, parentMarker.AABB);

		return hit;
	}

	public static final class AddGridOccupant extends AbstractCommand
	{
		private final GridOccupant occ;

		public AddGridOccupant(Marker m, int x, int z, OccupantType type)
		{
			super("Add " + type);
			occ = new GridOccupant(m.gridComponent.gridOccupants, x, z, type);
		}

		@Override
		public void exec()
		{
			super.exec();
			occ.parentList.add(occ);
		}

		@Override
		public void undo()
		{
			super.undo();
			occ.parentList.remove(occ);
		}
	}

	public static final class RemoveGridOccupant extends AbstractCommand
	{
		private final GridOccupant occ;

		public RemoveGridOccupant(GridOccupant occ)
		{
			super("Remove " + occ.type.get());
			this.occ = occ;
		}

		@Override
		public void exec()
		{
			super.exec();
			occ.parentList.remove(occ);
		}

		@Override
		public void undo()
		{
			super.undo();
			occ.parentList.add(occ);
		}
	}

	public void addHeaderDefines(HeaderEntry h)
	{
		h.addDefine("GRID_PARAMS", String.format("%d, %d, %d, %d, %d, %d, NULL",
			gridIndex.get(), gridSizeX.get(), gridSizeZ.get(),
			parentMarker.position.getX(), parentMarker.position.getY(), parentMarker.position.getZ()));

		if (gridOccupants.size() > 0) {
			FormatStringList lines = new FormatStringList();

			int sizeX = gridSizeX.get();
			int sizeZ = gridSizeZ.get();
			int[][] grid = new int[sizeX][sizeZ];
			for (int i = 0; i < sizeX; i++)
				for (int j = 0; j < sizeZ; j++)
					grid[i][j] = 0;

			for (GridOccupant occ : gridOccupants) {
				grid[occ.posX][occ.posZ] = occ.type.get().id;
			}

			// optimize generated script size using FillPushBlockZ
			// find consecutive sequences of occupants as X varies
			for (int j = 0; j < sizeZ; j++) {
				for (int i = 0; i < sizeX; i++) {
					int cur = grid[i][j];
					if (cur == 0)
						continue;

					int end = i;
					for (int k = i + 1; k < sizeX; k++) {
						if (cur != grid[k][j]) {
							break;
						}
						end = k;
					}

					String occupant;
					if (cur == 1)
						occupant = "PUSH_GRID_BLOCK";
					else
						occupant = "PUSH_GRID_OBSTRUCTION";

					if (end != i) {
						lines.addf("    Call(FillPushBlockZ, %d, %d, %d, %d, %s)",
							gridIndex.get(), j, i, end, occupant);
					}
					else {
						lines.addf("    Call(SetPushBlock, %d, %d, %d, %s)",
							gridIndex.get(), i, j, occupant);
					}
					i = end;
				}
			}
			h.addDefine("GRID_CONTENT", lines);
		}
		else {
			h.addDefine("GRID_CONTENT", Collections.singletonList("Set(LVar0, LVar0)")); // 'NOP'
		}
	}
}
