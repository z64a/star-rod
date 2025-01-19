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

	public void setLabel(int pos, String name)
	{
		labels.put(pos, name);
	}

	public boolean hasLabel(int pos)
	{
		return labels.containsKey(pos);
	}

	public String getLabel(int pos)
	{
		return labels.get(pos);
	}

	public int getPos(String label)
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
}
