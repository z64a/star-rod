package game.audio.bgm;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.StarRodException;

public class Composition
{
	private static final int COMP_CMD_END = 0;
	private static final int COMP_CMD_PLAY = 1;
	private static final int COMP_CMD_WAIT = 4;
	private static final int COMP_CMD_START_LOOP = 3; // support up to 32 loop start points, up to a nested depth of 4
	private static final int COMP_CMD_END_LOOP = 5; // count of zero means 'forever'
	private static final int COMP_CMD_COND_LOOP_FALSE = 6;
	private static final int COMP_CMD_COND_LOOP_TRUE = 7;

	private final Song song;
	protected ArrayList<CompCommand> commands = new ArrayList<>();

	public int index; // which composition (0-3) this corresponds to

	public List<CompCommand> getCommands()
	{
		return Collections.unmodifiableList(commands);
	}

	protected Composition(Song song, ByteBuffer bb, int index, int filePos)
	{
		this.song = song;
		this.index = index;

		int v;
		bb.position(filePos);

		int startPos = bb.position();
		while ((v = bb.getInt()) != COMP_CMD_END) {
			int type = v >> 28;
			int data = v & 0xFFFFFFF;
			int offset;
			int loopIndex;
			int loopCount;

			switch (type) {
				case COMP_CMD_PLAY:
					offset = data << 2;
					commands.add(new PlayCompCommand(this, bb, startPos + offset));
					break;

				case COMP_CMD_START_LOOP:
					loopIndex = data & 0x1F; // can have up to 32 loop start labels per composition
					commands.add(new StartLoopCompCommand(this, loopIndex));
					break;
				case COMP_CMD_END_LOOP:
					loopIndex = data & 0x1F; // 01F (bits 0-4)
					loopCount = (data >> 5) & 0x7F; // FE0 (bits 5-11)
					commands.add(new EndLoopCompCommand(this, loopIndex, loopCount));
					break;

				// unused commands
				case COMP_CMD_WAIT:
				case COMP_CMD_COND_LOOP_FALSE:
				case COMP_CMD_COND_LOOP_TRUE:
				default:
					throw new StarRodException("Unknown composition command: %08X", v);
			}
		}

		if (Track.debugPrint)
			System.out.println();
	}

	public static abstract class CompCommand
	{
		public final Composition comp;

		public CompCommand(Composition comp)
		{
			this.comp = comp;
		}

	}

	public static class PlayCompCommand extends CompCommand
	{
		private Phrase phrase;

		public PlayCompCommand(Composition comp, ByteBuffer bb, int phraseOffset)
		{
			super(comp);

			int nextPos = bb.position();
			phrase = comp.song.addPhrase(bb, phraseOffset);
			bb.position(nextPos);
		}

		public Phrase getPhrase()
		{
			return phrase;
		}

	}

	public static class StartLoopCompCommand extends CompCommand
	{
		private int loopIndex;

		public StartLoopCompCommand(Composition comp, int loopIndex)
		{
			super(comp);
			this.loopIndex = loopIndex;
		}

		public int getLoopIndex()
		{
			return loopIndex;
		}

	}

	public static class EndLoopCompCommand extends CompCommand
	{
		private int loopIndex;
		private int loopCount;

		public EndLoopCompCommand(Composition comp, int loopIndex, int loopCount)
		{
			super(comp);
			this.loopIndex = loopIndex;
			this.loopCount = loopCount;
		}

		public int getLoopIndex()
		{
			return loopIndex;
		}

		public int getLoopCount()
		{
			return loopCount;
		}

	}
}
