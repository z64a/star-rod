package game.map.scripts.generators;

import static game.map.MapKey.*;

import org.w3c.dom.Element;

import game.map.editor.DeepCopyable;
import game.map.scripts.ScriptData;
import game.map.scripts.generators.foliage.Foliage;
import game.map.scripts.generators.foliage.Foliage.FoliageType;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlSerializable;
import util.xml.XmlWrapper.XmlWriter;

public abstract class Generator implements XmlSerializable, DeepCopyable
{
	/*
	 * Adding a new type of generator is not terribly complicated, but it does
	 * involve editing several classes to get it working:
	 *
	 * Generator.java (this file)
	 * (1) Add a new type XYZ to the enum
	 * (2) Add code for XML read/writing
	 *
	 * Create a class XYZ extending Generator and a matching class XYZInfoPanel.
	 *
	 * GeneratorsPanel.java
	 * (1) Add new method for generator creation options: addCreateXYZOptions
	 * (2) Add code to updateInfoPanel() for the new XYZInfoPanel
	 *
	 * ScriptGenerator.java
	 * Add code that will actually write data from your new generator to the
	 * generated patch file.
	 */

	public static enum GeneratorType
	{
		// @formatter:off
		Entrance	("Entrances"),
		Exit		("Exits"),
		Tree		("Trees"),
		Bush		("Bushes");
		// @formatter:on

		private final String nodeName;

		private GeneratorType(String nodeName)
		{
			this.nodeName = nodeName;
		}

		public String getName()
		{
			return nodeName;
		}
	}

	public static enum ValidationState
	{
		Invalid,
		Incomplete,
		Valid
	}

	public final GeneratorType type;

	public Generator(GeneratorType type)
	{
		this.type = type;
	}

	@Override
	public abstract Generator deepCopy();

	public static void readXml(XmlReader xmr, Element generatorsElem, ScriptData data)
	{
		for (Element elem : xmr.getTags(generatorsElem, TAG_ENTRANCE)) {
			Entrance entrance = Entrance.read(xmr, elem);
			data.generatorsTreeModel.addToCategory(GeneratorType.Entrance, entrance);
		}

		for (Element elem : xmr.getTags(generatorsElem, TAG_EXIT)) {
			Exit exit = Exit.read(xmr, elem);
			data.generatorsTreeModel.addToCategory(GeneratorType.Exit, exit);
		}

		for (Element elem : xmr.getTags(generatorsElem, TAG_TREE)) {
			Foliage tree = Foliage.read(FoliageType.Tree, xmr, elem);
			data.generatorsTreeModel.addToCategory(GeneratorType.Tree, tree);
		}

		for (Element elem : xmr.getTags(generatorsElem, TAG_BUSH)) {
			Foliage bush = Foliage.read(FoliageType.Bush, xmr, elem);
			data.generatorsTreeModel.addToCategory(GeneratorType.Bush, bush);
		}
	}

	public static void writeXml(XmlWriter xmw, ScriptData data)
	{
		for (Generator generator : data.generatorsTreeModel.getObjectsInCategory(GeneratorType.Entrance)) {
			Entrance entrance = (Entrance) generator;
			entrance.toXML(xmw);
		}

		for (Generator generator : data.generatorsTreeModel.getObjectsInCategory(GeneratorType.Exit)) {
			Exit exit = (Exit) generator;
			exit.toXML(xmw);
		}

		for (Generator generator : data.generatorsTreeModel.getObjectsInCategory(GeneratorType.Tree)) {
			Foliage tree = (Foliage) generator;
			tree.toXML(xmw);
		}

		for (Generator generator : data.generatorsTreeModel.getObjectsInCategory(GeneratorType.Bush)) {
			Foliage bush = (Foliage) generator;
			bush.toXML(xmw);
		}
	}
}
