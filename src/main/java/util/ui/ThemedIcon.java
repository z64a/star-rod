package util.ui;

import java.io.IOException;
import java.io.InputStream;

import javax.swing.UIManager;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import app.Resource;
import app.Resource.ResourceType;
import util.Logger;

public abstract class ThemedIcon
{
	private static FlatSVGIcon getIcon(String name)
	{
		try (InputStream is = Resource.getStream(ResourceType.Icon, name + ".svg")) {
			FlatSVGIcon icon = new FlatSVGIcon(is);
			icon.setColorFilter(new FlatSVGIcon.ColorFilter(color -> UIManager.getColor("Label.foreground")));
			return icon;
		}
		catch (IOException e) {
			Logger.logError(e.getMessage());
			return null;
		}
	}

	public static final FlatSVGIcon VISIBILITY_OFF_24 = getIcon("visibility_off_24");
	public static final FlatSVGIcon VISIBILITY_ON_24 = getIcon("visibility_on_24");

	public static final FlatSVGIcon VISIBILITY_OFF_16 = VISIBILITY_OFF_24.derive(16, 16);
	public static final FlatSVGIcon VISIBILITY_ON_16 = VISIBILITY_ON_24.derive(16, 16);

	public static final FlatSVGIcon NEXT_24 = getIcon("next_24");
	public static final FlatSVGIcon PREV_24 = getIcon("prev_24");
	public static final FlatSVGIcon STOP_24 = getIcon("stop_24");
	public static final FlatSVGIcon PLAY_24 = getIcon("play_24");
	public static final FlatSVGIcon PAUSE_24 = getIcon("pause_24");
	public static final FlatSVGIcon REWIND_24 = getIcon("rewind_24");
	public static final FlatSVGIcon FFWD_24 = getIcon("fast_forward_24");

	public static final FlatSVGIcon NEXT_16 = NEXT_24.derive(16, 16);
	public static final FlatSVGIcon PREV_16 = PREV_24.derive(16, 16);
	public static final FlatSVGIcon STOP_16 = STOP_24.derive(16, 16);
	public static final FlatSVGIcon PLAY_16 = PLAY_24.derive(16, 16);
	public static final FlatSVGIcon PAUSE_16 = PAUSE_24.derive(16, 16);
	public static final FlatSVGIcon REWIND_16 = REWIND_24.derive(16, 16);
	public static final FlatSVGIcon FFWD_16 = FFWD_24.derive(16, 16);

	public static final FlatSVGIcon ADD_24 = getIcon("add_24");
	public static final FlatSVGIcon ADD_16 = ADD_24.derive(16, 16);
}
