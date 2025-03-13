package game.sprite;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

// an animator-agnostic representation of a sprite animation consisting
// only of a stream of s16 commands and a set of label names
public class RawAnimation extends ArrayList<Short>
{
	// label names match indices in the ArrayList to names
	private final Map<Integer, String> labels;

	public RawAnimation()
	{
		super();
		labels = new TreeMap<>();
	}

	public RawAnimation(RawAnimation other)
	{
		this();
		this.addAll(other);
		labels.putAll(other.labels);
	}

	public void setLabel(int streamPos, String name)
	{
		labels.put(streamPos, name);
	}

	public boolean hasLabel(int streamPos)
	{
		return labels.containsKey(streamPos);
	}

	public String getLabel(int streamPos)
	{
		return labels.get(streamPos);
	}

	public int getStreamPos(String label)
	{
		for (Entry<Integer, String> e : labels.entrySet()) {
			if (e.getValue().equals(label))
				return e.getKey();
		}
		return -1;
	}

	public List<Entry<Integer, String>> getAllLabels()
	{
		return new ArrayList<>(labels.entrySet());
	}

	public RawAnimation deepCopy()
	{
		RawAnimation copy = new RawAnimation();

		for (Short s : this)
			copy.add(s);

		for (Entry<Integer, String> e : labels.entrySet())
			copy.labels.put(e.getKey(), e.getValue());

		return copy;
	}

	public void print()
	{
		int i = 0;
		for (Short s : this) {
			System.out.printf("%04X ", s);
			if (++i % 8 == 0)
				System.out.println();
		}
		if (i % 8 != 0)
			System.out.println();

		for (Entry<Integer, String> e : labels.entrySet()) {
			System.out.printf("%02X %s%n", e.getKey(), e.getValue());
		}
	}
}
