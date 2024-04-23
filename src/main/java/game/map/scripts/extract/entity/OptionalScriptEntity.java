package game.map.scripts.extract.entity;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import game.map.marker.Marker;
import game.map.scripts.extract.Extractor;
import game.map.scripts.extract.HeaderEntry;
import game.map.scripts.extract.HeaderEntry.HeaderParseException;

public class OptionalScriptEntity extends ExtractedEntity
{
	/**
		Call(MakeEntity, Ref(Entity_BrickBlock), -345, 77, -117, 0, MAKE_ENTITY_END)
		Call(AssignScript, Ref(N(EVS_BreakBlock_ThunderBolt)))
	*/

	private static final String TYPES = "("
		+ "BrickBlock|TriggerBlock|InertYellowBlock|PowBlock|"
		+ "RedSwitch|GreenStompSwitch|"
		+ "Signpost|BoardedFloor|ScriptSpring|StarBoxLauncher|"
		+ "BombableRock|BombableRockWide|"
		+ "Hammer1Block|Hammer1BlockWideX|Hammer1BlockWideZ|Hammer1BlockTiny|"
		+ "Hammer2Block|Hammer2BlockWideX|Hammer2BlockWideZ|Hammer2BlockTiny|"
		+ "Hammer3Block|Hammer3BlockWideX|Hammer3BlockWideZ|Hammer3BlockTiny"
		+ ")";
	private static final String RegexString = ExtractedEntity.INDENT +
		"Call\\(MakeEntity, Ref\\(Entity_" + TYPES + "\\)" + ExtractedEntity.ARG.repeat(4) + ",\\s*MAKE_ENTITY_END\\)" +
		"(?:\\n\\s*Call\\(AssignScript,\\s*Ref\\((\\S+)\\)\\))?";
	public static final Matcher RegexMatcher = Pattern.compile(RegexString).matcher("");

	private boolean hasScript = false;
	private String scriptName;

	// required
	public OptionalScriptEntity()
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

		scriptName = matcher.group(7);
		hasScript = (scriptName != null);

		setName(extractor.getNextName(type));

		Marker m = super.getBaseMarker();
		extractor.addMarker(m);

		if (hasScript)
			m.entityComponent.scriptName.setAndEnable(scriptName);
	}

	public OptionalScriptEntity(Marker m)
	{
		super(m);

		hasScript = m.entityComponent.scriptName.isEnabled();
		scriptName = m.entityComponent.scriptName.get();
	}

	@Override
	public List<String> getLines()
	{
		List<String> lines = super.getLines();
		if (hasScript)
			lines.add(String.format("Call(AssignScript, Ref(%s_SCRIPT))", genName));
		return lines;
	}

	@Override
	public void addHeaderDefines(HeaderEntry h)
	{
		super.addHeaderDefines(h);
		h.addDefine("PARAMS", makeParamList(h));

		if (hasScript)
			h.addDefine("SCRIPT", scriptName);
	}

	@Override
	public void parseHeaderDefines(Marker m, HeaderEntry h) throws HeaderParseException
	{
		if (h.hasDefine("SCRIPT"))
			m.entityComponent.scriptName.setAndEnable(h.getDefine("SCRIPT"));
	}
}
