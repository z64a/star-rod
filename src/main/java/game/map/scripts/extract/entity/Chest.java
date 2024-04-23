package game.map.scripts.extract.entity;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import game.map.marker.Marker;
import game.map.scripts.extract.Extractor;
import game.map.scripts.extract.HeaderEntry;
import game.map.scripts.extract.HeaderEntry.HeaderParseException;

public class Chest extends ExtractedEntity
{
	/**
		Call(MakeEntity, Ref(Entity_GiantChest), 180, 0, -175, -10, ITEM_NONE, MAKE_ENTITY_END)
		Call(AssignChestFlag, GF_OBK04_GiantChest)
		Call(AssignScript, Ref(N(EVS_OpenGiantChest)))
	*/

	private static final String TYPES = "(Chest|GiantChest)";
	private static final String RegexString = ExtractedEntity.INDENT +
		"Call\\(MakeEntity, Ref\\(Entity_" + TYPES + "\\)" + ExtractedEntity.ARG.repeat(5) + ",\\s*MAKE_ENTITY_END\\)" +
		"(?:\\n\\s*Call\\(AssignChestFlag,\\s*(\\S+)\\))?" +
		"(?:\\n\\s*Call\\(AssignScript,\\s*Ref\\((\\S+)\\)\\))?";
	public static final Matcher RegexMatcher = Pattern.compile(RegexString).matcher("");

	private boolean hasFlag;
	private String flagName;

	private boolean hasScript;
	private String scriptName;

	// required
	public Chest()
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
		// varargs itemID ignored, unused for real chests

		flagName = matcher.group(8);
		hasFlag = (flagName != null);

		scriptName = matcher.group(9);
		hasScript = (scriptName != null);

		setName(extractor.getNextName(type));

		Marker m = super.getBaseMarker();
		extractor.addMarker(m);

		if (hasFlag)
			m.entityComponent.gameFlagName.setAndEnable(flagName);

		if (hasScript)
			m.entityComponent.scriptName.setAndEnable(scriptName);
	}

	public Chest(Marker m)
	{
		super(m);

		hasFlag = m.entityComponent.gameFlagName.isEnabled();
		flagName = m.entityComponent.gameFlagName.get();

		hasScript = m.entityComponent.scriptName.isEnabled();
		scriptName = m.entityComponent.scriptName.get();
	}

	@Override
	public List<String> getLines()
	{
		List<String> lines = super.getLines();
		if (hasFlag)
			lines.add(String.format("Call(AssignChestFlag, %s_FLAG)", genName));
		if (hasScript)
			lines.add(String.format("Call(AssignScript, Ref(%s_SCRIPT))", genName));
		return lines;
	}

	@Override
	public void addHeaderDefines(HeaderEntry h)
	{
		super.addHeaderDefines(h);
		h.addDefine("PARAMS", makeParamList(h));

		if (hasFlag)
			h.addDefine("FLAG", flagName);

		if (hasScript)
			h.addDefine("SCRIPT", scriptName);
	}

	@Override
	public void parseHeaderDefines(Marker m, HeaderEntry h) throws HeaderParseException
	{
		if (h.hasDefine("FLAG"))
			m.entityComponent.gameFlagName.setAndEnable(h.getDefine("FLAG"));

		if (h.hasDefine("SCRIPT"))
			m.entityComponent.scriptName.setAndEnable(h.getDefine("SCRIPT"));
	}
}
