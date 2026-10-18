package game.audio;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import app.Environment;
import app.input.InputFileException;

/**
 * The songs of a project. In papermario-dx, every BGM file in the songs directory is a song, named after its file;
 * other projects list their songs in songs.xml.
 */
public abstract class Songs
{
	public static final String DIR = "songs";
	public static final String EXTENSION = ".bgm";

	static final int MAX_ID = 0xFF;
	private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_]*");

	/**
	 * auxBanks holds the BK file each aux bank loads, or null for a bank the song leaves empty. The source is the
	 * table that lists the song, or its BGM file in papermario-dx.
	 */
	public record Song(String name, int id, String bgm, String[] auxBanks, File source)
	{}

	/** The songs indexed by ID. Unused IDs below the highest one repeat the lowest song. */
	public static List<Song> read(AudioLayers layers)
	{
		return Environment.isDX() ? readFiles(layers) : AudioTables.readSongs(layers);
	}

	/** Whether any layer has songs. */
	public static boolean exist(AudioLayers layers)
	{
		return Environment.isDX() ? !layers.listFiles(DIR, EXTENSION).isEmpty() : !layers.getTables("songs.xml").isEmpty();
	}

	/** papermario-dx's songs in order of ID, which is the order of their names. */
	private static List<Song> readFiles(AudioLayers layers)
	{
		List<Song> songs = new ArrayList<>();
		for (String bgm : layers.listFiles(DIR, EXTENSION)) {
			File source = layers.getFile(DIR + "/" + bgm);
			String name = bgm.substring(0, bgm.length() - EXTENSION.length());
			if (!NAME.matcher(name).matches())
				throw new InputFileException(source,
					"a song's file name must start with a lowercase letter and contain only lowercase letters, digits, and underscores");
			if (songs.size() > MAX_ID)
				throw new InputFileException(source, "there are more than %d songs, but song IDs go up to %X", MAX_ID + 1,
					MAX_ID);

			byte[] data;
			try {
				data = Files.readAllBytes(source.toPath());
			}
			catch (IOException e) {
				throw new InputFileException(source, e.getMessage());
			}
			songs.add(new Song(name, songs.size(), bgm, BgmMetadata.readAuxBanks(source, data), source));
		}

		if (songs.isEmpty())
			throw new InputFileException(new File(DIR), "there are no songs in any layer");
		return songs;
	}
}
