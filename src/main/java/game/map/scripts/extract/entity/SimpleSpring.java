package game.map.scripts.extract.entity;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import game.map.marker.Marker;
import game.map.scripts.extract.MapExtractor;

public class SimpleSpring extends ExtractedEntity
{
	/**
		Call(MakeEntity, Ref(Entity_SimpleSpring), 210, -50, -20, 0, 100, MAKE_ENTITY_END)
	*/

	private static final String TYPES = "(SimpleSpring)";
	private static final String RegexString = ExtractedEntity.INDENT +
		"Call\\(MakeEntity, Ref\\(Entity_" + TYPES + "\\)" + ExtractedEntity.ARG.repeat(5) + ",\\s*MAKE_ENTITY_END\\)";
	public static final Matcher RegexMatcher = Pattern.compile(RegexString).matcher("");

	private int launchHeight;

	// required
	public SimpleSpring()
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
		launchHeight = Integer.decode(matcher.group(7));

		setName(extractor.getNextName(type));

		Marker m = super.getBaseMarker();
		extractor.addMarker(m);

		m.entityComponent.launchDist.setAndEnable(launchHeight);
	}

	public SimpleSpring(Marker m)
	{
		super(m);

		launchHeight = m.entityComponent.launchDist.get();
	}
}
