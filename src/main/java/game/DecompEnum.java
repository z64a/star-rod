package game;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.eclipse.cdt.core.dom.ast.ExpansionOverlapsBoundaryException;
import org.eclipse.cdt.core.dom.ast.IASTDeclaration;
import org.eclipse.cdt.core.dom.ast.IASTNode;
import org.eclipse.cdt.core.dom.ast.IASTTranslationUnit;
import org.eclipse.cdt.core.dom.ast.gnu.cpp.GPPLanguage;
import org.eclipse.cdt.core.parser.DefaultLogService;
import org.eclipse.cdt.core.parser.FileContent;
import org.eclipse.cdt.core.parser.IncludeFileContentProvider;
import org.eclipse.cdt.core.parser.ScannerInfo;
import org.eclipse.cdt.internal.core.dom.parser.cpp.CPPASTEnumerationSpecifier;
import org.eclipse.cdt.internal.core.dom.parser.cpp.CPPASTEnumerator;
import org.eclipse.cdt.internal.core.dom.parser.cpp.CPPASTName;
import org.eclipse.cdt.internal.core.dom.parser.cpp.CPPASTSimpleDeclaration;
import org.eclipse.core.runtime.CoreException;

import app.Directories;
import app.StarRodException;
import app.input.InvalidInputException;
import util.CaseInsensitiveMap;
import util.Logger;

public class DecompEnum
{
	private static final Matcher enumDefMatcher = Pattern.compile(
		"\\s*(\\w+)\\s*=\\s*(-?(?:0x[0-9a-fA-F]+|\\d+)),?").matcher("");

	public static void addEnums(CaseInsensitiveMap<DecompEnum> enums, String headerFileLocation)
	{
		IASTTranslationUnit translationUnit;

		// must make a copy of the header file inside the workspace for FileContent to work across WSL
		File copy = new File(Directories.DATABASE + "/include/" + FilenameUtils.getBaseName(headerFileLocation) + ".h");
		try {
			FileUtils.copyFile(new File(headerFileLocation), copy);
		}
		catch (IOException e) {
			Logger.printStackTrace(e);
			return;
		}

		try {
			Map<String, String> definedSymbols = new HashMap<>();
			String[] includePaths = {};
			translationUnit = GPPLanguage.getDefault().getASTTranslationUnit(
				FileContent.createForExternalFileLocation(copy.getAbsolutePath()),
				new ScannerInfo(definedSymbols, includePaths),
				IncludeFileContentProvider.getEmptyFilesProvider(),
				null,
				0,
				new DefaultLogService());
		}
		catch (CoreException e) {
			Logger.printStackTrace(e);
			return;
		}

		for (IASTDeclaration declaration : translationUnit.getDeclarations()) {
			try {
				// ignore compound declarations
				if (!(declaration instanceof CPPASTSimpleDeclaration))
					continue;

				String img = declaration.getSyntax().getImage();
				if (declaration.getChildren().length == 0) {
					Logger.logError("Unexpected AST for " + img);
					continue;
				}

				// only look for enums
				IASTNode declareType = declaration.getChildren()[0];
				if (!(declareType instanceof CPPASTEnumerationSpecifier))
					continue;

				try {
					DecompEnum denum = new DecompEnum((CPPASTEnumerationSpecifier) declareType);
					if (denum.name != null && !denum.name.isEmpty())
						enums.put(denum.name, denum);
				}
				catch (InvalidInputException e) {
					throw new StarRodException(e);
				}
			}
			catch (ExpansionOverlapsBoundaryException e) {
				Logger.printStackTrace(e);
			}
		}
	}

	private String name;
	private LinkedHashMap<String, Integer> encodeMap = new LinkedHashMap<>();
	private LinkedHashMap<Integer, String> decodeMap = new LinkedHashMap<>();

	private DecompEnum(CPPASTEnumerationSpecifier declaration) throws InvalidInputException, ExpansionOverlapsBoundaryException
	{
		int lastValue = -1;

		for (IASTNode child : declaration.getChildren()) {
			//	printNode(child);

			if (child instanceof CPPASTName) {
				try {
					name = child.getSyntax().getImage();
				}
				catch (UnsupportedOperationException e) {
					// no name, ignore
					return;
				}
			}
			else if (child instanceof CPPASTEnumerator) {
				String enumEntry = child.getRawSignature();

				if (child.getChildren().length == 2) {
					enumDefMatcher.reset(enumEntry);
					if (!enumDefMatcher.matches()) {
						Logger.logDetail("Unexpected enum entry: " + enumEntry);
						continue;
					}

					String key = enumDefMatcher.group(1);
					String valueText = enumDefMatcher.group(2);
					lastValue = (int) (long) Long.decode(valueText);
					encodeMap.put(key, lastValue);
					decodeMap.put(lastValue, key);
				}
				else {
					lastValue++;
					encodeMap.put(enumEntry, lastValue);
					decodeMap.put(lastValue, enumEntry);
				}
			}
		}
	}

	public String[] getValues()
	{
		String[] array = new String[decodeMap.size()];
		int i = 0;
		for (Entry<Integer, String> e : decodeMap.entrySet())
			array[i++] = e.getValue();
		return array;
	}

	public List<String> getValueList()
	{
		return new ArrayList<>(decodeMap.values());
	}

	public String getName(int id)
	{
		return decodeMap.get(id);
	}

	public Integer getID(String name)
	{
		return encodeMap.get(name);
	}

	public Collection<Entry<String, Integer>> getEntries()
	{
		return encodeMap.entrySet();
	}

	public static void printNode(IASTNode node) throws ExpansionOverlapsBoundaryException
	{
		printNode(node, "");
	}

	private static void printNode(IASTNode node, String indent) throws ExpansionOverlapsBoundaryException
	{
		String nodeType = node.getClass().getSimpleName();
		String nodeContent = "???";
		try {
			if (node.getSyntax() != null)
				nodeContent = node.getSyntax().toString();
		}
		catch (UnsupportedOperationException e) {}

		System.out.println(indent + nodeType + " : " + nodeContent);
		for (IASTNode child : node.getChildren()) {
			printNode(child, indent + "  ");
		}
	}
}
