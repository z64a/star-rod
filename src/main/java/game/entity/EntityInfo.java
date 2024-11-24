package game.entity;

import static app.Directories.DUMP_ENTITY_SRC;
import static game.entity.EntityInfo.EntityParam.*;
import static game.entity.EntityInfo.ShadowType.*;
import static game.entity.EntityMenuGroup.*;
import static game.entity.EntitySet.*;
import static game.map.MapKey.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.HashMap;

import app.Environment;
import app.Resource;
import app.Resource.ResourceType;
import app.input.InvalidInputException;
import common.Vector3f;
import game.DataUtils;
import game.entity.EntityModel.RenderablePart;
import game.map.Axis;
import game.map.BoundingBox;
import game.map.MapKey;
import game.map.editor.render.SortedRenderable;
import game.map.editor.selection.PickRay;
import game.map.editor.selection.PickRay.PickHit;
import game.map.marker.Marker;
import game.map.shape.TransformMatrix;
import renderer.shaders.RenderState;
import renderer.shaders.ShaderManager;
import renderer.shaders.scene.LineShader;
import util.Logger;

public abstract class EntityInfo
{
	private static HashMap<String, EntityType> typeNameMap = new HashMap<>();

	// @formatter:off
	public static enum ShadowType
	{
		NO_SHADOW,
		ROUND_SHADOW,
		SQUARE_SHADOW
	};

	public static enum EntityParam
	{
		OptionalItem		(ATTR_NTT_ITEM, false),
		RequiredItem		(ATTR_NTT_ITEM, true),

		OptionalGameFlag	(ATTR_NTT_GAME_FLAG, false),
		RequiredGameFlag	(ATTR_NTT_GAME_FLAG, true),

		OptionalAreaFlag	(ATTR_NTT_AREA_FLAG, false),
		RequiredAreaFlag	(ATTR_NTT_AREA_FLAG, true),

		OptionalScript		(ATTR_NTT_SCRIPT, false),
		RequiredScript		(ATTR_NTT_SCRIPT, true),

		OptionalIndex		(ATTR_NTT_INDEX, false),
		OptionalStyle		(ATTR_NTT_STYLE, false),

		OptionalModel		(ATTR_NTT_MODEL, false),
		RequiredModel		(ATTR_NTT_MODEL, true),

		OptionalCollider	(ATTR_NTT_COLLIDER, false),
		RequiredCollider	(ATTR_NTT_COLLIDER, true),

		OptionalTarget		(ATTR_NTT_TARGET, false),

		RequiredEntry		(ATTR_NTT_ENTRY, true),

		RequiredMapVar		(ATTR_NTT_MAP_VAR, false),

		RequiredAngle		(ATTR_NTT_ANGLE, true),

		RequiredPaths		(ATTR_NTT_PATHS, true),

		RequiredLaunchDist	(ATTR_NTT_LAUNCH_DIST, true),

		RequiredSpawnMode	(ATTR_NTT_SPAWN_MODE, true);

		public final String name;
		public final boolean required;

		private EntityParam(MapKey key, boolean required)
		{
			this.name = key.toString();
			this.required = required;
		}
	}

	public static enum EntityType
	{
		BoardedFloor			(OVERLAY_STANDARD, OtherAreas, 0x802BCE84, NO_SHADOW, OptionalScript), // FIELD_FLAG | FIELD_COLLIDER | FIELD_MODEL),
		BombableRock			(OVERLAY_STANDARD, OtherAreas, 0x802BCF00, NO_SHADOW, OptionalScript), // FIELD_FLAG | FIELD_COLLIDER),
		BombableRockWide		(OVERLAY_STANDARD, OtherAreas, 0x802BCF24, NO_SHADOW, OptionalScript), // FIELD_FLAG | FIELD_COLLIDER),
		Padlock					(OVERLAY_STANDARD, OtherAreas, 0x802BCD68, NO_SHADOW), // FIELD_FLAG | FIELD_ITEM | FIELD_MAP_VAR, OptionalItem),
		PadlockRedFrame			(OVERLAY_STANDARD, OtherAreas, 0x802BCD8C, NO_SHADOW), // FIELD_FLAG | FIELD_ITEM | FIELD_MAP_VAR, OptionalItem),
		PadlockRedFace			(OVERLAY_STANDARD, OtherAreas, 0x802BCDB0, NO_SHADOW), // FIELD_FLAG | FIELD_ITEM | FIELD_MAP_VAR, OptionalItem),
		PadlockBlueFace			(OVERLAY_STANDARD, OtherAreas, 0x802BCDD4, NO_SHADOW), // FIELD_FLAG | FIELD_ITEM | FIELD_MAP_VAR, OptionalItem),

		CymbalPlant				(OVERLAY_JUNGLE_RUGGED, JanIwaOnly, 0x802BC788, NO_SHADOW),
		PinkFlower				(OVERLAY_JUNGLE_RUGGED, JanIwaOnly, 0x802BC7AC, NO_SHADOW),
		SpinningFlower			(OVERLAY_JUNGLE_RUGGED, JanIwaOnly, 0x802BC7F4, NO_SHADOW, OptionalTarget),
		BellbellPlant			(OVERLAY_JUNGLE_RUGGED, JanIwaOnly, 0x802BCBD8, NO_SHADOW),
		TrumpetPlant			(OVERLAY_JUNGLE_RUGGED, JanIwaOnly, 0x802BCBFC, NO_SHADOW),
		Munchlesia				(OVERLAY_JUNGLE_RUGGED, JanIwaOnly, 0x802BCC20, NO_SHADOW),
		ArrowSign				(OVERLAY_JUNGLE_RUGGED, JanIwaOnly, 0x802BCD9C, NO_SHADOW, RequiredAngle),

		Tweester				(OVERLAY_TOYBOX_DESERT, SbkOmoOnly, 0x802BCA74, NO_SHADOW, RequiredPaths), // FIELD_DEST_MAP | FIELD_DEST_ENTRY | FIELD_PATH_MARKER),
		StarBoxLauncher			(OVERLAY_TOYBOX_DESERT, SbkOmoOnly, 0x802BCB44, NO_SHADOW, OptionalScript), // FIELD_TARGET_MARKER),

		SavePoint				(COMMON, Block, 0x802E9A18, SQUARE_SHADOW),
		HeartBlock				(COMMON, Block, 0x802EA7E0, SQUARE_SHADOW, OptionalStyle),
		SuperBlock				(COMMON, Block, 0x802EA910, SQUARE_SHADOW, RequiredGameFlag, RequiredMapVar),
		BrickBlock				(COMMON, Block, 0x802EA0C4, SQUARE_SHADOW, OptionalScript),
		MulticoinBlock			(COMMON, Block, 0x802EA0E8, SQUARE_SHADOW, OptionalGameFlag),
		YellowBlock				(COMMON, Block, 0x802EA564, SQUARE_SHADOW, RequiredItem, OptionalGameFlag, OptionalScript), // FIELD_FLAG | FIELD_ITEM | FIELD_HAS_SCRIPT),
		HiddenYellowBlock		(COMMON, Block, 0x802EA588, SQUARE_SHADOW, RequiredItem, OptionalGameFlag, OptionalScript), //  FIELD_FLAG | FIELD_ITEM | FIELD_HAS_SCRIPT),
		RedBlock				(COMMON, Block, 0x802EA5AC, SQUARE_SHADOW, RequiredItem, OptionalGameFlag, OptionalScript), //  FIELD_FLAG | FIELD_ITEM | FIELD_HAS_SCRIPT),
		HiddenRedBlock			(COMMON, Block, 0x802EA5D0, SQUARE_SHADOW, RequiredItem, OptionalGameFlag, OptionalScript), //  FIELD_FLAG | FIELD_ITEM | FIELD_HAS_SCRIPT),

		Item					(DUMMY, Misc, -1, ROUND_SHADOW, RequiredItem, OptionalGameFlag, RequiredSpawnMode), //FIELD_FLAG | FIELD_ITEM | FIELD_ITEM_SPAWN), // logical entity, not actual one
		Chest					(COMMON, Misc, 0x802EAE30, NO_SHADOW, OptionalGameFlag, OptionalScript), // FIELD_FLAG | FIELD_ITEM),
		GiantChest				(COMMON, Misc, 0x802EAE0C, NO_SHADOW, OptionalGameFlag, OptionalScript), // FIELD_FLAG | FIELD_ITEM),
		WoodenCrate				(COMMON, Misc, 0x802EAED4, NO_SHADOW, RequiredItem, OptionalGameFlag), // FIELD_FLAG | FIELD_ITEM | FIELD_HAS_SCRIPT | FIELD_HAS_ITEM),
		HiddenPanel				(COMMON, Misc, 0x802EAB04, NO_SHADOW, RequiredModel, OptionalGameFlag),
		Signpost				(COMMON, Misc, 0x802EAFDC, NO_SHADOW, OptionalScript),
		SimpleSpring			(COMMON, Misc, 0x802EAA54, NO_SHADOW, RequiredLaunchDist),
		ScriptSpring			(COMMON, Misc, 0x802EAA30, NO_SHADOW, OptionalScript, OptionalTarget), // FIELD_TARGET_MARKER | FIELD_LAUNCH_T),
		BlueWarpPipe			(COMMON, Misc, 0x802EAF80, NO_SHADOW, RequiredEntry, RequiredScript, RequiredGameFlag), // FIELD_FLAG | FIELD_PIPE_ENTRY | FIELD_DEST_MAP | FIELD_DEST_ENTRY | FIELD_HAS_AREA_FLAG | FIELD_AREA_FLAG),
		PushBlock				(COMMON, Hidden, 0x802EA2BC, NO_SHADOW), // logical entity which cannot normally be created

		RedSwitch				(COMMON, Mechanism, 0x802E9BB0, NO_SHADOW, OptionalScript),
		BlueSwitch				(COMMON, Mechanism, 0x802E9BD4, NO_SHADOW, OptionalAreaFlag, OptionalIndex), // FIELD_HAS_SCRIPT | FIELD_AREA_FLAG | FIELD_HAS_SPAWN_FLAG | FIELD_SPAWN_FLAG),
		HugeBlueSwitch			(COMMON, Mechanism, 0x802E9BF8, NO_SHADOW, OptionalAreaFlag, OptionalIndex), // FIELD_HAS_SCRIPT | FIELD_AREA_FLAG | FIELD_HAS_SPAWN_FLAG | FIELD_SPAWN_FLAG),
		GreenStompSwitch		(COMMON, Mechanism, 0x802E9C1C, NO_SHADOW, OptionalScript), // FIELD_HAS_SCRIPT | FIELD_AREA_FLAG | FIELD_HAS_SPAWN_FLAG | FIELD_SPAWN_FLAG),
		TriggerBlock			(COMMON, Mechanism, 0x802EA5F4, NO_SHADOW, OptionalScript, OptionalGameFlag),
		InertYellowBlock		(COMMON, Mechanism, 0x802EA07C, NO_SHADOW, OptionalScript),
		PowBlock				(COMMON, Mechanism, 0x802EA2E0, SQUARE_SHADOW, OptionalScript),

		Hammer1Block			(COMMON, HammerBlock, 0x802EA10C, NO_SHADOW, OptionalScript, OptionalCollider),
		Hammer1BlockWideX		(COMMON, HammerBlock, 0x802EA130, NO_SHADOW, OptionalScript, OptionalCollider),
		Hammer1BlockWideZ		(COMMON, HammerBlock, 0x802EA154, NO_SHADOW, OptionalScript, OptionalCollider),
		Hammer1BlockTiny		(COMMON, HammerBlock, 0x802EA178, NO_SHADOW, OptionalScript, OptionalCollider),
		Hammer2Block			(COMMON, HammerBlock, 0x802EA19C, NO_SHADOW, OptionalScript, OptionalCollider),
		Hammer2BlockWideX		(COMMON, HammerBlock, 0x802EA1C0, NO_SHADOW, OptionalScript, OptionalCollider),
		Hammer2BlockWideZ		(COMMON, HammerBlock, 0x802EA1E4, NO_SHADOW, OptionalScript, OptionalCollider),
		Hammer2BlockTiny		(COMMON, HammerBlock, 0x802EA208, NO_SHADOW, OptionalScript, OptionalCollider),
		Hammer3Block			(COMMON, HammerBlock, 0x802EA22C, NO_SHADOW, OptionalScript, OptionalCollider),
		Hammer3BlockWideX		(COMMON, HammerBlock, 0x802EA250, NO_SHADOW, OptionalScript, OptionalCollider),
		Hammer3BlockWideZ		(COMMON, HammerBlock, 0x802EA274, NO_SHADOW, OptionalScript, OptionalCollider),
		Hammer3BlockTiny		(COMMON, HammerBlock, 0x802EA298, NO_SHADOW, OptionalScript, OptionalCollider);
		// @formatter:on

		public final EntitySet set;
		public final EntityMenuGroup menuGroup;
		public final int addr;
		public final String name;
		public final ShadowType shadowType;
		private final EntityParam[] params;

		private EntityModel model = null;
		public EntityTypeData typeData = null;

		private EntityType(EntitySet set, EntityMenuGroup menuGroup, int addr, ShadowType shadowType, EntityParam ... fields)
		{
			this.set = set;
			this.menuGroup = menuGroup;
			this.addr = addr;
			this.shadowType = shadowType;
			this.params = fields;

			this.name = name();
			typeNameMap.put(name, this);
		}

		public boolean hasModel()
		{
			return (model != null);
		}

		public void renderCollision(boolean selected, float yaw, int x, int y, int z)
		{
			if (typeData == null)
				return;

			RenderState.pushModelMatrix();
			TransformMatrix mtx = TransformMatrix.identity();
			mtx.rotate(Axis.Y, yaw);
			mtx.translate(x, y, z);

			ShaderManager.use(LineShader.class);

			if (selected)
				typeData.collisionBox.renderNow(mtx, 0.0f, 1.0f, 0.0f, 2.0f);
			else
				typeData.collisionBox.renderNow(mtx, 0.0f, 1.0f, 1.0f, 2.0f);

			RenderState.popModelMatrix();
		}

		public PickHit tryPick(PickRay ray, float modifier, float yaw, float x, float y, float z)
		{
			// inverse transform pick ray to local space
			Vector3f origin = Marker.transformWorldToLocal(ray.origin, -yaw, -x, -y, -z);
			Vector3f direction = Marker.transformWorldToLocal(ray.direction, -yaw, 0, 0, 0);
			PickRay newRay = new PickRay(ray.channel, origin, direction);

			PickHit hit = new PickHit(newRay);

			if (model == null)
				return hit;

			hit = model.tryPick(newRay, this, modifier);

			if (!hit.missed() && hit.norm != null)
				hit.norm = Marker.transformWorldToLocal(hit.norm, yaw, 0, 0, 0);

			return hit;
		}

		static {
			for (String line : Resource.getTextInput(ResourceType.EntityModelRoots, "EntityTypeData.txt", false)) {
				try {
					EntityTypeData data = new EntityTypeData(line);
					typeNameMap.get(data.name).typeData = data;
				}
				catch (InvalidInputException e) {
					Logger.printStackTrace(e);
				}
			}
		}

		public void addRenderables(Collection<SortedRenderable> renderables,
			boolean selected, float x, float y, float z, float yaw, float modifier)
		{
			if (!hasModel())
				return;

			for (int i = 0; i < model.parts.size(); i++)
				renderables.add(new RenderablePart(model, i, selected, x, y, z, yaw, modifier));
		}

		public boolean hasParam(MapKey key)
		{
			for (EntityParam param : params) {
				if (param.name.equals(key.toString())) {
					return true;
				}
			}
			return false;
		}

		public EntityParam getParam(MapKey key)
		{
			for (EntityParam param : params) {
				if (param.name.equals(key.toString())) {
					return param;
				}
			}
			return null;
		}
	}

	public static void loadModels()
	{
		for (EntityType type : EntityType.values()) {
			if (type.model != null)
				type.model.freeTextures();

			if (type.set == EntitySet.DUMMY || type == EntityType.Munchlesia)
				continue; // these types dont have models

			try {
				type.model = new EntityModel(type);
				type.model.readFromObj(DUMP_ENTITY_SRC + type.name + "/", true);
				type.model.loadTextures();
			}
			catch (Exception e) {
				type.model = null;
				Logger.printStackTrace(e);
			}
		}
	}

	public static class EntityTypeData
	{
		public final String name;
		public final int address;
		public final int offset;

		public final int[] pointers = new int[5];

		public final int[][] dmaArgs = new int[2][2];

		public final int flags;
		public final int bufferSize;
		public final int typeID;
		public final int[] collisionSize = new int[3];
		public final BoundingBox collisionBox;

		public EntityTypeData(EntityType type, ByteBuffer fileBuffer)
		{
			this.name = type.name;
			this.address = type.addr;
			this.offset = type.set.toOffset(type.addr);

			fileBuffer.position(offset);
			flags = fileBuffer.getShort() & 0xFFFF;
			bufferSize = fileBuffer.getShort() & 0xFFFF;

			pointers[0] = fileBuffer.getInt();
			pointers[1] = fileBuffer.getInt();
			pointers[2] = fileBuffer.getInt();

			pointers[3] = fileBuffer.getInt();
			pointers[4] = fileBuffer.getInt();

			dmaArgs[0][0] = fileBuffer.getInt(); // dma start
			dmaArgs[0][1] = fileBuffer.getInt(); // dma end

			typeID = fileBuffer.get() & 0xFF;
			collisionSize[0] = fileBuffer.get() & 0xFF;
			collisionSize[1] = fileBuffer.get() & 0xFF;
			collisionSize[2] = fileBuffer.get() & 0xFF;

			if ((dmaArgs[0][0] & 0x80000000) != 0) {
				fileBuffer.position(type.set.toOffset(dmaArgs[0][0]));
				dmaArgs[0][0] = fileBuffer.getInt();
				dmaArgs[0][1] = fileBuffer.getInt();
				dmaArgs[1][0] = fileBuffer.getInt();
				dmaArgs[1][1] = fileBuffer.getInt();
			}

			collisionBox = new BoundingBox();
			collisionBox.encompass(-collisionSize[0] / 2, 0, -collisionSize[2] / 2);
			collisionBox.encompass(collisionSize[0] / 2, collisionSize[1], collisionSize[2] / 2);
		}

		@Override
		public String toString()
		{
			StringBuilder sb = new StringBuilder(String.format("%08X:%08X", dmaArgs[0][0], dmaArgs[0][1]));
			for (int i = 1; i < dmaArgs.length; i++)
				sb.append(String.format(";%08X:%08X", dmaArgs[i][0], dmaArgs[i][1]));

			return String.format("%-20s %8X %8X %4X %4X %8X %4X %8X %8X %8X %s %2X %2X %2X %2X",
				name, address, offset,
				flags, bufferSize,
				pointers[0], pointers[1], pointers[2], pointers[3], pointers[4],
				sb.toString(), typeID,
				collisionSize[0], collisionSize[1], collisionSize[2]);
		}

		public EntityTypeData(String line) throws InvalidInputException
		{
			String[] tokens = line.split("\\s+");
			name = tokens[0];
			address = DataUtils.parseIntString(tokens[1]);
			offset = DataUtils.parseIntString(tokens[2]);
			flags = DataUtils.parseIntString(tokens[3]);
			bufferSize = DataUtils.parseIntString(tokens[4]);
			pointers[0] = DataUtils.parseIntString(tokens[5]);
			pointers[1] = DataUtils.parseIntString(tokens[6]);
			pointers[2] = DataUtils.parseIntString(tokens[7]);
			pointers[3] = DataUtils.parseIntString(tokens[8]);
			pointers[4] = DataUtils.parseIntString(tokens[9]);
			typeID = DataUtils.parseIntString(tokens[11]);
			collisionSize[0] = DataUtils.parseIntString(tokens[12]);
			collisionSize[1] = DataUtils.parseIntString(tokens[13]);
			collisionSize[2] = DataUtils.parseIntString(tokens[14]);

			String[] argPairs = tokens[10].split(";");
			for (int i = 0; i < argPairs.length; i++) {
				String[] args = argPairs[i].split(":");
				dmaArgs[i][0] = DataUtils.parseIntString(args[0]);
				dmaArgs[i][1] = DataUtils.parseIntString(args[1]);
			}

			collisionBox = new BoundingBox();
			collisionBox.encompass(-collisionSize[0] / 2, 0, -collisionSize[2] / 2);
			collisionBox.encompass(collisionSize[0] / 2, collisionSize[1], collisionSize[2] / 2);
		}
	}

	public static void main(String[] args) throws IOException
	{
		Environment.initialize();
		printEntityTypeData(Environment.getBaseRomBuffer());
		Environment.exit();
	}

	private static void printEntityTypeData(ByteBuffer fileBuffer) throws IOException
	{
		for (EntityType type : EntityType.values()) {
			if (type == EntityType.Item)
				continue;
			System.out.println(new EntityTypeData(type, fileBuffer));
		}
	}
}
