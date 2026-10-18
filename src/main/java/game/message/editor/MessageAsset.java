package game.message.editor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import app.input.FileSource;
import app.input.IOUtils;
import app.input.InputFileException;
import app.input.Line;
import assets.AssetHandle;
import game.message.Message;
import game.message.StringEncoder;
import util.Logger;

/** A group of effective messages with the same relative container path. */
public class MessageAsset
{
	public AssetHandle asset;
	public List<Message> messages;
	private long modificationRevision;
	private final Object saveLock = new Object();
	private final Map<Message, SavedState> savedStates = new IdentityHashMap<>();
	private final Set<Message> structuralChanges = Collections.newSetFromMap(new IdentityHashMap<>());
	private byte[] loadedBytes;
	private MessageLibrary library;

	public volatile boolean hasModified;
	public boolean hasError;

	public MessageAsset(AssetHandle asset)
	{
		this.asset = asset;
		reload();
	}

	MessageAsset(AssetHandle asset, MessageLibrary library)
	{
		this.asset = asset;
		this.library = library;
		messages = new ArrayList<>();
	}

	public void reload()
	{
		synchronized (saveLock) {
			synchronized (this) {
				try {
					loadedBytes = Files.readAllBytes(asset.toPath());
					List<Line> lines = IOUtils.readPlainInputStream(new FileSource(asset), new ByteArrayInputStream(loadedBytes));
					messages = StringEncoder.parseMessages(this, lines);
				}
				catch (IOException e) {
					throw new InputFileException(asset, e.getMessage());
				}
				savedStates.clear();
				structuralChanges.clear();
				for (Message message : messages)
					savedStates.put(message, new SavedState(message));
				updateIndices();
				library = null;
				modificationRevision = 0;
				hasModified = false;
			}
		}
	}

	public boolean saveChanges()
	{
		return saveChanges(getModificationRevision());
	}

	boolean saveChanges(long expectedRevision)
	{
		return saveChanges(expectedRevision, () -> {});
	}

	boolean saveChanges(long expectedRevision, Runnable snapshotReadyCallback)
	{
		synchronized (saveLock) {
			try {
				if (library == null) {
					if (!asset.isFile() || !Arrays.equals(loadedBytes, Files.readAllBytes(asset.toPath())))
						throw new IOException("Could not save " + asset + ": it was changed outside Star Rod. Reload resources first.");
					library = MessageLibrary.forAsset(this);
				}
				return library.save(this, expectedRevision, snapshotReadyCallback);
			}
			catch (IOException | InputFileException e) {
				Logger.logError(e.getMessage());
				return false;
			}
		}
	}

	public synchronized void markModified(Message message)
	{
		modificationRevision++;
		refreshModified(message);
	}

	synchronized void setStructuralModified(Message message, boolean changed)
	{
		if (changed)
			structuralChanges.add(message);
		else
			structuralChanges.remove(message);
		markModified(message);
	}

	private void refreshModified(Message message)
	{
		SavedState saved = savedStates.get(message);
		message.modified = structuralChanges.contains(message) || saved == null || !saved.matches(message);
		hasModified = message.modified || messages.stream().anyMatch(item -> item.modified);
	}

	public synchronized long getModificationRevision()
	{
		return modificationRevision;
	}

	public synchronized void addMessage(Message message)
	{
		if (message.source != this)
			throw new IllegalArgumentException("Message belongs to a different asset");
		messages.add(message);
		updateIndices();
		markModified(message);
	}

	void addLoadedMessage(Message message)
	{
		messages.add(message);
		savedStates.put(message, new SavedState(message));
	}

	synchronized void forgetMessage(Message message)
	{
		messages.remove(message);
		savedStates.remove(message);
		clearModified(message);
	}

	synchronized void clearModified(Message message)
	{
		structuralChanges.remove(message);
		if (messages.contains(message))
			savedStates.put(message, new SavedState(message));
		message.modified = false;
		modificationRevision++;
		hasModified = messages.stream().anyMatch(item -> item.modified);
	}

	void updateIndices()
	{
		for (int i = 0; i < messages.size(); i++) {
			// Legacy unnamed declarations carry an explicit ID, not a list position.
			if (!messages.get(i).name.isEmpty())
				messages.get(i).index = i;
		}
	}

	synchronized String savedKey(Message message)
	{
		SavedState saved = savedStates.get(message);
		return saved == null ? null : saved.key;
	}

	synchronized SaveSnapshot snapshotIfCurrent(long expectedRevision)
	{
		if (modificationRevision != expectedRevision)
			return null;
		List<MessageState> states = new ArrayList<>();
		List<String> order = new ArrayList<>();
		for (Message message : messages) {
			String savedKey = savedKey(message);
			order.add(savedKey == null ? MessageLibrary.key(message) : savedKey);
			if (message.modified)
				states.add(new MessageState(message));
		}
		return new SaveSnapshot(expectedRevision, states, order);
	}

	synchronized void recordSaved(MessageState state)
	{
		// The baseline must describe the snapshot written, even if a newer edit or undo
		// arrived during the save. Such edits are compared against the new on-disk state.
		savedStates.put(state.owner, new SavedState(state.key, state.section, state.markup));
		structuralChanges.remove(state.owner);
		refreshModified(state.owner);
	}

	synchronized boolean finishSave(SaveSnapshot snapshot)
	{
		if (modificationRevision != snapshot.revision) {
			Logger.logWarning("Saved " + asset.getName() + ", but newer edits remain unsaved.");
			return false;
		}
		for (MessageState state : snapshot.messages) {
			state.owner.applySavedMarkup(state.markup);
			structuralChanges.remove(state.owner);
			state.owner.modified = false;
		}
		hasModified = messages.stream().anyMatch(message -> message.modified);
		return !hasModified;
	}

	private record SavedState(String key, int section, String markup)
	{
		SavedState(Message message)
		{
			this(MessageLibrary.key(message), message.section, message.getMarkup());
		}

		boolean matches(Message message)
		{
			return key.equals(MessageLibrary.key(message)) && section == message.section && markup.equals(message.getMarkup());
		}
	}

	static final class SaveSnapshot
	{
		final long revision;
		final List<MessageState> messages;
		final List<String> order;

		SaveSnapshot(long revision, List<MessageState> messages, List<String> order)
		{
			this.revision = revision;
			this.messages = messages;
			this.order = order;
		}
	}

	static final class MessageState
	{
		final Message owner;
		final String key;
		final int section;
		final String markup;
		final List<String> lines;

		MessageState(Message message)
		{
			owner = message;
			key = MessageLibrary.key(message);
			section = message.section;
			markup = Message.getSanitizedMarkup(message.getMarkup());
			lines = new ArrayList<>();
			lines.add(message.name.isEmpty()
				? String.format("#message:%02X:%03X", message.section, message.index)
				: String.format("#message:%02X:(%s)", message.section, message.name));
			lines.add("{");
			for (String line : markup.split("\\r?\\n"))
				lines.add(message.leadingTabs + line);
			lines.add("}");
		}
	}
}
