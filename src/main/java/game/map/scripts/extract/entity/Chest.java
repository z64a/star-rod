package game.map.scripts.nextract.entity;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import game.map.marker.Marker;
import game.map.scripts.nextract.NewExtractor;

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
		"(?:\\R\\s*Call\\(AssignChestFlag,\\s*(\\S+)\\))?" +
		"(?:\\R\\s*Call\\(AssignScript,\\s*Ref\\((\\S+)\\)\\))?";
	public static final Matcher RegexMatcher = Pattern.compile(RegexString).matcher("");

	private boolean hasItem;
	private String itemName;

	private boolean hasFlag;
	private String flagName;

	private boolean hasScript;
	private String scriptName;

	// required
	public Chest()
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
		// varargs itemID ignored, unused for real chests

		itemName = matcher.group(7);
		if (itemName.equals("0")) {
			hasItem = false;
			itemName = "ITEM_NONE";
		}
		else {
			hasItem = true;
		}

		flagName = matcher.group(8);
		hasFlag = (flagName != null);

		scriptName = matcher.group(9);
		hasScript = (scriptName != null);

		String niceName = type;
		if (flagName.contains("Chest_"))
			niceName = flagName.substring(flagName.indexOf("Chest_"));

		// setName(extractor.getNextName(niceName));
		setName(niceName);

		Marker m = super.getBaseMarker();
		extractor.addMarker(m);

		if (hasItem)
			m.entityComponent.itemName.setAndEnable(itemName);

		if (hasFlag)
			m.entityComponent.gameFlagName.setAndEnable(flagName);

		if (hasScript)
			m.entityComponent.scriptName.setAndEnable(scriptName);
	}

	public Chest(Marker m)
	{
		super(m);

		hasItem = m.entityComponent.itemName.isEnabled();
		itemName = m.entityComponent.itemName.get();

		hasFlag = m.entityComponent.gameFlagName.isEnabled();
		flagName = m.entityComponent.gameFlagName.get();

		hasScript = m.entityComponent.scriptName.isEnabled();
		scriptName = m.entityComponent.scriptName.get();
	}
}
