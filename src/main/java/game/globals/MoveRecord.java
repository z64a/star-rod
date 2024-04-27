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

public class MoveRecord extends GlobalsRecord
{
	public static final String NO_ACTION = "NONE";

	private MoveRecord backup;

	public String name;
	public String enumName;
	public String desc;

	public String msgName;
	public String msgShortDesc;
	public String msgFullDesc;

	public FlagSet targetFlags;

	public String actionTip;
	public String category;

	public byte fpCost;
	public byte bpCost;

	private MoveRecord()
	{
		targetFlags = new FlagSet(ProjectDatabase.TargetFlags, "TARGET_FLAG_", true);
	}

	public MoveRecord(int index)
	{
		this();
		setIndex(index);

		backup = new MoveRecord();
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
		enumName = "MOVE_" + NameUtils.toEnumStyle(name);
	}

	public void copyFrom(MoveRecord other)
	{
		this.setName(other.name);
		this.desc = other.desc;

		this.msgName = other.msgName;
		this.msgShortDesc = other.msgShortDesc;
		this.msgFullDesc = other.msgFullDesc;

		this.targetFlags = new FlagSet(other.targetFlags);

		this.actionTip = other.actionTip;
		this.category = other.category;

		this.fpCost = other.fpCost;
		this.bpCost = other.bpCost;
	}

	@SuppressWarnings("unchecked")
	public static ArrayList<MoveRecord> fromYAML(File yamlFile)
	{
		ArrayList<MoveRecord> moves = new ArrayList<>();

		ArrayList<Object> moveList = YamlHelper.readAsList(yamlFile);
		if (moveList == null)
			return null;

		int i = 0;
		for (Object outer : moveList) {
			MoveRecord rec = new MoveRecord(i);

			Map<String, Object> map = (Map<String, Object>) outer;

			for (Entry<String, Object> e : map.entrySet()) {
				Map<String, Object> inner = (Map<String, Object>) e.getValue();

				rec.setName(e.getKey());
				rec.msgName = YamlHelper.getString(inner, "nameMsg", "MSG_NONE");
				rec.msgFullDesc = YamlHelper.getString(inner, "fullDescMsg", "MSG_NONE");
				rec.msgShortDesc = YamlHelper.getString(inner, "shortDescMsg", "MSG_NONE");

				rec.category = YamlHelper.getString(inner, "category", "");
				rec.actionTip = YamlHelper.getString(inner, "actionTip", "");

				rec.fpCost = (byte) YamlHelper.getInt(inner, "costFP", 0);
				rec.bpCost = (byte) YamlHelper.getInt(inner, "costBP", 0);

				rec.targetFlags.setReal(YamlHelper.getList(inner, "flags"));
			}

			moves.add(rec);
			i++;
		}

		return moves;
	}

	public static void toYAML(File yamlFile, List<MoveRecord> moveList)
	{
		try (PrintWriter pw = IOUtils.getBufferedPrintWriter(yamlFile)) {
			for (MoveRecord rec : moveList) {
				pw.printf("- %s:%n", rec.name);
				pw.printf("    nameMsg: %s%n", rec.msgName);
				pw.printf("    fullDescMsg: %s%n", rec.msgFullDesc);
				pw.printf("    shortDescMsg: %s%n", rec.msgShortDesc);
				pw.printf("    flags: %s%n", rec.targetFlags.getYamlOut());
				pw.printf("    category: %s%n", rec.category);
				pw.printf("    actionTip: %s%n", rec.actionTip);
				pw.printf("    costFP: %d%n", rec.fpCost);
				pw.printf("    costBP: %d%n", rec.bpCost);
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
		return enumName;
	}
}
