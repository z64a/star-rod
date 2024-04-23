package game.globals.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import game.DecompEnum;

public class FlagSet
{
	private final DecompEnum flagEnum;
	private final String prefix;
	private final boolean changeCasing;

	// actual internal representation of the flags
	private int flagBits;

	public FlagSet(DecompEnum flagEnum, String prefix, boolean changeCasing)
	{
		this.flagEnum = flagEnum;
		this.prefix = prefix;
		this.changeCasing = changeCasing;
	}

	public FlagSet(FlagSet other)
	{
		this.flagEnum = other.flagEnum;
		this.prefix = other.prefix;
		this.flagBits = other.flagBits;
		this.changeCasing = other.changeCasing;
	}

	public void setReal(List<String> names)
	{
		flagBits = 0;

		for (String name : names) {
			Integer v = flagEnum.getID(name);
			if (v != null) {
				flagBits |= v;
			}
		}
	}

	public void setDisp(List<String> names)
	{
		flagBits = 0;

		for (String name : names) {
			name = name.toUpperCase().replaceAll(" ", "_");
			Integer v = flagEnum.getID(prefix + name);
			if (v != null) {
				flagBits |= v;
			}
		}
	}

	public List<String> getSelectedReal()
	{ return getNamesImpl("", false, false); }

	public List<String> getSelectedDisp()
	{ return getNamesImpl(prefix, changeCasing, false); }

	public List<String> getAllReal()
	{ return getNamesImpl("", false, true); }

	public String getYamlOut()
	{
		List<String> list = getNamesImpl("", false, false);
		String s = String.join(", ", list);
		if (s.isBlank()) {
			return "[]";
		}
		else {
			return "[ " + s + " ]";
		}
	}

	public List<String> getAllDisp()
	{ return getNamesImpl(prefix, changeCasing, true); }

	private List<String> getNamesImpl(String prefix, boolean shouldLower, boolean all)
	{
		List<String> names = new ArrayList<>();

		for (Entry<String, Integer> e : flagEnum.getEntries()) {
			String name = e.getKey();
			int bit = e.getValue();

			if (all || (flagBits & bit) != 0) {
				name = name.substring(prefix.length());

				if (shouldLower) {
					String[] tokens = name.split("_");

					for (int i = 0; i < tokens.length; i++)
						tokens[i] = tokens[i].substring(0, 1).toUpperCase() + tokens[i].substring(1).toLowerCase();

					name = String.join(" ", tokens);
				}

				names.add(name);
			}
		}

		return names;
	}

	public List<Integer> getAllBits()
	{
		List<Integer> bits = new ArrayList<>();

		for (Entry<String, Integer> e : flagEnum.getEntries()) {
			bits.add(e.getValue());
		}

		return bits;
	}

	public boolean testBit(int bit)
	{
		return (flagBits & bit) != 0;
	}

	public int getBits()
	{ return flagBits; }

	public void setBits(int bits)
	{ flagBits = bits; }
}
