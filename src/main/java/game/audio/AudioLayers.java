package game.audio;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import app.Environment;
import app.input.InputFileException;
import assets.AssetSubdir;
import assets.Sidecars;

/**
 * A merged view of the audio directories in the asset stack. Tables merge by entry (see AudioTables),
 * every other file comes from the highest layer that has it, and `.meta` sidecars with `delete: true`
 * remove files or directories from all layers.
 */
public class AudioLayers
{
	public static final List<String> TABLES = List.of(
		"songs.xml", "banks.xml", "ambient_sounds.xml", "sound_effects.xml", "presets.xml", "drums.xml",
		"sfx/envelopes.xml");

	/** Names that tables and bank sources went by before they were snake case, with their current paths. */
	private static final Map<String, String> RENAMED = Map.of(
		"Songs.xml", "songs.xml", "Banks.xml", "banks.xml", "AmbientSounds.xml", "ambient_sounds.xml",
		"SoundEffects.xml", "sound_effects.xml", "Presets.xml", "presets.xml", "Drums.xml", "drums.xml",
		"sfx/Envelopes.xml", "sfx/envelopes.xml");

	/** Asset stack roots, highest priority first. */
	private final List<File> layers;
	private final Set<File> inputs = new TreeSet<>();
	private final Sidecars sidecars;

	public AudioLayers(List<File> layers)
	{
		this.layers = layers;
		sidecars = new Sidecars(layers);
	}

	public static AudioLayers fromEnvironment()
	{
		return new AudioLayers(Environment.assetDirectories);
	}

	/** Every file that was read so far, including sidecars. */
	public Set<File> getInputs()
	{
		Set<File> all = new TreeSet<>(inputs);
		all.addAll(sidecars.getRead());
		return all;
	}

	/** Errors if a sidecar deletes a table file, or a layer has a file under a name from before the names were snake case. */
	public void checkTables()
	{
		for (File layer : layers) {
			File audio = AssetSubdir.AUDIO.get(layer);
			RENAMED.forEach((oldPath, newPath) -> checkRenamed(audio, oldPath, newPath));
			String[] banks = new File(audio, "bank").list();
			if (banks != null) {
				for (String bank : banks)
					checkRenamed(audio, "bank/" + bank + "/SoundBank.xml", "bank/" + bank + "/sound_bank.xml");
			}
		}
		for (String table : TABLES)
			getTables(table);
	}

	private static void checkRenamed(File audio, String oldPath, String newPath)
	{
		File file = new File(audio, oldPath);
		String[] siblings = file.getParentFile().list();
		if (siblings != null && Arrays.asList(siblings).contains(file.getName()))
			throw new InputFileException(file, "this file must be named %s", newPath);
	}

	/** Copies of a table in every layer that has one, lowest priority first. */
	public List<File> getTables(String name)
	{
		List<File> found = new ArrayList<>();
		for (int i = layers.size() - 1; i >= 0; i--) {
			File file = new File(AssetSubdir.AUDIO.get(layers.get(i)), name);
			if (file.isFile())
				found.add(file);
		}

		if (!found.isEmpty() && isDeleted(name))
			throw new InputFileException(found.get(found.size() - 1),
				"tables merge by entry; delete entries with delete=\"true\"");

		inputs.addAll(found);
		return found;
	}

	/** The highest layer's copy of a file, or null if no layer has it or a sidecar deletes it. */
	public File getFile(String path)
	{
		if (isDeleted(path))
			return null;

		for (File layer : layers) {
			File file = new File(AssetSubdir.AUDIO.get(layer), path);
			if (file.isFile()) {
				inputs.add(file);
				return file;
			}
		}
		return null;
	}

	/** The highest layer's copy of a directory, or null if no layer has it or a sidecar deletes it. */
	public File getDirectory(String path)
	{
		if (isDeleted(path))
			return null;

		for (File layer : layers) {
			File dir = new File(AssetSubdir.AUDIO.get(layer), path);
			if (dir.isDirectory())
				return dir;
		}
		return null;
	}

	/** A directory that shares its layer with a file of the same item, or null. */
	public File findConflict(String dirPath, String filePath)
	{
		if (isDeleted(dirPath) || isDeleted(filePath))
			return null;

		for (File layer : layers) {
			File dir = new File(AssetSubdir.AUDIO.get(layer), dirPath);
			if (dir.isDirectory() && new File(AssetSubdir.AUDIO.get(layer), filePath).isFile())
				return dir;
		}
		return null;
	}

	/** Sorted names of the subdirectories of a directory, across all layers. */
	public List<String> listDirectories(String dir)
	{
		Set<String> names = new TreeSet<>();
		for (File layer : layers) {
			File[] dirs = new File(AssetSubdir.AUDIO.get(layer), dir).listFiles(File::isDirectory);
			if (dirs != null) {
				for (File d : dirs)
					names.add(d.getName());
			}
		}

		names.removeIf(name -> isDeleted(dir + "/" + name));
		return new ArrayList<>(names);
	}

	/** The position of the layer that holds a file, where 0 is the highest priority. */
	public int getPriority(File file)
	{
		for (int i = 0; i < layers.size(); i++) {
			if (file.toPath().startsWith(AssetSubdir.AUDIO.get(layers.get(i)).toPath()))
				return i;
		}
		throw new IllegalArgumentException(file + " is not in an asset layer");
	}

	/** Sorted names of the files with an extension in a directory, across all layers. */
	public List<String> listFiles(String dir, String extension)
	{
		Set<String> names = new TreeSet<>();
		for (File layer : layers) {
			File[] files = new File(AssetSubdir.AUDIO.get(layer), dir)
				.listFiles(f -> f.isFile() && f.getName().endsWith(extension));
			if (files != null) {
				for (File file : files)
					names.add(file.getName());
			}
		}

		names.removeIf(name -> isDeleted(dir + "/" + name));
		return new ArrayList<>(names);
	}

	private boolean isDeleted(String path)
	{
		return sidecars.isDeleted("audio/" + path);
	}
}
