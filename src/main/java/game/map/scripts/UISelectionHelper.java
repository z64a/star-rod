package game.map.scripts;

import java.awt.Toolkit;

import game.map.MapObject;
import game.map.MapObject.MapObjectType;
import game.map.editor.MapEditor;
import game.map.marker.Marker;
import game.map.marker.Marker.MarkerType;
import util.Logger;

public abstract class UISelectionHelper
{
	public static void selectObject(MapObjectType objectType, String name)
	{
		if (name == null || name.isEmpty()) {
			Logger.logError("Name is empty!");
			Toolkit.getDefaultToolkit().beep();
			return;
		}

		MapObject obj = MapEditor.instance().map.find(objectType, name);
		if (obj == null) {
			Logger.logfError("Could not find %s named \"%s\".", objectType, name);
			Toolkit.getDefaultToolkit().beep();
			return;
		}

		MapEditor.execute(MapEditor.instance().selectionManager.getAddObject(obj));
	}

	public static Marker getLastMarker(MarkerType type)
	{
		MapObject mostRecent = MapEditor.instance().selectionManager.getMostRecentObject();
		if (mostRecent == null) {
			Logger.logError("Nothing is selected!");
			Toolkit.getDefaultToolkit().beep();
			return null;
		}
		if (!(mostRecent instanceof Marker)) {
			Logger.logfError("%s is a %s, expected %s Marker!", mostRecent.getName(), mostRecent.getObjectType(), type);
			Toolkit.getDefaultToolkit().beep();
			return null;
		}
		Marker m = (Marker) mostRecent;
		if (m.getType() != type) {
			Logger.logfError("%s is a not a %s Marker!", mostRecent.getName(), type);
			Toolkit.getDefaultToolkit().beep();
			return null;
		}
		return m;
	}

	public static MapObject getLastObject(MapObjectType desiredType)
	{
		MapObject mostRecent = MapEditor.instance().selectionManager.getMostRecentObject();
		if (mostRecent == null) {
			Logger.logError("Nothing is selected!");
			Toolkit.getDefaultToolkit().beep();
			return null;
		}
		if (mostRecent.getObjectType() != desiredType) {
			Logger.logfError("%s is a %s, expected %s!", mostRecent.getName(), mostRecent.getObjectType(), desiredType);
			Toolkit.getDefaultToolkit().beep();
			return null;
		}
		return mostRecent;
	}

	/*
	public static Model getLastModel()
	{
		return getLastModel(true);
	}

	public static Model getLastModel(boolean requireMesh)
	{
		MapObject mostRecent = getLastObject(MapObjectType.MODEL);
		if(mostRecent == null)
			return null;

		Model mdl = (Model)mostRecent;
		if(requireMesh && !mdl.hasMesh())
		{
			Logger.logError(mdl + " does not have a mesh!");
			Toolkit.getDefaultToolkit().beep();
			return null;
		}

		return mdl;
	}

	public static Collider getLastCollider()
	{
		return getLastCollider(true);
	}

	public static Collider getLastCollider(boolean requireMesh)
	{
		MapObject mostRecent = getLastObject(MapObjectType.COLLIDER);
		if(mostRecent == null)
			return null;

		Collider c = (Collider)mostRecent;
		if(requireMesh && !c.hasMesh())
		{
			Logger.logError(c + " does not have a mesh!");
			Toolkit.getDefaultToolkit().beep();
			return null;
		}

		return c;
	}

	public static Zone getLastZone()
	{
		return getLastZone(true);
	}

	public static Zone getLastZone(boolean requireMesh)
	{
		MapObject mostRecent = getLastObject(MapObjectType.COLLIDER);
		if(mostRecent == null)
			return null;

		Zone z = (Zone)mostRecent;
		if(requireMesh && !z.hasMesh())
		{
			Logger.logError(z + " does not have a mesh!");
			Toolkit.getDefaultToolkit().beep();
			return null;
		}

		return z;
	}
	*/
}
