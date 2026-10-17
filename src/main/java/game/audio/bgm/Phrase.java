package game.audio.bgm;

import java.nio.ByteBuffer;
import java.util.HashMap;

public class Phrase
{
	private static final int NUM_TRACKS = 16;

	public final Song song;
	public Track[] tracks = new Track[NUM_TRACKS];

	public transient int filePos; // file offset where phrase begins

	public Phrase(Song song)
	{
		this.song = song;
	}

	public Phrase(Song song, ByteBuffer bb, int pos)
	{
		this(song);

		this.filePos = pos;

		int[] trackInfo = new int[NUM_TRACKS];
		int[] firstSeen = new int[NUM_TRACKS];
		HashMap<Integer, Integer> seenTracks = new HashMap<>(2 * NUM_TRACKS);

		bb.position(pos);
		for (int i = 0; i < NUM_TRACKS; i++) {
			int v = bb.getInt();
			trackInfo[i] = v;

			if (seenTracks.containsKey(v)) {
				firstSeen[i] = seenTracks.get(v);
			}
			else {
				firstSeen[i] = i;
				seenTracks.put(v, i);
			}
		}

		for (int i = 0; i < NUM_TRACKS; i++) {
			tracks[i] = new Track(this, i, bb, trackInfo[i]);

			if (trackInfo[i] != 0 && firstSeen[i] != i)
				tracks[i].copyOf = firstSeen[i];
		}

	}

}
