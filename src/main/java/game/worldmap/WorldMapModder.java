package game.worldmap;

import static game.worldmap.WorldMapKey.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.w3c.dom.Element;

import app.Directories;
import app.StarRodMain;
import app.input.InputFileException;
import util.Logger;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class WorldMapModder
{
	public static final int MAP_SIZE = 320;
	public static final String PREFIX_LOC = "LOCATION_";
	public static final String PREFIX_STORY = "STORY_";

	public static class WorldMarker
	{
		public boolean mouseOver;

		protected int x, y;
		public float dragX, dragY;

		public WorldMarker(int x, int y)
		{
			this.x = x;
			this.y = y;
		}

		public double getDistTo(float posX, float posY)
		{
			float dX = x - posX;
			float dY = y - posY;
			return Math.sqrt(dX * dX + dY * dY);
		}

		public int getX()
		{
			return x + Math.round(dragX);
		}

		public int getY()
		{
			return y + Math.round(dragY);
		}
	}

	public static final class WorldLocation extends WorldMarker
	{
		public transient WorldLocation parent;
		private String parentName;

		public String name;
		public String descUpdate;

		public ArrayList<WorldPathElement> path = new ArrayList<>();

		public WorldLocation(int x, int y)
		{
			super(x, y);
		}
	}

	public static final class WorldPathElement extends WorldMarker
	{
		public final WorldLocation owner;

		public WorldPathElement(WorldLocation owner, int x, int y)
		{
			super(x, y);
			this.owner = owner;
		}
	}

	private static List<WorldLocation> readXML(File xmlFile) throws IOException
	{
		List<WorldLocation> locations = new ArrayList<>();

		XmlReader xmr = new XmlReader(xmlFile);
		Element rootElem = xmr.getRootElement();

		List<Element> locationElems = xmr.getRequiredTags(rootElem, TAG_LOCATION);
		for (Element locationElem : locationElems) {
			int x = xmr.readInt(locationElem, ATTR_START_X);
			int y = xmr.readInt(locationElem, ATTR_START_Y);

			WorldLocation loc = new WorldLocation(x, MAP_SIZE - y);
			locations.add(loc);
			loc.name = stripPrefix(xmr.getAttribute(locationElem, ATTR_NAME), PREFIX_LOC);
			loc.parentName = stripPrefix(xmr.getAttribute(locationElem, ATTR_PARENT), PREFIX_LOC);
			loc.descUpdate = stripPrefix(xmr.getAttribute(locationElem, ATTR_REQUIRES), PREFIX_STORY);

			String pathStr = xmr.getAttribute(locationElem, ATTR_PATH);
			pathStr = pathStr.replaceAll("//s+", "");
			if (!pathStr.isEmpty()) {
				String[] points = pathStr.split(";");
				if (points.length > 0x20)
					throw new InputFileException(xmlFile, "Path length exceeds limit: (" + points.length + " / 32)");

				int curX = loc.x;
				int curY = loc.y;
				for (String point : points) {
					String[] coords = point.split(",");
					if (coords.length != 2)
						throw new InputFileException(xmlFile, "Path has invalid coordinate: " + point);

					curX += (byte) Integer.parseInt(coords[0]);
					curY -= (byte) Integer.parseInt(coords[1]);
					loc.path.add(new WorldPathElement(loc, curX, curY));
				}
			}
		}

		return locations;
	}

	private static void writeXML(List<WorldLocation> locations, File xmlFile)
	{
		try (XmlWriter xmw = new XmlWriter(xmlFile)) {
			XmlTag root = xmw.createTag(TAG_ROOT, false);
			xmw.openTag(root);

			for (WorldLocation loc : locations) {
				XmlTag locTag = xmw.createTag(TAG_LOCATION, true);

				xmw.addAttribute(locTag, ATTR_NAME, addPrefix(loc.name, PREFIX_LOC));
				xmw.addAttribute(locTag, ATTR_PARENT, addPrefix(loc.parentName, PREFIX_LOC));
				xmw.addAttribute(locTag, ATTR_REQUIRES, addPrefix(loc.descUpdate, PREFIX_STORY));
				xmw.addInt(locTag, ATTR_START_X, loc.x);
				xmw.addInt(locTag, ATTR_START_Y, MAP_SIZE - loc.y);

				int lastX = loc.x;
				int lastY = MAP_SIZE - loc.y;
				StringBuilder sb = new StringBuilder();
				for (int j = 0; j < loc.path.size(); j++) {
					WorldPathElement marker = loc.path.get(j);
					sb.append(String.format("%d,%d", marker.x - lastX, -(marker.y - lastY)));
					lastX = marker.x;
					lastY = marker.y;

					if (j < loc.path.size() - 1)
						sb.append(";");
				}
				xmw.addAttribute(locTag, ATTR_PATH, sb.toString());

				xmw.printTag(locTag);
			}

			xmw.closeTag(root);
			xmw.save();
			Logger.log("Saved world map");
		}
		catch (Throwable t) {
			Logger.logError("Failed to save world map");
			StarRodMain.displayStackTrace(t);
		}
	}

	public static List<WorldLocation> loadLocations() throws IOException
	{
		List<WorldLocation> locations = readXML(Directories.PROJ_SRC.file(Directories.FN_WORLD_MAP));

		HashMap<String, WorldLocation> locationMap = new HashMap<>();
		for (WorldLocation loc : locations) {
			locationMap.put(loc.name, loc);
		}

		for (WorldLocation loc : locations) {
			loc.parent = locationMap.get(loc.parentName);
		}

		return locations;
	}

	public static void saveLocations(List<WorldLocation> locations)
	{
		for (WorldLocation loc : locations) {
			if (loc.parent == null)
				loc.parentName = "";
			else
				loc.parentName = loc.parent.name;
		}

		writeXML(locations, Directories.PROJ_SRC.file(Directories.FN_WORLD_MAP));
	}

	public static String stripPrefix(String s, String prefix)
	{
		if (s.startsWith(prefix)) {
			return s.substring(prefix.length());
		}
		else {
			return s;
		}
	}

	public static String addPrefix(String s, String prefix)
	{
		if (s == null || s.isBlank()) {
			return s;
		}
		else {
			return prefix + s;
		}
	}

	public static String[] stripAllPrefix(String[] in, String prefix)
	{
		String[] out = new String[in.length];
		for (int i = 0; i < in.length; i++) {
			out[i] = WorldMapModder.stripPrefix(in[i], prefix);
		}
		return out;
	}
}
