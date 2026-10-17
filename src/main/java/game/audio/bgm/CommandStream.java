package game.audio.bgm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import game.audio.bgm.Track.TrackCommand;

/** Decoded track, detour, or proximity branch commands in playback order. */
public class CommandStream
{
	public static enum StreamType
	{
		TRACK,
		BRANCH,
		DETOUR
	}

	public final StreamType type;
	public boolean isDrum;
	public int duration;

	public ArrayList<TrackCommand> all = new ArrayList<>();

	public CommandStream(StreamType type)
	{
		this.type = type;
	}

	public List<TrackCommand> getCommands()
	{
		return Collections.unmodifiableList(all);
	}

	public boolean isDrum()
	{
		return isDrum;
	}

	public int getDuration()
	{
		return duration;
	}

}
