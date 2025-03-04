package game;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;

import app.Directories;
import app.Environment;
import assets.AssetHandle;
import assets.AssetManager;
import assets.AssetSubdir;
import game.texture.Tile;
import game.texture.TileFormat;
import util.Logger;
import util.YamlHelper;

public class SimpleItem
{
	public final int index;
	public final String name;
	public final String enumName;
	public final String iconName;
	public final Tile iconTile;

	private SimpleItem(SimpleItemTemplate template)
	{
		this.index = template.index;
		this.name = template.name;
		this.iconName = template.iconName;

		this.enumName = "ITEM_" + name.replaceAll("((?<=[a-z0-9])[A-Z]|(?!^)(?<!_)[A-Z](?=[a-z]))", "_$1").toUpperCase();

		AssetHandle ah = AssetManager.get(AssetSubdir.ICON, iconName + ".png");
		Tile tile = null;

		try {
			tile = Tile.load(ah, TileFormat.CI_4);
		}
		catch (IOException e) {
			Logger.logfWarning("Exception while loading icon: %s%n%s", name, e.getMessage());
		}

		iconTile = tile;
	}

	@SuppressWarnings("unchecked")
	public static List<SimpleItem> readAll()
	{
		ArrayList<SimpleItemTemplate> templates = new ArrayList<>();

		File yamlFile = Directories.PROJ_SRC.file("item_table.yaml");
		ArrayList<Object> itemList = YamlHelper.readAsList(yamlFile);
		if (itemList == null)
			return null;

		int i = 0;
		for (Object outer : itemList) {
			Map<String, Object> map = (Map<String, Object>) outer;
			for (Entry<String, Object> e : map.entrySet()) {
				Map<String, Object> inner = (Map<String, Object>) e.getValue();

				String name = e.getKey();
				String iconName = YamlHelper.getString(inner, "icon", "key/Gift");
				templates.add(new SimpleItemTemplate(i, name, iconName));
				i++;
			}
		}

		//ArrayList<SimpleItem> items = new ArrayList<>();

		//FIXME doing this is parallel will drastically improve startup time
		//for (SimpleItemTemplate template : templates) {
		//	items.add(new SimpleItem(template));
		//}

		List<CompletableFuture<SimpleItem>> futures = templates.stream()
			.map(template -> CompletableFuture.supplyAsync(() -> new SimpleItem(template), Environment.getExecutor()))
			.toList();

		// Wait for all tasks to complete and collect results
		List<SimpleItem> items = futures.stream()
			.map(CompletableFuture::join)
			.toList();

		return items;
	}

	private static class SimpleItemTemplate
	{
		public final int index;
		public final String name;
		public final String iconName;

		private SimpleItemTemplate(int index, String name, String iconName)
		{
			this.index = index;
			this.name = name;
			this.iconName = iconName;
		}
	}
}
