package game.map.scripts.extract.entity;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import game.map.marker.Marker;
import game.map.scripts.extract.Extractor;
import game.map.scripts.extract.HeaderEntry;
import game.map.scripts.extract.HeaderEntry.HeaderParseException;

public class SuperBlock extends ExtractedEntity
{
	private static final Matcher SuperBlockMatcher = Pattern.compile(
		"EVT_MAKE_SUPER_BLOCK\\(\\s*(\\S+),\\s*(\\S+),\\s*(\\S+),\\s*(\\S+)\\s*\\)").matcher("");

	private static final Matcher SuperVarMatcher = Pattern.compile(
		"#define\\s+SUPER_BLOCK_MAPVAR\\s+(?!GEN_)(\\w+)").matcher("");

	private static final Matcher SuperFlagMatcher = Pattern.compile(
		"#define\\s+SUPER_BLOCK_GAMEFLAG\\s+(?!GEN_)(\\w+)").matcher("");

	private String varName;
	private String flagName;

	// required
	public SuperBlock()
	{}

	public static void scan(Extractor extractor)
	{
		String workingText = extractor.getFileText();
		SuperBlockMatcher.reset(workingText);
		if (!SuperBlockMatcher.find())
			return;

		SuperBlock block = new SuperBlock();
		block.type = "SuperBlock";
		block.posX = Integer.decode(SuperBlockMatcher.group(1));
		block.posY = Integer.decode(SuperBlockMatcher.group(2));
		block.posZ = Integer.decode(SuperBlockMatcher.group(3));
		block.angle = Integer.decode(SuperBlockMatcher.group(4));
		block.setName(extractor.getNextName(block.type));

		workingText = SuperBlockMatcher.replaceFirst("EVT_MAKE_SUPER_BLOCK(" + block.genName + "_PARAMS)");

		SuperVarMatcher.reset(workingText);
		if (!SuperVarMatcher.find())
			return;

		block.varName = SuperVarMatcher.group(1);
		workingText = SuperVarMatcher.replaceFirst("#define SUPER_BLOCK_MAPVAR " + block.genName + "_VAR");

		SuperFlagMatcher.reset(workingText);
		if (!SuperFlagMatcher.find())
			return;

		block.flagName = SuperFlagMatcher.group(1);
		workingText = SuperFlagMatcher.replaceFirst("#define SUPER_BLOCK_GAMEFLAG " + block.genName + "_FLAG");

		extractor.setFileText(workingText);

		Marker m = block.getBaseMarker();
		extractor.addMarker(m);

		m.entityComponent.mapVarName.setAndEnable(block.varName);
		m.entityComponent.gameFlagName.setAndEnable(block.flagName);
	}

	public SuperBlock(Marker m)
	{
		super(m);

		varName = m.entityComponent.mapVarName.get();
		flagName = m.entityComponent.gameFlagName.get();
	}

	@Override
	public void fromSourceMatcher(Extractor extractor, Matcher matcher)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public List<String> getLines()
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public void addHeaderDefines(HeaderEntry h)
	{
		super.addHeaderDefines(h);

		h.addDefine("PARAMS", makeParamList(h));

		h.addDefine("VAR", varName);
		h.addDefine("FLAG", flagName);
	}

	@Override
	public void parseHeaderDefines(Marker m, HeaderEntry h) throws HeaderParseException
	{
		m.entityComponent.mapVarName.setAndEnable(h.getDefine("VAR"));
		m.entityComponent.gameFlagName.setAndEnable(h.getDefine("FLAG"));
	}
}
