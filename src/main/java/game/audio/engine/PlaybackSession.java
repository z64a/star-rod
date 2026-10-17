package game.audio.engine;

public interface PlaybackSession extends AutoCloseable
{
	public default void attach()
	{}

	public boolean isPlaying();

	public boolean isPaused();

	public void setPaused(boolean paused);

	public void stop();

	public void restart();

	// Timeline positions and durations are measured in output samples.
	public int getTime();

	public int getDuration();

	public void seekTime(int time);

	public default int getTimelineLoopCount()
	{
		return 0;
	}

	@Override
	public default void close()
	{
		stop();
	}
}
