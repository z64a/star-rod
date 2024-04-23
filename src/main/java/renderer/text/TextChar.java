package renderer.text;

public class TextChar
{
	protected final int index;
	protected final int x, y;
	protected final int width, height;
	protected final int xoffset, yoffset;
	protected final int xadvance;

	protected final float u1, u2;
	protected final float v1, v2;

	public TextChar(String line, float imgWidth, float imgHeight)
	{
		// char id=0       x=0    y=0    width=0    height=0    xoffset=-8   yoffset=0    xadvance=14   page=0    chnl=0

		String[] tokens = line.trim().split("\\s+");

		if (!tokens[0].equals("char"))
			throw new IllegalArgumentException("Invalid line: " + line);

		int index = -1;
		int x = 0;
		int y = 0;
		int width = 0;
		int height = 0;
		int xoffset = 0;
		int yoffset = 0;
		int xadvance = 0;

		for (int i = 1; i < tokens.length; i++) {
			int eqPos = tokens[i].indexOf("=");
			if (eqPos < 1)
				throw new IllegalArgumentException("Invalid line: " + line);

			String key = tokens[i].substring(0, eqPos);
			int value = Integer.parseInt(tokens[i].substring(eqPos + 1));

			switch (key) {
				// @formatter:off
				case "id":			index = value; break;
				case "x":			x = value; break;
				case "y":			y = value; break;
				case "width":		width = value; break;
				case "height":		height = value; break;
				case "xoffset":		xoffset = value; break;
				case "yoffset":		yoffset = value; break;
				case "xadvance":	xadvance = value; break;
				// @formatter:on
			}
		}

		this.index = index;
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		this.xoffset = xoffset;
		this.yoffset = yoffset;
		this.xadvance = xadvance;

		u1 = x / imgWidth;
		u2 = (x + width) / imgWidth;

		v1 = 1 - (y / imgHeight);
		v2 = 1 - ((y + height) / imgHeight);
	}
}
