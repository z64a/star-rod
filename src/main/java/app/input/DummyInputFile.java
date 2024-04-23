package app.input;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class DummyInputFile
{
	private final AbstractSource source;
	public final List<Line> lines;

	public DummyInputFile(DummySource source)
	{
		this.source = source;
		lines = new LinkedList<>();
	}

	public DummyInputFile(String name)
	{
		source = new DummySource(name);
		lines = new LinkedList<>();
	}

	public DummyInputFile(Class<?> c)
	{
		source = new DummySource(c.getName());
		lines = new LinkedList<>();
	}

	public DummyInputFile(DummySource source, String[] line)
	{
		this.source = source;

		lines = new ArrayList<>(line.length);
		for (int i = 0; i < line.length; i++)
			lines.add(new Line(source, i + 1, line[i]));
	}

	public DummyInputFile(String name, String[] line)
	{
		source = new DummySource(name);

		lines = new ArrayList<>(line.length);
		for (int i = 0; i < line.length; i++)
			lines.add(new Line(source, i + 1, line[i]));
	}

	public DummyInputFile(Class<?> c, String[] line)
	{
		source = new DummySource(c.getName());

		lines = new ArrayList<>(line.length);
		for (int i = 0; i < line.length; i++)
			lines.add(new Line(source, i + 1, line[i]));
	}

	public void add(String line)
	{
		lines.add(new Line(source, lines.size() + 1, line));
	}
}
