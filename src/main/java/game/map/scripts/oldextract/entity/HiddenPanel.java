package game.map.scripts.extract.entity;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import game.map.marker.Marker;
import game.map.scripts.extract.Extractor;
import game.map.scripts.extract.HeaderEntry;
import game.map.scripts.extract.HeaderEntry.HeaderParseException;

public class HiddenPanel extends ExtractedEntity
{
	/**
		Call(MakeEntity, Ref(Entity_HiddenPanel), -650, 75, -50, 0, MODEL_o251, MAKE_ENTITY_END)
		Call(AssignPanelFlag, GF_NOK14_HiddenPanel)
	*/

	private static final String TYPES = "(HiddenPanel)";
	private static final String RegexString = ExtractedEntity.INDENT +
		"Call\\(MakeEntity, Ref\\(Entity_" + TYPES + "\\)" + ExtractedEntity.ARG.repeat(5) + ",\\s*MAKE_ENTITY_END\\)" +
		"(?:\\n\\s*Call\\(AssignPanelFlag" + ExtractedEntity.ARG + "\\))?";
	public static final Matcher RegexMatcher = Pattern.compile(RegexString).matcher("");

	private String modelName;
	private boolean hasFlag;
	private String flagName;

	// required
	public HiddenPanel()
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
		modelName = matcher.group(7);
		flagName = matcher.group(8);
		hasFlag = (flagName != null);

		setName(extractor.getNextName(type));

		Marker m = super.getBaseMarker();
		extractor.addMarker(m);

		m.entityComponent.modelName.set(modelName);

		if (hasFlag)
			m.entityComponent.gameFlagName.setAndEnable(flagName);
	}

	public HiddenPanel(Marker m)
	{
		super(m);

		modelName = m.entityComponent.modelName.get();

		hasFlag = m.entityComponent.gameFlagName.isEnabled();
		flagName = m.entityComponent.gameFlagName.get();
	}

	@Override
	public List<String> getLines()
	{
		List<String> lines = super.getLines();
		if (hasFlag)
			lines.add(String.format("Call(AssignPanelFlag, %s_FLAG)", genName));
		return lines;
	}

	@Override
	public void addHeaderDefines(HeaderEntry h)
	{
		super.addHeaderDefines(h);

		h.addDefine("MODEL", modelName);
		h.addDefine("PARAMS", makeParamList(h, "MODEL"));

		if (hasFlag)
			h.addDefine("FLAG", flagName);
	}

	@Override
	public void parseHeaderDefines(Marker m, HeaderEntry h) throws HeaderParseException
	{
		m.entityComponent.modelName.set(h.getDefine("MODEL"));

		if (h.hasDefine("FLAG"))
			m.entityComponent.gameFlagName.setAndEnable(h.getDefine("FLAG"));
	}
}
