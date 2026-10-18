package game.message.editor;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import app.Environment;
import app.input.FileSource;
import app.input.IOUtils;
import app.input.InputFileException;
import app.input.Line;
import assets.AssetHandle;
import assets.AssetManager;
import game.message.Message;
import game.message.StringEncoder;
import game.message.editor.MessageAsset.MessageState;
import game.message.editor.MessageAsset.SaveSnapshot;

/** Resolves individual definitions across layers and coordinates writes to shared containers. */
public final class MessageLibrary
{
	private final List<File> layers;
	private final Map<Path, MessageFile> files = new LinkedHashMap<>();
	private final Map<Message, Location> locations = new IdentityHashMap<>();
	private final List<MessageAsset> assets = new ArrayList<>();
	private final Set<Path> discoveredPaths;
	private final Map<Message, AssetHandle> destinations = new IdentityHashMap<>();
	private final Set<Message> removed = Collections.newSetFromMap(new IdentityHashMap<>());
	private Map<String, List<Definition>> definitions;
	private final Set<Location> hiddenSources = new LinkedHashSet<>();

	public static MessageLibrary load() throws IOException
	{
		try {
			MessageLibrary library = new MessageLibrary(Environment.assetDirectories);
			library.buildAssets();
			return library;
		}
		catch (InputFileException e) {
			throw new IOException(e.getSourceFile() + ": " + e.getMessage(), e);
		}
	}

	private MessageLibrary(List<File> layers) throws IOException
	{
		if (layers == null || layers.isEmpty())
			throw new IOException("No message asset layers are configured.");
		this.layers = List.copyOf(layers);
		List<AssetHandle> handles = discover();
		discoveredPaths = new LinkedHashSet<>();
		for (AssetHandle handle : handles) {
			Path path = path(handle);
			discoveredPaths.add(path);
			files.put(path, MessageFile.read(handle));
		}
		validate(files.values());
		indexDefinitions();
	}

	/** Compatibility for callers opening a single physical file rather than the complete library. */
	static MessageLibrary forAsset(MessageAsset asset) throws IOException
	{
		MessageLibrary library = new MessageLibrary(Environment.assetDirectories == null
			? List.of(asset.asset.assetDir)
			: Environment.assetDirectories);
		Path sourcePath = path(asset.asset);
		if (!library.files.containsKey(sourcePath))
			library.files.put(sourcePath, MessageFile.read(asset.asset));
		validate(library.files.values());
		library.indexDefinitions();
		synchronized (asset) {
			for (Message message : asset.messages) {
				String savedKey = asset.savedKey(message);
				if (savedKey != null)
					library.locations.put(message, new Location(sourcePath, savedKey));
			}
		}
		library.assets.add(asset);
		return library;
	}

	public synchronized List<MessageAsset> getAssets()
	{
		return List.copyOf(assets);
	}

	private void indexDefinitions()
	{
		definitions = new LinkedHashMap<>();
		for (File layer : layers) {
			for (MessageFile file : files.values()) {
				if (!path(file.asset.assetDir).equals(path(layer)))
					continue;
				for (Message message : file.messages)
					definitions.computeIfAbsent(key(message), ignored -> new ArrayList<>()).add(new Definition(file, message));
			}
		}
	}

	/** A definition in the stack; the effective row may contain an unsaved mod draft. */
	public record MessageSource(AssetHandle asset, Message message, int layer, boolean draft)
	{
		public String filename()
		{
			return asset.assetPath.replaceFirst("^msg/", "");
		}

		public String layerName()
		{
			return MessageLibrary.layerName(asset.assetDir);
		}
	}

	private static String layerName(File layer)
	{
		File project = Environment.getProjectDirectory();
		Path directory = path(layer);
		if (project != null && directory.startsWith(path(project))) {
			String relative = path(project).relativize(directory).toString().replace('\\', '/');
			return relative.isEmpty() ? "." : relative;
		}
		return project == null ? layer.getName() : directory.toString().replace('\\', '/');
	}

	public String getWritableLayerName()
	{
		return layerName(layers.get(0));
	}

	/** Resolve again after structural edits so renaming/removing an override immediately reveals its fallback. */
	public synchronized List<Message> getMessages()
	{
		updateHiddenSources();
		Map<Location, Message> live = new HashMap<>();
		locations.forEach((message, location) -> {
			if (!removed.contains(message) && key(message).equals(location.key))
				live.put(location, message);
		});
		Map<String, Message> effective = new LinkedHashMap<>();
		for (var entry : definitions.entrySet()) {
			for (Definition definition : entry.getValue()) {
				Location location = new Location(path(definition.file.asset), entry.getKey());
				if (hiddenSources.contains(location))
					continue;
				Message message = live.get(location);
				if (message == null) {
					Message original = definition.message;
					MessageAsset group = group(definition.file.asset);
					message = new Message(group, original.unit, original.section, original.index, original.name);
					group.addLoadedMessage(message);
					locations.put(message, location);
				}
				effective.put(entry.getKey(), message);
				break;
			}
		}
		for (MessageAsset asset : assets) {
			synchronized (asset) {
				for (Message message : asset.messages) {
					if (message.modified && !removed.contains(message))
						effective.put(key(message), message);
				}
			}
		}
		return List.copyOf(effective.values());
	}

	private void updateHiddenSources()
	{
		hiddenSources.clear();
		locations.forEach((message, location) -> {
			if (files.get(location.path).asset.assetDir.equals(layers.get(0))
				&& (removed.contains(message) || !key(message).equals(location.key)))
				hiddenSources.add(location);
		});
	}

	private MessageAsset group(AssetHandle handle)
	{
		for (MessageAsset asset : assets) {
			if (asset.asset.assetPath.equals(handle.assetPath))
				return asset;
		}
		MessageAsset asset = new MessageAsset(handle, this);
		assets.add(asset);
		return asset;
	}

	public synchronized List<MessageSource> getSources(Message message)
	{
		List<MessageSource> sources = new ArrayList<>();
		if (message.modified && !removed.contains(message))
			sources.add(new MessageSource(getDestination(message), message, 0, true));
		for (Definition definition : definitions.getOrDefault(key(message), List.of())) {
			int layer = layers.indexOf(definition.file.asset.assetDir);
			if (layer == 0 && message.modified)
				continue;
			Location location = new Location(path(definition.file.asset), key(message));
			if (!hiddenSources.contains(location))
				sources.add(new MessageSource(definition.file.asset, sources.isEmpty() ? message : definition.message, layer, false));
		}
		return List.copyOf(sources);
	}

	public synchronized List<String> getFilenames()
	{
		Set<String> names = new java.util.TreeSet<>();
		for (MessageFile file : files.values())
			names.add(file.asset.assetPath.replaceFirst("^msg/", ""));
		for (AssetHandle destination : destinations.values())
			names.add(destination.assetPath.replaceFirst("^msg/", ""));
		return List.copyOf(names);
	}

	public synchronized boolean matchesFiles(Message message, Set<String> filenames)
	{
		if (filenames.isEmpty())
			return true;
		return getSources(message).stream().anyMatch(source -> filenames.contains(source.filename()));
	}

	public synchronized boolean hasModified()
	{
		return assets.stream().anyMatch(asset -> asset.hasModified);
	}

	public synchronized boolean isModMessage(Message message)
	{
		if (message == null || removed.contains(message))
			return false;
		Location location = locations.get(message);
		return message.modified || location == null || files.get(location.path).asset.assetDir.equals(layers.get(0));
	}

	public synchronized boolean hasInheritedDefinition(Message message)
	{
		return definitions.getOrDefault(key(message), List.of()).stream()
			.anyMatch(definition -> !definition.file.asset.assetDir.equals(layers.get(0)));
	}

	public synchronized AssetHandle getDestination(Message message)
	{
		if (destinations.containsKey(message))
			return destinations.get(message);
		Location location = locations.get(message);
		return new AssetHandle(layers.get(0), location == null ? message.source.asset.assetPath : files.get(location.path).asset.assetPath);
	}

	public synchronized void validateName(String name, Message except)
	{
		if (name == null || !name.matches("[A-Za-z_][A-Za-z0-9_]*"))
			throw new IllegalArgumentException("Use a name beginning with a letter or underscore, followed by letters, digits or underscores.");
		if (getMessages().stream().anyMatch(message -> message != except && message.name.equals(name)))
			throw new IllegalArgumentException("A message named " + name + " already exists.");
	}

	private AssetHandle destination(String filename)
	{
		String relative = filename.trim().replace('\\', '/');
		if (!relative.endsWith(".msg"))
			relative += ".msg";
		if (relative.equals(".msg") || relative.startsWith("/") || relative.contains(":")
			|| Arrays.stream(relative.split("/", -1)).anyMatch(part -> part.isBlank() || part.equals(".") || part.equals("..")))
			throw new IllegalArgumentException("Choose a relative .msg filename, optionally inside a subfolder.");
		return new AssetHandle(layers.get(0), "msg/" + relative);
	}

	public synchronized Message create(String name, String filename, Message original)
	{
		validateName(name, null);
		AssetHandle target = destination(filename);
		MessageAsset owner = group(target);
		Message message = new Message(owner);
		message.name = name;
		if (original != null) {
			message.section = original.section;
			message.setMarkup(original.getMarkup());
		}
		owner.addMessage(message);
		destinations.put(message, target);
		return message;
	}

	private void requireMod(Message message)
	{
		if (!isModMessage(message))
			throw new IllegalArgumentException("This action requires a definition in the mod layer.");
	}

	public synchronized void rename(Message message, String name)
	{
		requireMod(message);
		validateName(name, message);
		message.setName(name);
		updateHiddenSources();
	}

	public synchronized void move(Message message, String filename)
	{
		requireMod(message);
		AssetHandle target = destination(filename);
		if (!target.equals(getDestination(message))) {
			Location location = locations.get(message);
			AssetHandle original = new AssetHandle(layers.get(0),
				location == null ? message.source.asset.assetPath : files.get(location.path).asset.assetPath);
			boolean moved = !target.equals(original);
			if (moved)
				destinations.put(message, target);
			else
				destinations.remove(message);
			message.source.setStructuralModified(message, moved);
		}
	}

	public synchronized void remove(Message message)
	{
		requireMod(message);
		Location location = locations.get(message);
		if (location == null || !files.get(location.path).asset.assetDir.equals(layers.get(0))) {
			discard(message);
			return;
		}
		removed.add(message);
		message.source.setStructuralModified(message, true);
		updateHiddenSources();
	}

	public synchronized void discard(Message message)
	{
		Location location = locations.get(message);
		if (location != null && !key(message).equals(location.key)
			&& files.get(location.path).asset.assetDir.equals(layers.get(0))) {
			boolean conflict = getMessages().stream().anyMatch(other -> other != message && key(other).equals(location.key) && isModMessage(other));
			if (conflict)
				throw new IllegalArgumentException("Another mod draft now uses the original name. Rename or discard that draft before restoring this message.");
		}
		destinations.remove(message);
		removed.remove(message);
		if (location == null) {
			message.source.forgetMessage(message);
			return;
		}
		Message original = files.get(location.path).messages.stream().filter(item -> key(item).equals(location.key)).findFirst().orElseThrow();
		message.name = original.name;
		message.section = original.section;
		message.index = original.index;
		message.setMarkup(original.getMarkup());
		message.editorShouldSync = true;
		message.source.clearModified(message);
		updateHiddenSources();
	}

	static String key(Message message)
	{
		return message.name.isEmpty()
			? String.format("id:%04X:%04X", message.section, message.index)
			: "name:" + message.name;
	}

	private static Path path(File file)
	{
		return file.toPath().toAbsolutePath().normalize();
	}

	private List<AssetHandle> discover() throws IOException
	{
		List<AssetHandle> handles = new ArrayList<>();
		for (File layer : layers)
			handles.addAll(AssetManager.getMessageFiles(layer));
		return handles;
	}

	private void buildAssets()
	{
		Map<String, Definition> effective = new LinkedHashMap<>();
		Map<String, List<String>> orders = new TreeMap<>();
		Map<String, AssetHandle> containers = new HashMap<>();
		for (MessageFile file : files.values()) {
			String relative = file.asset.assetPath;
			containers.putIfAbsent(relative, file.asset);
			List<String> order = orders.computeIfAbsent(relative, ignored -> new ArrayList<>());
			List<String> sourceOrder = file.order();
			for (Message message : file.messages) {
				String key = key(message);
				effective.putIfAbsent(key, new Definition(file, message));
				if (!order.contains(key))
					order.add(insertionIndex(order, sourceOrder, key), key);
			}
		}
		for (var entry : orders.entrySet()) {
			MessageAsset group = new MessageAsset(containers.get(entry.getKey()), this);
			for (String key : entry.getValue()) {
				Definition definition = effective.get(key);
				if (!definition.file.asset.assetPath.equals(entry.getKey()))
					continue;
				Message original = definition.message;
				Message message = new Message(group, original.unit, original.section, original.index, original.name);
				group.addLoadedMessage(message);
				locations.put(message, new Location(path(definition.file.asset), key));
			}
			group.updateIndices();
			// Keep empty containers available for creating messages, but hide wholly overridden files.
			if (!group.messages.isEmpty() || entry.getValue().isEmpty())
				assets.add(group);
		}
	}

	private static Map<Path, Map<String, Definition>> validate(Collection<MessageFile> files) throws IOException
	{
		Map<Path, Map<String, Definition>> byLayer = new HashMap<>();
		for (MessageFile file : files) {
			Map<String, Definition> definitions = byLayer.computeIfAbsent(path(file.asset.assetDir), ignored -> new LinkedHashMap<>());
			for (Message message : file.messages) {
				String key = key(message);
				Definition previous = definitions.putIfAbsent(key, new Definition(file, message));
				if (previous != null)
					throw new IOException(String.format("Duplicate message %s in layer %s:%n%s:%d%n%s:%d",
						message.name.isEmpty() ? message.getIDName() : message.name, file.asset.assetDir,
						previous.file.asset, previous.message.startLineNum, file.asset, message.startLineNum));
			}
		}
		return byLayer;
	}

	private void verifyUnchanged() throws IOException
	{
		Set<Path> current = new LinkedHashSet<>();
		for (AssetHandle handle : discover())
			current.add(path(handle));
		if (!current.equals(discoveredPaths))
			throw new IOException("Message files were added or removed outside Star Rod. Reload resources before saving.");
		for (MessageFile file : files.values()) {
			if (!file.asset.isFile() || !Arrays.equals(file.bytes, Files.readAllBytes(file.asset.toPath())))
				throw new IOException("Message file changed outside Star Rod: " + file.asset + ". Reload resources before saving.");
		}
	}

	/** One lock for all groups: edits from different groups can target the same mod file. */
	synchronized boolean save(MessageAsset owner, long expectedRevision, Runnable snapshotReadyCallback) throws IOException
	{
		SaveSnapshot snapshot = owner.snapshotIfCurrent(expectedRevision);
		if (snapshot == null)
			return false;
		snapshotReadyCallback.run();
		return saveSnapshots(Map.of(owner, snapshot));
	}

	/** Structural edits can span groups and must be validated and committed together. */
	public synchronized boolean saveAll() throws IOException
	{
		Map<MessageAsset, SaveSnapshot> snapshots = new LinkedHashMap<>();
		for (MessageAsset asset : assets) {
			synchronized (asset) {
				snapshots.put(asset, asset.snapshotIfCurrent(asset.getModificationRevision()));
			}
		}
		return saveSnapshots(snapshots);
	}

	private FilePlan plan(Map<Path, FilePlan> plans, AssetHandle destination) throws IOException
	{
		Path target = path(destination);
		FilePlan plan = plans.get(target);
		if (plan == null) {
			MessageFile current = files.get(target);
			plan = new FilePlan(current == null ? MessageFile.empty(destination) : current);
			plans.put(target, plan);
		}
		return plan;
	}

	private boolean saveSnapshots(Map<MessageAsset, SaveSnapshot> snapshots) throws IOException
	{
		if (snapshots.values().stream().allMatch(snapshot -> snapshot.messages.isEmpty()))
			return true;
		verifyUnchanged();

		Path top = path(layers.get(0));
		Map<String, Definition> topDefinitions = validate(files.values()).getOrDefault(top, Map.of());
		Map<Path, FilePlan> plans = new LinkedHashMap<>();
		Map<MessageState, Path> savedLocations = new LinkedHashMap<>();
		Map<Location, Message> editedDefinitions = new HashMap<>();
		for (SaveSnapshot snapshot : snapshots.values()) {
			for (MessageState state : snapshot.messages) {
				Location source = locations.get(state.owner);
				if (source != null && files.get(source.path).asset.assetDir.equals(layers.get(0)))
					editedDefinitions.put(source, state.owner);
			}
		}
		// Remove old definitions first, including the source side of moves.
		for (SaveSnapshot snapshot : snapshots.values()) {
			for (MessageState state : snapshot.messages) {
				Location source = locations.get(state.owner);
				Definition existing = source == null ? null : topDefinitions.get(source.key);
				AssetHandle target = destinations.get(state.owner);
				if (existing != null && (removed.contains(state.owner) || (target != null && !target.equals(existing.file.asset))))
					plan(plans, existing.file.asset).remove(source.key);
			}
		}
		for (var entry : snapshots.entrySet()) {
			SaveSnapshot snapshot = entry.getValue();
			for (MessageState state : snapshot.messages) {
				if (removed.contains(state.owner))
					continue;
				Location source = locations.get(state.owner);
				String sourceKey = source == null ? state.key : source.key;
				Definition existing = source == null ? null : topDefinitions.get(sourceKey);
				if (existing != null) {
					Message other = editedDefinitions.get(new Location(path(existing.file.asset), sourceKey));
					if (other != null && other != state.owner)
						existing = null;
				}
				MessageFile sourceFile = source == null ? null : files.get(source.path);
				AssetHandle destination = destinations.getOrDefault(state.owner, existing != null ? existing.file.asset
					: new AssetHandle(layers.get(0), sourceFile == null ? entry.getKey().asset.assetPath : sourceFile.asset.assetPath));
				boolean inPlace = existing != null && existing.file.asset.equals(destination);
				plan(plans, destination).apply(state, inPlace ? sourceKey : null, sourceKey,
					sourceFile == null ? snapshot.order : sourceFile.order());
				savedLocations.put(state, path(destination));
			}
		}

		// Parse and validate every resulting file before writing any of them.
		Map<Path, MessageFile> proposed = new LinkedHashMap<>(files);
		for (var entry : plans.entrySet()) {
			FilePlan plan = entry.getValue();
			plan.result = MessageFile.fromLines(plan.original.asset, plan.render());
			proposed.put(entry.getKey(), plan.result);
		}
		validate(proposed.values());
		verifyUnchanged();
		for (FilePlan plan : plans.values()) {
			Path destination = path(plan.original.asset);
			if (!destination.startsWith(top))
				throw new IOException("Message output is outside the mod layer: " + destination);
			if (!Files.isDirectory(top))
				throw new IOException("Top-level asset directory does not exist: " + top);
			Files.createDirectories(destination.getParent());
			// Also detect an unexpected destination that was absent when the library loaded.
			if (!files.containsKey(destination) && Files.exists(destination))
				throw new IOException("Message output was created outside Star Rod: " + destination);
		}
		// Roll back already-written files if another write fails. Leave all drafts dirty on failure.
		List<FilePlan> written = new ArrayList<>();
		try {
			for (FilePlan plan : plans.values()) {
				IOUtils.atomicWriteLines(plan.result.lines, plan.original.asset);
				written.add(plan);
			}
		}
		catch (IOException failure) {
			Collections.reverse(written);
			for (FilePlan plan : written) {
				try {
					if (files.containsKey(path(plan.original.asset)))
						IOUtils.atomicWrite(plan.original.asset, ".rollback", temporary -> Files.write(temporary.toPath(), plan.original.bytes));
					else
						Files.deleteIfExists(plan.original.asset.toPath());
				}
				catch (IOException rollbackFailure) {
					failure.addSuppressed(rollbackFailure);
				}
			}
			throw failure;
		}
		for (var entry : savedLocations.entrySet()) {
			MessageState state = entry.getKey();
			locations.put(state.owner, new Location(entry.getValue(), state.key));
			state.owner.source.recordSaved(state);
			destinations.remove(state.owner);
		}
		for (var entry : plans.entrySet()) {
			Path destination = entry.getKey();
			files.put(destination, entry.getValue().result);
			if (destination.startsWith(top.resolve("msg")))
				discoveredPaths.add(destination);
			updateLineRanges(destination, entry.getValue().result);
		}
		boolean complete = true;
		for (var entry : snapshots.entrySet()) {
			complete &= entry.getKey().finishSave(entry.getValue());
			for (MessageState state : entry.getValue().messages) {
				if (removed.remove(state.owner)) {
					locations.remove(state.owner);
					destinations.remove(state.owner);
					state.owner.source.forgetMessage(state.owner);
				}
			}
		}
		indexDefinitions();
		return complete;
	}

	private void updateLineRanges(Path destination, MessageFile file)
	{
		Map<String, Message> definitions = new HashMap<>();
		for (Message message : file.messages)
			definitions.put(key(message), message);
		for (var entry : locations.entrySet()) {
			Location location = entry.getValue();
			if (!location.path.equals(destination))
				continue;
			Message parsed = definitions.get(location.key);
			if (parsed == null)
				continue;
			Message live = entry.getKey();
			synchronized (live.source) {
				live.startLineNum = parsed.startLineNum;
				live.endLineNum = parsed.endLineNum;
			}
		}
	}

	/** Existing order wins if the two files have conflicting orders. */
	private static int insertionIndex(List<String> destination, List<String> source, String key)
	{
		int sourceIndex = source.indexOf(key);
		if (sourceIndex < 0)
			return destination.size();
		int previous = -1;
		for (int i = sourceIndex - 1; i >= 0 && previous < 0; i--)
			previous = destination.indexOf(source.get(i));
		int next = -1;
		for (int i = sourceIndex + 1; i < source.size() && next < 0; i++)
			next = destination.indexOf(source.get(i));
		if (next >= 0 && (previous < 0 || previous < next))
			return next;
		return previous >= 0 ? previous + 1 : destination.size();
	}

	private record Location(Path path, String key)
	{}

	private record Definition(MessageFile file, Message message)
	{}

	private static final class MessageFile
	{
		final AssetHandle asset;
		final byte[] bytes;
		final List<String> lines;
		final List<Message> messages;

		private MessageFile(AssetHandle asset, byte[] bytes) throws IOException
		{
			this.asset = asset;
			this.bytes = bytes;
			List<Line> input = IOUtils.readPlainInputStream(new FileSource(asset), new ByteArrayInputStream(bytes));
			lines = input.stream().map(line -> line.str).toList();
			messages = StringEncoder.parseMessages(new MessageAsset(asset, null), input);
		}

		static MessageFile read(AssetHandle asset) throws IOException
		{
			return new MessageFile(asset, Files.readAllBytes(asset.toPath()));
		}

		static MessageFile empty(AssetHandle asset) throws IOException
		{
			return new MessageFile(asset, new byte[0]);
		}

		static MessageFile fromLines(AssetHandle asset, List<String> lines) throws IOException
		{
			String text = lines.isEmpty() ? "" : String.join(System.lineSeparator(), lines) + System.lineSeparator();
			return new MessageFile(asset, text.getBytes(StandardCharsets.UTF_8));
		}

		List<String> order()
		{
			return messages.stream().map(MessageLibrary::key).toList();
		}
	}

	private static final class FilePlan
	{
		final MessageFile original;
		final List<String> prefix;
		final List<String> suffix;
		final List<String> order = new ArrayList<>();
		final Map<String, List<String>> leading = new HashMap<>();
		final Map<String, List<String>> bodies = new HashMap<>();
		MessageFile result;

		FilePlan(MessageFile file)
		{
			original = file;
			int first = file.messages.isEmpty() ? file.lines.size() : file.messages.get(0).startLineNum - 1;
			prefix = file.lines.subList(0, first);
			int current = first;
			for (Message message : file.messages) {
				String key = key(message);
				order.add(key);
				leading.put(key, file.lines.subList(current, message.startLineNum - 1));
				bodies.put(key, file.lines.subList(message.startLineNum - 1, message.endLineNum));
				current = message.endLineNum;
			}
			suffix = file.lines.subList(current, file.lines.size());
		}

		void apply(MessageState state, String existingKey, String sourceKey, List<String> sourceOrder)
		{
			String slot = existingKey;
			if (existingKey == null) {
				// Slots represent positions in the original file, not the final identities.
				// A rename may free a name that a new override then uses in the same save.
				slot = state.key;
				int suffix = 0;
				while (bodies.containsKey(slot))
					slot = state.key + "#" + ++suffix;
				order.add(insertionIndex(order, sourceOrder, sourceKey), slot);
				leading.put(slot, List.of(""));
			}
			bodies.put(slot, state.lines);
		}

		void remove(String key)
		{
			// Preserve comments and whitespace between definitions, even when removing a body.
			bodies.put(key, List.of());
		}

		List<String> render()
		{
			List<String> lines = new ArrayList<>(prefix);
			for (String key : order) {
				lines.addAll(leading.get(key));
				lines.addAll(bodies.get(key));
			}
			lines.addAll(suffix);
			return lines;
		}
	}
}
