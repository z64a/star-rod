package game.map.scripts.nextract.entity;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import game.map.marker.Marker;
import game.map.scripts.nextract.NewExtractor;

public class BlueWarpPipe extends ExtractedEntity
{
	/**
	Call(MakeEntity, Ref(Entity_BlueWarpPipe), 430, 0, -120, 0,
		dro_01_ENTRY_2, Ref(N(EVS_WarpPipeExit)), EVT_INDEX_OF_GAME_FLAG(GF_DRO01_WarpPipe), MAKE_ENTITY_END)
	*/

	private static final String TYPES = "(BlueWarpPipe)";
	private static final String RegexString = ExtractedEntity.INDENT +
		"Call\\(MakeEntity, Ref\\(Entity_" + TYPES + "\\)" + ExtractedEntity.ARG.repeat(5) +
		",\\s*Ref\\((\\S+)\\)" +
		",\\s*EVT_INDEX_OF_GAME_FLAG\\((\\S+)\\)" +
		",\\s*MAKE_ENTITY_END\\)";
	public static final Matcher RegexMatcher = Pattern.compile(RegexString).matcher("");

	private String entryName;
	private String scriptName;
	private String flagName;

	// required
	public BlueWarpPipe()
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
		entryName = matcher.group(7);
		scriptName = matcher.group(8);
		flagName = matcher.group(9);

		setName(extractor.getNextName(type));

		Marker m = super.getBaseMarker();
		extractor.addMarker(m);

		m.entityComponent.entryName.setAndEnable(entryName);
		m.entityComponent.scriptName.setAndEnable(scriptName);
		m.entityComponent.gameFlagName.setAndEnable(flagName);
	}

	public BlueWarpPipe(Marker m)
	{
		super(m);

		entryName = m.entityComponent.entryName.get();
		scriptName = m.entityComponent.scriptName.get();
		flagName = m.entityComponent.gameFlagName.get();
	}
}
