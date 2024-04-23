package game.sprite.editor;

import static java.nio.file.StandardWatchEventKinds.*;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

import game.sprite.Sprite;
import util.Logger;

public class SpriteAssetWatcher
{
	private static final boolean ASSET_WATCHING_ENABLED = false;
	private WatchService assetWatcher = null;

	private WatchKey imgAssetsKey = null;
	private WatchKey palAssetsKey = null;

	private Path imgPath;
	private Path palPath;

	private boolean imgAssetsDirty = false;
	private boolean palAssetsDirty = false;

	public SpriteAssetWatcher()
	{
		try {
			assetWatcher = FileSystems.getDefault().newWatchService();
		}
		catch (IOException e) {
			assetWatcher = null;
			Logger.printStackTrace(e);
		}
	}

	public void release()
	{
		if (!ASSET_WATCHING_ENABLED)
			return;

		if (assetWatcher == null)
			return;

		if (imgAssetsKey != null) {
			imgAssetsKey.cancel();
			imgAssetsKey = null;
		}

		if (palAssetsKey != null) {
			palAssetsKey.cancel();
			palAssetsKey = null;
		}
	}

	public void acquire(Sprite sprite)
	{
		if (!ASSET_WATCHING_ENABLED)
			return;

		imgAssetsDirty = false;
		palAssetsDirty = false;

		if (assetWatcher == null)
			return;

		imgPath = Paths.get(sprite.getDirectoryName(), "rasters");
		try {
			imgAssetsKey = imgPath.register(assetWatcher, ENTRY_MODIFY, ENTRY_CREATE, ENTRY_DELETE);
		}
		catch (IOException e) {
			imgAssetsKey = null;
			Logger.printStackTrace(e);
		}

		palPath = Paths.get(sprite.getDirectoryName(), "palettes");
		try {
			palAssetsKey = palPath.register(assetWatcher, ENTRY_MODIFY, ENTRY_CREATE, ENTRY_DELETE);
		}
		catch (IOException e) {
			palAssetsKey = null;
			Logger.printStackTrace(e);
		}
	}

	public void process()
	{
		if (!ASSET_WATCHING_ENABLED)
			return;

		if (assetWatcher == null)
			return;

		while (true) {
			WatchKey key = assetWatcher.poll();
			if (key == null) {
				break;
			}

			Path path = (Path) key.watchable();
			if (path == imgPath) {
				imgAssetsDirty = true;
			}
			if (path == palPath) {
				palAssetsDirty = true;
			}

			key.reset();
		}
	}

	public boolean areImgDirty()
	{
		boolean val = imgAssetsDirty;
		imgAssetsDirty = false;
		return val;
	}

	public boolean arePalDirty()
	{
		boolean val = palAssetsDirty;
		palAssetsDirty = false;
		return val;
	}
}
