package game.map.scripts.extract.entity;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import game.map.marker.Marker;
import game.map.scripts.extract.Extractor;
import game.map.scripts.extract.HeaderEntry;
import game.map.scripts.extract.HeaderEntry.HeaderParseException;

public class HeartBlock extends ExtractedEntity
{
	/**
		Call(MakeEntity, Ref(Entity_HeartBlock), -211, 60, -50, 30, (6)? MAKE_ENTITY_END)
	*/

	private static final String TYPES = "(HeartBlock)";
	private static final String RegexString = ExtractedEntity.INDENT +
		"Call\\(MakeEntity, Ref\\(Entity_" + TYPES + "\\)" + ExtractedEntity.ARG.repeat(4) + ExtractedEntity.OPTIONAL_ARG + ",\\s*MAKE_ENTITY_END\\)";
	public static final Matcher RegexMatcher = Pattern.compile(RegexString).matcher("");

	private boolean hasStyle;
	private int style;

	// required
	public HeartBlock()
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

		hasStyle = (matcher.group(7) != null);
		if (hasStyle) {
			style = Integer.decode(matcher.group(7));
		}

		setName(extractor.getNextName(type));

		Marker m = super.getBaseMarker();
		extractor.addMarker(m);

		if (hasStyle)
			m.entityComponent.style.setAndEnable(style);
	}

	public HeartBlock(Marker m)
	{
		super(m);

		hasStyle = m.entityComponent.style.isEnabled();
		style = m.entityComponent.style.get();
	}

	@Override
	public void addHeaderDefines(HeaderEntry h)
	{
		super.addHeaderDefines(h);

		if (hasStyle) {
			h.addDefine("STYLE", style);
			h.addDefine("PARAMS", makeParamList(h, "STYLE"));
		}
		else {
			h.addDefine("PARAMS", makeParamList(h));
		}
	}

	@Override
	public void parseHeaderDefines(Marker m, HeaderEntry h) throws HeaderParseException
	{
		if (h.hasDefine("STYLE"))
			m.entityComponent.style.setAndEnable(h.getIntDefine("STYLE"));
	}
}
