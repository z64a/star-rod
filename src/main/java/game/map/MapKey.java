package game.map;

import util.xml.XmlKey;

public enum MapKey implements XmlKey
{
	// @formatter:off
	ATTR_VERSION		("ver"),
	ATTR_AUTHOR			("author"),

	TAG_EDITOR				("Editor"),
	TAG_EDITOR_CAMERA		("Camera"),
	ATTR_POS				("pos"),
	ATTR_EDITOR_CAM_YAW		("yaw"),
	ATTR_EDITOR_CAM_PITCH	("pitch"),

	// generic attributes
	ATTR_NAME			("name"),
	ATTR_DESC			("desc"),
	ATTR_TYPE			("type"),

	TAG_MAP				("Map"),
	ATTR_MAP_STAGE		("stage"),
	ATTR_MAP_BG			("background"),
	ATTR_MAP_TEX		("textures"),

	TAG_PREFAB			("Prefab"),

	TAG_MODEL_TREE		("ModelTree"),
	TAG_COLLIDER_TREE	("ColliderTree"),
	TAG_ZONE_TREE		("ZoneTree"),
	TAG_MARKER_TREE		("MarkerTree"),

	TAG_LIGHTSETS		("LightSets"),
	TAG_LIGHTSET		("LightSet"),
	ATTR_LIGHTS_NAME	("name"),
	ATTR_LIGHTS_A		("a"),
	ATTR_LIGHTS_B		("b"),
	TAG_LIGHT			("Light"),
	ATTR_LIGHT_V		("v"),

	TAG_MODELS			("Models"),
	TAG_COLLIDERS		("Colliders"),
	TAG_ZONES			("Zones"),
	TAG_MARKERS			("Markers"),

	TAG_NODE			("Node"),
	ATTR_NODE_NAME		("name"),
	ATTR_NODE_ID		("id"),

	TAG_MAP_OBJECT		("MapObject"),
	ATTR_OBJ_NAME		("name"),
	ATTR_OBJ_ID			("id"),
	ATTR_OBJ_HIDDEN		("hidden"),
	ATTR_OBJ_DUMPED		("dumped"),
	ATTR_OBJ_OFFSET		("fileOffset"),

	TAG_CAMERA			("Camera"),
	ATTR_CAM_TYPE		("type"),
	ATTR_CAM_FLAG		("flag"),
	ATTR_CAM_DISABLE	("disable"),

	ATTR_CAM_BOOM_LEN	("boomLength"),
	ATTR_CAM_BOOM_PITCH	("boomPitch"),
	ATTR_CAM_VIEW_PITCH	("viewPitch"),
	ATTR_CAM_POS_A		("posA"),
	ATTR_CAM_POS_B		("posB"),
	ATTR_CAM_POS_C		("posC"),

	TAG_MARKER			("Marker"),
	ATTR_MARKER_TYPE	("type"),
	ATTR_MARKER_DESC	("desc"),
	ATTR_MARKER_POS		("pos"),
	ATTR_MARKER_YAW		("yaw"),
	ATTR_EXTRACTED		("extracted"),

	TAG_MARKER_GRID		("Grid"),
	ATTR_MARKER_GRID_INDEX	("index"),
	ATTR_MARKER_GRID_SIZE	("size"),
	ATTR_MARKER_GRID_OCC	("gridContent"),
	ATTR_MARKER_GRID_GRAV	("blockGravity"),

	TAG_MARKER_PATH		("Path"),
	TAG_MARKER_WP		("Waypoint"),
	ATTR_PATH_INTERP	("showInterp"),

	TAG_MOVEMENT		("Movement"),
	ATTR_MOVEMENT_TYPE	("type"),
	ATTR_MOVEMENT_DATA	("data"),

	TAG_VOLUME			("Volume"),
	ATTR_VOLUME_RADIUS		("radius"),
	ATTR_VOLUME_HEIGHT		("height"),
	ATTR_VOLUME_MIN			("min"),
	ATTR_VOLUME_MAX			("max"),

	TAG_CAM_TARGET		("CamTarget"),
	ATTR_CAM_USE_SAMPLE		("sampleZone"),
	ATTR_CAM_OVERRIDE_LENGTH	("overrideLength"),
	ATTR_CAM_OVERRIDE_ANGLES	("overrideAngles"),
	ATTR_CAM_GENERATE_PAN	("generatePan"),
	ATTR_CAM_PAN_SPEED		("panSpeed"),

	TAG_SPRITE			("Sprite"),
	ATTR_ANIMATION		("animation"),
	ATTR_FLIP_X			("flipX"),
	ATTR_FLIP_Y			("flipY"),

	TAG_MODEL			("Model"),
	ATTR_SHAPE_TYPE		("type"),
	ATTR_LIGHT_SET		("lightset"),
	ATTR_SCROLL_UNIT	("panUnit"),

	TAG_TX_MAT			("TransformMatrix"),
	ATTR_TX_MAT_M		("m"),
	ATTR_TX_TRANS		("trans"),
	ATTR_TX_ROT			("rot"),
	ATTR_TX_SCALE		("scale"),
	ATTR_TX_MIRROR		("mirror"),

	TAG_PROPERTY_LIST	("PropertyList"),
	TAG_PROPERTY		("Property"),
	ATTR_PROPERTY_V		("v"),

	TAG_BOUNDING_BOX	("BoundingBox"),
	ATTR_AABB_EMPTY		("empty"),
	ATTR_AABB_MIN		("min"),
	ATTR_AABB_MAX		("max"),

	TAG_TEXTURED_MESH	("ShapeMesh"),
	ATTR_TEXTURE_NAME	("texture"),

	TAG_DISPLAY_LIST	("DisplayList"),
	TAG_DISPLAY_CMD		("F3DEX2"),
	ATTR_DISPLAY_CMD_V	("cmd"),

	TAG_HIT_OBJECT		("HitObject"),
	ATTR_HIT_TYPE		("type"),

	TAG_COLLIDER		("Collider"),
	ATTR_COL_FLAGS		("flags"),
	ATTR_COL_SURFACE	("surface"),

	TAG_ZONE			("Zone"),

	TAG_BASIC_MESH		("HitMesh"),
	ATTR_MDL_NAME		("name"),
	ATTR_MDL_TYPE		("type"),

	TAG_TRIANGLE_BATCH	("TriangleBatch"),
	TAG_VERTEX_TABLE	("VertexTable"),
	TAG_TRIANGLE_LIST	("TriangleList"),
	TAG_TRIANGLE		("Triangle"),
	ATTR_TRI_IJK		("ijk"),
	ATTR_TRI_TWOSIDE	("doubleSided"),

	TAG_VERTEX			("Vertex"),
	ATTR_VERT_XYZ		("xyz"),
	ATTR_VERT_COLOR		("rgba"),
	ATTR_VERT_UV		("uv"),

	TAG_SCRIPT_DATA		("ScriptData"),
	ATTR_HAS_CALLBACK	("hasCallback"),

	TAG_PANNER_LIST		("TexPanners"),
	TAG_PANNER			("TexPanner"),
	ATTR_PAN_ID			("id"),
	ATTR_PAN_MAX		("max"),
	ATTR_PAN_INIT		("init"),
	ATTR_PAN_STEP		("step"),
	ATTR_PAN_FREQ		("freq"),
	ATTR_GENERATE		("generate"),
	ATTR_USE_TEXELS		("useTexels"),

	TAG_GEN_LIST		("Generators"),
	TAG_EXIT			("Exit"),
	TAG_ENTRANCE		("Entrance"),
	ATTR_MARKER			("entry"),
	ATTR_TRIGGER		("trigger"),
	ATTR_WARP_PIPE		("entity"),
	ATTR_PIPE_COLLIDER	("collider"),
	ATTR_DOOR1			("door1"),
	ATTR_DOOR2			("door2"),
	ATTR_LOCK			("lock"),
	ATTR_DOOR_DIR		("doorDir"),
	ATTR_DOOR_SFX		("doorSFX"),
	ATTR_DEST_MAP		("destMap"),
	ATTR_DEST_MARKER	("destEntry"),
	ATTR_USE_DEST_ID	("destUseID"),
	TAG_TREE			("Tree"),
	TAG_BUSH			("Bush"),
	TAG_MDL_TRUNK		("TrunkModel"),
	TAG_MDL_LEAF		("LeafModel"),
	TAG_MDL_BUSH		("BushModel"),
	ATTR_FOLIAGE_COL	("collider"),
	ATTR_TREE_BOMB		("bombPos"),
	TAG_FOLIAGE_DROP	("FoliageDrop"),
	TAG_FOLIAGE_FX		("FoliageFX"),
	ATTR_FOLIAGE_FX_POS	("marker"),
	ATTR_DROP_TYPE		("type"),
	ATTR_DROP_ITEM		("item"),
	ATTR_DROP_SPAWN		("spawnFlag"),
	ATTR_DROP_PICKUP	("pickupFlag"),
	ATTR_TREE_STAR		("starTree"),

	TAG_PUSH_BLOCK		("PushBlockGrid"),

	TAG_ENTITY				("Entity"),
	ATTR_NTT_ITEM			("item"),
	ATTR_NTT_GAME_FLAG		("gameFlag"),
	ATTR_NTT_AREA_FLAG		("areaFlag"),
	ATTR_NTT_SCRIPT			("script"),
	ATTR_NTT_INDEX			("index"),
	ATTR_NTT_STYLE			("style"),
	ATTR_NTT_MODEL			("model"),
	ATTR_NTT_COLLIDER		("collider"),
	ATTR_NTT_TARGET			("target"),
	ATTR_NTT_ENTRY			("entry"),
	ATTR_NTT_ANGLE			("angle"),
	ATTR_NTT_LAUNCH_DIST	("launchDist"),
	ATTR_NTT_MAP_VAR		("mapVar"),
	ATTR_NTT_SPAWN_MODE		("spawnMode"),
	ATTR_NTT_PATHS			("paths"),

	TAG_NPC				("NPC"),

	TAG_OVERRIDE		("Override"),
	ATTR_SHAPE			("shape"),
	ATTR_HIT			("hit"),
	ATTR_TEX			("tex"),

	ATTR_CAM_VFOV		("vfov"),
	ATTR_CAM_NEAR		("near"),
	ATTR_CAM_FAR		("far"),
	ATTR_CAM_BGCOL		("bgcolor"),
	ATTR_CAM_LEADS		("leadsPlayer"),

	TAG_FOG				("Fog"),
	ATTR_FOG_WORLD		("world"),
	ATTR_FOG_ENTITY		("entity"),

	TAG_OPTIONS			("Options"),
	ATTR_CALLBACKS		("callbacks"),
	ATTR_CHECK_LOADTYPE ("checkLoadType"),
	ATTR_HAS_MUSIC		("hasMusic"),
	ATTR_SONG			("song"),
	ATTR_HAS_SOUNDS		("hasSounds"),
	ATTR_SOUNDS			("sounds"),
	ATTR_LOCATION		("location"),
	ATTR_HAS_SHADING	("hasShading"),
	ATTR_SHADING_NAME	("shadingName"),
	ATTR_DARK			("dark");
	// @formatter:on

	private final String key;

	private MapKey(String key)
	{
		this.key = key;
	}

	@Override
	public String toString()
	{
		return key;
	}
}
