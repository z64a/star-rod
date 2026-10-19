package game.globals.editor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Icon;

import app.Directories;
import app.Environment;
import app.StarRodException;
import assets.AssetManager;
import assets.AssetSubdir;
import game.globals.IconRecord;
import game.globals.ItemRecord;
import game.globals.MoveRecord;
import util.Logger;

public class GlobalsData
{
	public static void main(String[] args)
	{
		Environment.initialize();
		GlobalsData g = new GlobalsData();
		g.loadAllData();
		Environment.exit();
	}

	public static enum GlobalsCategory
	{
		MOVE_TABLE,
		ITEM_TABLE;
	}

	public final GlobalsListModel<IconRecord> icons = new GlobalsListModel<>();
	public final GlobalsListModel<ItemRecord> items = new GlobalsListModel<>();
	public final GlobalsListModel<MoveRecord> moves = new GlobalsListModel<>();

	public void loadAllData()
	{
		loadIcons();
		loadMoves();
		loadItems();
	}

	public void saveAllData()
	{
		for (GlobalsCategory type : GlobalsCategory.values())
			saveData(type);
	}

	public void loadData(GlobalsCategory type)
	{
		switch (type) {
			case ITEM_TABLE:
				loadItems();
				break;
			case MOVE_TABLE:
				loadMoves();
				break;
		}
	}

	public void saveData(GlobalsCategory type)
	{
		switch (type) {
			case ITEM_TABLE:
				saveItems();
				break;
			case MOVE_TABLE:
				saveMoves();
				break;
		}
	}

	private void loadIcons()
	{
		icons.clear();
		try {
			File xmlFile = AssetManager.get(AssetSubdir.ICON, "Icons.xml");
			icons.addAll(IconRecord.readXML(xmlFile));
			/*
			for(AssetHandle ah : AssetManager.getIcons()) {
				icons.addElement(new IconRecord(ah));
			}
			*/
		}
		catch (IOException e) {
			throw new StarRodException("IOException while loading icons! %n%s", e.getMessage());
		}
	}

	private void loadItems()
	{
		File yamlFile = Directories.PROJ_SRC.file("item_table.yaml");
		List<ItemRecord> itemList = ItemRecord.fromYAML(yamlFile);
		if (itemList == null)
			throw new StarRodException("Could not load item table: %s", yamlFile);

		int index = 0;
		for (ItemRecord item : itemList) {
			item.setIndex(index++);
			item.saveBackup();
		}
		items.clear();
		items.addAll(itemList);

		Logger.log("Loaded " + items.size() + " items");
	}

	private void saveItems()
	{
		List<ItemRecord> itemList = new ArrayList<>(items.getSize());
		for (ItemRecord item : items) {
			itemList.add(item);
		}

		File yamlFile = Directories.PROJ_SRC.file("item_table.yaml");
		ItemRecord.toYAML(yamlFile, itemList);
		for (ItemRecord item : itemList)
			item.saveBackup();

		Logger.log("Saved item table");
	}

	private void loadMoves()
	{
		File yamlFile = Directories.PROJ_SRC.file("move_table.yaml");
		List<MoveRecord> moveList = MoveRecord.fromYAML(yamlFile);
		if (moveList == null)
			throw new StarRodException("Could not load move table: %s", yamlFile);

		int index = 0;
		for (MoveRecord move : moveList) {
			move.setIndex(index++);
			move.saveBackup();
		}
		moves.clear();
		moves.addAll(moveList);

		Logger.log("Loaded " + moves.size() + " moves");
	}

	private void saveMoves()
	{
		List<MoveRecord> moveList = new ArrayList<>(moves.getSize());
		for (MoveRecord move : moves) {
			moveList.add(move);
		}

		File yamlFile = Directories.PROJ_SRC.file("move_table.yaml");
		MoveRecord.toYAML(yamlFile, moveList);
		for (MoveRecord move : moveList)
			move.saveBackup();

		Logger.log("Saved moves table");
	}

	public Icon getLargeIcon(String name)
	{
		for (IconRecord img : icons)
			if (img.getIdentifier().equals(name))
				return img.largeIcon;

		return null;
	}

	public Icon getSmallIcon(String name)
	{
		for (IconRecord img : icons)
			if (img.getIdentifier().equals(name))
				return img.smallIcon;

		return null;
	}

	public boolean hasMoveEnum(String enumName)
	{
		for (MoveRecord rec : moves) {
			if (enumName.equals(rec.enumName))
				return true;
		}
		return false;
	}
}
