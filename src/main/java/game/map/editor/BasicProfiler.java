package game.map.editor;

import java.util.ArrayList;
import java.util.List;

public class BasicProfiler
{
	private static class Snapshot
	{
		public final String name;
		public final long start;
		public final long end;

		private Snapshot(String name, long start, long end)
		{
			this.name = name;
			this.start = start;
			this.end = end;
		}
	}

	private List<Snapshot> times;
	private long startTime;

	public void begin()
	{
		times = new ArrayList<>();
		startTime = System.nanoTime();
	}

	public void record(String name)
	{
		if (times.isEmpty())
			times.add(new Snapshot(name, startTime, System.nanoTime()));
		else
			times.add(new Snapshot(name, times.get(times.size() - 1).end, System.nanoTime()));
	}

	public void print()
	{
		System.out.println("PERFORMANCE");
		double total = (System.nanoTime() - startTime) / 1e6;
		for (Snapshot s : times) {
			double time = (s.end - s.start) / 1e6;
			System.out.printf("%-10s %4.2f  %5.2f%% ", s.name, time, 100 * time / total);
			for (int i = 0; i < Math.round(50 * (time / total)); i++)
				System.out.print("-");
			System.out.println();
		}
		System.out.printf("TOTAL:   %6.2f%n", total);
		System.out.println();
	}
}
