package game.map;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import game.map.hit.Collider;
import game.map.hit.Zone;
import game.map.marker.Marker;
import game.map.marker.Marker.MarkerType;
import game.map.shape.Model;
import util.Logger;

public class MapIndex
{
	private String mapName;
	private String bgName;
	private long lastModified;

	private HashMap<String, IndexedModel> modelNameLookup = new HashMap<>();
	private HashMap<Integer, IndexedModel> modelIDLookup = new HashMap<>();
	private HashMap<String, IndexedCollider> colliderNameLookup = new HashMap<>();
	private HashMap<Integer, IndexedCollider> colliderIDLookup = new HashMap<>();
	private HashMap<String, IndexedZone> zoneNameLookup = new HashMap<>();
	private HashMap<Integer, IndexedZone> zoneIDLookup = new HashMap<>();
	private HashMap<String, Marker> markerNameLookup = new HashMap<>();

	private ArrayList<Marker> entryList = new ArrayList<>();

	public MapIndex()
	{}

	public MapIndex(Map map)
	{
		this.lastModified = map.lastModified;
		this.mapName = map.getName();
		this.bgName = map.hasBackground ? map.bgName : "";
		Map.validateObjectData(map);

		for (Model mdl : map.modelTree.getList()) {
			IndexedModel indexed = new IndexedModel(mdl);
			modelNameLookup.put(indexed.name, indexed);
			modelIDLookup.put(indexed.id, indexed);
		}

		for (Collider c : map.colliderTree.getList()) {
			IndexedCollider indexed = new IndexedCollider(c);
			colliderNameLookup.put(indexed.name, indexed);
			colliderIDLookup.put(indexed.id, indexed);
		}

		for (Zone z : map.zoneTree.getList()) {
			IndexedZone indexed = new IndexedZone(z);
			zoneNameLookup.put(indexed.name, indexed);
			zoneIDLookup.put(indexed.id, indexed);
		}

		for (Marker m : map.markerTree) {
			markerNameLookup.put(m.getName(), m);
			if (m.getType() == MarkerType.Entry) {
				m.entryID = entryList.size();
				entryList.add(m);
			}
			else
				m.entryID = -1;
		}
	}

	// dumper want to use this, but defer markers until later
	public void refreshMarkers(Map map)
	{
		markerNameLookup.clear();
		entryList.clear();

		for (Marker m : map.markerTree) {
			markerNameLookup.put(m.getName(), m);
			if (m.getType() == MarkerType.Entry) {
				m.entryID = entryList.size();
				entryList.add(m);
			}
			else
				m.entryID = -1;
		}
	}

	public long sourceLastModified()
	{
		return lastModified;
	}

	public String getMapName()
	{ return mapName; }

	public String getBackgroundName()
	{ return bgName; }

	private IndexedModel getModel(String name)
	{
		return modelNameLookup.get(name);
	}

	private IndexedModel getModel(int id)
	{
		return modelIDLookup.get(id);
	}

	private IndexedCollider getCollider(String name)
	{
		return colliderNameLookup.get(name);
	}

	private IndexedCollider getCollider(int id)
	{
		return colliderIDLookup.get(id);
	}

	private IndexedZone getZone(String name)
	{
		return zoneNameLookup.get(name);
	}

	private IndexedZone getZone(int id)
	{
		return zoneIDLookup.get(id);
	}

	public Marker getMarker(String name)
	{
		return markerNameLookup.get(name);
	}

	public int getEntry(String name)
	{
		Marker obj = getMarker(name);
		if (obj == null || obj.entryID < 0) {
			Logger.logWarning(name + " is not a valid entrance for " + mapName);
			return -1;
		}

		return obj.entryID;
	}

	public int getModelID(String name)
	{
		IndexedModel obj = getModel(name);
		return (obj == null) ? -1 : obj.id;
	}

	public int getColliderID(String name)
	{
		IndexedCollider obj = getCollider(name);
		return (obj == null) ? -1 : obj.id;
	}

	public int getZoneID(String name)
	{
		IndexedZone obj = getZone(name);
		return (obj == null) ? -1 : obj.id;
	}

	public String getModelName(int id)
	{
		IndexedModel obj = getModel(id);
		return (obj == null) ? null : obj.name;
	}

	public String getColliderName(int id)
	{
		IndexedCollider obj = getCollider(id);
		return (obj == null) ? null : obj.name;
	}

	public String getZoneName(int id)
	{
		IndexedZone obj = getZone(id);
		return (obj == null) ? null : obj.name;
	}

	public int getEntryCount()
	{ return entryList.size(); }

	public List<Marker> getEntryList()
	{ return entryList; }

	public static class IndexedModel
	{
		private int id;
		private String name;

		public IndexedModel()
		{}

		public IndexedModel(Model mdl)
		{
			this.id = mdl.getNode().getTreeIndex();
			this.name = mdl.getName();
		}
	}

	public static class IndexedCollider
	{
		private int id;
		private String name;

		public IndexedCollider()
		{}

		public IndexedCollider(Collider c)
		{
			this.id = c.getNode().getTreeIndex();
			this.name = c.getName();
		}
	}

	public static class IndexedZone
	{
		private int id;
		private String name;

		public IndexedZone()
		{}

		public IndexedZone(Zone z)
		{
			this.id = z.getNode().getTreeIndex();
			this.name = z.getName();
		}
	}
}
