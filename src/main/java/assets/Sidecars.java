package assets;

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

import app.input.InputFileException;

/** Reads the `.meta` sidecars of the asset stack, where `delete: true` removes a file or directory from every layer. */
public class Sidecars
{
	public static final String SIDECAR = ".meta";

	/** Asset stack roots, highest priority first. */
	private final List<File> layers;
	private final Set<File> read = new TreeSet<>();
	private final Map<File, Optional<Boolean>> deletes = new HashMap<>();

	public Sidecars(List<File> layers)
	{
		this.layers = layers;
	}

	/** Every sidecar that was read so far. */
	public Set<File> getRead()
	{
		return read;
	}

	/**
	 * Whether a path, relative to the layer roots, is deleted. Applies the sidecars that could name the path in
	 * every layer, lowest priority first, so the nearest declaration wins. A directory is declared by the `.meta`
	 * inside it, a file by `<file>.meta`, and each directory's `.meta` also covers everything below it.
	 */
	public boolean isDeleted(String path)
	{
		boolean isDirectory = layers.stream().anyMatch(layer -> new File(layer, path).isDirectory());
		String[] parts = path.split("/");

		boolean deleted = false;
		for (int i = layers.size() - 1; i >= 0; i--) {
			List<File> declarations = new ArrayList<>();

			File dir = layers.get(i);
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
		return deletes.computeIfAbsent(sidecar, file -> {
			if (!file.isFile())
				return Optional.empty();

			read.add(file);
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
