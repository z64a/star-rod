package game.audio.engine;

/**
 * Runs the press and release programs of an instrument envelope one audio frame at a time and reports the
 * resulting volume. Times are in microseconds.
 */
public class EnvelopePlayer
{
	/** The press and release programs of one envelope script: a list of two-byte commands ending in End. */
	public record Script(int[] press, int[] release)
	{
		public Script withPress(int[] press)
		{
			return new Script(press, release);
		}
	}

	private static final int CMD_END_LOOP = 0xFB;
	private static final int CMD_START_LOOP = 0xFC;
	private static final int CMD_ADD_SCALE = 0xFD;
	private static final int CMD_SET_SCALE = 0xFE;
	private static final int CMD_END = 0xFF;

	private static final int VOLUME_UNITY = 0x80;

	/** The duration of one audio frame sent to the RSP. */
	private static final int AUDIO_FRAME_USEC = 5750;

	/** The length of each duration index in units of AUDIO_FRAME_USEC. */
	private static final int[] FRAME_COUNTS = {
			10434, 9565, 8695, 7826, 6956, 6086, 5217, 4782,
			4347, 3913, 3478, 3304, 3130, 2956, 2782, 2608,
			2434, 2260, 2086, 1913, 1739, 1565, 1391, 1217,
			1043, 869, 782, 695, 608, 521, 478, 434,
			391, 347, 330, 313, 295, 278, 260, 243,
			226, 208, 191, 173, 165, 156, 147, 139,
			130, 121, 113, 104, 95, 86, 78, 69,
			65, 60, 56, 52, 50, 48, 46, 45,
			43, 41, 40, 38, 36, 34, 33, 31,
			29, 27, 26, 24, 22, 20, 19, 17,
			16, 14, 12, 11, 10, 9, 8, 7,
			6, 5, 4, 3, 2, 1, 0
	};

	private enum Phase
	{
		INIT, PRESS, SUSTAIN, RELEASE, DONE
	}

	private record Interval(int index, int volume)
	{}

	private Phase phase = Phase.INIT;

	private int[] cmdList;
	private int cmdPos;

	private int initial;
	private int target;

	private int timeLeft;
	private int duration;

	private float delta;

	private int scale = VOLUME_UNITY;
	private int relativeStart = VOLUME_UNITY;

	private boolean isRelativeRelease;

	private int loopStartPos = -1;
	private int loopCounter;

	public boolean isDone()
	{
		return phase == Phase.DONE;
	}

	public void press(Script env)
	{
		phase = Phase.PRESS;

		cmdList = env.press;
		cmdPos = 0;
		loopStartPos = -1;
		loopCounter = 0;

		scale = VOLUME_UNITY;

		Interval interval = step();
		if (interval == null) {
			phase = Phase.DONE;
			return;
		}

		initial = 0;
		target = interval.volume;

		duration = getTime(interval.index);
		timeLeft = duration;

		delta = (duration != 0) ? (float) (target - initial) / duration : 0.0f;

		relativeStart = VOLUME_UNITY;
		isRelativeRelease = false;
	}

	public void release(Script env)
	{
		phase = Phase.RELEASE;

		cmdList = env.release;
		cmdPos = 0;
		loopStartPos = -1;
		loopCounter = 0;

		if (cmdList.length < 2) {
			phase = Phase.DONE;
			return;
		}

		// if press hasn't completed before we release, compute the current value
		if (timeLeft > AUDIO_FRAME_USEC)
			initial = initial + Math.round(delta * (duration - timeLeft));
		else
			initial = target;

		int cmd = cmdList[cmdPos++];
		int arg = cmdList[cmdPos++];

		target = arg & 0x7F;

		duration = getTime(cmd);
		timeLeft = duration;

		if ((byte) arg < 0) {
			isRelativeRelease = true;
			relativeStart = initial;
		}
		else {
			relativeStart = VOLUME_UNITY;
		}

		delta = (duration != 0) ? (float) (target - initial) / duration : 0.0f;
	}

	public void update()
	{
		Interval interval;

		switch (phase) {
			case PRESS:
				timeLeft -= AUDIO_FRAME_USEC;
				if (timeLeft > 0)
					return;

				interval = step();
				if (interval == null) {
					phase = Phase.SUSTAIN;
					initial = target;
					timeLeft = -1;
					return;
				}
				break;

			case RELEASE:
				timeLeft -= AUDIO_FRAME_USEC;
				if (timeLeft > 0)
					return;

				interval = step();
				if (interval == null) {
					phase = Phase.DONE;
					return;
				}
				break;

			default:
				return;
		}

		initial = target;
		target = interval.volume & 0x7F;
		duration = getTime(interval.index);
		timeLeft = duration;

		delta = (duration != 0) ? (float) (target - initial) / duration : 0.0f;
	}

	private Interval step()
	{
		while (cmdPos + 1 < cmdList.length) {
			int cmd = cmdList[cmdPos++];
			int arg = cmdList[cmdPos++];

			if ((byte) cmd >= 0)
				return new Interval(cmd, arg);

			switch (cmd & 0xFF) {
				case CMD_SET_SCALE:
					scale = Math.min(arg, VOLUME_UNITY);
					break;
				case CMD_ADD_SCALE:
					scale = Math.max(0, Math.min(VOLUME_UNITY, scale + (byte) arg));
					break;
				case CMD_START_LOOP:
					loopCounter = arg;
					loopStartPos = cmdPos;
					break;
				case CMD_END_LOOP:
					if (loopCounter == 0 || --loopCounter > 0)
						cmdPos = loopStartPos;
					break;
				case CMD_END:
					return null;
				default:
					break;
			}
		}

		// the program ended without an End command
		return null;
	}

	public float getEnvelopeVolume()
	{
		int current;
		if (timeLeft > 0)
			current = initial + Math.round(delta * (duration - timeLeft));
		else
			current = target;

		float volume = (float) current / VOLUME_UNITY;
		volume *= (float) scale / VOLUME_UNITY;

		if (isRelativeRelease)
			volume *= (float) relativeStart / VOLUME_UNITY;

		return volume;
	}

	private static int getTime(int index)
	{
		if (index >= 0 && index < FRAME_COUNTS.length)
			return FRAME_COUNTS[index] * AUDIO_FRAME_USEC;
		return 0;
	}
}
