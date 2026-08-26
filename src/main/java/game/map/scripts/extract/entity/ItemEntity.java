package game.map.scripts.extract.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import game.map.marker.Marker;
import game.map.scripts.extract.MapExtractor;

public class ItemEntity extends ExtractedEntity
{
	/**
		Call(MakeItemEntity, ITEM_DIZZY_DIAL, -248, 193, 45, ITEM_SPAWN_MODE_FIXED_NEVER_VANISH, GF_ARN02_Item_DizzyDial)
	*/

	private static final String RegexString = ExtractedEntity.INDENT + "Call\\(MakeItemEntity" + ExtractedEntity.ARG.repeat(6) + "\\)";
	public static final Matcher RegexMatcher = Pattern.compile(RegexString).matcher("");

	private String itemName;
	private String spawnMode;
	private String flagName;

	// required
	public ItemEntity()
	{}

	@Override
	public void fromSourceMatcher(MapExtractor extractor, Matcher matcher)
	{
		indent = matcher.group(1);
		itemName = matcher.group(2);
		posX = Integer.decode(matcher.group(3));
		posY = Integer.decode(matcher.group(4));
		posZ = Integer.decode(matcher.group(5));
		spawnMode = matcher.group(6);
		flagName = matcher.group(7);

		if (!itemName.startsWith("ITEM_"))
			throw new IllegalArgumentException();

		type = "Item";

		String niceName = extractor.getNiceItemName(itemName);
		if (niceName != null)
			niceName = type + "_" + niceName;
		else
			niceName = type;

		setName(extractor.getNextName(niceName));

		Marker m = super.getBaseMarker();
		extractor.addMarker(m);

		m.entityComponent.itemName.setAndEnable(itemName);
		m.entityComponent.gameFlagName.setAndEnable(flagName);
		m.entityComponent.spawnMode.setAndEnable(spawnMode);
	}

	public ItemEntity(Marker m)
	{
		super(m);

		itemName = m.entityComponent.itemName.get();
		flagName = m.entityComponent.gameFlagName.get();
		spawnMode = m.entityComponent.spawnMode.get();
	}

	@Override
	public List<String> getLines()
	{
		List<String> lines = new ArrayList<>();
		lines.add(String.format("AUTO_ENTITY(%s)", genName.substring("GEN_".length())));
		return lines;
	}
}
