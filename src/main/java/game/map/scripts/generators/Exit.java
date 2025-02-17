package game.map.scripts.generators;

import static game.map.MapKey.*;

import org.w3c.dom.Element;

import common.commands.EditableField;
import common.commands.EditableField.EditableFieldFactory;
import common.commands.EditableField.StandardBoolName;
import game.ProjectDatabase;
import game.map.scripts.GeneratorsPanel;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public final class Exit extends Generator
{
	public static enum ExitType
	{
		// @formatter:off
		Walk			("Walk"),
		SingleDoor		("Single Door"),
		DoubleDoor		("Double Door"),
		VerticalPipe	("Vertical Pipe"),
		HorizontalPipe	("Horizontal Pipe");
		// @formatter:on

		private final String name;

		private ExitType(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	public final EditableField<String> overrideName;
	public final EditableField<ExitType> type;

	public final EditableField<String> destMap;
	public final EditableField<String> destMarkerName;
	public final EditableField<Boolean> useDestMarkerID;

	public final EditableField<String> markerName;
	public final EditableField<String> colliderName;
	public final EditableField<String> door1Name;
	public final EditableField<String> door2Name;
	public final EditableField<String> lockName;

	public final EditableField<String> doorSound;
	public final EditableField<String> doorSwing;

	public final EditableField<Boolean> hasCallback;

	// used during dump
	public transient int markerID;
	public transient int colliderID;
	public transient int door1ID;
	public transient int door2ID;
	public transient int ptrDestMapName;
	public transient int destMarkerID;

	@Override
	public Exit deepCopy()
	{
		Exit copy = new Exit();

		copy.overrideName.copy(overrideName);
		copy.type.copy(type);

		copy.destMap.copy(destMap);
		copy.destMarkerName.copy(destMarkerName);
		copy.useDestMarkerID.copy(useDestMarkerID);

		copy.markerName.copy(markerName);
		copy.colliderName.copy(colliderName);
		copy.door1Name.copy(door1Name);
		copy.door2Name.copy(door2Name);
		copy.lockName.copy(lockName);

		copy.doorSound.copy(doorSound);
		copy.doorSwing.copy(doorSwing);

		copy.hasCallback.copy(hasCallback);

		return copy;
	}

	private Exit()
	{
		this(ExitType.Walk);
	}

	public Exit(ExitType type)
	{
		super(GeneratorType.Exit);

		this.type = EditableFieldFactory.create(type)
			.setCallback((o) -> {
				ExitInfoPanel.instance().updateFields(this);
			}).setName("Set Exit Type").build();

		overrideName = EditableFieldFactory.create("")
			.setCallback((o) -> {
				ExitInfoPanel.instance().updateFields(this);
				GeneratorsPanel.instance().repaintTree();
			}).setName("Set Exit Name").build();

		destMap = EditableFieldFactory.create("")
			.setCallback((o) -> {
				ExitInfoPanel.instance().updateFields(this);
			}).setName("Set Destination Map").build();

		destMarkerName = EditableFieldFactory.create("")
			.setCallback((o) -> {
				ExitInfoPanel.instance().updateFields(this);
			}).setName("Set Destination Marker").build();

		useDestMarkerID = EditableFieldFactory.create(false)
			.setCallback((o) -> {
				ExitInfoPanel.instance().updateFields(this);
			}).setName((newValue) -> {
				return newValue ? "Interpret Dest Marker as ID" : "Interpret Dest Marker as Name";
			})
			.build();

		markerName = EditableFieldFactory.create("")
			.setCallback((o) -> {
				ExitInfoPanel.instance().updateFields(this);
			}).setName("Set Exit Marker").build();

		colliderName = EditableFieldFactory.create("")
			.setCallback((o) -> {
				ExitInfoPanel.instance().updateFields(this);
			}).setName("Set Collider").build();

		door1Name = EditableFieldFactory.create("")
			.setCallback((o) -> {
				ExitInfoPanel.instance().updateFields(this);
			}).setName("Set Door Model").build();

		door2Name = EditableFieldFactory.create("")
			.setCallback((o) -> {
				ExitInfoPanel.instance().updateFields(this);
			}).setName("Set Door Model").build();

		lockName = EditableFieldFactory.create("")
			.setCallback((o) -> {
				ExitInfoPanel.instance().updateFields(this);
			}).setName("Set Lock Entity").build();

		doorSound = EditableFieldFactory.create(ProjectDatabase.EDoorSounds.getName(0))
			.setCallback((o) -> {
				ExitInfoPanel.instance().updateFields(this);
			}).setName("Set Door Sounds").build();

		doorSwing = EditableFieldFactory.create(ProjectDatabase.EDoorSwings.getName(1))
			.setCallback((o) -> {
				ExitInfoPanel.instance().updateFields(this);
			}).setName("Set Door Direction").build();

		hasCallback = EditableFieldFactory.create(false)
			.setCallback((o) -> {
				ExitInfoPanel.instance().updateFields(this);
			}).setName(new StandardBoolName("Callback")).build();
	}

	public static Exit read(XmlReader xmr, Element elem)
	{
		Exit exit = new Exit();
		exit.fromXML(xmr, elem);
		return exit;
	}

	@Override
	public void fromXML(XmlReader xmr, Element elem)
	{
		if (xmr.hasAttribute(elem, ATTR_NAME))
			overrideName.set(xmr.getAttribute(elem, ATTR_NAME));

		xmr.requiresAttribute(elem, ATTR_TYPE);
		type.set(ExitType.valueOf(xmr.getAttribute(elem, ATTR_TYPE)));

		if (xmr.hasAttribute(elem, ATTR_MARKER))
			markerName.set(xmr.getAttribute(elem, ATTR_MARKER));

		if (xmr.hasAttribute(elem, ATTR_HAS_CALLBACK))
			hasCallback.set(xmr.readBoolean(elem, ATTR_HAS_CALLBACK));

		if (xmr.hasAttribute(elem, ATTR_DEST_MAP))
			destMap.set(xmr.getAttribute(elem, ATTR_DEST_MAP));

		if (xmr.hasAttribute(elem, ATTR_DEST_MARKER))
			destMarkerName.set(xmr.getAttribute(elem, ATTR_DEST_MARKER));

		if (xmr.hasAttribute(elem, ATTR_USE_DEST_ID))
			useDestMarkerID.set(xmr.readBoolean(elem, ATTR_USE_DEST_ID));

		if (xmr.hasAttribute(elem, ATTR_TRIGGER))
			colliderName.set(xmr.getAttribute(elem, ATTR_TRIGGER));

		switch (type.get()) {
			case SingleDoor:
				if (xmr.hasAttribute(elem, ATTR_DOOR1))
					door1Name.set(xmr.getAttribute(elem, ATTR_DOOR1));

				if (xmr.hasAttribute(elem, ATTR_LOCK))
					lockName.set(xmr.getAttribute(elem, ATTR_LOCK));

				if (xmr.hasAttribute(elem, ATTR_DOOR_DIR)) {
					int doorSwingDir = xmr.readInt(elem, ATTR_DOOR_DIR);
					doorSwing.set(ProjectDatabase.EDoorSwings.getName(0));
				}

				if (xmr.hasAttribute(elem, ATTR_DOOR_SFX)) {
					int doorSoundID = xmr.readHex(elem, ATTR_DOOR_SFX);
					doorSound.set(ProjectDatabase.EDoorSounds.getName(0));
				}
				break;

			case DoubleDoor:
				if (xmr.hasAttribute(elem, ATTR_DOOR1))
					door1Name.set(xmr.getAttribute(elem, ATTR_DOOR1));

				if (xmr.hasAttribute(elem, ATTR_DOOR2))
					door2Name.set(xmr.getAttribute(elem, ATTR_DOOR2));

				if (xmr.hasAttribute(elem, ATTR_LOCK))
					lockName.set(xmr.getAttribute(elem, ATTR_LOCK));

				if (xmr.hasAttribute(elem, ATTR_DOOR_SFX)) {
					int doorSoundID = xmr.readHex(elem, ATTR_DOOR_SFX);
					doorSound.set(ProjectDatabase.EDoorSounds.getName(doorSoundID));
				}
				break;

			default:
		}
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag exitTag = xmw.createTag(TAG_EXIT, true);

		if (overrideName.get() != null && !overrideName.get().isEmpty())
			xmw.addAttribute(exitTag, ATTR_NAME, overrideName.get());

		xmw.addAttribute(exitTag, ATTR_TYPE, type.get().name());
		xmw.addBoolean(exitTag, ATTR_HAS_CALLBACK, hasCallback.get());

		if (markerName.get() != null && !markerName.get().isEmpty())
			xmw.addAttribute(exitTag, ATTR_MARKER, markerName.get());
		if (destMap.get() != null && !destMap.get().isEmpty())
			xmw.addAttribute(exitTag, ATTR_DEST_MAP, destMap.get());
		if (destMarkerName.get() != null && !destMarkerName.get().isEmpty())
			xmw.addAttribute(exitTag, ATTR_DEST_MARKER, destMarkerName.get());
		if (useDestMarkerID.get())
			xmw.addBoolean(exitTag, ATTR_USE_DEST_ID, true);
		if (colliderName.get() != null && !colliderName.get().isEmpty())
			xmw.addAttribute(exitTag, ATTR_TRIGGER, colliderName.get());

		switch (type.get()) {
			case SingleDoor:
				if (door1Name.get() != null && !door1Name.get().isEmpty())
					xmw.addAttribute(exitTag, ATTR_DOOR1, door1Name.get());
				if (lockName.get() != null && !lockName.get().isEmpty())
					xmw.addAttribute(exitTag, ATTR_LOCK, lockName.get());
				xmw.addInt(exitTag, ATTR_DOOR_DIR, ProjectDatabase.EDoorSwings.getID(doorSwing.get()));
				xmw.addHex(exitTag, ATTR_DOOR_SFX, ProjectDatabase.EDoorSounds.getID(doorSound.get()));
				break;

			case DoubleDoor:
				if (door1Name.get() != null && !door1Name.get().isEmpty())
					xmw.addAttribute(exitTag, ATTR_DOOR1, door1Name.get());
				if (door2Name.get() != null && !door2Name.get().isEmpty())
					xmw.addAttribute(exitTag, ATTR_DOOR2, door2Name.get());
				if (lockName.get() != null && !lockName.get().isEmpty())
					xmw.addAttribute(exitTag, ATTR_LOCK, lockName.get());
				xmw.addHex(exitTag, ATTR_DOOR_SFX, ProjectDatabase.EDoorSounds.getID(doorSound.get()));
				break;

			default:
		}

		xmw.printTag(exitTag);
	}

	@Override
	public String toString()
	{
		String name = getName();
		if (name == null)
			return "Invalid Exit";

		if (markerName.get() == null || destMap.get() == null)
			return name;

		return name + (destMap.get().isEmpty() ? "" : " (to " + destMap + ")");
	}

	public String getName()
	{
		if (overrideName.get() == null || overrideName.get().isEmpty()) {
			if (markerName.get() == null || markerName.get().isEmpty())
				return null;

			return "Exit_" + markerName.get().replaceAll("\\s+", "_");
		}
		return overrideName.get().replaceAll("\\s+", "_");
	}
}
