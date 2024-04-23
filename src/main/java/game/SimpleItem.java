package game;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;

import app.Directories;
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

	private SimpleItem(int index, String name, String iconName)
	{
		this.index = index;
		this.name = name;
		this.enumName = "ITEM_" + name.replaceAll("((?<=[a-z0-9])[A-Z]|(?!^)(?<!_)[A-Z](?=[a-z]))", "_$1").toUpperCase();

		this.iconName = iconName;

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
	public static ArrayList<SimpleItem> readAll()
	{
		ArrayList<SimpleItem> items = new ArrayList<>();

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
				items.add(new SimpleItem(i, name, iconName));
				i++;
			}
		}

		return items;
	}
}
