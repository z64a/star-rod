package app;

import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;

import app.Resource.ResourceType;

public class IconResource
{
	// @formatter:off
	public static final Icon ICON_PLAY  = loadIcon("play.png");
	public static final Icon ICON_PAUSE = loadIcon("pause.png");
	public static final Icon ICON_STOP  = loadIcon("stop.png");
	public static final Icon ICON_NEXT  = loadIcon("next.png");
	public static final Icon ICON_PREV  = loadIcon("prev.png");
	public static final Icon ICON_START = loadIcon("skip_start.png");
	public static final Icon ICON_END   = loadIcon("skip_end.png");

	public static final Icon ICON_UP    = loadIcon("arrow_up.png");
	public static final Icon ICON_DOWN  = loadIcon("arrow_down.png");

	public static final Icon CHECK_16   = loadIcon("check_mark_16.png");
	public static final Icon CHECK_24   = loadIcon("check_mark_24.png");
	public static final Icon CHECK_48   = loadIcon("check_mark_48.png");

	public static final Icon CROSS_16   = loadIcon("cross_mark_16.png");
	public static final Icon CROSS_24   = loadIcon("cross_mark_24.png");
	public static final Icon CROSS_48   = loadIcon("cross_mark_48.png");
	// @formatter:on

	private static ImageIcon loadIcon(String resourceName)
	{
		InputStream is = Resource.getStream(ResourceType.Icon, resourceName);
		if (is == null) {
			System.err.println("Unable to find resource " + resourceName);
			return null;
		}

		try {
			return new ImageIcon(ImageIO.read(is));
		}
		catch (IOException e) {
			System.err.println("Exception while reading shader " + resourceName);
			return null;
		}
	}
}
