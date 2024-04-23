package game.map;

import static game.map.MapKey.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Function;

import javax.swing.ListModel;

import org.w3c.dom.Element;

import app.StarRodException;
import game.map.hit.Collider;
import game.map.hit.Zone;
import game.map.marker.Marker;
import game.map.shape.LightSet;
import game.map.shape.Model;
import game.map.tree.ColliderTreeModel;
import game.map.tree.MapObjectNode;
import game.map.tree.MapObjectTreeModel;
import game.map.tree.MarkerTreeModel;
import game.map.tree.ModelTreeModel;
import game.map.tree.ZoneTreeModel;
import util.identity.IdentityHashSet;
import util.xml.XmlKey;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class Prefab
{
	public String texName = "";

	public MapObjectTreeModel<Model> modelTree;
	public MapObjectTreeModel<Collider> colliderTree;
	public MapObjectTreeModel<Zone> zoneTree;
	public MapObjectTreeModel<Marker> markerTree;
	public ArrayList<LightSet> lightSets;

	public static Prefab read(XmlReader xmr)
	{
		Prefab p = new Prefab("");
		p.fromXML(xmr);
		return p;
	}

	public Prefab(String texName)
	{
		modelTree = new ModelTreeModel();
		colliderTree = new ColliderTreeModel();
		zoneTree = new ZoneTreeModel();
		markerTree = new MarkerTreeModel();

		lightSets = new ArrayList<>();
		this.texName = texName;
	}

	public Prefab(Iterable<MapObject> objects, ListModel<LightSet> lightSetList, String texName)
	{
		this(texName);

		LinkedHashMap<MapObject, MapObject> duplicateMap = new LinkedHashMap<>();

		// make copies of the selected objects
		for (MapObject obj : objects)
			duplicateMap.put(obj, obj.deepCopy());

		// set their parents
		for (Entry<MapObject, MapObject> e : duplicateMap.entrySet()) {
			MapObject original = e.getKey();
			MapObject copy = e.getValue();
			MapObjectNode<?> copyNode = copy.getNode();

			MapObject originalParent = original.getNode().getParent().getUserObject();

			if (duplicateMap.containsKey(originalParent)) {
				MapObject copiedParent = duplicateMap.get(originalParent);
				MapObjectNode<?> copiedParentNode = copiedParent.getNode();

				copiedParentNode.add(copyNode);
			}
			else {
				copyNode.parentNode = null;
				addToTree(copyNode);
			}
		}

		IdentityHashSet<Integer> referencedLightSets = new IdentityHashSet<>();
		for (Model mdl : modelTree.getList()) {
			int index = mdl.lightsIndex;
			if (!referencedLightSets.contains(index)) {
				referencedLightSets.add(index);

				if (index >= lightSetList.getSize())
					throw new StarRodException("Model %s light set %d is invalid.", mdl.getName(), index);

				lightSets.add(lightSetList.getElementAt(index).deepCopy());
			}
		}

		modelTree.recalculateIndicies();
		colliderTree.recalculateIndicies();
		zoneTree.recalculateIndicies();
		markerTree.recalculateIndicies();
	}

	private void addToTree(MapObjectNode<?> node)
	{
		MapObject obj = node.getUserObject();
		switch (obj.getObjectType()) {
			case MODEL:
				modelTree.create((Model) obj);
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
			default:
		}
	}

	public void fromXML(XmlReader xmr)
	{
		Element prefabElem = xmr.getRootElement();

		xmr.requiresAttribute(prefabElem, ATTR_MAP_TEX);

		texName = xmr.getAttribute(prefabElem, ATTR_MAP_TEX);

		if (xmr.getUniqueTag(prefabElem, TAG_MODELS) != null) {
			lightSets = new ArrayList<>();
			Element lightsetList = xmr.getUniqueRequiredTag(prefabElem, TAG_LIGHTSETS);
			for (Element lightsetElem : xmr.getTags(lightsetList, TAG_LIGHTSET))
				lightSets.add(LightSet.read(xmr, lightsetElem));

			if (lightSets.isEmpty())
				lightSets.add(LightSet.createEmptySet());

			MapObjectNode<Model> modelRoot = readTree((elem) -> Model.read(xmr, elem),
				xmr, prefabElem, TAG_MODELS, TAG_MODEL, TAG_MODEL_TREE);
			modelTree = new ModelTreeModel(modelRoot);

			for (Model mdl : modelTree.getList())
				mdl.lights.set(lightSets.get(mdl.lightsIndex));
		}

		if (xmr.getUniqueTag(prefabElem, TAG_COLLIDERS) != null) {
			MapObjectNode<Collider> colliderRoot = readTree((elem) -> Collider.read(xmr, elem),
				xmr, prefabElem, TAG_COLLIDERS, TAG_COLLIDER, TAG_COLLIDER_TREE);
			colliderTree = new ColliderTreeModel(colliderRoot);
		}

		if (xmr.getUniqueTag(prefabElem, TAG_ZONES) != null) {
			MapObjectNode<Zone> zoneRoot = readTree((elem) -> Zone.read(xmr, elem),
				xmr, prefabElem, TAG_ZONES, TAG_ZONE, TAG_ZONE_TREE);
			zoneTree = new ZoneTreeModel(zoneRoot);
		}

		if (xmr.getUniqueTag(prefabElem, TAG_MARKERS) != null) {
			MapObjectNode<Marker> markerRoot = readTree((elem) -> Marker.read(xmr, elem),
				xmr, prefabElem, TAG_MARKERS, TAG_MARKER, TAG_MARKER_TREE);
			markerTree = new MarkerTreeModel(markerRoot);
		}
	}

	public void toXML(XmlWriter xmw)
	{
		XmlTag root = xmw.createTag(TAG_PREFAB, false);

		xmw.addAttribute(root, ATTR_MAP_TEX, texName);
		xmw.openTag(root);

		boolean hasModels = modelTree.getRoot().getChildCount() > 0;
		boolean hasColliders = colliderTree.getRoot().getChildCount() > 0;
		boolean hasZones = zoneTree.getRoot().getChildCount() > 0;
		boolean hasMarkers = markerTree.getRoot().getChildCount() > 0;

		if (hasModels) {
			XmlTag mdlTreeTag = xmw.createTag(TAG_MODEL_TREE, false);
			xmw.openTag(mdlTreeTag);
			modelTree.toXML(xmw);
			xmw.closeTag(mdlTreeTag);
		}

		if (hasColliders) {
			XmlTag colTreeTag = xmw.createTag(TAG_COLLIDER_TREE, false);
			xmw.openTag(colTreeTag);
			colliderTree.toXML(xmw);
			xmw.closeTag(colTreeTag);
		}

		if (hasZones) {
			XmlTag zoneTreeTag = xmw.createTag(TAG_ZONE_TREE, false);
			xmw.openTag(zoneTreeTag);
			zoneTree.toXML(xmw);
			xmw.closeTag(zoneTreeTag);
		}

		if (hasMarkers) {
			XmlTag markerTreeTag = xmw.createTag(TAG_MARKER_TREE, false);
			xmw.openTag(markerTreeTag);
			markerTree.toXML(xmw);
			xmw.closeTag(markerTreeTag);
		}

		if (hasModels) {
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
		}

		if (hasColliders) {
			XmlTag collidersTag = xmw.createTag(TAG_COLLIDERS, false);
			xmw.openTag(collidersTag);
			for (Collider c : colliderTree.getList())
				c.toXML(xmw);
			xmw.closeTag(collidersTag);
		}

		if (hasZones) {
			XmlTag zonesTag = xmw.createTag(TAG_ZONES, false);
			xmw.openTag(zonesTag);
			for (Zone z : zoneTree.getList())
				z.toXML(xmw);
			xmw.closeTag(zonesTag);
		}

		if (hasMarkers) {
			XmlTag markersTag = xmw.createTag(TAG_MARKERS, false);
			xmw.openTag(markersTag);
			for (Marker m : markerTree.getList())
				m.toXML(xmw);
			xmw.closeTag(markersTag);
		}

		xmw.closeTag(root);
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
}
