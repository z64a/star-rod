package game.map.scripts.nextract.entity;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import game.map.marker.Marker;
import game.map.scripts.nextract.NewExtractor;

public class WoodenCrate extends ExtractedEntity
{
	/**
		Call(MakeEntity, Ref(Entity_WoodenCrate), 855, 30, -565, 0, ITEM_LIFE_SHROOM, MAKE_ENTITY_END)
		Call(AssignCrateFlag, GF_KPA17_Crate_LifeShroom)
	*/

	private static final String TYPES = "(WoodenCrate)";
	private static final String RegexString = ExtractedEntity.INDENT +
		"Call\\(MakeEntity, Ref\\(Entity_" + TYPES + "\\)" + ExtractedEntity.ARG.repeat(5) + ",\\s*MAKE_ENTITY_END\\)" +
		"(?:\\R\\s*Call\\(AssignCrateFlag" + ExtractedEntity.ARG + "\\))?";
	public static final Matcher RegexMatcher = Pattern.compile(RegexString).matcher("");

	private String itemName;
	private boolean hasFlag;
	private String flagName;

	// required
	public WoodenCrate()
	{}

	@Override
	public void fromSourceMatcher(NewExtractor extractor, Matcher matcher)
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
}
