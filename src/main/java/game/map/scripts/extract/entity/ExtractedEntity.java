package game.map.scripts.extract.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

import game.entity.EntityInfo.EntityType;
import game.map.marker.Marker;
import game.map.marker.Marker.MarkerType;
import game.map.scripts.extract.Extractor;
import game.map.scripts.extract.HeaderEntry;
import game.map.scripts.extract.HeaderEntry.HeaderParseException;
import util.NameUtils;

public abstract class ExtractedEntity
{
	public static final String ARG = ",\\s*(\\S+)";
	public static final String OPTIONAL_ARG = "(?:" + ARG + ")?";
	public static final String INDENT = "([\t ]+)";

	public String name = "???";
	public String genName = "GEN_???";
	public String indent = "";

	protected String type;
	protected int posX;
	protected int posY;
	protected int posZ;
	protected int angle;

	protected ExtractedEntity()
	{}

	public ExtractedEntity(Marker m)
	{
		setName(m.getName());
		type = m.entityComponent.type.get().name;
		posX = m.position.getX();
		posY = m.position.getY();
		posZ = m.position.getZ();
		angle = (int) Math.round(m.yaw.getAngle());
	}

	protected Marker getBaseMarker()
	{
		Marker m = new Marker(name, MarkerType.Entity, posX, posY, posZ, angle);
		m.entityComponent.type.set(EntityType.valueOf(type));
		m.extracted = true;

		return m;
	}

	public void setName(String newName)
	{
		this.name = newName;
		genName = NameUtils.toEnumStyle("GEN_" + name);
	}

	public String getIndent()
	{
		return indent;
	}

	public List<String> getLines()
	{
		List<String> lines = new ArrayList<>();
		lines.add(String.format("EVT_MAKE_ENTITY(%s, %s_PARAMS)", type, genName));
		return lines;
	}

	protected final String makeParamList(HeaderEntry h, String ... additionalParams)
	{
		StringBuilder sb = new StringBuilder(h.namespace("XYZA"));
		for (String s : additionalParams)
			sb.append(", ").append(h.namespace(s));
		return sb.toString();
	}

	public void addHeaderDefines(HeaderEntry h)
	{
		h.addDefine("XYZA", "%d, %d, %d, %d", posX, posY, posZ, angle);
	}

	public abstract void parseHeaderDefines(Marker m, HeaderEntry h) throws HeaderParseException;

	public abstract void fromSourceMatcher(Extractor extractor, Matcher matcher);

}
