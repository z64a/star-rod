package game.audio;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import app.Environment;
import app.input.InputFileException;
import assets.AssetSubdir;

/**
 * A merged view of the audio directories in the asset stack. Tables merge by entry (see AudioTables),
 * every other file comes from the highest layer that has it, and `.meta` sidecars with `delete: true`
 * remove files or directories from all layers.
 */
public class AudioLayers
{
	public static final List<String> TABLES = List.of(
		"Songs.xml", "Banks.xml", "AmbientSounds.xml", "SoundEffects.xml", "Presets.xml", "Drums.xml");

	private static final String SIDECAR = ".meta";

	/** Asset stack roots, highest priority first. */
	private final List<File> layers;
	private final Set<File> inputs = new TreeSet<>();
	private final Map<File, Optional<Boolean>> sidecars = new HashMap<>();

	public AudioLayers(List<File> layers)
	{
		this.layers = layers;
	}

	public static AudioLayers fromEnvironment()
	{
		return new AudioLayers(Environment.assetDirectories);
	}

	/** Every file that was read so far, including sidecars. */
	public Set<File> getInputs()
	{
		return inputs;
	}

	/** Errors if a sidecar deletes a table file. */
	public void checkTables()
	{
		for (String table : TABLES)
			getTables(table);
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

	/**
	 * Applies the sidecars that could name a path in every layer, lowest priority first, so the
	 * nearest declaration wins. A directory is declared by the `.meta` inside it, a file by
	 * `<file>.meta`. Sidecars above a layer's root are not consulted.
	 */
	private boolean isDeleted(String path)
	{
		boolean isDirectory = layers.stream()
			.anyMatch(layer -> new File(AssetSubdir.AUDIO.get(layer), path).isDirectory());
		String[] parts = path.split("/");

		boolean deleted = false;
		for (int i = layers.size() - 1; i >= 0; i--) {
			List<File> declarations = new ArrayList<>();
			declarations.add(new File(layers.get(i), SIDECAR));

			File dir = AssetSubdir.AUDIO.get(layers.get(i));
			for (int j = 0; j < parts.length - 1; j++) {
				declarations.add(new File(dir, SIDECAR));
				dir = new File(dir, parts[j]);
			}
			declarations.add(new File(dir, SIDECAR));

			String name = parts[parts.length - 1];
			declarations.add(isDirectory ? new File(new File(dir, name), SIDECAR) : new File(dir, name + SIDECAR));

			for (File declaration : declarations) {
				Optional<Boolean> delete = readDelete(declaration);
				if (delete.isPresent())
					deleted = delete.get();
			}
		}
		return deleted;
	}

	private Optional<Boolean> readDelete(File sidecar)
	{
		return sidecars.computeIfAbsent(sidecar, file -> {
			if (!file.isFile())
				return Optional.empty();

			inputs.add(file);
			try (Reader reader = new FileReader(file)) {
				Object values = new Yaml().load(reader);
				if (values instanceof Map<?, ?> map && map.containsKey("delete"))
					return Optional.of(Boolean.TRUE.equals(map.get("delete")));
				return Optional.empty();
			}
			catch (IOException | YAMLException e) {
				throw new InputFileException(file, e.getMessage());
			}
		});
	}
}
