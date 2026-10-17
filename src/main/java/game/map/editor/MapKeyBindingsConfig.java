package game.map.editor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import common.KeyBinding;
import common.KeyInput;

import app.input.IOUtils;
import util.Logger;

/** Saves only overrides, so new controls retain their defaults across upgrades. */
public final class MapKeyBindingsConfig
{
	private MapKeyBindingsConfig()
	{}

	public static void load(File file, MapKeyConfig config)
	{
		config.resetAll();
		if (!file.exists())
			return;
		Map<KeyInput, KeyBinding> bindings = config.copyBindings();
		List<String> lines;
		try {
			lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			Logger.logWarning("Could not read Map Editor key bindings: " + e.getMessage());
			return;
		}
		for (String line : lines) {
			line = line.split("%", 2)[0].trim();
			if (line.isEmpty())
				continue;
			try {
				String[] parts = line.split("=", 2);
				if (parts.length != 2)
					throw new IllegalArgumentException();
				EditorShortcut input = EditorShortcut.valueOf(parts[0].trim());
				KeyBinding binding = KeyBinding.parse(parts[1].trim());
				if (!config.isUserBindable(input) || config.validateBinding(input, binding) != null)
					throw new IllegalArgumentException();
				bindings.put(input, binding);
			}
			catch (IllegalArgumentException e) {
				Logger.logWarning("Ignoring invalid Map Editor key binding: " + line);
			}
		}
		// Manually edited files can contain duplicates. Keep the first action in display order.
		Map<KeyBinding, KeyInput> used = new LinkedHashMap<>();
		for (KeyInput input : config.getUserBindableInputs()) {
			KeyBinding binding = bindings.get(input);
			if (binding.isBound() && used.putIfAbsent(binding, input) != null) {
				Logger.logWarning("Disabling duplicate Map Editor key binding for " + input);
				bindings.put(input, KeyBinding.NONE);
			}
		}
		config.setBindings(bindings);
	}

	public static void save(File file, Map<KeyInput, KeyBinding> bindings) throws IOException
	{
		MapKeyConfig validated = new MapKeyConfig();
		validated.setBindings(bindings);
		List<String> lines = new ArrayList<>();
		lines.add("% Map Editor key bindings. Only overrides from defaults are listed.");
		lines.add("% Use NONE to leave an action unbound.");
		for (KeyInput input : validated.getUserBindableInputs()) {
			KeyBinding binding = validated.getBinding(input);
			if (!binding.equals(validated.getDefaultBinding(input)))
				lines.add(input + " = " + binding.serialize());
		}
		IOUtils.atomicWriteLines(lines, file);
	}
}
