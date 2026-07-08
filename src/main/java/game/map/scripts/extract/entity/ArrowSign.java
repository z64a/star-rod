package game.map.scripts.nextract.entity;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import game.map.marker.Marker;
import game.map.scripts.nextract.NewExtractor;

public class ArrowSign extends ExtractedEntity
{
	/**
		Call(MakeEntity, Ref(Entity_ArrowSign), 825, 170, 115, 0, 90, MAKE_ENTITY_END)
	*/

	private static final String TYPES = "(ArrowSign)";
	private static final String RegexString = ExtractedEntity.INDENT +
		"Call\\(MakeEntity, Ref\\(Entity_" + TYPES + "\\)" + ExtractedEntity.ARG.repeat(5) + ",\\s*MAKE_ENTITY_END\\)";
	public static final Matcher RegexMatcher = Pattern.compile(RegexString).matcher("");

	private int pitch;

	@Override
	public void fromSourceMatcher(NewExtractor extractor, Matcher matcher)
	{
		indent = matcher.group(1);
		type = matcher.group(2);
		posX = Integer.decode(matcher.group(3));
		posY = Integer.decode(matcher.group(4));
		posZ = Integer.decode(matcher.group(5));
		angle = Integer.decode(matcher.group(6));
		pitch = Integer.decode(matcher.group(7));

		setName(extractor.getNextName(type));

		Marker m = super.getBaseMarker();
		extractor.addMarker(m);

		m.entityComponent.angle.setAndEnable(pitch);
	}

	@Override
	public List<String> getLines()
	{
		return super.getLines();
	}
}
