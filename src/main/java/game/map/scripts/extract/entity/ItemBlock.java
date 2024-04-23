package game.map.scripts.extract.entity;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import game.map.marker.Marker;
import game.map.scripts.extract.Extractor;
import game.map.scripts.extract.HeaderEntry;
import game.map.scripts.extract.HeaderEntry.HeaderParseException;

public class ItemBlock extends ExtractedEntity
{
	/**
		Call(MakeEntity, Ref(Entity_YellowBlock), -350, 172, 170, 0, ITEM_COIN, MAKE_ENTITY_END)
		Call(AssignBlockFlag, GF_ARN02_ItemBlock_CoinA)
	*/

	private static final String TYPES = "(YellowBlock|HiddenYellowBlock|RedBlock|HiddenRedBlock)";
	private static final String RegexString = ExtractedEntity.INDENT +
		"Call\\(MakeEntity, Ref\\(Entity_" + TYPES + "\\)" + ExtractedEntity.ARG.repeat(5) + ",\\s*MAKE_ENTITY_END\\)" +
		"(?:\\n\\s*Call\\(AssignBlockFlag" + ExtractedEntity.ARG + "\\))?";
	public static final Matcher RegexMatcher = Pattern.compile(RegexString).matcher("");

	private String itemName;
	private String flagName;
	private boolean hasFlag;
	private String scriptName;
	private boolean hasScript;

	// required
	public ItemBlock()
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
		itemName = matcher.group(7);
		flagName = matcher.group(8);
		hasFlag = (flagName != null);

		//TODO script detection

		setName(extractor.getNextName(type));

		Marker m = super.getBaseMarker();
		extractor.addMarker(m);

		m.entityComponent.itemName.setAndEnable(itemName);

		if (hasFlag)
			m.entityComponent.gameFlagName.setAndEnable(flagName);

		if (hasScript)
			m.entityComponent.scriptName.setAndEnable(scriptName);
	}

	public ItemBlock(Marker m)
	{
		super(m);

		itemName = m.entityComponent.itemName.get();

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
			lines.add(String.format("Call(AssignBlockFlag, %s_FLAG)", genName));
		if (hasScript)
			lines.add(String.format("Call(AssignScript, Ref(%s_SCRIPT)))", genName));

		return lines;
	}

	@Override
	public void addHeaderDefines(HeaderEntry h)
	{
		super.addHeaderDefines(h);

		h.addDefine("ITEM", itemName);
		h.addDefine("PARAMS", makeParamList(h, "ITEM"));

		if (hasFlag)
			h.addDefine("FLAG", flagName);

		if (hasScript)
			h.addDefine("SCRIPT", scriptName);
	}

	@Override
	public void parseHeaderDefines(Marker m, HeaderEntry h) throws HeaderParseException
	{
		m.entityComponent.itemName.setAndEnable(h.getDefine("ITEM"));

		if (h.hasDefine("FLAG"))
			m.entityComponent.gameFlagName.setAndEnable(h.getDefine("FLAG"));

		if (h.hasDefine("SCRIPT"))
			m.entityComponent.scriptName.setAndEnable(h.getDefine("SCRIPT"));
	}
}
