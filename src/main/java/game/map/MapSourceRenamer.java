package game.map;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.HashMap;

import javax.swing.JOptionPane;

import org.ahocorasick.trie.Token;
import org.ahocorasick.trie.Trie;
import org.ahocorasick.trie.Trie.TrieBuilder;

import app.SwingUtils;
import app.config.Options;
import app.input.IOUtils;
import game.map.editor.MapEditor;
import game.map.tree.MapObjectTreeModel;
import util.Logger;

public class MapSourceRenamer
{
	private int renamedCount = 0;

	public MapSourceRenamer(Map map)
	{
		process(map);

		resetNames(map.modelTree);
		resetNames(map.colliderTree);
		resetNames(map.zoneTree);
	}

	private void resetNames(MapObjectTreeModel<?> tree)
	{
		tree.depthFirstTraversal((mobj) -> {
			mobj.captureOriginalName();
		});
	}

	private void addToRenameMap(HashMap<String, String> renames, MapObjectTreeModel<?> tree, String prefix)
	{
		tree.depthFirstTraversal((mobj) -> {
			if (!mobj.getNode().isRoot() && mobj.hasBeenRenamed())
				renamedCount++;

			String oldName = mobj.getOriginalName();
			String newName = mobj.getName();
			if (oldName == null)
				oldName = newName; // should not occur

			renames.put(prefix + "_" + oldName, prefix + "_" + newName);
		});
	}

	private void process(Map map)
	{
		String policy = MapEditor.instance().editorConfig.getString(Options.RenameOnSave);
		if (policy.equalsIgnoreCase("never"))
			return;

		HashMap<String, String> renames = new HashMap<>();
		renamedCount = 0;

		addToRenameMap(renames, map.modelTree, "MODEL");
		addToRenameMap(renames, map.colliderTree, "COLLIDER");
		addToRenameMap(renames, map.zoneTree, "ZONE");

		if (renamedCount == 0 || !map.getProjDir().exists())
			return;

		String countMsg;
		if (renamedCount == 1)
			countMsg = "1 object has been renamed.";
		else
			countMsg = renamedCount + " objects have been renamed.";

		if (!policy.equalsIgnoreCase("always")) {
			// ask permission first
			int choice = SwingUtils.getConfirmDialog()
				.setTitle("Confirm Renames")
				.setMessage(countMsg, "Should Star Rod update source files?")
				.choose();

			if (choice != JOptionPane.OK_OPTION)
				return;
		}

		TrieBuilder builder = Trie.builder().ignoreOverlaps();
		for (String key : renames.keySet())
			builder.addKeyword(key);
		Trie trie = builder.build();

		Iterable<File> files;
		try {
			files = IOUtils.getFilesWithExtension(map.getProjDir(), new String[] { "h", "c" }, true);
		}
		catch (IOException e) {
			Logger.printStackTrace(e);
			Logger.logError("Could not find source files for " + map.getName() + ": " + e.getMessage());
			return;
		}

		for (File srcFile : files) {
			try {
				Logger.log("Replacing names in " + srcFile.getName());
				String fileText = Files.readString(srcFile.toPath());

				Collection<Token> tokens = trie.tokenize(fileText);
				StringBuilder sb = new StringBuilder();

				for (Token token : tokens) {
					String s = token.getFragment();
					if (token.isMatch()) {
						sb.append(renames.get(s));
					}
					else {
						sb.append(s);
					}
				}

				Files.writeString(srcFile.toPath(), sb.toString());
			}
			catch (IOException e) {
				Logger.printStackTrace(e);
				Logger.logError("Rename failed for " + srcFile.getName() + ": " + e.getMessage());
			}
		}
	}
}
