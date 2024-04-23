package game.map.scripts.extract.entity;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import game.map.marker.Marker;
import game.map.scripts.extract.Extractor;
import game.map.scripts.extract.HeaderEntry;
import game.map.scripts.extract.HeaderEntry.HeaderParseException;

public class Tweester extends ExtractedEntity
{
	/**
		Call(MakeEntity, Ref(Entity_Tweester), 327, 0, 8, 0, Ref(N(TweesterPaths)), MAKE_ENTITY_END)
	*/

	private static final String TYPES = "(Tweester)";
	private static final String RegexString = ExtractedEntity.INDENT +
		"Call\\(MakeEntity, Ref\\(Entity_" + TYPES + "\\)" + ExtractedEntity.ARG.repeat(4) + ",\\s*Ref\\((\\S+)\\),\\s*MAKE_ENTITY_END\\)";
	public static final Matcher RegexMatcher = Pattern.compile(RegexString).matcher("");

	private String pathsName;

	// required
	public Tweester()
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
		pathsName = matcher.group(7);

		setName(extractor.getNextName(type));

		Marker m = super.getBaseMarker();
		extractor.addMarker(m);

		m.entityComponent.pathsName.setAndEnable(pathsName);
	}

	public Tweester(Marker m)
	{
		super(m);

		pathsName = m.entityComponent.pathsName.get();
	}

	@Override
	public void addHeaderDefines(HeaderEntry h)
	{
		super.addHeaderDefines(h);

		h.addDefine("PATHS", pathsName);
		h.addDefine("PARAMS", makeParamList(h, "PATHS"));
	}

	@Override
	public void parseHeaderDefines(Marker m, HeaderEntry h) throws HeaderParseException
	{
		m.entityComponent.pathsName.setAndEnable(h.getDefine("PATHS"));
	}
}
