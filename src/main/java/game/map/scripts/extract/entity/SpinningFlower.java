package game.map.scripts.extract.entity;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import game.map.marker.Marker;
import game.map.marker.Marker.MarkerType;
import game.map.scripts.extract.Extractor;
import game.map.scripts.extract.HeaderEntry;
import game.map.scripts.extract.HeaderEntry.HeaderParseException;
import util.NameUtils;

public class SpinningFlower extends ExtractedEntity
{
	/**
		Call(MakeEntity, Ref(Entity_SpinningFlower), 30, 0, -250, 0, (-33, 90, -347,)? MAKE_ENTITY_END)
	*/

	private static final String TYPES = "(SpinningFlower)";
	private static final String THREE_OPTIONAL_ARGS = "(?:" + ExtractedEntity.ARG.repeat(3) + ")?";
	private static final String RegexString = ExtractedEntity.INDENT +
		"Call\\(MakeEntity, Ref\\(Entity_" + TYPES + "\\)" + ExtractedEntity.ARG.repeat(4) + THREE_OPTIONAL_ARGS + ",\\s*MAKE_ENTITY_END\\)";
	public static final Matcher RegexMatcher = Pattern.compile(RegexString).matcher("");

	private boolean hasTarget;
	private String targetName;

	// required
	public SpinningFlower()
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

		setName(extractor.getNextName(type));

		Marker m = super.getBaseMarker();
		extractor.addMarker(m);

		hasTarget = (matcher.group(7) != null);
		if (hasTarget) {
			int targetX = Integer.decode(matcher.group(7));
			int targetY = Integer.decode(matcher.group(8));
			int targetZ = Integer.decode(matcher.group(9));

			targetName = extractor.getNextName("Target");
			m.entityComponent.targetName.setAndEnable(targetName);

			Marker target = new Marker(targetName, MarkerType.Position, targetX, targetY, targetZ, 0);
			extractor.addMarker(target);
		}
	}

	public SpinningFlower(Marker m)
	{
		super(m);

		hasTarget = m.entityComponent.targetName.isEnabled();
		targetName = m.entityComponent.targetName.get();
	}

	@Override
	public void addHeaderDefines(HeaderEntry h)
	{
		super.addHeaderDefines(h);

		if (hasTarget) {
			String genTarget = NameUtils.toEnumStyle("GEN_" + targetName);
			h.addDefine("TARGET", "%s_VEC", genTarget);
			h.addDefine("PARAMS", makeParamList(h, "TARGET"));
		}
		else {
			h.addDefine("PARAMS", makeParamList(h));
		}
	}

	@Override
	public void parseHeaderDefines(Marker m, HeaderEntry h) throws HeaderParseException
	{
		if (h.hasDefine("TARGET"))
			m.entityComponent.targetName.setAndEnable(h.getDefine("TARGET"));
	}
}
