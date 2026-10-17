package app.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import app.input.PatchFileParser.PatchUnit;
import util.CaseInsensitiveMap;

public class PatchFileParserRegressionTest
{
	@Test
	public void nestedConditionDoesNotLeakFromSkippedParent()
	{
		CaseInsensitiveMap<String> rules = new CaseInsensitiveMap<>();
		rules.put("Outer", "No");
		rules.put("Inner", "Yes");

		PatchUnit unit = parseUnit(String.join("\n",
			"#new:Data $Nested {",
			"##[IF:Outer:Yes]",
			"OUTER",
			"##[IF:Inner:Yes]",
			"INNER",
			"##[ENDIF]",
			"OUTER_TAIL",
			"##[ELSE]",
			"NOT_OUTER",
			"##[ENDIF]",
			"TAIL",
			"}"), rules);

		assertEquals(List.of("NOT_OUTER", "TAIL"), body(unit));
	}

	@Test
	public void elseIfNotDoesNotOverrideEarlierMatch()
	{
		CaseInsensitiveMap<String> rules = new CaseInsensitiveMap<>();
		rules.put("Mode", "A");

		PatchUnit unit = parseUnit(String.join("\n",
			"#new:Data $Selected {",
			"##[IF:Mode:A]",
			"MODE_A",
			"##[ELSEIFNOT:Mode:B]",
			"NOT_B",
			"##[ELSE]",
			"OTHER",
			"##[ENDIF]",
			"}"), rules);

		assertEquals(List.of("MODE_A"), body(unit));
	}

	@Test
	public void undefinedValueInInactiveBranchIsIgnored()
	{
		PatchUnit unit = parseUnit(String.join("\n",
			"#new:Data $Value {",
			"##[IF:Missing]",
			"##[VALUE:AlsoMissing]",
			"##[ELSE]",
			"KEPT",
			"##[ENDIF]",
			"}"), new CaseInsensitiveMap<>());

		assertEquals(List.of("KEPT"), body(unit));
	}

	@Test
	public void rejectsElseIfAfterElse()
	{
		InputFileException exception = assertThrows(InputFileException.class, () -> parse(String.join("\n",
			"#new:Data $Invalid {",
			"##[IF:Mode:A]",
			"A",
			"##[ELSE]",
			"OTHER",
			"##[ELSEIF:Mode:B]",
			"B",
			"##[ENDIF]",
			"}"), new CaseInsensitiveMap<>()));

		assertEquals("ElseIf found after Else: ELSEIF:Mode:B", exception.getMessage());
		assertEquals("inline.patch [Line 6]", exception.getOrigin());
	}

	@Test
	public void keepsCStyleCommentRulesAndReportsUnclosedBlockAtItsOpeningLine()
	{
		PatchUnit unit = parseUnit(String.join("\n",
			"#new:Data $Comments {",
			"Alpha /* removed",
			"still removed */ Beta",
			"Gamma // removed",
			"\"Delta // literal /* literal */\"",
			"}"), new CaseInsensitiveMap<>());

		assertEquals(List.of("Alpha Beta", "Gamma", "\"Delta // literal /* literal */\""), body(unit));

		InputFileException exception = assertThrows(InputFileException.class, () -> parse(String.join("\n",
			"#new:Data $Invalid {",
			"BODY /* never closed",
			"}"), new CaseInsensitiveMap<>()));

		assertEquals("Missing */ -- comment was not closed by end of file!", exception.getMessage());
		assertEquals("inline.patch [Line 2]", exception.getOrigin());
	}

	@Test
	public void reportsUnclosedQuotedLiteralAtItsOpeningLine()
	{
		InputFileException exception = assertThrows(InputFileException.class, () -> parse(String.join("\n",
			"#new:Data $Invalid {",
			"\"BODY",
			"}"), new CaseInsensitiveMap<>()));

		assertEquals("Missing \" -- string was not closed by end of file!", exception.getMessage());
		assertEquals("inline.patch [Line 2]", exception.getOrigin());
	}

	@Test
	public void preservesBlankLinesInMessageBodies()
	{
		PatchUnit unit = parseUnit(String.join("\n",
			"#message:0A:(Test) {",
			"  Hello",
			"",
			"World  [End]",
			"}"), new CaseInsensitiveMap<>());

		assertEquals(List.of("  Hello", "", "World  [End]"), body(unit));
	}

	private static PatchUnit parseUnit(String source, CaseInsensitiveMap<String> rules)
	{
		List<PatchUnit> units = parse(source, rules);
		assertEquals(1, units.size());
		return units.get(0);
	}

	private static List<PatchUnit> parse(String source, CaseInsensitiveMap<String> rules)
	{
		StreamSource origin = new StreamSource("inline.patch");
		String[] sourceLines = source.split("\\R", -1);
		List<Line> lines = new ArrayList<>(sourceLines.length);
		for (int i = 0; i < sourceLines.length; i++)
			lines.add(new Line(origin, i + 1, sourceLines[i]));
		return PatchFileParser.parse(lines, rules);
	}

	private static List<String> body(PatchUnit unit)
	{
		return unit.body.stream().map(line -> line.str).collect(Collectors.toList());
	}
}
