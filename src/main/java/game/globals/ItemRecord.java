package game.globals;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import app.StarRodException;
import app.input.IOUtils;
import game.ProjectDatabase;
import game.globals.editor.FlagSet;
import game.globals.editor.GlobalsRecord;
import util.NameUtils;
import util.YamlHelper;

public class ItemRecord extends GlobalsRecord
{
	public boolean isUnused;
	private ItemRecord backup;

	public String name;
	public String enumName;

	public String iconName;
	public String itemEntityName;
	public String hudElemName;
	private boolean noIconArg;

	public FlagSet typeFlags;
	public FlagSet targetFlags;

	public String moveName = "MOVE_NONE";
	public String category;

	// msg values may be NULL, pointers, or string names
	public String msgName = "MSG_NONE";
	public String msgFullDesc = "MSG_NONE";
	public String msgShortDesc = "MSG_NONE";

	public short sortValue; // badges only
	public byte potencyA; // contextual: HP Bonus | Damage | Duration
	public byte potencyB; // FP bonus
	public short sellValue = -1;

	private ItemRecord()
	{
		targetFlags = new FlagSet(ProjectDatabase.TargetFlags, "TARGET_FLAG_", true);
		typeFlags = new FlagSet(ProjectDatabase.ItemTypeFlags, "ITEM_TYPE_FLAG_", true);
	}

	public ItemRecord(int index)
	{
		this();
		setIndex(index);

		backup = new ItemRecord();
	}

	public void saveBackup()
	{
		backup.copyFrom(this);
	}

	public void restoreBackup()
	{
		this.copyFrom(backup);
		setModified(false);
	}

	public void setName(String newName)
	{
		name = newName;
		enumName = "ITEM_" + NameUtils.toEnumStyle(name);
	}

	public void copyFrom(ItemRecord other)
	{
		this.setName(other.name);

		this.isUnused = other.isUnused;

		this.iconName = other.iconName;
		this.itemEntityName = other.itemEntityName;
		this.hudElemName = other.hudElemName;
		this.noIconArg = other.noIconArg;

		this.typeFlags.setBits(other.typeFlags.getBits());
		this.targetFlags.setBits(other.typeFlags.getBits());

		this.moveName = other.moveName;
		this.category = other.category;

		this.msgName = other.msgName;
		this.msgFullDesc = other.msgFullDesc;
		this.msgShortDesc = other.msgShortDesc;

		this.sortValue = other.sortValue;
		this.potencyA = other.potencyA;
		this.potencyB = other.potencyB;
		this.sellValue = other.sellValue;
	}

	@SuppressWarnings("unchecked")
	public static ArrayList<ItemRecord> fromYAML(File yamlFile)
	{
		ArrayList<ItemRecord> items = new ArrayList<>();

		ArrayList<Object> itemList = YamlHelper.readAsList(yamlFile);
		if (itemList == null)
			return null;

		int i = 0;
		for (Object outer : itemList) {
			ItemRecord rec = new ItemRecord(i);

			Map<String, Object> map = (Map<String, Object>) outer;

			for (Entry<String, Object> e : map.entrySet()) {
				Map<String, Object> inner = (Map<String, Object>) e.getValue();

				rec.setName(e.getKey());
				rec.isUnused = YamlHelper.getBoolean(inner, "unused", false);
				rec.msgName = YamlHelper.getString(inner, "nameMsg", "MSG_NONE");
				rec.msgFullDesc = YamlHelper.getString(inner, "fullDescMsg", "MSG_NONE");
				rec.msgShortDesc = YamlHelper.getString(inner, "shortDescMsg", "MSG_NONE");

				rec.hudElemName = YamlHelper.getString(inner, "hudElementTemplate", "ITEM");
				rec.itemEntityName = YamlHelper.getString(inner, "itemEntityTemplate", "STANDARD");
				rec.iconName = YamlHelper.getString(inner, "icon", "key/Gift");
				rec.noIconArg = YamlHelper.getBoolean(inner, "noArgScripts", false);

				rec.sellValue = (short) YamlHelper.getInt(inner, "sellValue", -1);
				rec.sortValue = (short) YamlHelper.getInt(inner, "sortValue", 0);
				rec.potencyA = (byte) YamlHelper.getInt(inner, "potencyA", 0);
				rec.potencyB = (byte) YamlHelper.getInt(inner, "potencyB", 0);

				rec.moveName = YamlHelper.getString(inner, "moveID", "MOVE_NONE");

				rec.targetFlags.setReal(YamlHelper.getList(inner, "targetFlags"));
				rec.typeFlags.setReal(YamlHelper.getList(inner, "typeFlags"));
			}

			items.add(rec);
			i++;
		}

		return items;
	}

	public static void toYAML(File yamlFile, List<ItemRecord> itemList)
	{
		try (PrintWriter pw = IOUtils.getBufferedPrintWriter(yamlFile)) {
			for (ItemRecord rec : itemList) {
				pw.printf("- %s:%n", rec.name);
				if (rec.isUnused) {
					pw.println("    unused: true");
				}
				pw.printf("    nameMsg: %s%n", rec.msgName);
				pw.printf("    fullDescMsg: %s%n", rec.msgFullDesc);
				pw.printf("    shortDescMsg: %s%n", rec.msgShortDesc);
				pw.printf("    icon: %s%n", rec.iconName);
				pw.printf("    itemEntityTemplate: %s%n", rec.itemEntityName);
				pw.printf("    hudElementTemplate: %s%n", rec.hudElemName);
				if (rec.noIconArg) {
					pw.println("    noArgScripts: true");
				}
				pw.printf("    sellValue: %d%n", rec.sellValue);
				pw.printf("    sortValue: %d%n", rec.sortValue);
				pw.printf("    targetFlags: %s%n", rec.targetFlags.getYamlOut());
				pw.printf("    typeFlags: %s%n", rec.typeFlags.getYamlOut());
				pw.printf("    moveID: %s%n", rec.moveName);
				pw.printf("    potencyA: %d%n", rec.potencyA);
				pw.printf("    potencyB: %d%n", rec.potencyB);
			}
		}
		catch (FileNotFoundException e) {
			throw new StarRodException(e);
		}
	}

	@Override
	public String getFilterableString()
	{
		return name + " " + enumName;
	}

	@Override
	public boolean canDeleteFromList()
	{
		return listIndex > 0;
	}

	@Override
	public String getIdentifier()
	{
		return name;
	}

	@Override
	public String toString()
	{
		return name;
	}
}
