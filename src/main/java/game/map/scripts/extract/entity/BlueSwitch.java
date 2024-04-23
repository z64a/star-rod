package game.map.scripts.extract.entity;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import game.map.marker.Marker;
import game.map.scripts.extract.Extractor;
import game.map.scripts.extract.HeaderEntry;
import game.map.scripts.extract.HeaderEntry.HeaderParseException;

public class BlueSwitch extends ExtractedEntity
{
	/**
	    Call(MakeEntity, Ref(Entity_BlueSwitch), 60, 115, 10, 0, MAKE_ENTITY_END)
	    Call(AssignSwitchFlag, EVT_INDEX_OF_AREA_FLAG(AF_KPA133_HitWaterSwitch))
	*/

	private static final String TYPES = "(BlueSwitch|HugeBlueSwitch)";
	private static final String RegexString = ExtractedEntity.INDENT +
		"Call\\(MakeEntity, Ref\\(Entity_" + TYPES + "\\)" + ExtractedEntity.ARG.repeat(4) + ExtractedEntity.OPTIONAL_ARG + ",\\s*MAKE_ENTITY_END\\)" +
		"(?:\\n\\s*Call\\(AssignSwitchFlag,\\s*EVT_INDEX_OF_AREA_FLAG\\((\\S+)\\)\\))?";
	public static final Matcher RegexMatcher = Pattern.compile(RegexString).matcher("");

	private boolean hasFlag;
	private String flagName;

	private boolean hasIndex;
	private int index;

	// required
	public BlueSwitch()
	{}

	@Override
	public void fromSourceMatcher(Extractor extractor, Matcher matcher)
	{
		indent = matcher.group(1);
		type = matcher.group(2);
		posX = Integer.decode(matcher.group(3));
		posY = Integer.decode(matcher.group(4));
		posZ = Integer.decode(matcher.group(5));
		angle = Integer.decode(matcher.group(6));

		hasIndex = (matcher.group(7) != null);
		if (hasIndex) {
			index = Integer.decode(matcher.group(7));
		}

		flagName = matcher.group(8);
		hasFlag = (flagName != null);

		setName(extractor.getNextName(type));

		Marker m = super.getBaseMarker();
		extractor.addMarker(m);

		if (hasIndex)
			m.entityComponent.index.setAndEnable(index);

		if (hasFlag)
			m.entityComponent.areaFlagName.setAndEnable(flagName);
	}

	public BlueSwitch(Marker m)
	{
		super(m);

		hasIndex = m.entityComponent.index.isEnabled();
		index = m.entityComponent.index.get();

		hasFlag = m.entityComponent.areaFlagName.isEnabled();
		flagName = m.entityComponent.areaFlagName.get();
	}

	@Override
	public List<String> getLines()
	{
		List<String> lines = super.getLines();
		if (hasFlag)
			lines.add(String.format("Call(AssignSwitchFlag, EVT_INDEX_OF_AREA_FLAG(%s_FLAG))", genName));
		return lines;
	}

	@Override
	public void addHeaderDefines(HeaderEntry h)
	{
		super.addHeaderDefines(h);

		if (hasIndex) {
			h.addDefine("INDEX", index);
			h.addDefine("PARAMS", makeParamList(h, "INDEX"));
		}
		else {
			h.addDefine("PARAMS", makeParamList(h));
		}

		if (hasFlag)
			h.addDefine("FLAG", flagName);
	}

	@Override
	public void parseHeaderDefines(Marker m, HeaderEntry h) throws HeaderParseException
	{
		if (h.hasDefine("INDEX"))
			m.entityComponent.index.setAndEnable(h.getIntDefine("INDEX"));

		if (h.hasDefine("FLAG"))
			m.entityComponent.areaFlagName.setAndEnable(h.getDefine("FLAG"));
	}
}
