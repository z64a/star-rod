package game.map.scripts.extract.entity;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import game.map.marker.Marker;
import game.map.scripts.extract.MapExtractor;

public class CoinBlock extends ExtractedEntity
{
	/**
	    Call(MakeEntity, Ref(Entity_MulticoinBlock), 100, 0, -70, 0, MAKE_ENTITY_END)
	    Call(AssignBlockFlag, GF_KMR04_MultiCoinBrick)
	*/

	private static final String TYPES = "(MulticoinBlock)";
	private static final String RegexString = ExtractedEntity.INDENT +
		"Call\\(MakeEntity, Ref\\(Entity_" + TYPES + "\\)" + ExtractedEntity.ARG.repeat(4) + ",\\s*MAKE_ENTITY_END\\)" +
		"(?:\\R\\s*Call\\(AssignBlockFlag" + ExtractedEntity.ARG + "\\))?";
	public static final Matcher RegexMatcher = Pattern.compile(RegexString).matcher("");

	private boolean hasFlag;
	private String flagName;

	// required
	public CoinBlock()
	{}

	@Override
	public void fromSourceMatcher(MapExtractor extractor, Matcher matcher)
	{
		indent = matcher.group(1);
		type = matcher.group(2);
		posX = Integer.decode(matcher.group(3));
		posY = Integer.decode(matcher.group(4));
		posZ = Integer.decode(matcher.group(5));
		angle = Integer.decode(matcher.group(6));
		flagName = matcher.group(7);
		hasFlag = (flagName != null);

		setName(extractor.getNextName(type));

		Marker m = super.getBaseMarker();
		extractor.addMarker(m);

		if (hasFlag)
			m.entityComponent.gameFlagName.setAndEnable(flagName);
	}

	public CoinBlock(Marker m)
	{
		super(m);

		hasFlag = m.entityComponent.gameFlagName.isEnabled();
		flagName = m.entityComponent.gameFlagName.get();
	}
}
