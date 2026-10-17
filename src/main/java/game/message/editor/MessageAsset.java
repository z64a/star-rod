package game.message.editor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import app.input.IOUtils;
import app.input.InputFileException;
import app.input.Line;
import assets.AssetHandle;
import assets.AssetManager;
import game.message.Message;
import game.message.StringEncoder;
import util.Logger;

public class MessageAsset
{
	public AssetHandle asset;
	public List<Message> messages = null;
	private long lastModified;
	private long modificationRevision;
	private final Object saveLock = new Object();

	public volatile boolean hasModified;
	public boolean hasError;

	public MessageAsset(AssetHandle asset)
	{
		this.asset = asset;
		reload();
	}

	public void reload()
	{
		synchronized (saveLock) {
			reloadImpl();
		}
	}

	private synchronized void reloadImpl()
	{
		try {
			messages = StringEncoder.parseMessages(this);
		}
		catch (IOException e) {
			throw new InputFileException(asset, e.getMessage());
		}

		int msgIndex = 0;
		for (Message msg : messages) {
			msg.index = msgIndex++;
		}

		lastModified = asset.lastModified();
		modificationRevision = 0;
		hasModified = false;
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
			SaveSnapshot snapshot = snapshotIfCurrent(expectedRevision);
			if (snapshot == null)
				return false;

			snapshotReadyCallback.run();
			return saveSnapshot(snapshot);
		}
	}

	private boolean saveSnapshot(SaveSnapshot snapshot)
	{
		if (!snapshot.sourceAsset.exists()) {
			Logger.logError("Could not save changes because " + snapshot.sourceAsset.getName() + " no longer exists.");
			return false;
		}
		if (snapshot.sourceAsset.lastModified() != snapshot.sourceLastModified) {
			Logger.logError("Could not save changes because " + snapshot.sourceAsset.getName()
				+ " was modified outside Star Rod.");
			return false;
		}

		List<Line> linesIn;
		try {
			linesIn = IOUtils.readPlainInputFile(snapshot.sourceAsset);
		}
		catch (IOException e) {
			Logger.logError("Failed to save " + snapshot.sourceAsset.getName());
			Logger.printStackTrace(e);
			return false;
		}

		List<String> linesOut = render(snapshot, linesIn);
		AssetHandle saveAsset;
		try {
			saveAsset = AssetManager.getTopLevelForSave(snapshot.sourceAsset);
			IOUtils.atomicWriteLines(linesOut, saveAsset);
		}
		catch (IOException e) {
			Logger.logError("Failed to save " + snapshot.sourceAsset.getName());
			Logger.printStackTrace(e);
			return false;
		}

		if (commitSnapshot(snapshot, saveAsset))
			return true;

		Logger.logWarning("Saved " + saveAsset.getName() + ", but newer edits remain unsaved.");
		return false;
	}

	private static List<String> render(SaveSnapshot snapshot, List<Line> linesIn)
	{
		List<String> linesOut = new ArrayList<>((int) (linesIn.size() * 1.2));
		int currentLine = 0;

		for (MessageState msg : snapshot.messages) {
			if (msg.startLineNum <= 0)
				continue;

			while (currentLine < msg.startLineNum - 1)
				linesOut.add(linesIn.get(currentLine++).str);

			msg.savedStartLineNum = linesOut.size() + 1;
			writeString(msg, linesOut);
			currentLine = msg.endLineNum;
			msg.savedEndLineNum = linesOut.size();
		}

		while (currentLine < linesIn.size())
			linesOut.add(linesIn.get(currentLine++).str);

		for (MessageState msg : snapshot.messages) {
			if (msg.startLineNum > 0)
				continue;

			msg.savedStartLineNum = linesOut.size() + 1;
			writeString(msg, linesOut);
			msg.savedEndLineNum = linesOut.size();
			linesOut.add("");
		}

		return linesOut;
	}

	public synchronized void markModified(Message message)
	{
		modificationRevision++;
		message.modified = true;
		hasModified = true;
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
		markModified(message);
	}

	private synchronized SaveSnapshot snapshotIfCurrent(long expectedRevision)
	{
		if (modificationRevision != expectedRevision)
			return null;

		List<MessageState> states = new ArrayList<>(messages.size());
		for (Message message : messages)
			states.add(new MessageState(message));
		return new SaveSnapshot(expectedRevision, new AssetHandle(asset), lastModified, states);
	}

	private synchronized boolean commitSnapshot(SaveSnapshot snapshot, AssetHandle saveAsset)
	{
		asset = saveAsset;
		lastModified = saveAsset.lastModified();

		for (MessageState state : snapshot.messages) {
			if (messages.contains(state.owner)) {
				state.owner.startLineNum = state.savedStartLineNum;
				state.owner.endLineNum = state.savedEndLineNum;
			}
		}

		if (modificationRevision != snapshot.revision)
			return false;

		for (MessageState state : snapshot.messages) {
			if (messages.contains(state.owner))
				state.owner.applySavedMarkup(state.markup);
		}

		for (Message msg : messages)
			msg.modified = false;
		hasModified = false;
		return true;
	}

	private static void writeString(MessageState msg, List<String> linesOut)
	{
		linesOut.add(String.format("#message:%02X:(%s)", msg.section, msg.name));
		linesOut.add("{");

		String[] lines = msg.markup.split("\r?\n");
		for (String line : lines) {
			linesOut.add(msg.leadingTabs + line);
		}

		linesOut.add("}");
		linesOut.add("");
	}

	private static final class SaveSnapshot
	{
		private final long revision;
		private final AssetHandle sourceAsset;
		private final long sourceLastModified;
		private final List<MessageState> messages;

		private SaveSnapshot(long revision, AssetHandle sourceAsset, long sourceLastModified, List<MessageState> messages)
		{
			this.revision = revision;
			this.sourceAsset = sourceAsset;
			this.sourceLastModified = sourceLastModified;
			this.messages = messages;
		}
	}

	private static final class MessageState
	{
		private final Message owner;
		private final int startLineNum;
		private final int endLineNum;
		private final String leadingTabs;
		private final int section;
		private final String name;
		private final String markup;

		private int savedStartLineNum;
		private int savedEndLineNum;

		private MessageState(Message message)
		{
			owner = message;
			startLineNum = message.startLineNum;
			endLineNum = message.endLineNum;
			leadingTabs = message.leadingTabs;
			section = message.section;
			name = message.name;
			markup = Message.getSanitizedMarkup(message.getMarkup());
		}
	}
}
