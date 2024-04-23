package game.map.marker;

import static game.map.MapKey.*;

import java.util.Collection;
import java.util.function.Consumer;

import org.w3c.dom.Element;

import game.entity.EntityInfo.EntityParam;
import game.entity.EntityInfo.EntityType;
import game.entity.EntityInfo.ShadowType;
import game.map.MapKey;
import game.map.editor.camera.MapEditViewport;
import game.map.editor.commands.fields.EditableField;
import game.map.editor.commands.fields.EditableField.EditableFieldFactory;
import game.map.editor.render.Renderer;
import game.map.editor.render.RenderingOptions;
import game.map.editor.render.ShadowRenderer.RenderableShadow;
import game.map.editor.render.SortedRenderable;
import game.map.editor.selection.PickRay;
import game.map.editor.selection.PickRay.Channel;
import game.map.editor.selection.PickRay.PickHit;
import game.map.editor.ui.info.MarkerInfoPanel;
import game.map.mesh.Triangle;
import game.map.mesh.Vertex;
import game.map.scripts.extract.HeaderEntry;
import game.map.scripts.extract.HeaderEntry.HeaderParseException;
import game.map.scripts.extract.entity.ArrowSign;
import game.map.scripts.extract.entity.BasicEntity;
import game.map.scripts.extract.entity.BlueSwitch;
import game.map.scripts.extract.entity.BlueWarpPipe;
import game.map.scripts.extract.entity.Chest;
import game.map.scripts.extract.entity.CoinBlock;
import game.map.scripts.extract.entity.ExtractedEntity;
import game.map.scripts.extract.entity.HeartBlock;
import game.map.scripts.extract.entity.HiddenPanel;
import game.map.scripts.extract.entity.ItemBlock;
import game.map.scripts.extract.entity.ItemEntity;
import game.map.scripts.extract.entity.OptionalScriptEntity;
import game.map.scripts.extract.entity.SimpleSpring;
import game.map.scripts.extract.entity.SpinningFlower;
import game.map.scripts.extract.entity.SuperBlock;
import game.map.scripts.extract.entity.Tweester;
import game.map.scripts.extract.entity.WoodenCrate;
import util.identity.IdentityArrayList;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class EntityComponent extends BaseMarkerComponent
{
	private final Consumer<Object> notifyCallback = (o) -> {
		parentMarker.updateListeners(MarkerInfoPanel.tag_EntityTab);
	};

	public EntityComponent(Marker parent)
	{
		super(parent);
	}

	//TODO refactor this to be String type
	public EditableField<EntityType> type = EditableFieldFactory.create(EntityType.YellowBlock)
		.setCallback(notifyCallback).setName("Set Entity Type").build();

	public EditableField<String> itemName = EditableFieldFactory.create("ITEM_NONE")
		.setCallback(notifyCallback).setName("Set Item").build();

	public EditableField<String> gameFlagName = EditableFieldFactory.create("")
		.setCallback(notifyCallback).setName("Set Game Flag").build();

	public EditableField<String> areaFlagName = EditableFieldFactory.create("")
		.setCallback(notifyCallback).setName("Set Area Flag").build();

	public EditableField<String> scriptName = EditableFieldFactory.create("")
		.setCallback(notifyCallback).setName("Set Script Name").build();

	public EditableField<Integer> index = EditableFieldFactory.create(0)
		.setCallback(notifyCallback).setName("Set Entity Index").build();

	public EditableField<Integer> style = EditableFieldFactory.create(0)
		.setCallback(notifyCallback).setName("Set Entity Style").build();

	public EditableField<String> modelName = EditableFieldFactory.create("")
		.setCallback(notifyCallback).setName("Set Model").build();

	public EditableField<String> colliderName = EditableFieldFactory.create("")
		.setCallback(notifyCallback).setName("Set Collider").build();

	public EditableField<String> targetName = EditableFieldFactory.create("")
		.setCallback(notifyCallback).setName("Set Target").build();

	public EditableField<String> entryName = EditableFieldFactory.create("")
		.setCallback(notifyCallback).setName("Set Entry Name").build();

	public EditableField<Integer> angle = EditableFieldFactory.create(0)
		.setCallback(notifyCallback).setName("Set Angle").build();

	public EditableField<Integer> launchDist = EditableFieldFactory.create(60)
		.setCallback(notifyCallback).setName("Set Launch Dist").build();

	public EditableField<String> mapVarName = EditableFieldFactory.create("")
		.setCallback(notifyCallback).setName("Set Map Variable").build();

	public EditableField<String> spawnMode = EditableFieldFactory.create("")
		.setCallback(notifyCallback).setName("Set Item Spawn Mode").build();

	public EditableField<String> pathsName = EditableFieldFactory.create("TweesterPaths")
		.setCallback(notifyCallback).setName("Set Tweester Paths").build();

	@Override
	public EntityComponent deepCopy(Marker copyParent)
	{
		EntityComponent copy = new EntityComponent(copyParent);

		copy.type.copy(type);

		copy.itemName.copy(itemName);
		copy.gameFlagName.copy(gameFlagName);
		copy.scriptName.copy(scriptName);

		copy.index.copy(index);
		copy.style.copy(style);

		copy.modelName.copy(modelName);
		copy.colliderName.copy(colliderName);
		copy.targetName.copy(targetName);
		copy.entryName.copy(entryName);

		copy.angle.copy(angle);
		copy.launchDist.copy(launchDist);

		copy.mapVarName.copy(mapVarName);
		copy.spawnMode.copy(spawnMode);
		copy.pathsName.copy(pathsName);

		return copy;
	}

	private void writeStringField(XmlWriter xmw, XmlTag entityTag, MapKey key, EditableField<String> field)
	{
		if (type.get().hasParam(key)) {
			EntityParam param = type.get().getParam(key);
			String value = field.get();

			if (param.required || field.isEnabled())
				xmw.addAttribute(entityTag, key, value);
		}
	}

	private void readStringField(XmlReader xmr, Element entityElem, MapKey key, EditableField<String> field)
	{
		if (type.get().hasParam(key)) {
			EntityParam param = type.get().getParam(key);

			field.setEnabled(param.required);

			if (xmr.hasAttribute(entityElem, key)) {
				field.set(xmr.getAttribute(entityElem, key));
				field.setEnabled(true);
			}
		}
	}

	private void writeIntegerField(XmlWriter xmw, XmlTag entityTag, MapKey key, EditableField<Integer> field)
	{
		if (type.get().hasParam(key)) {
			EntityParam param = type.get().getParam(key);
			Integer value = field.get();

			if (param.required || field.isEnabled())
				xmw.addInt(entityTag, key, value);
		}
	}

	private void readIntegerField(XmlReader xmr, Element entityElem, MapKey key, EditableField<Integer> field)
	{
		if (type.get().hasParam(key)) {
			EntityParam param = type.get().getParam(key);

			field.setEnabled(param.required);

			if (xmr.hasAttribute(entityElem, key)) {
				field.set(xmr.readInt(entityElem, key));
				field.setEnabled(true);
			}
		}
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag entityTag = xmw.createTag(TAG_ENTITY, true);
		xmw.addAttribute(entityTag, ATTR_TYPE, type.get().name);

		// only deserialize fields belonging to type

		writeStringField(xmw, entityTag, ATTR_NTT_ITEM, itemName);
		writeStringField(xmw, entityTag, ATTR_NTT_GAME_FLAG, gameFlagName);
		writeStringField(xmw, entityTag, ATTR_NTT_AREA_FLAG, areaFlagName);
		writeStringField(xmw, entityTag, ATTR_NTT_SCRIPT, scriptName);

		writeIntegerField(xmw, entityTag, ATTR_NTT_INDEX, index);
		writeIntegerField(xmw, entityTag, ATTR_NTT_STYLE, style);

		writeStringField(xmw, entityTag, ATTR_NTT_MODEL, modelName);
		writeStringField(xmw, entityTag, ATTR_NTT_COLLIDER, colliderName);
		writeStringField(xmw, entityTag, ATTR_NTT_TARGET, targetName);
		writeStringField(xmw, entityTag, ATTR_NTT_ENTRY, entryName);

		writeIntegerField(xmw, entityTag, ATTR_NTT_ANGLE, angle);
		writeIntegerField(xmw, entityTag, ATTR_NTT_LAUNCH_DIST, launchDist);

		writeStringField(xmw, entityTag, ATTR_NTT_MAP_VAR, mapVarName);
		writeStringField(xmw, entityTag, ATTR_NTT_SPAWN_MODE, spawnMode);
		writeStringField(xmw, entityTag, ATTR_NTT_PATHS, pathsName);

		xmw.printTag(entityTag);
	}

	@Override
	public void fromXML(XmlReader xmr, Element markerElem)
	{
		Element entityElem = xmr.getUniqueRequiredTag(markerElem, TAG_ENTITY);

		xmr.requiresAttribute(entityElem, ATTR_TYPE);
		type.set(EntityType.valueOf(xmr.getAttribute(entityElem, ATTR_TYPE)));

		// only serialize fields belonging to type

		readStringField(xmr, entityElem, ATTR_NTT_ITEM, itemName);
		readStringField(xmr, entityElem, ATTR_NTT_GAME_FLAG, gameFlagName);
		readStringField(xmr, entityElem, ATTR_NTT_AREA_FLAG, areaFlagName);
		readStringField(xmr, entityElem, ATTR_NTT_SCRIPT, scriptName);

		readIntegerField(xmr, entityElem, ATTR_NTT_INDEX, index);
		readIntegerField(xmr, entityElem, ATTR_NTT_STYLE, style);

		readStringField(xmr, entityElem, ATTR_NTT_MODEL, modelName);
		readStringField(xmr, entityElem, ATTR_NTT_COLLIDER, colliderName);
		readStringField(xmr, entityElem, ATTR_NTT_TARGET, targetName);
		readStringField(xmr, entityElem, ATTR_NTT_ENTRY, entryName);

		readIntegerField(xmr, entityElem, ATTR_NTT_ANGLE, angle);
		readIntegerField(xmr, entityElem, ATTR_NTT_LAUNCH_DIST, launchDist);

		readStringField(xmr, entityElem, ATTR_NTT_MAP_VAR, mapVarName);
		readStringField(xmr, entityElem, ATTR_NTT_SPAWN_MODE, spawnMode);
		readStringField(xmr, entityElem, ATTR_NTT_PATHS, pathsName);
	}

	@Override
	public void tick(double deltaTime)
	{
		buildCollisionMesh();
	}

	@Override
	public boolean hasCollision()
	{
		return (type.get() != EntityType.Item);
	}

	private void buildCollisionMesh()
	{
		EntityType entity = type.get();

		if (entity == null || entity.typeData == null)
			return;

		IdentityArrayList<Triangle> triangles = parentMarker.collisionMesh.batch.triangles;
		triangles.clear();
		parentMarker.collisionAABB.clear();

		int[] size = entity.typeData.collisionSize;
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
		int posX = parentMarker.position.getX();
		int posY = parentMarker.position.getY();
		int posZ = parentMarker.position.getZ();

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

		if (type.get() == null || !type.get().hasModel())
			return super.trySelectionPick(ray);

		return type.get().tryPick(ray, angle.get() - 90,
			(float) parentMarker.yaw.getAngle(),
			parentMarker.position.getX(),
			parentMarker.position.getY(),
			parentMarker.position.getZ());
	}

	@Override
	public void addRenderables(RenderingOptions opts, Collection<SortedRenderable> renderables, PickHit shadowHit)
	{
		EntityType entityType = type.get();
		if (entityType != null) {
			entityType.addRenderables(renderables, parentMarker.selected,
				parentMarker.position.getX(), parentMarker.position.getY(), parentMarker.position.getZ(),
				(float) parentMarker.yaw.getAngle(), angle.get());

			if (entityType.shadowType == ShadowType.SQUARE_SHADOW) {
				if (entityType.typeData != null && shadowHit != null && shadowHit.dist < Float.MAX_VALUE)
					renderables.add(new RenderableShadow(
						shadowHit.point, shadowHit.norm, shadowHit.dist,
						false, false, 5.0f * entityType.typeData.collisionSize[0])); // = 100.0 * (size / 20.0)
			}
			else if (entityType.shadowType == ShadowType.ROUND_SHADOW) {
				if (entityType.typeData != null && shadowHit != null && shadowHit.dist < Float.MAX_VALUE)
					renderables.add(new RenderableShadow(
						shadowHit.point, shadowHit.norm, shadowHit.dist,
						false, true, 5.0f * entityType.typeData.collisionSize[0])); // = 100.0 * (size / 20.0)
			}
		}
	}

	@Override
	public void render(RenderingOptions opts, MapEditViewport view, Renderer renderer)
	{
		if (!type.get().hasModel())
			parentMarker.renderCube(opts, view, renderer);
		parentMarker.renderDirectionIndicator(opts, view, renderer);

		if (opts.showEntityCollision) {
			type.get().renderCollision(parentMarker.selected, (float) parentMarker.yaw.getAngle(),
				parentMarker.position.getX(), parentMarker.position.getY(), parentMarker.position.getZ());
		}
	}

	public void addHeaderDefines(HeaderEntry h)
	{
		ExtractedEntity e = null;

		switch (type.get()) {
			case SavePoint:
			case Padlock:
			case PadlockRedFrame:
			case PadlockRedFace:
			case PadlockBlueFace:
			case CymbalPlant:
			case PinkFlower:
			case BellbellPlant:
			case TrumpetPlant:
			case Munchlesia:
				e = new BasicEntity(parentMarker);
				break;

			case Hammer1Block:
			case Hammer1BlockWideX:
			case Hammer1BlockWideZ:
			case Hammer1BlockTiny:
			case Hammer2Block:
			case Hammer2BlockWideX:
			case Hammer2BlockWideZ:
			case Hammer2BlockTiny:
			case Hammer3Block:
			case Hammer3BlockWideX:
			case Hammer3BlockWideZ:
			case Hammer3BlockTiny:
			case BombableRock:
			case BombableRockWide:
			case Signpost:
			case BoardedFloor:
			case ScriptSpring:
			case StarBoxLauncher:
			case RedSwitch:
			case GreenStompSwitch:
			case BrickBlock:
			case TriggerBlock:
			case InertYellowBlock:
			case PowBlock:
				e = new OptionalScriptEntity(parentMarker);
				break;

			case ArrowSign:
				e = new ArrowSign(parentMarker);
				break;

			case BlueSwitch:
			case HugeBlueSwitch:
				e = new BlueSwitch(parentMarker);
				break;

			case BlueWarpPipe:
				e = new BlueWarpPipe(parentMarker);
				break;

			case Chest:
			case GiantChest:
				e = new Chest(parentMarker);
				break;

			case MulticoinBlock:
				e = new CoinBlock(parentMarker);
				break;

			case HeartBlock:
				e = new HeartBlock(parentMarker);
				break;

			case YellowBlock:
			case HiddenYellowBlock:
			case RedBlock:
			case HiddenRedBlock:
				e = new ItemBlock(parentMarker);
				break;

			case Item:
				e = new ItemEntity(parentMarker);
				break;

			case SimpleSpring:
				e = new SimpleSpring(parentMarker);
				break;

			case SpinningFlower:
				e = new SpinningFlower(parentMarker);
				break;

			case Tweester:
				e = new Tweester(parentMarker);
				break;

			case SuperBlock:
				e = new SuperBlock(parentMarker);
				break;

			case WoodenCrate:
				e = new WoodenCrate(parentMarker);
				break;

			case HiddenPanel:
				e = new HiddenPanel(parentMarker);
				break;

			case PushBlock:
				break;
		}

		if (e != null)
			e.addHeaderDefines(h);
	}

	public void fromHeader(HeaderEntry h) throws HeaderParseException
	{
		String[] subtype = h.getProperty("type").split(":");

		if (subtype.length != 3)
			throw new HeaderParseException("Entity HeaderEntry is missing subtype!");

		try {
			type.set(EntityType.valueOf(subtype[2]));
		}
		catch (Exception e) {
			throw new HeaderParseException("Could not parse Entity type: " + subtype[2]);
		}

		ExtractedEntity e = null;

		switch (type.get()) {
			case SavePoint:
			case Padlock:
			case PadlockRedFrame:
			case PadlockRedFace:
			case PadlockBlueFace:
			case CymbalPlant:
			case PinkFlower:
			case BellbellPlant:
			case TrumpetPlant:
			case Munchlesia:
				e = new BasicEntity(parentMarker);
				break;

			case Hammer1Block:
			case Hammer1BlockWideX:
			case Hammer1BlockWideZ:
			case Hammer1BlockTiny:
			case Hammer2Block:
			case Hammer2BlockWideX:
			case Hammer2BlockWideZ:
			case Hammer2BlockTiny:
			case Hammer3Block:
			case Hammer3BlockWideX:
			case Hammer3BlockWideZ:
			case Hammer3BlockTiny:
			case BombableRock:
			case BombableRockWide:
			case Signpost:
			case BoardedFloor:
			case ScriptSpring:
			case StarBoxLauncher:
			case RedSwitch:
			case GreenStompSwitch:
			case BrickBlock:
			case TriggerBlock:
			case InertYellowBlock:
			case PowBlock:
				e = new OptionalScriptEntity(parentMarker);
				break;

			case ArrowSign:
				e = new ArrowSign(parentMarker);
				break;

			case BlueSwitch:
			case HugeBlueSwitch:
				e = new BlueSwitch(parentMarker);
				break;

			case BlueWarpPipe:
				e = new BlueWarpPipe(parentMarker);
				break;

			case Chest:
			case GiantChest:
				e = new Chest(parentMarker);
				break;

			case MulticoinBlock:
				e = new CoinBlock(parentMarker);
				break;

			case HeartBlock:
				e = new HeartBlock(parentMarker);
				break;

			case YellowBlock:
			case HiddenYellowBlock:
			case RedBlock:
			case HiddenRedBlock:
				e = new ItemBlock(parentMarker);
				break;

			case Item:
				e = new ItemEntity(parentMarker);
				break;

			case SimpleSpring:
				e = new SimpleSpring(parentMarker);
				break;

			case SpinningFlower:
				e = new SpinningFlower(parentMarker);
				break;

			case Tweester:
				e = new Tweester(parentMarker);
				break;

			case SuperBlock:
				e = new SuperBlock(parentMarker);
				break;

			case WoodenCrate:
				e = new WoodenCrate(parentMarker);
				break;

			case HiddenPanel:
				e = new HiddenPanel(parentMarker);
				break;

			case PushBlock:
				break;
		}

		if (e != null) {
			e.parseHeaderDefines(parentMarker, h);
		}
	}
}
