package game.map;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import common.commands.EditableField;
import game.entity.EntityInfo.EntityType;
import game.map.marker.Marker.MarkerType;
import game.map.marker.NpcComponent.MoveType;
import game.map.scripts.generators.Entrance.EntranceType;
import game.map.scripts.generators.Exit.ExitType;

public abstract class JsonFeatures
{
	private static final Gson MAP_GSON = new GsonBuilder().setPrettyPrinting().create();

	public static void toJson(JsonMap out, File file) throws IOException
	{
		try (Writer writer = new FileWriter(file)) {
			MAP_GSON.toJson(out, writer);
		}
	}

	public static JsonMap fromJson(File file) throws IOException
	{
		try (Reader reader = new FileReader(file)) {
			return MAP_GSON.fromJson(reader, JsonMap.class);
		}
	}

	public static String getOrNull(EditableField<String> f)
	{
		String s = f.get();
		if (s == null || s.isBlank())
			return null;
		else
			return s;
	}

	public static class JsonMap
	{
		public boolean overrideShape;
		public boolean overrideHit;
		public boolean overrideTex;
		public String shapeOverrideName;
		public String hitOverrideName;

		public Integer overrideEntryCount;

		public int camVfov;
		public int camNearClip;
		public int camFarClip;
		public int[] bgColor;

		public int[] fogWorld;
		public int[] fogEntity;

		/*
		public boolean hasMusic;
		public String songName;
		public boolean hasAmbientSFX;
		public String ambientSFX;
		public boolean hasSpriteShading;
		*/

		public String locationName;

		public String shadingProfile;

		/*
		public boolean cameraLeadsPlayer;
		public boolean isDark;
		
		public boolean callbackBeforeEnter;
		public boolean callbackAfterEnter;
		*/

		public JsonTexturePanner[] texPanners;
		public JsonEntrance[] entrances;
		public JsonExit[] exits;
		public JsonTree[] trees;
		public JsonBush[] bushes;

		public JsonMarker[] markers;
	}

	public static class JsonEntrance
	{
		public EntranceType type;
		public String name;
		public String markerName;

		public String door1Name;
		public String door2Name;
		public String doorSound;
		public String doorSwing;

		public String pipeCollider;
		public String warpPipeEntity;

		public boolean hasCallback;
	}

	public static class JsonExit
	{
		public ExitType type;
		public String name;

		public String destMap;
		public String destMarkerName;
		public boolean useDestMarkerID;

		public String markerName;
		public String colliderName;

		public String door1Name;
		public String door2Name;
		public String doorSound;
		public String doorSwing;

		public String lockName;

		public boolean hasCallback;
	}

	public static class JsonTree
	{
		//TODO
	}

	public static class JsonBush
	{
		//TODO
	}

	public static class JsonMapProperties
	{

	}

	public static class JsonTexturePanner
	{
		public int id;

		public boolean generate;
		public boolean useTexels;

		public int max;

		// [mainU, mainV, auxU, auxV] OR [mainS, mainT, auxS, auxT]
		public int[] init;
		public int[] step;
		public int[] freq;
	}

	public static class JsonMarker
	{
		public int id;
		public Integer parent;

		public String name;
		public String desc;
		public boolean hidden;

		public MarkerType type;
		public int[] pos;
		public double yaw;
		public boolean extracted;

		public JsonNpcComp npcComp;
		public JsonGridComp gridComp;
		public JsonPathComp pathComp;
		public JsonVolumeComp volComp;
		public JsonEntityComp entityComp;
		public JsonCamTargetComp camTargetComp;
	}

	public static class JsonNpcComp
	{
		public MoveType moveType;
		public boolean flying;

		public JsonSpriteData sprite;

		public JsonDetectData detect;
		public JsonWanderData wander;
		public JsonPatrolData patrol;
	}

	public static class JsonDetectData
	{
		public int[] center;
		public boolean useCircle;
		public int radius;
		public int height;
		public int sizeX;
		public int sizeZ;
	}

	public static class JsonWanderData
	{
		public int[] center;
		public boolean useCircle;
		public int radius;
		public int sizeX;
		public int sizeZ;

		public boolean overrideSpeed;
		public float speed;
	}

	public static class JsonPatrolData
	{
		public int[][] points;

		public boolean overrideSpeed;
		public float speed;
	}

	public static class JsonSpriteData
	{
		public int id;
		public int palette;
		public int anim;

		public String animName;
		public boolean flipX;
		public boolean flipY;
	}

	public static class JsonGridComp
	{
		public int gridIndex;
		public int gridSizeX;
		public int gridSizeZ;
		public int gridSpacing;
		public boolean gridUseGravity;
		public int[][] occupants; // [ [x, z, typeID], ... ]
	}

	public static class JsonPathComp
	{
		public int[][] waypoints;
		public boolean showInterp;
	}

	public static class JsonVolumeComp
	{
		public float radius;
		public float height;
		public float[] minPos;
		public float[] maxPos;
	}

	public static class JsonCamTargetComp
	{
		public boolean useZone;

		public boolean overrideDist;
		public float boomLength;

		public boolean overrideAngles;
		public float boomPitch;
		public float viewPitch;

		public boolean generatePan;
		public float moveSpeed;

		public JsonCameraZone cameraZone;
	}

	public static class JsonCameraZone
	{
		public int type;
		public boolean flag;

		public float boomLength;
		public float boomPitch;
		public float viewPitch;

		public int[] posA;
		public int[] posB;
		public int[] posC;
	}

	public static class JsonEntityComp
	{
		public EntityType type;

		public String itemName;
		public String gameFlagName;
		public String areaFlagName;
		public String scriptName;

		public Integer index;
		public Integer style;

		public String modelName;
		public String colliderName;
		public String targetName;
		public String entryName;

		public Integer angle;
		public Integer launchDist;

		public String mapVarName;
		public String spawnMode;
		public String pathsName;
	}
}
