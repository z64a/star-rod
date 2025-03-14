package game.map;

import static game.map.MapKey.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Stack;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.w3c.dom.Element;

import app.Directories;
import app.SwingUtils;
import app.input.IOUtils;
import assets.AssetManager;
import common.commands.AbstractCommand;
import common.commands.CommandBatch;
import game.map.MapObject.MapObjectType;
import game.map.editor.EditorObject;
import game.map.editor.MapEditor;
import game.map.editor.MapEditor.MapEditorMetadata;
import game.map.editor.commands.CreateObjects;
import game.map.editor.render.TextureManager;
import game.map.editor.selection.PickRay;
import game.map.editor.selection.PickRay.PickHit;
import game.map.hit.Collider;
import game.map.hit.Zone;
import game.map.impex.AssimpImporter;
import game.map.impex.AssimpImporter.AssimpImportOptions;
import game.map.impex.ObjExporter;
import game.map.impex.ObjImporter;
import game.map.marker.Marker;
import game.map.marker.Marker.MarkerType;
import game.map.mesh.AbstractMesh;
import game.map.mesh.TexturedMesh;
import game.map.mesh.Triangle;
import game.map.mesh.Vertex;
import game.map.scripts.LightingPanel;
import game.map.scripts.ScriptData;
import game.map.scripts.extract.EntryListExtractor;
import game.map.scripts.extract.HeaderEntry;
import game.map.scripts.extract.MapPropertiesExtractor;
import game.map.scripts.extract.TexPannerExtractor;
import game.map.shape.LightSet;
import game.map.shape.LightSet.LightSetDigest;
import game.map.shape.Model;
import game.map.shape.UV;
import game.map.tree.ColliderTreeModel;
import game.map.tree.MapObjectNode;
import game.map.tree.MapObjectTreeModel;
import game.map.tree.MarkerTreeModel;
import game.map.tree.ModelTreeModel;
import game.map.tree.ZoneTreeModel;
import util.IterableListModel;
import util.Logger;
import util.identity.IdentityHashSet;
import util.xml.XmlKey;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlSerializable;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class Map implements XmlSerializable
{
	private static final int latestVersion = 3;
	private int instanceVersion = latestVersion;

	public MapEditorMetadata editorData = null;

	public MapObjectTreeModel<Model> modelTree;
	public MapObjectTreeModel<Collider> colliderTree;
	public MapObjectTreeModel<Zone> zoneTree;
	public MapObjectTreeModel<Marker> markerTree;

	public ScriptData scripts;

	public IterableListModel<LightSet> lightSets;

	private String name;
	private String areaName;
	private String areaAffix;
	private File projDir;

	public String texName;
	public boolean hasBackground;
	public String bgName = NO_BG;

	public boolean isStage;

	public String desc;

	public static final String NO_BG = "none";

	public List<String> areaByteNames = new ArrayList<>();
	public List<String> areaFlagNames = new ArrayList<>();
	public List<String> mapVarNames = new ArrayList<>();
	public List<String> mapFlagNames = new ArrayList<>();

	// for reading names during map decompiling
	public transient Queue<String> d_modelNames;
	public transient Queue<String> d_colliderNames;
	public transient Queue<String> d_zoneNames;

	// used by the editor for bookkeeping
	//	public transient File source;
	//	public transient File saveFile;
	public transient boolean modified = false;
	public transient BufferedImage bgImage = null;
	public transient int glBackgroundTexID = -1;
	public transient long lastModified;

	@Override
	public void fromXML(XmlReader xmr, Element mapElem)
	{
		if (xmr.hasAttribute(mapElem, ATTR_DESC))
			desc = xmr.getAttribute(mapElem, ATTR_DESC);

		if (xmr.hasAttribute(mapElem, ATTR_MAP_STAGE))
			isStage = xmr.readBoolean(mapElem, ATTR_MAP_STAGE);

		xmr.requiresAttribute(mapElem, ATTR_MAP_TEX);
		texName = xmr.getAttribute(mapElem, ATTR_MAP_TEX);

		hasBackground = xmr.hasAttribute(mapElem, ATTR_MAP_BG);
		if (hasBackground)
			bgName = xmr.getAttribute(mapElem, ATTR_MAP_BG);

		lightSets = new IterableListModel<>();
		Element lightsetList = xmr.getUniqueRequiredTag(mapElem, TAG_LIGHTSETS);
		for (Element lightsetElem : xmr.getTags(lightsetList, TAG_LIGHTSET))
			lightSets.addElement(LightSet.read(xmr, lightsetElem));

		if (lightSets.isEmpty())
			lightSets.addElement(LightSet.createEmptySet());

		MapObjectNode<Model> modelRoot = readTree((elem) -> Model.read(xmr, elem),
			xmr, mapElem, TAG_MODELS, TAG_MODEL, TAG_MODEL_TREE);
		modelTree = new ModelTreeModel(modelRoot);

		MapObjectNode<Collider> colliderRoot = readTree((elem) -> Collider.read(xmr, elem),
			xmr, mapElem, TAG_COLLIDERS, TAG_COLLIDER, TAG_COLLIDER_TREE);
		colliderTree = new ColliderTreeModel(colliderRoot);

		MapObjectNode<Zone> zoneRoot = readTree((elem) -> Zone.read(xmr, elem),
			xmr, mapElem, TAG_ZONES, TAG_ZONE, TAG_ZONE_TREE);
		zoneTree = new ZoneTreeModel(zoneRoot);

		MapObjectNode<Marker> markerRoot = readTree((elem) -> Marker.read(xmr, elem),
			xmr, mapElem, TAG_MARKERS, TAG_MARKER, TAG_MARKER_TREE);
		markerTree = new MarkerTreeModel(markerRoot);

		for (Model mdl : modelTree.getList())
			mdl.lights.set(lightSets.get(mdl.lightsIndex));

		scripts = new ScriptData();

		Element scriptsElem = xmr.getUniqueTag(mapElem, TAG_SCRIPT_DATA);
		if (scriptsElem != null)
			scripts.fromXML(xmr, scriptsElem);

		Element editorElem = xmr.getUniqueTag(mapElem, TAG_EDITOR);
		if (editorElem != null) {
			editorData = new MapEditorMetadata(null);
			editorData.fromXML(xmr, editorElem);
		}
	}

	/**
	 * Reads the nodes for a complete MapObjectTreeModel from XML, along with its
	 * constituent MapObjects. Does not construct the tree itself. Takes a Function
	 * argument to create MapObjects via their static deserialization methods.
	 * @return root of the MapObjectTreeModel
	 */
	private <T extends MapObject> MapObjectNode<T> readTree(
		Function<Element, T> supplier,
		XmlReader xmr,
		Element mapElem,
		XmlKey listKey, XmlKey objKey, XmlKey treeKey)
	{
		Element elemList = xmr.getUniqueRequiredTag(mapElem, listKey);
		List<Element> objNodes = xmr.getTags(elemList, objKey);
		HashMap<Integer, T> objMap = new HashMap<>(objNodes.size());
		for (Element objElement : objNodes) {
			T obj = supplier.apply(objElement);
			objMap.put(obj.deserializationID, obj);
		}

		Element objTreeElement = xmr.getUniqueRequiredTag(mapElem, treeKey);
		return MapObjectTreeModel.load(xmr, objTreeElement, objMap);
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag root = xmw.createTag(TAG_MAP, false);

		if (desc != null && !desc.isBlank())
			xmw.addAttribute(root, ATTR_DESC, desc);

		if (isStage)
			xmw.addBoolean(root, ATTR_MAP_STAGE, isStage);

		if (hasBackground)
			xmw.addAttribute(root, ATTR_MAP_BG, bgName);

		xmw.addAttribute(root, ATTR_MAP_TEX, texName);
		xmw.openTag(root);

		if (editorData != null) {
			XmlTag editorTag = xmw.createTag(TAG_EDITOR, false);
			xmw.openTag(editorTag);
			editorData.toXML(xmw);
			xmw.closeTag(editorTag);
		}

		XmlTag mdlTreeTag = xmw.createTag(TAG_MODEL_TREE, false);
		xmw.openTag(mdlTreeTag);
		modelTree.toXML(xmw);
		xmw.closeTag(mdlTreeTag);

		XmlTag colTreeTag = xmw.createTag(TAG_COLLIDER_TREE, false);
		xmw.openTag(colTreeTag);
		colliderTree.toXML(xmw);
		xmw.closeTag(colTreeTag);

		XmlTag zoneTreeTag = xmw.createTag(TAG_ZONE_TREE, false);
		xmw.openTag(zoneTreeTag);
		zoneTree.toXML(xmw);
		xmw.closeTag(zoneTreeTag);

		XmlTag markerTreeTag = xmw.createTag(TAG_MARKER_TREE, false);
		xmw.openTag(markerTreeTag);
		markerTree.toXML(xmw);
		xmw.closeTag(markerTreeTag);

		XmlTag lightsetsTag = xmw.createTag(TAG_LIGHTSETS, false);
		xmw.openTag(lightsetsTag);
		for (int i = 0; i < lightSets.size(); i++) {
			LightSet lights = lightSets.get(i);
			lights.io_listIndex = i;
			lights.toXML(xmw);
		}
		xmw.closeTag(lightsetsTag);

		XmlTag modelsTag = xmw.createTag(TAG_MODELS, false);
		xmw.openTag(modelsTag);
		for (Model mdl : modelTree.getList())
			mdl.toXML(xmw);
		xmw.closeTag(modelsTag);

		XmlTag collidersTag = xmw.createTag(TAG_COLLIDERS, false);
		xmw.openTag(collidersTag);
		for (Collider c : colliderTree.getList())
			c.toXML(xmw);
		xmw.closeTag(collidersTag);

		XmlTag zonesTag = xmw.createTag(TAG_ZONES, false);
		xmw.openTag(zonesTag);
		for (Zone z : zoneTree.getList())
			z.toXML(xmw);
		xmw.closeTag(zonesTag);

		XmlTag markersTag = xmw.createTag(TAG_MARKERS, false);
		xmw.openTag(markersTag);
		for (Marker m : markerTree.getList())
			m.toXML(xmw);
		xmw.closeTag(markersTag);

		XmlTag scriptsTag = xmw.createTag(TAG_SCRIPT_DATA, false);
		xmw.openTag(scriptsTag);
		scripts.toXML(xmw);
		xmw.closeTag(scriptsTag);

		xmw.closeTag(root);
	}

	/**
	 * For serialization purposes only!
	 */
	public Map()
	{}

	public Map(String name)
	{
		this.instanceVersion = latestVersion;
		setName(name);

		modelTree = new ModelTreeModel();
		colliderTree = new ColliderTreeModel();
		zoneTree = new ZoneTreeModel();
		markerTree = new MarkerTreeModel();

		lightSets = new IterableListModel<>();
		lightSets.addElement(LightSet.createEmptySet());
		modelTree.getRoot().getUserObject().lights.set(lightSets.get(0));

		scripts = new ScriptData();
	}

	private void setName(String name)
	{
		this.name = name;
		areaAffix = name.substring(0, 3);

		while (areaAffix.endsWith("_")) {
			areaAffix = areaAffix.substring(0, areaAffix.length() - 1);
		}

		areaName = "area_" + areaAffix;
		projDir = Directories.PROJ_SRC_WORLD.file(areaName + "/" + name);
	}

	public String getName()
	{
		return name;
	}

	public File getProjDir()
	{
		return projDir;
	}

	public void add(MapObject obj)
	{
		switch (obj.getObjectType()) {
			case MODEL:
				modelTree.add((Model) obj);
				TextureManager.increment((Model) obj);
				break;
			case COLLIDER:
				colliderTree.add((Collider) obj);
				break;
			case ZONE:
				zoneTree.add((Zone) obj);
				break;
			case MARKER:
				markerTree.add((Marker) obj);
				break;
			case EDITOR:
				throw new IllegalStateException("Cannot add EDITOR object! " + obj.getName());
		}
	}

	public void remove(MapObject obj)
	{
		switch (obj.getObjectType()) {
			case MODEL:
				modelTree.remove((Model) obj);
				TextureManager.decrement((Model) obj);
				break;
			case COLLIDER:
				colliderTree.remove((Collider) obj);
				break;
			case ZONE:
				zoneTree.remove((Zone) obj);
				break;
			case MARKER:
				markerTree.remove((Marker) obj);
				break;
			case EDITOR:
				throw new IllegalStateException("Cannot remove EDITOR object! " + obj.getName());
		}
	}

	public void create(MapObject obj)
	{
		switch (obj.getObjectType()) {
			case MODEL:
				modelTree.create((Model) obj);
				TextureManager.increment((Model) obj);
				break;
			case COLLIDER:
				colliderTree.create((Collider) obj);
				break;
			case ZONE:
				zoneTree.create((Zone) obj);
				break;
			case MARKER:
				markerTree.create((Marker) obj);
				break;
			case EDITOR:
				throw new IllegalStateException("Cannot create EDITOR object! " + obj.getName());
		}

		obj.initialize();
	}

	@Override
	public String toString()
	{
		return name;
	}

	public List<MapObject> getObjectsWithinRegion(BoundingBox selectionBox, List<EditorObject> editorObjects)
	{
		List<MapObject> objs = new LinkedList<>();

		for (MapObject o : editorObjects)
			if (!o.hidden && selectionBox.contains(o.AABB.getCenter()))
				objs.add(o);
		for (MapObject o : modelTree)
			if (!o.hidden && selectionBox.contains(o.AABB.getCenter()))
				objs.add(o);
		for (MapObject o : colliderTree)
			if (!o.hidden && selectionBox.contains(o.AABB.getCenter()))
				objs.add(o);
		for (MapObject o : zoneTree)
			if (!o.hidden && selectionBox.contains(o.AABB.getCenter()))
				objs.add(o);
		for (MapObject o : markerTree)
			if (!o.hidden && selectionBox.contains(o.AABB.getCenter()))
				objs.add(o);

		return objs;
	}

	public PickHit pickNearestObject(PickRay pickRay, MapObjectType favoredType, List<EditorObject> editorObjects)
	{
		// list order = selection priority!
		LinkedList<MapObject> candidates = new LinkedList<>();

		switch (favoredType) {
			case MODEL:
				for (MapObject o : modelTree)
					if (!o.hidden && o.shouldTryPick(pickRay))
						candidates.add(o);
				break;
			case COLLIDER:
				for (MapObject o : colliderTree)
					if (!o.hidden && o.shouldTryPick(pickRay))
						candidates.add(o);
				break;
			case ZONE:
				for (MapObject o : zoneTree)
					if (!o.hidden && o.shouldTryPick(pickRay))
						candidates.add(o);
				break;
			case MARKER:
				for (MapObject o : markerTree)
					if (!o.hidden && o.shouldTryPick(pickRay))
						candidates.add(o);
				break;
			case EDITOR:
				for (MapObject o : editorObjects)
					if (!o.hidden && o.shouldTryPick(pickRay))
						candidates.add(o);
				break;
		}

		if (favoredType != MapObjectType.EDITOR) {
			for (MapObject o : editorObjects)
				if (!o.hidden && o.shouldTryPick(pickRay))
					candidates.add(o);
		}

		if (favoredType != MapObjectType.MODEL) {
			for (MapObject o : modelTree)
				if (!o.hidden && o.shouldTryPick(pickRay))
					candidates.add(o);
		}

		if (favoredType != MapObjectType.COLLIDER) {
			for (MapObject o : colliderTree)
				if (!o.hidden && o.shouldTryPick(pickRay))
					candidates.add(o);
		}

		if (favoredType != MapObjectType.ZONE) {
			for (MapObject o : zoneTree)
				if (!o.hidden && o.shouldTryPick(pickRay))
					candidates.add(o);
		}

		if (favoredType != MapObjectType.MARKER) {
			for (MapObject o : markerTree)
				if (!o.hidden && o.shouldTryPick(pickRay))
					candidates.add(o);
		}

		return pickObjectFromSet(pickRay, candidates);
	}

	public static <T extends MapObject> PickHit pickObjectFromSet(PickRay pickRay, Iterable<T> candidates)
	{
		return pickObjectFromSet(pickRay, candidates, true);
	}

	public static <T extends MapObject> PickHit pickObjectFromSet(PickRay pickRay, Iterable<T> candidates, boolean skipHidden)
	{
		PickHit closestHit = new PickHit(pickRay);
		for (T obj : candidates) {
			if (skipHidden && obj.hidden)
				continue;

			PickHit hit = obj.tryPick(pickRay);
			if (hit.dist < closestHit.dist) {
				closestHit = hit;
				closestHit.obj = obj;
			}
		}

		return closestHit;
	}

	public PickHit pickNearestTriangle(PickRay pickRay)
	{
		ArrayList<MapObject> candidates = new ArrayList<>();
		for (Model mdl : modelTree)
			if (!mdl.hidden && mdl.shouldTryPick(pickRay))
				candidates.add(mdl);
		for (Collider c : colliderTree)
			if (!c.hidden && c.shouldTryPick(pickRay))
				candidates.add(c);
		for (Zone z : zoneTree)
			if (!z.hidden && z.shouldTryPick(pickRay))
				candidates.add(z);

		return pickTriangleFromObjectList(pickRay, candidates);
	}

	public List<Triangle> getTrianglesWithinRegion(BoundingBox box)
	{
		List<Triangle> triangleList = new ArrayList<>();

		ArrayList<AbstractMesh> meshes = new ArrayList<>();
		for (Model mdl : modelTree)
			if (!mdl.hidden && mdl.hasMesh())
				meshes.add(mdl.getMesh());
		for (Collider c : colliderTree)
			if (!c.hidden && c.hasMesh())
				meshes.add(c.mesh);
		for (Zone z : zoneTree)
			if (!z.hidden && z.hasMesh())
				meshes.add(z.mesh);

		for (AbstractMesh m : meshes) {
			for (Triangle t : m) {
				if (box.contains(t.getCenter()) && !triangleList.contains(t))
					triangleList.add(t);
			}
		}

		return triangleList;
	}

	public static PickHit pickTriangleFromObjectList(PickRay pickRay, Iterable<? extends MapObject> candidates)
	{
		PickHit closestHit = new PickHit(pickRay, Float.MAX_VALUE);
		for (MapObject obj : candidates) {
			if (obj.hidden)
				continue;

			for (Triangle t : obj.getMesh()) {
				PickHit hit = PickRay.getIntersection(pickRay, t);
				if (hit.dist < closestHit.dist) {
					closestHit = hit;
					closestHit.obj = t;
				}
			}
		}
		return closestHit;
	}

	public static PickHit pickTriangleFromList(PickRay pickRay, Iterable<Triangle> candidates)
	{
		PickHit closestHit = new PickHit(pickRay, Float.MAX_VALUE);
		for (Triangle t : candidates) {
			PickHit hit = PickRay.getIntersection(pickRay, t);
			if (hit.dist < closestHit.dist) {
				closestHit = hit;
				closestHit.obj = t;
			}
		}
		return closestHit;
	}

	public static PickHit pickVertexFromList(PickRay pickRay, Iterable<Vertex> candidates)
	{
		PickHit closestHit = new PickHit(pickRay, Float.MAX_VALUE);
		for (Vertex v : candidates) {
			PickHit hit = PickRay.getPointIntersection(pickRay, v.getCurrentX(), v.getCurrentY(), v.getCurrentZ(), 1.0f);
			if (hit.dist < closestHit.dist) {
				closestHit = hit;
				closestHit.obj = v;
			}
		}
		return closestHit;
	}

	public static PickHit pickUVFromList(PickRay pickRay, Iterable<UV> candidates)
	{
		PickHit closestHit = new PickHit(pickRay, Float.MAX_VALUE);
		for (UV uv : candidates) {
			PickHit hit = PickRay.getIntersection(pickRay, uv);
			if (hit.dist < closestHit.dist) {
				closestHit = hit;
				closestHit.obj = uv;
			}
		}
		return closestHit;
	}

	public Iterable<Vertex> getVerticesWithinVolume(BoundingBox viewingVolume)
	{
		LinkedList<MapObject> objects = new LinkedList<>();

		for (MapObject o : modelTree)
			if (!o.hidden && o.hasMesh() && o.AABB.overlaps(viewingVolume))
				objects.add(o);

		for (MapObject o : colliderTree)
			if (!o.hidden && o.hasMesh() && o.AABB.overlaps(viewingVolume))
				objects.add(o);

		for (MapObject o : zoneTree)
			if (!o.hidden && o.hasMesh() && o.AABB.overlaps(viewingVolume))
				objects.add(o);

		IdentityHashSet<Vertex> vertices = new IdentityHashSet<>();
		for (MapObject obj : objects) {
			for (Triangle t : obj.getMesh()) {
				for (Vertex v : t.vert) {
					if (viewingVolume.contains(v))
						vertices.add(v);
				}
			}
		}
		return vertices;
	}

	public Iterable<Vertex> getVerticesWithinVolume(BoundingBox viewingVolume, MapObjectType category)
	{
		LinkedList<MapObject> objects = new LinkedList<>();

		switch (category) {
			case MODEL:
				for (MapObject o : modelTree)
					if (!o.hidden && o.hasMesh() && o.AABB.overlaps(viewingVolume))
						objects.add(o);
				break;
			case COLLIDER:
				for (MapObject o : colliderTree)
					if (!o.hidden && o.hasMesh() && o.AABB.overlaps(viewingVolume))
						objects.add(o);
				break;
			case ZONE:
				for (MapObject o : zoneTree)
					if (!o.hidden && o.hasMesh() && o.AABB.overlaps(viewingVolume))
						objects.add(o);
				break;
			default:
				break;
		}

		IdentityHashSet<Vertex> vertices = new IdentityHashSet<>();
		for (MapObject obj : objects) {
			for (Triangle t : obj.getMesh()) {
				for (Vertex v : t.vert) {
					if (viewingVolume.contains(v))
						vertices.add(v);
				}
			}
		}
		return vertices;
	}

	public static Map loadMap(File f)
	{
		long t0 = System.nanoTime();
		Map map = null;

		XmlReader xmr = new XmlReader(f);
		map = new Map();
		map.fromXML(xmr, xmr.getRootElement());

		map.setName(deriveName(f));
		map.lastModified = f.lastModified();
		validateObjectData(map);

		long t1 = System.nanoTime();
		double sec = (t1 - t0) / 1e9;
		if (sec > 0.5)
			Logger.logf("Loaded %s in %.02f seconds", f.getName(), sec);
		else
			Logger.logf("Loaded %s in %.02f ms", f.getName(), sec * 1e3);

		return map;
	}

	private static final Matcher AreaByteMatcher = Pattern.compile("\\s*(\\w+)\\s*=\\s*AreaByte\\(\\d+\\),?.*").matcher("");
	private static final Matcher AreaFlagMatcher = Pattern.compile("\\s*(\\w+)\\s*=\\s*AreaFlag\\(\\d+\\),?.*").matcher("");
	private static final Matcher MapVarMatcher = Pattern.compile("\\s*(\\w+)\\s*=\\s*MapVar\\(\\d+\\),?.*").matcher("");
	private static final Matcher MapFlagMatcher = Pattern.compile("\\s*(\\w+)\\s*=\\s*MapFlag\\(\\d+\\),?.*").matcher("");

	public void loadVarNames()
	{
		String areaHeaderName = String.format("%s/%s.h", areaName, areaAffix);
		File areaHeader = Directories.PROJ_SRC_WORLD.file(areaHeaderName);

		areaByteNames.clear();
		areaFlagNames.clear();

		if (areaHeader.exists()) {
			try {
				for (String line : IOUtils.readPlainTextFile(areaHeader)) {
					AreaByteMatcher.reset(line);
					if (AreaByteMatcher.matches()) {
						areaByteNames.add(AreaByteMatcher.group(1));
						continue;
					}

					AreaFlagMatcher.reset(line);
					if (AreaFlagMatcher.matches()) {
						areaFlagNames.add(AreaFlagMatcher.group(1));
						continue;
					}
				}
			}
			catch (IOException e) {
				Logger.printStackTrace(e);
				Logger.log("Could not read area variables from " + areaHeaderName);
			}
		}

		String mapHeaderName = String.format("%s/%s/%s.h", areaName, name, name);
		File mapHeader = Directories.PROJ_SRC_WORLD.file(mapHeaderName);

		mapVarNames.clear();
		mapFlagNames.clear();

		if (mapHeader.exists()) {
			try {
				for (String line : IOUtils.readPlainTextFile(mapHeader)) {
					MapVarMatcher.reset(line);
					if (MapVarMatcher.matches()) {
						mapVarNames.add(MapVarMatcher.group(1));
						continue;
					}

					MapFlagMatcher.reset(line);
					if (MapFlagMatcher.matches()) {
						mapFlagNames.add(MapFlagMatcher.group(1));
						continue;
					}
				}
			}
			catch (IOException e) {
				Logger.printStackTrace(e);
				Logger.log("Could not read map variables from " + areaHeaderName);
			}
		}
	}

	public static void validateObjectData(Map map)
	{
		if (map.modelTree == null)
			Logger.logWarning("Map has no geometry data!");

		map.recalculateBoundingBoxes();

		MapObjectNode<Model> modelRoot = map.modelTree.getRoot();
		modelRoot.getUserObject().updateTransformHierarchy();
		map.modelTree.recalculateIndicies();

		if (map.colliderTree == null)
			Logger.logWarning("Map has no collider data!");

		map.colliderTree.recalculateIndicies();

		if (map.zoneTree == null)
			Logger.logWarning("Map has no zone data!");

		map.zoneTree.recalculateIndicies();

		if (map.markerTree == null)
			Logger.logWarning("Map has no marker data!");

		map.markerTree.recalculateIndicies();
	}

	public void initializeAllObjects()
	{
		for (MapObject obj : modelTree.getList()) {
			obj.initialize();
			obj.captureOriginalName();
		}

		for (MapObject obj : colliderTree.getList()) {
			obj.initialize();
			obj.captureOriginalName();
		}

		for (MapObject obj : zoneTree.getList()) {
			obj.initialize();
			obj.captureOriginalName();
		}

		for (MapObject obj : markerTree.getList())
			obj.initialize();
	}

	/**
	 * Scans through all MapObjects and rebuilds bounding boxes for any marked
	 * with dirtyAABB. Bounding box changes are propagated up the tree hierarchy.
	 */
	public void recalculateBoundingBoxes()
	{
		modelTree.recalculateBoundingBoxes();
		colliderTree.recalculateBoundingBoxes();
		zoneTree.recalculateBoundingBoxes();
		markerTree.recalculateBoundingBoxes();
	}

	public static String deriveName(File f)
	{
		String name = f.getName();
		int firstDot = name.indexOf('.');
		if (firstDot >= 0) {
			name = name.substring(0, firstDot);
		}
		return name;
	}

	public void saveMapWithoutHeader(File file) throws Exception
	{
		this.editorData = null;
		saveMapAs_impl(file, false);
	}

	public void saveMap() throws Exception
	{
		this.editorData = null;
		saveMapAs_impl(AssetManager.getSaveMapFile(name), true);
	}

	public void saveMap(MapEditorMetadata editorData) throws Exception
	{
		this.editorData = editorData;
		saveMapAs_impl(AssetManager.getSaveMapFile(name), true);
	}

	public void saveMapAs(File file) throws Exception
	{
		this.editorData = null;
		saveMapAs_impl(file, true);
	}

	public void saveMapAs(File file, MapEditorMetadata editorData) throws Exception
	{
		this.editorData = editorData;
		saveMapAs_impl(file, true);
	}

	private void saveMapAs_impl(File file, boolean generateHeader) throws Exception
	{
		FileUtils.touch(file);
		File tempFile = new File(file.getAbsolutePath() + ".temp");

		long t0 = System.nanoTime();

		markerTree.recalculateIndicies();

		try (XmlWriter xmw = new XmlWriter(tempFile)) {
			toXML(xmw);
			xmw.save();
		} // flushed on auto-close

		FileUtils.copyFile(tempFile, file);
		FileUtils.deleteQuietly(tempFile);

		long t1 = System.nanoTime();
		double sec = (t1 - t0) / 1e9;
		if (sec > 0.5)
			Logger.logf("Saved %s in %.02f seconds", file.getName(), sec);
		else
			Logger.logf("Saved %s in %.02f ms", file.getName(), sec * 1e3);

		setName(Map.deriveName(file));
		lastModified = file.lastModified();
		modified = false;

		if (generateHeader) {
			tryInjectHeader();
			writeHeader();
		}
	}

	/**
	 * Similar to the saveMap family of methods, but does not update map name of modification time fields.
	 */
	public void saveBackup(File f)
	{
		markerTree.recalculateIndicies();

		try (XmlWriter xmw = new XmlWriter(f)) {
			toXML(xmw);
			xmw.save();
		} // flushed on auto-close
		catch (Exception e) {
			Logger.printStackTrace(e);
			Logger.logError("Error during map backup: " + e.getMessage());
			return;
		}

		Logger.log("Saved backup for " + name);
	}

	public String getExpectedTexFilename()
	{
		// mac_tex, gv__tex, etc
		return name.substring(0, 3) + "_tex";
	}

	public List<MapObject> getCompleteSelection()
	{
		List<MapObject> objectList = new LinkedList<>();

		addSelectionFromTree(objectList, modelTree);
		addSelectionFromTree(objectList, colliderTree);
		addSelectionFromTree(objectList, zoneTree);
		addSelectionFromTree(objectList, markerTree);

		return objectList;
	}

	private <T extends MapObject> void addSelectionFromTree(List<MapObject> selectedObjects, MapObjectTreeModel<T> treeModel)
	{
		Stack<MapObjectNode<T>> nodes = new Stack<>();
		List<MapObjectNode<T>> selectedNodes = new ArrayList<>();

		MapObjectNode<T> root = treeModel.getRoot();
		nodes.push(root);

		while (!nodes.isEmpty()) {
			MapObjectNode<T> node = nodes.pop();
			T obj = node.getUserObject();

			if (obj.selected && node != root)
				selectedNodes.add(node);

			for (int i = 0; i < node.getChildCount(); i++)
				nodes.push(node.getChildAt(i));
		}

		// selection order not important, keep them in tree index order
		Collections.sort(selectedNodes);
		for (MapObjectNode<T> node : selectedNodes)
			selectedObjects.add(node.getUserObject());
	}

	public void addChildrenToList(List<MapObject> objectList)
	{
		Stack<MapObject> objectStack = new Stack<>();
		IdentityHashSet<MapObject> objectSet = new IdentityHashSet<>();

		for (MapObject obj : objectList) {
			objectStack.push(obj);
			objectSet.add(obj);
		}

		while (!objectStack.isEmpty()) {
			MapObject obj = objectStack.pop();
			MapObjectNode<? extends MapObject> node = obj.getNode();

			for (int i = 0; i < node.getChildCount(); i++) {
				MapObject child = node.getChildAt(i).getUserObject();

				if (!objectSet.contains(child)) {
					objectSet.add(child);
					objectList.add(child);
					objectStack.push(child);
				}
			}
		}
	}

	public int exportPrefab(File f, boolean quiet) throws IOException
	{
		List<MapObject> selectedList = getCompleteSelection();

		if (selectedList.size() > 0) {
			Prefab prefab = new Prefab(selectedList, lightSets, texName);

			try (XmlWriter xmw = new XmlWriter(f)) {
				prefab.toXML(xmw);
				xmw.save();
			}

			if (!quiet) {
				for (MapObject obj : selectedList)
					Logger.log("Exported " + obj.getName());
			}
		}

		return selectedList.size();
	}

	private int exportOBJ(File f) throws IOException
	{
		List<Model> models = modelTree.getList().stream()
			.filter(mdl -> mdl.isSelected() && mdl.hasMesh())
			.collect(Collectors.toList());

		List<Collider> colliders = colliderTree.getList().stream()
			.filter(c -> c.isSelected() && c.hasMesh())
			.collect(Collectors.toList());

		List<Zone> zones = zoneTree.getList().stream()
			.filter(c -> c.isSelected() && c.hasMesh())
			.collect(Collectors.toList());

		int num = models.size() + colliders.size() + zones.size();

		if (num == 0)
			return 0;

		ObjExporter exporter = new ObjExporter(f);
		exporter.writeModels(models, texName);
		exporter.writeColliders(colliders);
		exporter.writeZones(zones);
		exporter.close();

		Logger.log("Succesfully exported selected meshes to " + f.getName());
		return num;
	}

	public void exportToFile(File f) throws IOException
	{
		String ext = FilenameUtils.getExtension(f.getName());

		if (ext.equalsIgnoreCase("obj")) {
			exportOBJ(f);
		}
		else {
			SwingUtils.getWarningDialog()
				.setTitle("Export Failed")
				.setMessage("Unsupported export format: " + ext)
				.show();
		}
	}

	public static class PrefabImportData
	{
		private List<MapObject> topLevelObjects = new ArrayList<>();
		private List<LightSet> lightSets = new ArrayList<>();

		public CommandBatch getCommand(String commandName)
		{
			CommandBatch batch = new CommandBatch(commandName);
			batch.addCommand(new CreateObjects(topLevelObjects));

			for (LightSet set : lightSets)
				batch.addCommand(LightingPanel.instance().new CreateLightset(set));

			return batch;
		}

		public int getNumObjects()
		{
			return topLevelObjects.size();
		}
	}

	private <T extends MapObject> void importTree(List<MapObject> topLevelObjects, MapObjectTreeModel<T> prefabTree)
	{
		MapObjectNode<T> prefabRoot = prefabTree.getRoot();

		for (int i = 0; i < prefabRoot.getChildCount(); i++) {
			MapObjectNode<T> prefabChild = prefabRoot.getChildAt(i);
			prefabChild.parentNode = null;
			topLevelObjects.add(prefabChild.getUserObject());
		}
	}

	@SuppressWarnings("unchecked")
	private <T extends MapObject> void importTree(List<MapObject> topLevelObjects, MapObjectTreeModel<T> prefabTree, MapObjectNode<?> newRoot)
	{
		MapObjectNode<T> prefabRoot = prefabTree.getRoot();

		for (int i = 0; i < prefabRoot.getChildCount(); i++) {
			MapObjectNode<T> prefabChild = prefabRoot.getChildAt(i);
			prefabChild.parentNode = (MapObjectNode<T>) newRoot;
			topLevelObjects.add(prefabChild.getUserObject());
		}
	}

	public PrefabImportData importPrefab(File f)
	{
		XmlReader xmr = new XmlReader(f);
		Prefab prefab = Prefab.read(xmr);

		if (!prefab.texName.equals(texName) && prefab.modelTree.getRoot().getChildCount() > 0)
			Logger.logWarning("Prefab textures differ from current map: " + prefab.texName);

		PrefabImportData importData = new PrefabImportData();

		HashMap<LightSetDigest, LightSetDigest> hashedLightSets = new HashMap<>();

		for (LightSet lights : lightSets) {
			LightSetDigest digest = new LightSetDigest(lights);
			hashedLightSets.put(digest, digest);
		}

		HashMap<LightSet, LightSet> lightSetMap = new HashMap<>();
		for (LightSet importedSet : prefab.lightSets) {
			LightSetDigest digest = new LightSetDigest(importedSet);
			if (hashedLightSets.containsKey(digest)) {
				lightSetMap.put(importedSet, hashedLightSets.get(digest).lights);
			}
			else {
				lightSetMap.put(importedSet, importedSet);
				importData.lightSets.add(importedSet);
			}
		}

		for (Model mdl : prefab.modelTree.getList()) {
			if (mdl.hasMesh()) {
				TexturedMesh mesh = mdl.getMesh();
				mesh.setTexture(mesh.textureName);
			}

			mdl.lights.set(lightSetMap.get(mdl.lights.get()));
		}

		importTree(importData.topLevelObjects, prefab.modelTree);
		importTree(importData.topLevelObjects, prefab.colliderTree);
		importTree(importData.topLevelObjects, prefab.zoneTree);
		importTree(importData.topLevelObjects, prefab.markerTree);

		return importData;
	}

	public PrefabImportData importPrefab(File f, MapObjectNode<? extends MapObject> node)
	{
		MapObjectType filterType = node.getUserObject().getObjectType();

		XmlReader xmr = new XmlReader(f);
		Prefab prefab = Prefab.read(xmr);

		PrefabImportData importData = new PrefabImportData();

		if (filterType == MapObjectType.MODEL) {
			if (!prefab.texName.equals(texName) && prefab.modelTree.getRoot().getChildCount() > 0)
				Logger.logWarning("Prefab textures differ from current map: " + prefab.texName);

			HashMap<LightSet, LightSet> currentLightSets = new HashMap<>();
			for (LightSet set : lightSets)
				currentLightSets.put(set, set);

			HashMap<LightSet, LightSet> lightSetMap = new HashMap<>();
			for (LightSet importedSet : prefab.lightSets) {
				if (currentLightSets.containsKey(importedSet)) {
					lightSetMap.put(importedSet, currentLightSets.get(importedSet));
				}
				else {
					lightSetMap.put(importedSet, importedSet);
					importData.lightSets.add(importedSet);
				}
			}
		}

		switch (filterType) {
			case MODEL:
				importTree(importData.topLevelObjects, prefab.modelTree, node);
				break;
			case COLLIDER:
				importTree(importData.topLevelObjects, prefab.colliderTree, node);
				break;
			case ZONE:
				importTree(importData.topLevelObjects, prefab.zoneTree, node);
				break;
			case MARKER:
				importTree(importData.topLevelObjects, prefab.markerTree, node);
				break;
			default:
				throw new IllegalStateException("Cannot import objects of type: " + filterType);
		}

		return importData;
	}

	public void importFromFile(File f, MapObjectNode<? extends MapObject> node)
	{
		String ext = FilenameUtils.getExtension(f.getName());

		if (ext.equalsIgnoreCase("prefab")) {
			int numObjects = 0;

			PrefabImportData importData;
			if (node == null)
				importData = importPrefab(f);
			else
				importData = importPrefab(f, node);

			if (importData.getNumObjects() > 0) {
				MapEditor.execute(importData.getCommand("Import Objects"));
				numObjects = importData.getNumObjects();
			}

			Logger.log("Succesfully loaded " + numObjects + " objects from " + f.getName());
		}

		else if (ext.equalsIgnoreCase("obj")) {
			int numObjects = 0;

			MapObjectType type = (node == null) ? MapObjectType.MODEL : node.getObjectType();
			try {
				ObjImporter importer = new ObjImporter();
				switch (type) {
					case MODEL:
						List<Model> models = importer.readModels(f);
						MapEditor.execute(new CreateObjects(models));
						numObjects = models.size();
						break;

					case COLLIDER:
						List<Collider> colliders = importer.readColliders(f);
						MapEditor.execute(new CreateObjects(colliders));
						numObjects = colliders.size();
						break;

					case ZONE:
						List<Zone> zones = importer.readZones(f);
						MapEditor.execute(new CreateObjects(zones));
						numObjects = zones.size();
						break;

					default:
				}
			}
			catch (IOException e) {
				Logger.logError("IOException while reading " + f.getName());
				return;
			}

			Logger.log("Succesfully loaded " + numObjects + " objects from " + f.getName());
		}
	}

	@SuppressWarnings("unchecked")
	public void importViaAssimp(File f, AssimpImportOptions options, MapObjectNode<? extends MapObject> node)
	{
		int numObjects = 0;

		MapObjectType importAs;
		if (node == null)
			importAs = options.importAs.getType();
		else
			importAs = node.getObjectType();

		switch (importAs) {
			case MODEL:
				List<Model> models = AssimpImporter.importModels(f, options, (MapObjectNode<Model>) node);
				MapEditor.execute(new CreateObjects(models));
				numObjects = models.size();
				break;

			case COLLIDER:
				List<Collider> colliders = AssimpImporter.importColliders(f, options, (MapObjectNode<Collider>) node);
				MapEditor.execute(new CreateObjects(colliders));
				numObjects = colliders.size();
				break;

			case ZONE:
				List<Zone> zones = AssimpImporter.importZones(f, options, (MapObjectNode<Zone>) node);
				MapEditor.execute(new CreateObjects(zones));
				numObjects = zones.size();
				break;

			default:
		}

		Logger.log("Succesfully loaded " + numObjects + " objects from " + f.getName());
	}

	/*
	 * Returns the first object with a given name in a depth-first traversal
	 * of the corresponding object tree.
	 */
	public MapObject find(MapObjectType objType, String objName)
	{
		MapObjectTreeModel<?> tree = null;
		switch (objType) {
			case MODEL:
				tree = modelTree;
				break;
			case COLLIDER:
				tree = colliderTree;
				break;
			case ZONE:
				tree = zoneTree;
				break;
			case MARKER:
				tree = markerTree;
				break;
			case EDITOR:
				throw new IllegalStateException("Cannot search for EDITOR object! " + objName);
		}

		Stack<MapObjectNode<?>> stack = new Stack<>();
		stack.push(tree.getRoot());

		while (!stack.isEmpty()) {
			MapObjectNode<?> node = stack.pop();
			for (int i = 0; i < node.getChildCount(); i++)
				stack.push(node.getChildAt(i));

			MapObject obj = node.getUserObject();
			if (obj.getName().equals(objName))
				return obj;
		}

		return null;
	}

	public int count(MapObjectType objType, String objName)
	{
		List<String> names = getNameList(objType, true);
		int count = 0;
		for (String name : names) {
			if (name.equals(objName))
				count++;
		}
		return count;
	}

	public int count(MarkerType markerType, String objName)
	{
		List<String> names = getNameList(markerType);
		int count = 0;
		for (String name : names) {
			if (name.equals(objName))
				count++;
		}
		return count;
	}

	public List<String> getNameList(MapObjectType objType, boolean includeGroups)
	{
		List<String> nameList = new ArrayList<>();

		MapObjectTreeModel<?> tree = null;
		switch (objType) {
			case MODEL:
				tree = modelTree;
				break;
			case COLLIDER:
				tree = colliderTree;
				break;
			case ZONE:
				tree = zoneTree;
				break;
			case MARKER:
				tree = markerTree;
				break;
			case EDITOR:
				throw new IllegalStateException("Cannot look for EDITOR objects!");
		}

		Stack<MapObjectNode<?>> stack = new Stack<>();
		stack.push(tree.getRoot());

		while (!stack.isEmpty()) {
			MapObjectNode<?> node = stack.pop();
			for (int i = 0; i < node.getChildCount(); i++)
				stack.push(node.getChildAt(i));

			MapObject obj = node.getUserObject();
			if (!node.isRoot() && (includeGroups || node.isLeaf()))
				nameList.add(obj.getName());
		}

		return nameList;
	}

	public List<String> getNameList(MarkerType markerType)
	{
		List<String> nameList = new ArrayList<>();

		MapObjectTreeModel<Marker> tree = markerTree;

		Stack<MapObjectNode<Marker>> stack = new Stack<>();
		stack.push(tree.getRoot());

		while (!stack.isEmpty()) {
			MapObjectNode<Marker> node = stack.pop();
			for (int i = 0; i < node.getChildCount(); i++)
				stack.push(node.getChildAt(i));

			Marker m = node.getUserObject();
			if (node.isLeaf() && m.getType() == markerType)
				nameList.add(m.getName());
		}

		return nameList;
	}

	public static final class ToggleStage extends AbstractCommand
	{
		private final Map m;

		public ToggleStage()
		{
			super(MapEditor.instance().map.isStage ? "Set World Map" : "Set Battle Map");
			this.m = MapEditor.instance().map;
		}

		@Override
		public void exec()
		{
			super.exec();
			m.isStage = !m.isStage;
			MapEditor.instance().gui.isStageCheckbox.setSelected(m.isStage);
		}

		@Override
		public void undo()
		{
			super.undo();
			m.isStage = !m.isStage;
			MapEditor.instance().gui.isStageCheckbox.setSelected(m.isStage);
		}
	}

	public static final class ToggleBackground extends AbstractCommand
	{
		private final Map m;

		public ToggleBackground()
		{
			super(MapEditor.instance().map.hasBackground ? "Disable Background" : "Enable Background");
			this.m = MapEditor.instance().map;
		}

		@Override
		public void exec()
		{
			super.exec();
			m.hasBackground = !m.hasBackground;
			MapEditor.instance().gui.hasBackgroundCheckbox.setSelected(m.hasBackground);
		}

		@Override
		public void undo()
		{
			super.undo();
			m.hasBackground = !m.hasBackground;
			MapEditor.instance().gui.hasBackgroundCheckbox.setSelected(m.hasBackground);
		}
	}

	public static final class SetBackground extends AbstractCommand
	{
		private final Map map;
		private final String oldName;
		private final String newName;

		private final BufferedImage oldImage;
		private final BufferedImage newImage;

		public SetBackground(String name, BufferedImage img)
		{
			super("Set Background");
			this.map = MapEditor.instance().map;

			oldName = map.bgName;
			oldImage = map.bgImage;

			newName = name;
			newImage = img;
		}

		@Override
		public boolean shouldExec()
		{
			return !newName.isEmpty() && !newName.equals(oldName);
		}

		@Override
		public void exec()
		{
			super.exec();
			map.bgName = newName;
			map.bgImage = newImage;
			MapEditor.instance().needsBackgroundReload = true;
		}

		@Override
		public void undo()
		{
			super.undo();
			map.bgName = oldName;
			map.bgImage = oldImage;
			MapEditor.instance().needsBackgroundReload = true;
		}
	}

	private static final int[][] BATTLE_ENEMY_POSITIONS = {
			{ 5, 0, -20 }, { 45, 0, -5 }, { 85, 0, 10 }, { 125, 0, 25 },
			{ 10, 50, -20 }, { 50, 45, -5 }, { 90, 50, 10 }, { 130, 55, 25 },
			{ 15, 85, -20 }, { 55, 80, -5 }, { 95, 85, 10 }, { 135, 90, 25 },
			{ 15, 125, -20 }, { 55, 120, -5 }, { 95, 125, 10 }, { 135, 130, 25 },
			{ 105, 0, 0 } };

	public List<Marker> getStageMarkers()
	{
		List<Marker> actors = new ArrayList<>();
		Marker actor;

		actor = new Marker("Player", MarkerType.NPC, -95, 0, 0, 0);
		actor.npcComponent.setAnimByName("ANIM_ParadeMario_Idle");
		actor.npcComponent.flipX = true;

		if (!hasMarker(actor.getName()))
			actors.add(actor);

		actor = new Marker("Partner", MarkerType.NPC, -130, 0, -10, 0);
		actor.npcComponent.setAnimByName("ANIM_BattleKooper_Idle");
		actor.npcComponent.flipX = true;

		if (!hasMarker(actor.getName()))
			actors.add(actor);

		for (int i = 0; i < 8; i++) {
			int[] vec = BATTLE_ENEMY_POSITIONS[i];
			String name = String.format("Enemy %X", i);

			actor = new Marker(name, MarkerType.NPC, vec[0], vec[1], vec[2], 0);

			if (i < 4) {
				actor.npcComponent.setAnimByName("ANIM_Goomba_Idle");
			}
			else if (i < 8) {
				actor.npcComponent.setAnimByName("ANIM_Paragoomba_Idle");
			}

			if (!hasMarker(actor.getName()))
				actors.add(actor);
		}

		return actors;
	}

	private boolean hasMarker(String name)
	{
		for (Marker m : markerTree) {
			if (name.equals(m.getName())) {
				return true;
			}
		}
		return false;
	}

	private static final String INC_GEN = "#include \"generated.h\"";
	private static final String INC_COM = "#include \"common.h\"";

	private void tryInjectHeader() throws IOException
	{
		File mapHeader = new File(projDir, name + ".h");
		File genHeader = new File(projDir, "generated.h");

		if (!mapHeader.exists()) {
			Logger.logError("Could not find header file for " + name);
			return;
		}

		String headerText = Files.readString(mapHeader.toPath());
		if (headerText.contains(INC_GEN))
			return;

		// try injecting on the line after "common.h", else append to the end
		if (headerText.contains(INC_COM))
			headerText = headerText.replace(INC_COM, INC_COM + "\n" + INC_GEN);
		else
			headerText += "\n#include \"generated.h\"";

		Files.writeString(mapHeader.toPath(), headerText);
		FileUtils.touch(genHeader);
	}

	private void writeHeader() throws IOException
	{
		File genHeader = new File(projDir, "generated.h");
		FileUtils.touch(genHeader);

		try (PrintWriter pw = IOUtils.getBufferedPrintWriter(genHeader)) {
			pw.println("/* auto-generated, do not edit */");
			pw.println("#include \"star_rod_macros.h\"");
			pw.println();

			MapPropertiesExtractor.print(pw, this);
			EntryListExtractor.print(pw, markerTree);
			TexPannerExtractor.print(pw, this);

			for (Marker m : markerTree) {
				HeaderEntry h = m.getHeaderEntry();
				if (h != null)
					h.print(pw);
			}
		}
	}
}
