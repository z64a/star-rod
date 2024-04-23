package game.map.scripts.generators.foliage;

import static game.map.MapKey.*;

import org.w3c.dom.Element;

import game.map.Map;
import game.map.MapObject.MapObjectType;
import game.map.editor.commands.AbstractCommand;
import game.map.scripts.generators.Generator.GeneratorType;
import game.map.scripts.generators.Generator.ValidationState;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlSerializable;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class FoliageDrop extends FoliageData implements XmlSerializable
{
	public final Foliage owner;

	public int type;
	public String markerName = "";
	public String itemName = "";
	public String spawnFlag = "";
	public String pickupFlag = "";

	@Override
	public FoliageDrop deepCopy()
	{
		FoliageDrop copy = new FoliageDrop(owner);

		copy.markerName = markerName;
		copy.itemName = itemName;
		copy.type = type;

		copy.spawnFlag = spawnFlag;
		copy.pickupFlag = pickupFlag;

		return copy;
	}

	public FoliageDrop(Foliage owner)
	{
		this.owner = owner;

		if (owner.type == GeneratorType.Tree) {
			type = 0xE; // Fall
			itemName = "Mushroom";
		}
		else if (owner.type == GeneratorType.Bush) {
			type = 6; // Toss
			itemName = "Coin";
		}
	}

	public static FoliageDrop read(Foliage owner, XmlReader xmr, Element elem)
	{
		FoliageDrop drop = new FoliageDrop(owner);
		drop.fromXML(xmr, elem);
		return drop;
	}

	@Override
	public void fromXML(XmlReader xmr, Element elem)
	{
		if (xmr.hasAttribute(elem, ATTR_DROP_TYPE))
			type = xmr.readHex(elem, ATTR_DROP_TYPE);

		if (xmr.hasAttribute(elem, ATTR_MARKER))
			markerName = xmr.getAttribute(elem, ATTR_MARKER);

		if (xmr.hasAttribute(elem, ATTR_DROP_ITEM))
			itemName = xmr.getAttribute(elem, ATTR_DROP_ITEM);

		if (xmr.hasAttribute(elem, ATTR_DROP_SPAWN))
			spawnFlag = xmr.getAttribute(elem, ATTR_DROP_SPAWN);

		if (xmr.hasAttribute(elem, ATTR_DROP_PICKUP))
			pickupFlag = xmr.getAttribute(elem, ATTR_DROP_PICKUP);
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag tag = xmw.createTag(TAG_FOLIAGE_DROP, true);

		xmw.addHex(tag, ATTR_TYPE, type);

		if (markerName != null && !markerName.isEmpty())
			xmw.addAttribute(tag, ATTR_MARKER, markerName);

		if (itemName != null && !itemName.isEmpty())
			xmw.addAttribute(tag, ATTR_DROP_ITEM, itemName);

		if (spawnFlag != null && !spawnFlag.isEmpty())
			xmw.addAttribute(tag, ATTR_DROP_SPAWN, spawnFlag);

		if (pickupFlag != null && !pickupFlag.isEmpty())
			xmw.addAttribute(tag, ATTR_DROP_PICKUP, pickupFlag);

		xmw.printTag(tag);
	}

	public ValidationState checkValidity(Map map)
	{
		ValidationState state = ValidationState.Valid;

		if (state == ValidationState.Valid && (markerName == null || markerName.isEmpty()))
			state = ValidationState.Incomplete;
		if (markerName != null && map.find(MapObjectType.MARKER, markerName) == null)
			state = ValidationState.Invalid;

		if (state == ValidationState.Valid && (itemName == null || itemName.isEmpty()))
			state = ValidationState.Incomplete;

		if (state == ValidationState.Valid && (spawnFlag == null || spawnFlag.isEmpty()))
			state = ValidationState.Incomplete;

		if (state == ValidationState.Valid && (pickupFlag == null || pickupFlag.isEmpty()))
			state = ValidationState.Incomplete;

		return state;
	}

	@Override
	public String toString()
	{
		if (itemName == null || itemName.isEmpty())
			return "???";

		return itemName + " Drop";
	}

	public static class EditFoliageDrop extends AbstractCommand
	{
		private final FoliageDrop newValues;
		private final FoliageDrop oldValues;
		private final FoliageDrop drop;

		public EditFoliageDrop(FoliageDrop drop, FoliageDrop newValues)
		{
			super("Edit Foliage Drop");
			this.drop = drop;
			this.newValues = newValues;
			this.oldValues = drop.deepCopy();
		}

		@Override
		public boolean shouldExec()
		{
			if (oldValues.type != newValues.type)
				return true;

			if (!oldValues.markerName.equals(newValues.markerName))
				return true;

			if (!oldValues.itemName.equals(newValues.itemName))
				return true;

			if (!oldValues.pickupFlag.equals(newValues.pickupFlag))
				return true;

			if (!oldValues.spawnFlag.equals(newValues.spawnFlag))
				return true;

			return false;
		}

		@Override
		public void exec()
		{
			super.exec();
			drop.type = newValues.type;
			drop.markerName = newValues.markerName;
			drop.itemName = newValues.itemName;
			drop.pickupFlag = newValues.pickupFlag;
			drop.spawnFlag = newValues.spawnFlag;
			FoliageInfoPanel.instance().updateFields(drop.owner);
		}

		@Override
		public void undo()
		{
			super.undo();
			drop.type = oldValues.type;
			drop.markerName = oldValues.markerName;
			drop.itemName = oldValues.itemName;
			drop.pickupFlag = oldValues.pickupFlag;
			drop.spawnFlag = oldValues.spawnFlag;
			FoliageInfoPanel.instance().updateFields(drop.owner);
		}
	}
}
