package game.map.scripts.generators;

import game.map.editor.DeepCopyable;
import util.xml.XmlWrapper.XmlSerializable;

@Deprecated
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
}
