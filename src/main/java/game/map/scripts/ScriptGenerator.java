package game.map.scripts;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import game.map.editor.geometry.Vector3f;

import app.input.InvalidInputException;
import game.DataUtils;
import game.ScriptVariable;
import game.entity.EntityInfo.EntityType;
import game.map.Map;
import game.map.MapIndex;
import game.map.MapObject;
import game.map.MapObject.MapObjectType;
import game.map.hit.CameraZoneData;
import game.map.hit.ControlType;
import game.map.marker.GridComponent;
import game.map.marker.GridOccupant;
import game.map.marker.Marker;
import game.map.marker.Marker.MarkerType;
import game.map.scripts.generators.Entrance;
import game.map.scripts.generators.Entrance.EntranceType;
import game.map.scripts.generators.Exit;
import game.map.scripts.generators.Exit.ExitType;
import game.map.scripts.generators.Generator;
import game.map.scripts.generators.Generator.GeneratorType;
import game.map.scripts.generators.foliage.Foliage;
import game.map.scripts.generators.foliage.Foliage.FoliageDataCategory;
import game.map.scripts.generators.foliage.FoliageData;
import game.map.scripts.generators.foliage.FoliageDrop;
import game.map.scripts.generators.foliage.FoliageModel;
import game.map.scripts.generators.foliage.FoliageVector;
import game.map.shape.Model;
import game.map.shape.TexturePanner;

public class ScriptGenerator
{
	public final Map map;
	public final MapIndex index;
	private boolean checkForSavePoint = false;

	public final List<String> defineLines = new ArrayList<>();
	private final List<String> imports = new LinkedList<>();

	private final List<String> importLines = new LinkedList<>();
	private final List<String> callbackLines = new LinkedList<>();

	public final List<String> mainHooks = new LinkedList<>();
	private final HashSet<String> generatorNames = new HashSet<>();

	private static final String INDENT = "    ";
	private static final String INDENT_2 = INDENT.repeat(2);
	private static final String INDENT_3 = INDENT.repeat(3);
	private static final String INDENT_4 = INDENT.repeat(4);

	private static final String autogenPrefix = ""; //"AUTOLIB_";

	private static final String nameHeader = "$Header";
	private static final String nameMain = "$Script_Main";
	private static final String nameEntryList = "$EntryList";

	private static final String script_EnterMap = "$Script_EnterMap";
	private static final String script_CreateExits = "$Script_CreateExitTriggers";
	private static final String script_MakeEntities = "$Script_MakeEntities";
	private static final String script_PanTarget = "$Script_PanCamera_";
	private static final String script_CamTarget = "$Script_SetCamera_";
	private static final String script_SetupMusic = "$Script_SetupMusic";
	private static final String script_TexPan = "$Script_SetupTexturePan";
	private static final String script_BindFoliage = "$Script_BindFoliage";
	private static final String script_DarkRoom = "$Script_SetDarkRoom";

	//	private static final Pattern validNamePattern = Pattern.compile("[\\w-?]+");

	public ScriptGenerator(Map map) throws IOException, InvalidInputException
	{
		this.map = map;
		this.index = new MapIndex(map);

		List<Marker> entityList = new ArrayList<>();
		List<String> entityNames = new ArrayList<>();
		for (Marker m : map.markerTree) {
			if (m.getType() == MarkerType.Entity) {
				String entityName = m.getName();
				if (entityNames.contains(entityName))
					throw new InvalidInputException("Entity name is not unique: " + entityName);
				entityNames.add(entityName);

				if (m.entityComponent.type.get() == EntityType.SavePoint)
					checkForSavePoint = true;

				entityList.add(m);
			}
		}

		List<String> lines = new LinkedList<>();

		addInitScript(lines);
		// NPCs
		addEntities(entityList, lines);
		addCameraTargets(lines);
		addMusic(lines);
		addPanners(lines);
		addFoliage(lines);
		addDarkness(lines);
		addFog(lines);
		addEntrances(lines);
		addExits(lines);

		// reorder to start with header, entry list, main, ...
		List<String> out = new LinkedList<>();
		addHeader(out);
		addEntryList(out);
		out.addAll(defineLines);
		if (defineLines.size() > 0)
			out.add("");
		out.addAll(importLines);
		addMain(out);
		out.addAll(lines);
		out.addAll(callbackLines);

		File genFile = new File(map.getProjDir(), map.getName() + ".inc.c");

		//TODO
		/*
		File gen = new File(MOD_MAP_GEN + genFilename);
		try (PrintWriter pw = IOUtils.getBufferedPrintWriter(gen))
		{
			// header
			pw.println("% Auto-generated script for " + map.name);
			pw.println();
		
			for(String line : out)
				pw.println(line);
		}
		
		File[] matches = IOUtils.getFileWithin(MOD_MAP_PATCH, patchName, true);
		File modPatch = (matches.length > 0) ? matches[0] : new File(MOD_MAP_PATCH + patchName);
		if(!modPatch.exists())
			FileUtils.touch(modPatch);
		*/
	}

	private int mapVarIndex = 0;
	private int mapFlagIndex = 0;

	public int getNextMapVarIndex()
	{
		if (mapVarIndex < 0x10)
			return mapVarIndex++;
		else
			return -1;
	}

	public int getNextMapFlagIndex()
	{
		if (mapFlagIndex < 0x60)
			return mapFlagIndex++;
		else
			return -1;
	}

	private void addInitScript(List<String> lines) throws InvalidInputException
	{
		boolean replaceTex = map.scripts.overrideTex.get() && !map.texName.equals(map.getExpectedTexFilename());

		if (!map.scripts.overrideShape.get() && !map.scripts.overrideHit.get() && !replaceTex)
			return;

		lines.add("b32 N(map_init)(void) {");

		if (map.scripts.overrideShape.get()) {
			String assetName = map.scripts.shapeOverrideName.get();
			if (assetName.isEmpty() || !assetName.endsWith("_shape"))
				throw new InvalidInputException("Geometry override assets must end in _shape: " + assetName);

			lines.add(INDENT + "sprintf(wMapShapeName, \"" + assetName + "\"");
		}

		if (map.scripts.overrideHit.get()) {
			String assetName = map.scripts.hitOverrideName.get();
			if (assetName.isEmpty() || !assetName.endsWith("_hit"))
				throw new InvalidInputException("Collision override assets must end in _hit: " + assetName);

			lines.add(INDENT + "sprintf(wMapHitName, \"" + assetName + "\"");
		}

		if (replaceTex) {
			String assetName = map.texName;
			if (assetName.isEmpty() || !assetName.endsWith("_tex"))
				throw new InvalidInputException("Texture override assets must end in _tex: " + assetName);

			if (!map.texName.equals(map.getExpectedTexFilename()))
				lines.add(INDENT + "sprintf(wMapTexName, \"" + assetName + "\"");
		}

		lines.add("}");
		lines.add("");
	}

	private void addHeader(List<String> lines)
	{
		/*
		lines.add("#new:Header " + nameHeader);
		lines.add("{");
		lines.add(String.format("\t[%s] %s", Header.MainScript.name,	nameMain));
		lines.add(String.format("\t[%s] %s", Header.Background.name,	map.hasBackground ? "80200000" : "00000000"));
		lines.add(String.format("\t[%s] %s", Header.EntryList.name,		nameEntryList));
		lines.add(String.format("\t[%s] %X", Header.EntryCount.name,	index.getEntryCount()));
		lines.add("}");
		lines.add("");
		*/
	}

	private void addEntryList(List<String> lines)
	{
		lines.add("#new:EntryList " + nameEntryList);
		lines.add("{");
		for (Marker m : index.getEntryList())
			lines.add(String.format("\t~Vec4f:%s", m.getName()));
		lines.add("}");
		lines.add("");
	}

	private void addMain(List<String> lines) throws InvalidInputException
	{
		lines.add("EvtScript N(EVS_Main) = {");

		lines.add(INDENT + "Set(GB_WorldLocation, GEN_MAP_LOCATION )");

		lines.add("\tSet   *GB_WorldLocation  .Location:" + map.scripts.locationName.get());
		if (map.scripts.hasSpriteShading.get()) {
			String profileName = map.scripts.shadingProfile.get().name.get();
			lines.add("\tCall  SetSpriteShading   ( .Shading:" + profileName + " )");
		}
		lines.add("\tCall  SetCamPerspective  ( .Cam:Default " + String.format("00000003 %08X %08X %08X )",
			map.scripts.camVfov.get(), map.scripts.camNearClip.get(), map.scripts.camFarClip.get()));
		lines.add("\tCall  SetCamBGColor      ( .Cam:Default " + String.format("%08X %08X %08X )",
			map.scripts.bgColorR.get(), map.scripts.bgColorG.get(), map.scripts.bgColorB.get()));
		lines.add("\tCall  SetCamEnabled      ( .Cam:Default .True");
		lines.add("\tCall  SetCamLeadPlayer   ( .Cam:Default " + (map.scripts.cameraLeadsPlayer.get() ? ".True" : ".False") + " )");
		// make npcs
		for (String execLine : mainHooks)
			lines.add("\t" + execLine);
		if (map.scripts.addCallbackBeforeEnterMap.get())
			lines.add("\tExec  $Script_Main_Callback_BeforeEnterMap");
		lines.add("\tExec  " + script_EnterMap);
		if (map.scripts.addCallbackAfterEnterMap.get())
			lines.add("\tExec  $Script_Main_Callback_AfterEnterMap");
		// music
		lines.add("\tReturn");
		lines.add("\tEnd");
		lines.add("}");
		lines.add("");

		if (map.scripts.addCallbackBeforeEnterMap.get()) {
			callbackLines.add("#new:Script $Script_Main_Callback_BeforeEnterMap");
			callbackLines.add("{");
			callbackLines.add("\tReturn");
			callbackLines.add("\tEnd");
			callbackLines.add("}");
		}

		if (map.scripts.addCallbackAfterEnterMap.get()) {
			callbackLines.add("#new:Script $Script_Main_Callback_AfterEnterMap");
			callbackLines.add("{");
			callbackLines.add("\tReturn");
			callbackLines.add("\tEnd");
			callbackLines.add("}");
		}
	}

	private void addEntrances(List<String> lines) throws InvalidInputException
	{
		List<Generator> entranceList = map.scripts.generatorsTreeModel.getObjectsInCategory(GeneratorType.Entrance);

		// anything other than teleport
		int numEntrances = 0;
		ArrayList<Entrance> entrances = new ArrayList<>();
		ArrayList<Entrance> basicWalk = new ArrayList<>();
		ArrayList<Entrance> basicTele = new ArrayList<>();
		for (Generator g : entranceList) {
			Entrance e = (Entrance) g;

			String name = e.getName();
			if (generatorNames.contains(name))
				throw new InvalidInputException("Entrance has duplicate name: " + name);
			generatorNames.add(name);

			if (e.type.get() == EntranceType.Walk && !e.hasCallback.get())
				basicWalk.add(e);
			else if (e.type.get() == EntranceType.Teleport && !e.hasCallback.get())
				basicTele.add(e);
			else
				entrances.add(e);
			numEntrances++;
		}

		entrances.sort(Entrance.TYPE_COMPARATOR);

		lines.add(String.format("#new:Script " + script_EnterMap));
		lines.add("{");

		if (checkForSavePoint) {
			lines.add("\tCall  GetLoadType   ( *Var[0] )");
			lines.add("\tIf  *Var[0]  ==  1");
			lines.add("\t\tExec  EnterSavePoint");
			lines.add("\t\tExec " + script_CreateExits);
			lines.add("\t\tReturn");
			lines.add("\tEndIf");
		}

		if (numEntrances > 0) {
			lines.add("\tCall  GetEntryID    ( *Var[0] )");
			lines.add("\tSwitch  *Var[0]");

			for (Entrance e : entrances) {
				String entryName = e.getName();
				String callbackName = "$Script_Callback_" + entryName;

				lines.add(String.format("\t\tCase  ==  ~Entry:%s", e.markerName));

				if (e.hasCallback.get()) {
					lines.add("\t\t\tExec  " + callbackName);

					callbackLines.add("#new:Script " + callbackName);
					callbackLines.add("{");
					callbackLines.add("\tReturn");
					callbackLines.add("\tEnd");
					callbackLines.add("}");
					callbackLines.add("");
				}

				switch (e.type.get()) {
					case SingleDoor:
						validateObject(entryName, "Entry", MapObjectType.MARKER, e.markerName.get());
						validateObject(entryName, "Door", MapObjectType.MODEL, e.door1Name.get());

						lines.add(String.format("\t\t\tSet   *Var[2]  ~Model:%s", e.door1Name.get()));
						lines.add(String.format("\t\t\tSet   *Var[3]  .DoorSwing:%s", e.doorSwing.get()));
						lines.add("\t\t\tExec  EnterSingleDoor");
						lines.add("\t\t\tExec  " + script_CreateExits);
						break;
					case DoubleDoor:
						validateObject(entryName, "Entry", MapObjectType.MARKER, e.markerName.get());
						validateObject(entryName, "Left Door", MapObjectType.MODEL, e.door1Name.get());
						validateObject(entryName, "Right Door", MapObjectType.MODEL, e.door2Name.get());

						lines.add(String.format("\t\t\tCall  UseDoorSounds ( .DoorSounds:%s )", e.doorSound.get()));
						lines.add(String.format("\t\t\tSet   *Var[2]  ~Model:%s", e.door1Name.get()));
						lines.add(String.format("\t\t\tSet   *Var[3]  ~Model:%s", e.door2Name.get()));
						lines.add("\t\t\tExec  EnterDoubleDoor");
						lines.add("\t\t\tExec  " + script_CreateExits);
						break;
					case Walk:
						lines.add("\t\t\tSet   *Var[0] " + script_CreateExits);
						lines.add("\t\t\tExec  EnterWalk");
						break;
					case VerticalPipe:
						importResource("WarpPipes.mpat");
						lines.add("\t\t\tSet   *Var[A] " + script_CreateExits);
						lines.add("\t\t\tExec  $Script_EnterVerticalPipe");
						break;
					case HorizontalPipe:
						importResource("WarpPipes.mpat");
						validateObject(entryName, "Pipe Collider", MapObjectType.COLLIDER, e.pipeCollider.get());

						lines.add("\t\t\tSet   *Var[A] " + script_CreateExits);
						lines.add(String.format("\t\t\tSet   *Var[B] ~Collider:%s", e.pipeCollider.get()));
						lines.add("\t\t\tExec  $Script_EnterHorizontalPipe");
						break;
					case BlueWarpPipe:
						importResource("WarpPipes.mpat");
						Marker entity = (Marker) validateObject(entryName, "Entry", MapObjectType.MARKER, e.warpPipeEntity.get());
						String flagName = entity.entityComponent.gameFlagName.get();
						ScriptVariable.parseScriptVariable(flagName);

						// raise the pipe if its not active yet
						lines.add("\t\t\tIf  " + flagName + "  ==  .False");
						lines.add("\t\t\t\tCall  DisablePlayerInput     ( .True )");
						lines.add("\t\t\t\tCall  DisablePlayerPhysics   ( .True )");
						lines.add("\t\t\t\tCall  GetPlayerPos   ( *Var[0] *Var[1] *Var[2] )");
						lines.add("\t\t\t\tCall  SetNpcPos      ( .Npc:Partner *Var[0] *Var[1] *Var[2] )");
						lines.add("\t\t\t\tCall  SetPlayerPos   ( *Var[0] -1000` *Var[2] )");
						lines.add("\t\t\t\tWait  30`");
						lines.add("\t\t\t\tCall  PlaySound      ( 208E )");
						lines.add("\t\t\t\tSet   " + flagName + " .True");
						lines.add("\t\t\t\tWait  30` ");
						lines.add("\t\t\t\tCall  SetPlayerActionState       ( 0 )");
						lines.add("\t\t\t\tCall  SetPlayerPos   ( *Var[0] *Var[1] *Var[2] )");
						lines.add("\t\t\t\tCall  SetNpcPos      ( .Npc:Partner *Var[0] *Var[1] *Var[2] )");
						lines.add("\t\t\t\tCall  DisablePlayerPhysics   ( .False )");
						lines.add("\t\t\t\tCall  DisablePlayerInput     ( .False )");
						lines.add("\t\t\tEndIf");

						lines.add("\t\t\tSet   *Var[A] " + script_CreateExits);
						lines.add("\t\t\tExecWait  $Script_EnterVerticalPipe");
						break;
					case Teleport:
						break; // adds nothing apart from possible callback
				}
			}

			// default walk entries may get a case group
			if (basicWalk.size() == 1) {
				lines.add(String.format("\t\tCase  ==  ~Entry:%s", basicWalk.get(0).markerName));
				lines.add("\t\t\tSet   *Var[0] " + script_CreateExits);
				lines.add("\t\t\tExec  EnterWalk");
			}
			else if (basicWalk.size() > 1) {
				for (Entrance e : basicWalk)
					lines.add(String.format("\t\tCaseOR  ==  ~Entry:%s", e.markerName));
				lines.add("\t\t\tSet   *Var[0] " + script_CreateExits);
				lines.add("\t\t\tExec  EnterWalk");
				lines.add("\t\tEndCaseGroup");
			}

			// treat all others as teleport
			lines.add("\t\tDefault");
			lines.add("\t\t\tExec  " + script_CreateExits);

			lines.add("\tEndSwitch");
		}

		lines.add("\tReturn");
		lines.add("\tEnd");
		lines.add("}");
		lines.add("");
	}

	// interaction between door exits and lock entities means we have to put these lines in MakeEntities script
	private List<String> getDoorBinds() throws InvalidInputException
	{
		List<String> lines = new ArrayList<>();
		List<Generator> exitList = map.scripts.generatorsTreeModel.getObjectsInCategory(GeneratorType.Exit);

		for (Generator generator : exitList) {
			Exit exit = (Exit) generator;

			switch (exit.type.get()) {
				case SingleDoor:
				case DoubleDoor:
					String bindDoorLine = String.format(
						"\tBind  $Script_%s .Trigger:WallPressA ~Collider:%s 00000001 00000000",
						exit.getName(), exit.colliderName.get());
					String lockName = exit.lockName.get();

					if (lockName == null || lockName.isEmpty()) {
						lines.add(bindDoorLine);
					}
					else {
						Marker m = validateObject(exit.getName(), "Lock", MarkerType.Entity, lockName);
						switch (m.entityComponent.type.get()) {
							case Padlock:
							case PadlockRedFrame:
							case PadlockRedFace:
							case PadlockBlueFace:
								String typeName = m.entityComponent.type.get().name;
								String flagName = m.entityComponent.gameFlagName.get();

								// validate fields
								ScriptVariable.parseScriptVariable(flagName);

								int mapVar = getNextMapVarIndex();
								if (mapVar < 0 || mapVar >= ScriptVariable.MapVar.getMaxIndex())
									throw new InvalidInputException("(Lock for %s) %X is not a valid map var index!", exit.getName(), mapVar);

								defineLines.add(String.format("#define .MapVar_Entity_%s %X", m.getName(), mapVar));

								lines.add("\tIf  " + flagName + "  ==  .False");
								lines.add(String.format("\t\tCall  MakeEntity    ( .Entity:%s ~Vec4d:%s 80000000 )", typeName, m.getName()));
								lines.add(String.format("\t\tSet   *MapVar[.MapVar_Entity_%s]  *Var[0]", m.getName()));
								lines.add(
									String.format("\t\tBindLock  $Script_CheckLock_%s .Trigger:WallPressA 00004000 $ItemList_%s 00000000 00000001",
										m.getName(), m.getName()));
								lines.add("\tElse");
								lines.add("\t" + bindDoorLine);
								lines.add("\tEndIf");
								break;
							default:
								throw new InvalidInputException("(Lock for %s) %s is not a Padlock entity!", exit.getName(), m.getName());
						}
					}
					break;
				case Walk:
				case HorizontalPipe:
				case VerticalPipe:
					break;
			}
		}

		return lines;
	}

	private void addExits(List<String> lines) throws InvalidInputException
	{
		List<Generator> exitList = map.scripts.generatorsTreeModel.getObjectsInCategory(GeneratorType.Exit);

		// add 'exit script' for each exit
		for (Generator generator : exitList) {
			Exit exit = (Exit) generator;
			String exitName = exit.getName();

			if (generatorNames.contains(exitName))
				throw new InvalidInputException("Exit has duplicate name: " + exitName);
			generatorNames.add(exitName);

			String destMap = exit.destMap.get();
			int destMarkerID = 0;
			String destMarkerName = exit.destMarkerName.get();
			if (exit.useDestMarkerID.get()) {
				try {
					destMarkerID = DataUtils.parseIntString(destMarkerName);
				}
				catch (InvalidInputException e) {
					throw new InvalidInputException("Could not generate exit using invalid dest marker ID: " + destMarkerName);
				}
			}

			lines.add(String.format("#new:Script $Script_%s", exit.getName()));
			lines.add("{");
			lines.add("\tSetGroup 0000001B");

			switch (exit.type.get()) {
				case Walk:
					lines.add(String.format("\tCall     UseExitHeading ( 60` ~Entry:%s )", exit.markerName.get()));
					lines.add("\tExec     ExitWalk");
					break;

				case SingleDoor:
					validateObject(exitName, "Entry", MapObjectType.MARKER, exit.markerName.get());
					validateObject(exitName, "Collider", MapObjectType.COLLIDER, exit.colliderName.get());
					validateObject(exitName, "Door", MapObjectType.MODEL, exit.door1Name.get());

					lines.add("\tCall  DisablePlayerInput ( .True )");
					lines.add(String.format("\tCall  UseDoorSounds ( .DoorSounds:%s )", exit.doorSound.get()));
					lines.add(String.format("\tSet   *Var[0]  ~Entry:%s", exit.markerName.get()));
					lines.add(String.format("\tSet   *Var[1]  ~Collider:%s", exit.colliderName.get()));
					lines.add(String.format("\tSet   *Var[2]  ~Model:%s", exit.door1Name.get()));
					lines.add(String.format("\tSet   *Var[3]  .DoorSwing:%s", exit.doorSwing.get()));
					lines.add("\tExec  ExitSingleDoor");
					lines.add("\tWait  17`");
					break;

				case DoubleDoor:
					validateObject(exitName, "Entry", MapObjectType.MARKER, exit.markerName.get());
					validateObject(exitName, "Collider", MapObjectType.COLLIDER, exit.colliderName.get());
					validateObject(exitName, "Left Door", MapObjectType.MODEL, exit.door1Name.get());
					validateObject(exitName, "Right Door", MapObjectType.MODEL, exit.door2Name.get());

					lines.add("\tCall  DisablePlayerInput ( .True )");
					lines.add(String.format("\tCall  UseDoorSounds ( .DoorSounds:%s )", exit.doorSound.get()));
					lines.add(String.format("\tSet   *Var[0]  ~Entry:%s", exit.markerName.get()));
					lines.add(String.format("\tSet   *Var[1]  ~Collider:%s", exit.colliderName.get()));
					lines.add(String.format("\tSet   *Var[2]  ~Model:%s", exit.door1Name.get()));
					lines.add(String.format("\tSet   *Var[3]  ~Model:%s", exit.door2Name.get()));
					lines.add("\tExec  ExitDoubleDoor");
					lines.add("\tWait  17`");
					break;

				case VerticalPipe:
				case HorizontalPipe:
					importResource("WarpPipes.mpat");
					validateObject(exitName, "Entry", MapObjectType.MARKER, exit.markerName.get());
					validateObject(exitName, "Pipe Collider", MapObjectType.COLLIDER, exit.colliderName.get());

					lines.add("\tSet   *Var[A] ~Entry:" + exit.markerName.get());
					lines.add("\tSet   *Var[B] ~Collider:" + exit.colliderName.get());
					lines.add("\tSet   *Var[C] $Script_GotoMap_" + exitName);
					if (exit.type.get() == ExitType.VerticalPipe)
						lines.add("\tExecWait  $Script_ExitVerticalPipe");
					else
						lines.add("\tExecWait  $Script_ExitHorizontalPipe");
					lines.add("\tReturn");
					lines.add("\tEnd");
					lines.add("}");
					lines.add("");

					lines.add("#new:Script $Script_GotoMap_" + exitName);
					lines.add("{");
					break;
			}

			if (exit.useDestMarkerID.get())
				lines.add(String.format("\tCall  GotoMap   ( \"%s\" %08X )", destMap, destMarkerID));
			else
				lines.add(String.format("\tCall  GotoMap   ( \"%s\" ~Entry:%s:%s )", destMap, destMap, destMarkerName));

			lines.add("\tWait  100`");
			lines.add("\tReturn");
			lines.add("\tEnd");
			lines.add("}");
			lines.add("");

			String callbackName = "$Script_Callback_" + exitName;

			if (exit.hasCallback.get()) {
				callbackLines.add("#new:Script " + callbackName);
				callbackLines.add("{");
				callbackLines.add("\tReturn");
				callbackLines.add("\tEnd");
				callbackLines.add("}");
				callbackLines.add("");
			}
		}

		lines.add("#new:Script " + script_CreateExits);
		lines.add("{");
		for (Generator generator : exitList) {
			Exit exit = (Exit) generator;

			switch (exit.type.get()) {
				case Walk:
					lines.add(String.format(
						"\tBind  $Script_%s .Trigger:FloorAbove ~Collider:%s 00000001 00000000",
						exit.getName(), exit.colliderName.get()));
					break;
				case HorizontalPipe:
					lines.add(String.format(
						"\tBind  $Script_%s .Trigger:WallPush ~Collider:%s 00000001 00000000",
						exit.getName(), exit.colliderName.get()));
					break;
				case VerticalPipe:
					lines.add(String.format(
						"\tBind  $Script_%s .Trigger:FloorTouch ~Collider:%s 00000001 00000000",
						exit.getName(), exit.colliderName.get()));
					break;
				case SingleDoor:
				case DoubleDoor:
					break;
			}
		}
		lines.add("\tReturn");
		lines.add("\tEnd");
		lines.add("}");
		lines.add("");
	}

	private void addEntities(List<Marker> entityList, List<String> lines) throws InvalidInputException
	{
		List<String> doorLines = getDoorBinds();
		EntityGenerator gen = new EntityGenerator(this, entityList, importLines, callbackLines);
		List<String> entityLines = gen.getLines();
		List<String> pushLines = getPushGridLines();

		if ((doorLines.size() + entityLines.size() + pushLines.size()) > 0) {
			mainHooks.add("Exec  " + script_MakeEntities);

			lines.add("#new:Script " + script_MakeEntities);
			lines.add("{");
			lines.addAll(doorLines);
			lines.addAll(entityLines);
			lines.addAll(pushLines);
			lines.add("\tReturn");
			lines.add("\tEnd");
			lines.add("}");
			lines.add("");
		}
	}

	private void addCameraTargets(List<String> camLines) throws InvalidInputException
	{
		List<String> names = new ArrayList<>();

		for (Marker m : map.markerTree) {
			if (m.getType() == MarkerType.CamTarget) {
				String name = m.getName();
				if (names.contains(name))
					throw new InvalidInputException("Camera target name is not unique: " + name);
				names.add(name);

				String camScriptName = m.cameraComponent.generatePan.get() ? script_PanTarget : script_CamTarget;
				camLines.add("#new:Script " + camScriptName + m.getName());
				camLines.add("{");

				if (m.cameraComponent.useZone.get()) {
					Vector3f samplePos = m.position.getVector();
					camLines.add(String.format("\tCall  UseSettingsFrom   ( .Cam:Default %d` %d` %d` )",
						Math.round(samplePos.x), Math.round(samplePos.y), Math.round(samplePos.z)));
					if (m.cameraComponent.overrideAngles.get())
						camLines.add(String.format("\tCall  SetCamPitch       ( .Cam:Default *Fixed[%f] *Fixed[%f] )",
							m.cameraComponent.boomPitch.get(), m.cameraComponent.viewPitch.get()));
					if (m.cameraComponent.overrideDist.get())
						camLines.add(String.format("\tCall  SetCamDistance    ( .Cam:Default %d` )",
							Math.round(m.cameraComponent.boomLength.get())));
					camLines.add(String.format("\tCall  SetPanTarget      ( .Cam:Default ~Vec3d:%s )", m.getName()));
				}
				else {
					CameraZoneData controlData = m.cameraComponent.controlData;
					camLines.add(String.format("\tCall  SetCamType        ( .Cam:Default %08X %s )",
						controlData.getType().index, controlData.getFlag() ? ".True" : ".False"));
					camLines.add(String.format("\tCall  SetCamPitch       ( .Cam:Default *Fixed[%f] *Fixed[%f] )",
						controlData.boomPitch.get(), controlData.viewPitch.get()));
					camLines.add(String.format("\tCall  SetCamDistance    ( .Cam:Default %d` )",
						Math.round(controlData.boomLength.get())));
					if (controlData.getType() == ControlType.TYPE_4) {
						camLines.add(String.format("\tCall  SetCamPosA        ( .Cam:Default %d` %d` )",
							controlData.posA.getX(), controlData.posA.getZ())); // 0/2 - Ax/Az
						camLines.add(String.format("\tCall  SetCamPosB        ( .Cam:Default %d` %d` )",
							controlData.posA.getY(), controlData.posB.getY())); // 3/5 - Bx/Bz
						camLines.add(String.format("\tCall  SetCamPosC        ( .Cam:Default %d` %d` )",
							controlData.posB.getX(), controlData.posB.getZ())); // 1/4 - Ay/By
					}
					else {
						camLines.add(String.format("\tCall  SetCamPosA        ( .Cam:Default %d` %d` )",
							controlData.posA.getX(), controlData.posC.getX())); // 0/2 - Ax/Az
						camLines.add(String.format("\tCall  SetCamPosB        ( .Cam:Default %d` %d` )",
							controlData.posA.getZ(), controlData.posB.getX())); // 3/5 - Bx/Bz
						camLines.add(String.format("\tCall  SetCamPosC        ( .Cam:Default %d` %d` )",
							controlData.posC.getZ(), controlData.posB.getZ())); // 1/4 - Ay/By
					}
					camLines.add(String.format("\tCall  SetPanTarget      ( .Cam:Default ~Vec3d:%s )", m.getName()));
				}

				if (m.cameraComponent.generatePan.get()) {
					camLines.add(String.format("\tCall  SetCamSpeed       ( .Cam:Default *Fixed[%f] )", m.cameraComponent.moveSpeed.get()));
					camLines.add("\tCall  PanToTarget       ( .Cam:Default 00000000 00000001 )");
					camLines.add("\tCall  WaitForCam        ( .Cam:Default *Fixed[1.0] )");
				}

				camLines.add("\tReturn");
				camLines.add("\tEnd");
				camLines.add("}");
				camLines.add("");
			}
		}
	}

	private List<String> getPushGridLines() throws InvalidInputException
	{
		List<String> gridLines = new ArrayList<>();
		List<Integer> gridIDs = new ArrayList<>();

		for (Marker m : map.markerTree) {
			if (m.getType() == MarkerType.BlockGrid) {
				GridComponent grid = m.gridComponent;

				if (gridIDs.contains(grid.gridIndex.get()))
					throw new InvalidInputException("Duplicate push block grid ID for " + m.getName());
				gridIDs.add(grid.gridIndex.get());

				gridLines.add(String.format("\tCall  CreatePushBlockGrid   ( %08X %08X %08X ~Vec3d:%s 00000000 )",
					grid.gridIndex.get(), grid.gridSizeX.get(), grid.gridSizeZ.get(), m.getName()));

				if (grid.gridUseGravity.get()) {
					importResource("PushBlockGravity.mpat");
					gridLines.add("\tCall  SetPushBlockFallEffect    ( 00000000 $Function_PushBlock_Gravity )");
				}

				for (GridOccupant occ : grid.gridOccupants) {
					gridLines.add(String.format("\tCall  SetPushBlock  ( %08X %08X %08X %08X )",
						grid.gridIndex.get(), occ.posX, occ.posZ, occ.type.get().id));
				}
			}
		}

		return gridLines;
	}

	private void addMusic(List<String> lines)
	{
		lines.add("#new:Script " + script_SetupMusic);
		lines.add("{");

		if (map.scripts.hasMusic.get())
			lines.add("\tCall  SetMusicTrack ( 00000000 .Song:" + map.scripts.songName.get() + " 00000000 00000008 )");
		else
			lines.add("\tCall  FadeOutMusic  ( 00000000 500` ) % usually between 500-1000");

		if (map.scripts.hasAmbientSFX.get())
			lines.add("\tCall  PlayAmbientSounds  ( .AmbientSounds:" + map.scripts.ambientSFX.get() + " )");
		else
			lines.add("\tCall  ClearAmbientSounds ( 250` )");

		lines.add("\tReturn");
		lines.add("\tEnd");
		lines.add("}");
		lines.add("");

		mainHooks.add("Exec  " + script_SetupMusic);
	}

	private void addFoliage(List<String> lines) throws InvalidInputException
	{
		List<Generator> trees = map.scripts.generatorsTreeModel.getObjectsInCategory(GeneratorType.Tree);
		List<Generator> bushes = map.scripts.generatorsTreeModel.getObjectsInCategory(GeneratorType.Bush);

		List<String> bindingLines = new ArrayList<>();

		if (trees.size() > 0 || bushes.size() > 0)
			importResource("FoliageHelpers.mpat");
		else
			return;

		if (bushes.size() > 0)
			importResource("FoliageBush.mpat");

		boolean useNormalTree = false;
		boolean useStarTree = false;

		for (Generator gen : trees) {
			Foliage fol = (Foliage) gen;
			if (fol.isStarTree.get())
				useStarTree = true;
			else
				useNormalTree = true;
		}

		if (useNormalTree)
			importResource("FoliageTree.mpat");

		if (useStarTree)
			importResource("FoliageStarTree.mpat");

		for (Generator gen : trees)
			addTree(lines, bindingLines, (Foliage) gen);

		for (Generator gen : bushes)
			addBush(lines, bindingLines, (Foliage) gen);

		if (bindingLines.size() > 0) {
			lines.add("#new:Script " + script_BindFoliage);
			lines.add("{");
			for (String line : bindingLines)
				lines.add("\t" + line);
			lines.add("\tReturn");
			lines.add("\tEnd");
			lines.add("}");
			lines.add("");

			mainHooks.add("Exec  " + script_BindFoliage);
		}
	}

	private void addBush(List<String> lines, List<String> bindingLines, Foliage bush) throws InvalidInputException
	{
		List<FoliageData> models = bush.dataTreeModel.getObjectsInCategory(FoliageDataCategory.BushModels);
		List<FoliageData> vectors = bush.dataTreeModel.getObjectsInCategory(FoliageDataCategory.FXPositions);
		List<FoliageData> drops = bush.dataTreeModel.getObjectsInCategory(FoliageDataCategory.Drops);

		String bushName = bush.getName();
		if (bushName == null)
			throw new InvalidInputException("Bush is missing valid name!");
		if (generatorNames.contains(bushName))
			throw new InvalidInputException("Bush has duplicate name: " + bush.overrideName);
		generatorNames.add(bushName);

		validateObject(bushName, "Trigger Collider", MapObjectType.COLLIDER, bush.colliderName.get());

		String bushModelListName = "$BushModels_" + bushName;
		String fxPosListName = "$FoliageFXPos_" + bushName;
		String dropListName = "$FoliageDrops_" + bushName;
		String callbackName = "$Script_Callback_" + bushName;
		String dataName = "$SearchBushData_" + bushName;

		if (models.size() > 0) {
			lines.add("#new:TreeModelList " + bushModelListName);
			lines.add("{");
			lines.add("\t" + models.size() + "`");
			for (FoliageData data : models) {
				String name = ((FoliageModel) data).modelName.get();
				validateObject(bushName, "Model", MapObjectType.MODEL, name);
				lines.add("\t~Model:" + name);
			}
			lines.add("}");
			lines.add("");
		}

		if (vectors.size() > 0) {
			lines.add("#new:TreeEffectVectors " + fxPosListName);
			lines.add("{");
			lines.add("\t" + vectors.size() + "`");
			for (FoliageData data : vectors) {
				String name = ((FoliageVector) data).modelName.get();
				validateObject(bushName, "Vector", MapObjectType.MARKER, name);
				lines.add("\t~Vec3d:" + name);
			}
			lines.add("}");
			lines.add("");
		}

		if (drops.size() > 0) {
			lines.add("#new:TreeDropList " + dropListName);
			lines.add("{");
			lines.add("\t" + drops.size() + "`");
			for (FoliageData data : drops) {
				FoliageDrop drop = (FoliageDrop) data;

				validateObject(bushName, "Spawn Pos", MapObjectType.MARKER, drop.markerName);

				String pickupFlag = drop.pickupFlag;
				if (pickupFlag == null || pickupFlag.isEmpty())
					pickupFlag = "00000000";
				else
					ScriptVariable.parseScriptVariable(pickupFlag);

				String spawnFlag = drop.spawnFlag;
				if (spawnFlag == null || spawnFlag.isEmpty())
					spawnFlag = "00000000";
				else
					ScriptVariable.parseScriptVariable(spawnFlag);

				lines.add(String.format("\t.Item:%s ~Vec3d:%s %08X %s %s",
					drop.itemName, drop.markerName, drop.type, pickupFlag, spawnFlag));
			}
			lines.add("}");
			lines.add("");
		}

		lines.add("#new:SearchBushEvent " + dataName);
		lines.add("{");
		lines.add("\t" + (0 >= models.size() ? "00000000" : bushModelListName));
		lines.add("\t" + (0 >= drops.size() ? "00000000" : dropListName));
		lines.add("\t" + (0 >= vectors.size() ? "00000000" : fxPosListName));
		lines.add("\t" + (!bush.hasCallback.get() ? "00000000" : callbackName));
		lines.add("}");
		lines.add("");

		if (bush.hasCallback.get()) {
			callbackLines.add("#new:Script " + callbackName);
			callbackLines.add("{");
			callbackLines.add("\tReturn");
			callbackLines.add("\tEnd");
			callbackLines.add("}");
			callbackLines.add("");
		}

		bindingLines.add("Set   *Var[0]  " + dataName);
		bindingLines.add("Bind  $" + autogenPrefix + "Script_Foliage_SearchBush .Trigger:WallPressA ~Collider:"
			+ bush.colliderName.get() + " 00000001 00000000");
	}

	private void addTree(List<String> lines, List<String> bindingLines, Foliage tree) throws InvalidInputException
	{
		List<FoliageData> trunkModels = tree.dataTreeModel.getObjectsInCategory(FoliageDataCategory.TrunkModels);
		List<FoliageData> leafModels = tree.dataTreeModel.getObjectsInCategory(FoliageDataCategory.LeafModels);
		List<FoliageData> vectors = tree.dataTreeModel.getObjectsInCategory(FoliageDataCategory.FXPositions);
		List<FoliageData> drops = tree.dataTreeModel.getObjectsInCategory(FoliageDataCategory.Drops);

		String treeName = tree.getName();
		if (treeName == null)
			throw new InvalidInputException("Tree is missing valid name!");
		if (generatorNames.contains(treeName))
			throw new InvalidInputException("Tree has duplicate name: " + treeName);
		generatorNames.add(treeName);

		validateObject(treeName, "Trigger Collider", MapObjectType.COLLIDER, tree.colliderName.get());
		validateObject(treeName, "Bomb Pos", MapObjectType.MARKER, tree.bombPosName.get());

		String trunkModelListName = "$TrunkModels_" + treeName;
		String leafModelListName = "$LeafModels_" + treeName;
		String fxPosListName = "$FoliageFXPos_" + treeName;
		String dropListName = "$FoliageDrops_" + treeName;
		String callbackName = "$Script_Callback_" + treeName;
		String dataName = "$ShakeTreeData_" + treeName;
		String triggerCoordName = "$TriggerCoord_" + treeName;

		if (trunkModels.size() > 0) {
			lines.add("#new:TreeModelList " + trunkModelListName);
			lines.add("{");
			lines.add("\t" + trunkModels.size() + "`");
			for (FoliageData data : trunkModels) {
				String name = ((FoliageModel) data).modelName.get();
				validateObject(treeName, "Model", MapObjectType.MODEL, name);
				lines.add("\t~Model:" + name);
			}
			lines.add("}");
			lines.add("");
		}

		if (leafModels.size() > 0) {
			lines.add("#new:TreeModelList " + leafModelListName);
			lines.add("{");
			lines.add("\t" + leafModels.size() + "`");
			for (FoliageData data : leafModels) {
				String name = ((FoliageModel) data).modelName.get();
				validateObject(treeName, "Model", MapObjectType.MODEL, name);
				lines.add("\t~Model:" + name);
			}
			lines.add("}");
			lines.add("");
		}

		if (vectors.size() > 0) {
			lines.add("#new:TreeEffectVectors " + fxPosListName);
			lines.add("{");
			lines.add("\t" + vectors.size() + "`");
			for (FoliageData data : vectors) {
				String name = ((FoliageVector) data).modelName.get();
				validateObject(treeName, "Vector", MapObjectType.MARKER, name);
				lines.add("\t~Vec3d:" + name);
			}
			lines.add("}");
			lines.add("");
		}

		if (drops.size() > 0) {
			lines.add("#new:TreeDropList " + dropListName);
			lines.add("{");
			lines.add("\t" + drops.size() + "`");
			for (FoliageData data : drops) {
				FoliageDrop drop = (FoliageDrop) data;

				validateObject(treeName, "Spawn Pos", MapObjectType.MARKER, drop.markerName);

				String pickupFlag = drop.pickupFlag;
				if (pickupFlag == null || pickupFlag.isEmpty())
					pickupFlag = "00000000";
				else
					ScriptVariable.parseScriptVariable(pickupFlag);

				String spawnFlag = drop.spawnFlag;
				if (spawnFlag == null || spawnFlag.isEmpty())
					spawnFlag = "00000000";
				else
					ScriptVariable.parseScriptVariable(spawnFlag);

				lines.add(String.format("\t.Item:%s ~Vec3d:%s %08X %s %s",
					drop.itemName, drop.markerName, drop.type, pickupFlag, spawnFlag));
			}
			lines.add("}");
			lines.add("");
		}

		lines.add("#new:ShakeTreeEvent " + dataName);
		lines.add("{");
		lines.add("\t" + (0 >= trunkModels.size() ? "00000000" : trunkModelListName));
		lines.add("\t" + (0 >= leafModels.size() ? "00000000" : leafModelListName));
		lines.add("\t" + (0 >= drops.size() ? "00000000" : dropListName));
		lines.add("\t" + (0 >= vectors.size() ? "00000000" : fxPosListName));
		lines.add("\t" + (!tree.hasCallback.get() ? "00000000" : callbackName));
		lines.add("}");
		lines.add("");

		lines.add("#new:TriggerCoord " + triggerCoordName);
		lines.add("{");
		lines.add("\t~BombPos:" + tree.bombPosName.get());
		lines.add("}");
		lines.add("");

		if (tree.hasCallback.get()) {
			callbackLines.add("#new:Script " + callbackName);
			callbackLines.add("{");
			callbackLines.add("\tReturn");
			callbackLines.add("\tEnd");
			callbackLines.add("}");
			callbackLines.add("");
		}

		String boundScript = "$" + autogenPrefix + (tree.isStarTree.get() ? "Script_Foliage_ShakeStarTree" : "Script_Foliage_ShakeTree");
		bindingLines.add("Set   *Var[0]  " + dataName);
		bindingLines.add("Bind  " + boundScript + " .Trigger:WallHammer ~Collider:" + tree.colliderName.get() + " 00000001 00000000");
		bindingLines.add("Bind  " + boundScript + " .Trigger:PointBomb " + triggerCoordName + " 00000001 00000000");
	}

	private void addDarkness(List<String> lines)
	{
		if (!map.scripts.isDark.get())
			return;

		importResource("DarkRoom.mpat");
		mainHooks.add("Exec  " + script_DarkRoom);
	}

	private void addFog(List<String> lines) throws InvalidInputException
	{
		if (!map.scripts.worldFogSettings.enabled.get() && !map.scripts.entityFogSettings.enabled.get())
			return;

		lines.add("#new:Function $Function_SetupFog");
		lines.add("{");
		lines.add("\tPUSH RA");
		FogSettings fog = map.scripts.worldFogSettings;
		if (fog.enabled.get()) {
			lines.add("\tJAL       8011BB50");
			lines.add("\tNOP");
			lines.add(String.format("\tADDIU     A0, R0, %X", fog.start.get()));
			lines.add("\tJAL       8011BB74");
			lines.add(String.format("\tADDIU     A1, R0, %X", fog.end.get()));
			lines.add(String.format("\tADDIU     A0, R0, %X", fog.R.get()));
			lines.add(String.format("\tADDIU     A1, R0, %X", fog.G.get()));
			lines.add(String.format("\tADDIU     A2, R0, %X", fog.B.get()));
			lines.add("\tJAL       8011BB88");
			lines.add(String.format("\tADDIU     A3, R0, %X", fog.A.get()));
		}
		fog = map.scripts.entityFogSettings;
		if (fog.enabled.get()) {
			lines.add("\tJAL       80122FEC");
			lines.add("\tNOP");
			lines.add(String.format("\tADDIU     A0, R0, %X", fog.start.get()));
			lines.add("\tJAL       80123010");
			lines.add(String.format("\tADDIU     A1, R0, %X", fog.end.get()));
			lines.add(String.format("\tADDIU     A0, R0, %X", fog.R.get()));
			lines.add(String.format("\tADDIU     A1, R0, %X", fog.G.get()));
			lines.add(String.format("\tADDIU     A2, R0, %X", fog.B.get()));
			lines.add("\tJAL       80123028");
			lines.add(String.format("\tADDIU     A3, R0, %X", fog.A.get()));
		}
		lines.add("\tADDIU     V0, R0, 2");
		lines.add("\tJPOP RA");
		lines.add("}");
		lines.add("");

		mainHooks.add("Call  $Function_SetupFog");
	}

	private void addPanners(List<String> lines) throws InvalidInputException
	{
		boolean hasPanners = false;
		for (int i = 0; i < map.scripts.texPanners.size(); i++) {
			TexturePanner panner = map.scripts.texPanners.get(i);
			if (panner.params.generate || panner.isNonzero())
				hasPanners = true;
		}

		if (!hasPanners)
			return;

		importResource("UpdateTexturePan.mpat");

		lines.add("#new:Script " + script_TexPan);
		lines.add("{");
		for (Model mdl : map.modelTree) {
			if (mdl.pannerID.get() >= 0) {
				validateObject(script_TexPan, "Model name", MapObjectType.MODEL, mdl.getName());
				lines.add(String.format("\tCall     802C9000 	( ~Model:%s %X )", mdl.getName(), mdl.pannerID.get()));
			}
		}
		for (int i = 0; i < map.scripts.texPanners.size(); i++) {
			TexturePanner panner = map.scripts.texPanners.get(i);
			if (panner.params.generate || panner.isNonzero()) {
				lines.add("\tThread");
				lines.add(String.format("\t\tSet  *Var[0] %X", panner.panID));

				lines.add(String.format("\t\tSet  *Var[1] %X", panner.params.rate[0]));
				lines.add(String.format("\t\tSet  *Var[2] %X", panner.params.rate[1]));
				lines.add(String.format("\t\tSet  *Var[3] %X", panner.params.rate[2]));
				lines.add(String.format("\t\tSet  *Var[4] %X", panner.params.rate[3]));

				lines.add(String.format("\t\tSet  *Var[5] %X", panner.params.freq[0]));
				lines.add(String.format("\t\tSet  *Var[6] %X", panner.params.freq[1]));
				lines.add(String.format("\t\tSet  *Var[7] %X", panner.params.freq[2]));
				lines.add(String.format("\t\tSet  *Var[8] %X", panner.params.freq[3]));

				lines.add(String.format("\t\tSet  *Var[9] %X", panner.params.init[0]));
				lines.add(String.format("\t\tSet  *Var[A] %X", panner.params.init[1]));
				lines.add(String.format("\t\tSet  *Var[B] %X", panner.params.init[2]));
				lines.add(String.format("\t\tSet  *Var[C] %X", panner.params.init[3]));

				lines.add("\t\tExec $" + autogenPrefix + "Script_TexturePan_Update");
				lines.add("\tEndThread");
			}
		}
		lines.add("\tReturn");
		lines.add("\tEnd");
		lines.add("}");
		lines.add("");

		mainHooks.add("Exec  " + script_TexPan);
	}

	public MapObject validateObject(String sourceName, String fieldName, MapObjectType type, String name) throws InvalidInputException
	{
		if (name == null || name.isEmpty())
			throw new InvalidInputException("(%s for %s) %s is missing!", fieldName, sourceName, type);

		if (name.contains(" ") || name.contains("\t"))
			throw new InvalidInputException("(%s for %s) %s name \"%s\" contains illegal characters for script!",
				fieldName, sourceName, type, name);

		MapObject obj = map.find(type, name);
		if (obj == null)
			throw new InvalidInputException("(%s for %s) Could not find %s named \"%s\"!", fieldName, sourceName, type, name);

		return obj;
	}

	public Marker validateObject(String sourceName, String fieldName, MarkerType type, String entryMarkerName) throws InvalidInputException
	{
		Marker m = (Marker) validateObject(sourceName, fieldName, MapObjectType.MARKER, entryMarkerName);
		if (m.getType() != type)
			throw new InvalidInputException("(%s for %s) %s must be %s!", fieldName, sourceName, m.getName(), type);
		return m;
	}

	private void importResource(String name)
	{
		if (imports.contains(name))
			return;

		String lastLine = null;
		for (String s : readScriptTemplate(name)) {
			importLines.add(s);
			lastLine = s;
		}

		// always ensure a padding line is present
		if (lastLine != null && !lastLine.isEmpty())
			importLines.add("");

		imports.add(name);
	}

	public static List<String> readScriptTemplate(String name)
	{
		List<String> lines = new ArrayList<>(); //Resource.getText(ResourceType.ScriptGenTemplate, name);
		for (int i = 0; i < lines.size(); i++) {
			String original = lines.get(i);
			String fixed = original.replaceAll("##PREFIX", autogenPrefix);
			lines.set(i, fixed);
		}
		return lines;
	}
}
