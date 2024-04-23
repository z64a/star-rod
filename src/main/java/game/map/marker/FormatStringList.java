package game.map.marker;

import java.util.ArrayList;

public class FormatStringList extends ArrayList<String>
{
	public void addf(String fmt, Object ... args)
	{
		add(String.format(fmt, args));
	}
}
