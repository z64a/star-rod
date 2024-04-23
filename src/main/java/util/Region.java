package util;

import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

public class Region implements Comparable<Region>
{
	public static void main(String[] args)
	{
		List<Region> mergedRegions = new LinkedList<>();
		mergedRegions.add(new Region(0x100, 0x300));
		mergedRegions.add(new Region(0x300, 0x500));
		mergedRegions.add(new Region(0x700, 0x900));
		mergedRegions.add(new Region(0x1100, 0x2000));

		List<Region> reservedRegions = new LinkedList<>();
		reservedRegions.add(new Region(0x200, 0x800));
		reservedRegions.add(new Region(0x1000, 0x1200));
		reservedRegions.add(new Region(0x1400, 0x1600));

		for (Region r : reservedRegions) {
			ListIterator<Region> iter = mergedRegions.listIterator();

			while (iter.hasNext()) {
				Region m = iter.next();

				if (m.length() == 0 || !Region.overlaps(r, m))
					continue;

				if (m.start <= r.start && m.end >= r.end) {
					iter.add(new Region(r.end, m.end));
					m.end = r.start;
				}
				else if (r.start <= m.start && r.end >= m.end)
					m.end = m.start; // make size = 0
				else if (r.start <= m.start)
					m.start = r.end;
				else
					m.end = r.start;
			}
		}

		LinkedList<Region> finalRegions = new LinkedList<>();
		for (Region r : mergedRegions) {
			if (r.length() > 0)
				finalRegions.add(r);
		}

		for (Region r : finalRegions)
			System.out.println(r);
	}

	public long start;
	public long end;

	public Region(int start, int end)
	{
		this.start = start & 0xFFFFFFFFL;
		this.end = end & 0xFFFFFFFFL;
	}

	public Region(long start, long end)
	{
		this.start = start;
		this.end = end;
	}

	public long length()
	{
		return end - start;
	}

	@Override
	public int compareTo(Region other)
	{
		return (int) (this.start - other.start);
	}

	public static boolean overlaps(Region a, Region b)
	{
		return (a.start <= b.end) && (a.end >= b.start);
	}

	public static boolean adjacent(Region a, Region b)
	{
		if (a.start < b.start)
			return a.end == b.start;
		else
			return b.end == a.start;
	}

	public boolean contains(int value)
	{
		return start <= value && value <= end;
	}

	public boolean contains(Region b)
	{
		return b.start >= start && b.end <= end;
	}

	/**
	 * @param a
	 * @param b
	 * @return positive for overlap, zero for space between them
	 */
	public static long overlap(Region a, Region b)
	{
		if (a.start < b.start)
			return a.end - b.start;
		else
			return b.end - a.start;
	}

	public static Region merge(Region a, Region b)
	{
		if (!adjacent(a, b))
			return null;

		if (a.start < b.start)
			return new Region(a.start, b.end);
		else
			return new Region(b.end, a.start);
	}

	@Override
	public String toString()
	{
		return String.format("[%X, %X]", start, end);
	}
}
