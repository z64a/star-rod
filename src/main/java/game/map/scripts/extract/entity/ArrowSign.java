package game.map.scripts.extract.entity;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import game.map.marker.Marker;
import game.map.scripts.extract.Extractor;
import game.map.scripts.extract.HeaderEntry;
import game.map.scripts.extract.HeaderEntry.HeaderParseException;

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

	// required
	public ArrowSign()
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
		pitch = Integer.decode(matcher.group(7));

		setName(extractor.getNextName(type));

		Marker m = super.getBaseMarker();
		extractor.addMarker(m);

		m.entityComponent.angle.setAndEnable(pitch);
	}

	public ArrowSign(Marker m)
	{
		super(m);

		pitch = m.entityComponent.angle.get();
	}

	@Override
	public List<String> getLines()
	{
		return super.getLines();
	}

	@Override
	public void addHeaderDefines(HeaderEntry h)
	{
		super.addHeaderDefines(h);
		h.addDefine("ANGLE", pitch);
		h.addDefine("PARAMS", makeParamList(h, "ANGLE"));
	}

	@Override
	public void parseHeaderDefines(Marker m, HeaderEntry h) throws HeaderParseException
	{
		m.entityComponent.angle.setAndEnable(h.getIntDefine("ANGLE"));
	}
}
