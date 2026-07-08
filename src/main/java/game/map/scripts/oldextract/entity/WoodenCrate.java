package game.map.scripts.extract.entity;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import game.map.marker.Marker;
import game.map.scripts.extract.Extractor;
import game.map.scripts.extract.HeaderEntry;
import game.map.scripts.extract.HeaderEntry.HeaderParseException;

public class WoodenCrate extends ExtractedEntity
{
	/**
		Call(MakeEntity, Ref(Entity_WoodenCrate), 855, 30, -565, 0, ITEM_LIFE_SHROOM, MAKE_ENTITY_END)
		Call(AssignCrateFlag, GF_KPA17_Crate_LifeShroom)
	*/

	private static final String TYPES = "(WoodenCrate)";
	private static final String RegexString = ExtractedEntity.INDENT +
		"Call\\(MakeEntity, Ref\\(Entity_" + TYPES + "\\)" + ExtractedEntity.ARG.repeat(5) + ",\\s*MAKE_ENTITY_END\\)" +
		"(?:\\n\\s*Call\\(AssignCrateFlag" + ExtractedEntity.ARG + "\\))?";
	public static final Matcher RegexMatcher = Pattern.compile(RegexString).matcher("");

	private String itemName;
	private boolean hasFlag;
	private String flagName;

	// required
	public WoodenCrate()
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

		if (itemName.equals("-1")) {
			itemName = "ITEM_NONE";
		}

		setName(extractor.getNextName(type));

		Marker m = super.getBaseMarker();
		extractor.addMarker(m);

		m.entityComponent.itemName.set(itemName);

		if (hasFlag)
			m.entityComponent.gameFlagName.setAndEnable(flagName);
	}

	public WoodenCrate(Marker m)
	{
		super(m);

		itemName = m.entityComponent.itemName.get();

		hasFlag = m.entityComponent.gameFlagName.isEnabled();
		flagName = m.entityComponent.gameFlagName.get();
	}

	@Override
	public List<String> getLines()
	{
		List<String> lines = super.getLines();
		if (hasFlag)
			lines.add(String.format("Call(AssignCrateFlag, %s_FLAG)", genName));
		return lines;
	}

	@Override
	public void addHeaderDefines(HeaderEntry h)
	{
		super.addHeaderDefines(h);

		if (hasFlag)
			h.addDefine("FLAG", flagName);

		String rawItemName = itemName.equals("ITEM_NONE") ? "-1" : itemName;
		h.addDefine("ITEM", rawItemName);

		h.addDefine("PARAMS", makeParamList(h, "ITEM"));
	}

	@Override
	public void parseHeaderDefines(Marker m, HeaderEntry h) throws HeaderParseException
	{
		String itemName = h.getDefine("ITEM");
		if (itemName.equals("-1"))
			itemName = "ITEM_NONE";

		m.entityComponent.itemName.set(itemName);

		if (h.hasDefine("FLAG"))
			m.entityComponent.gameFlagName.setAndEnable(h.getDefine("FLAG"));
	}
}
