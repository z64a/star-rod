package game.map.scripts.generators;

import static game.map.MapKey.*;

import java.util.Comparator;

import org.w3c.dom.Element;

import common.commands.EditableField;
import common.commands.EditableField.EditableFieldFactory;
import common.commands.EditableField.StandardBoolName;
import game.ProjectDatabase;
import game.map.scripts.GeneratorsPanel;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlSerializable;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public final class Entrance extends Generator implements XmlSerializable
{
	/*
	Call  GetLoadType   ( *Var[0] )
		If  *Var[0]  ==  1
			Exec  EnterSavePoint
			Exec  $Script_BindTriggers
			Return
		EndIf
		Call  GetEntryID    ( *Var[0] )
		Switch  *Var[0]
			Case  ==  ~Entry:DoubleDoor
				Call  UseDoorSounds ( .DoorSounds:Creaky )
				Set   *Var[2]  ~Model:DoorL
				Set   *Var[3]  ~Model:DoorR
				ExecWait  EnterDoubleDoor
			case == ~Entry:SingleDoor
				Set   *Var[2]  ~Model:Door
				Set   *Var[3]  .DoorSwing:Out
				ExecWait  EnterSingleDoor
			case == ~Entry:Teleport
				% do nothing
			default
				Set   *Var[0]  $Script_BindTriggers
				Exec  EnterWalk
				Return
		EndSwitch
		Exec  $Script_BindTriggers
		Return
		End
	 */

	public static enum EntranceType
	{
		// @formatter:off
		Walk			("Walk"),
		SingleDoor		("Single Door"),
		DoubleDoor		("Double Door"),
		VerticalPipe	("Vertical Pipe"),
		HorizontalPipe	("Horizontal Pipe"),
		BlueWarpPipe	("Blue Warp Pipe"),
		Teleport		("Teleport");
		// @formatter:on

		private final String name;

		private EntranceType(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	public static final Comparator<Entrance> TYPE_COMPARATOR = new Comparator<>() {
		@Override
		public int compare(Entrance e1, Entrance e2)
		{
			EntranceType type1 = e1.type.get();
			EntranceType type2 = e2.type.get();
			return type1.compareTo(type2);
		}
	};

	public final EditableField<String> overrideName;
	public final EditableField<EntranceType> type;

	public final EditableField<String> markerName;
	public final EditableField<String> door1Name;
	public final EditableField<String> door2Name;

	public final EditableField<String> doorSound;
	public final EditableField<String> doorSwing;

	public final EditableField<String> pipeCollider;
	public final EditableField<String> warpPipeEntity;
	public final EditableField<Boolean> hasCallback;

	// used during dump
	public transient int markerID;
	public transient int door1ID;
	public transient int door2ID;

	@Override
	public Entrance deepCopy()
	{
		Entrance copy = new Entrance();
		copy.overrideName.copy(overrideName);

		copy.type.copy(type);

		copy.markerName.copy(markerName);
		copy.door1Name.copy(door1Name);
		copy.door2Name.copy(door2Name);
		copy.doorSound.copy(doorSound);
		copy.doorSwing.copy(doorSwing);
		copy.pipeCollider.copy(pipeCollider);
		copy.warpPipeEntity.copy(warpPipeEntity);
		copy.hasCallback.copy(hasCallback);

		return copy;
	}

	private Entrance()
	{
		this(EntranceType.Walk);
	}

	public Entrance(EntranceType type)
	{
		super(GeneratorType.Entrance);

		this.type = EditableFieldFactory.create(type)
			.setCallback((o) -> {
				EntranceInfoPanel.instance().updateFields(this);
			}).setName("Set Entrance Type").build();

		overrideName = EditableFieldFactory.create("")
			.setCallback((o) -> {
				EntranceInfoPanel.instance().updateFields(this);
				GeneratorsPanel.instance().repaintTree();
			}).setName("Set Entrance Name").build();

		markerName = EditableFieldFactory.create("")
			.setCallback((o) -> {
				EntranceInfoPanel.instance().updateFields(this);
			}).setName("Set Entry Marker").build();

		door1Name = EditableFieldFactory.create("")
			.setCallback((o) -> {
				EntranceInfoPanel.instance().updateFields(this);
			}).setName("Set Door Model").build();

		door2Name = EditableFieldFactory.create("")
			.setCallback((o) -> {
				EntranceInfoPanel.instance().updateFields(this);
			}).setName("Set Door Model").build();

		doorSound = EditableFieldFactory.create(ProjectDatabase.EDoorSounds.getName(0))
			.setCallback((o) -> {
				EntranceInfoPanel.instance().updateFields(this);
			}).setName("Set Door Sounds").build();

		doorSwing = EditableFieldFactory.create(ProjectDatabase.EDoorSwings.getName(1))
			.setCallback((o) -> {
				EntranceInfoPanel.instance().updateFields(this);
			}).setName("Set Door Direction").build();

		pipeCollider = EditableFieldFactory.create("")
			.setCallback((o) -> {
				EntranceInfoPanel.instance().updateFields(this);
			}).setName("Set Collider").build();

		warpPipeEntity = EditableFieldFactory.create("")
			.setCallback((o) -> {
				EntranceInfoPanel.instance().updateFields(this);
			}).setName("Set Pipe Entity").build();

		hasCallback = EditableFieldFactory.create(false)
			.setCallback((o) -> {
				EntranceInfoPanel.instance().updateFields(this);
			}).setName(new StandardBoolName("Callback")).build();
	}

	/*
	//TODO
	public Entrance(EntranceType type, ArrayList<ScriptLine> script)
	{
		this(type);
		ScriptLine gotoMap = null;
	
		switch(type)
		{
		case Walk:
			markerID = script.get(1).args[2];
			gotoMap = script.get(3);
			break;
	
		case SingleDoor:
			doorSound = DataConstants.DoorSoundsType.getName(script.get(2).args[1]);
			markerID = script.get(3).args[1];
			door1ID = script.get(5).args[1];
			doorSwing = DataConstants.DoorSwingsType.getName(script.get(6).args[1]);
			gotoMap = script.get(9);
			break;
	
		case DoubleDoor:
			doorSound = DataConstants.DoorSoundsType.getName(script.get(2).args[1]);
			markerID = script.get(3).args[1];
			door1ID = script.get(5).args[1];
			door2ID = script.get(6).args[1];
			gotoMap = script.get(9);
			break;
		}
	}
	 */

	public static Entrance read(XmlReader xmr, Element elem)
	{
		Entrance entrance = new Entrance();
		entrance.fromXML(xmr, elem);
		return entrance;
	}

	@Override
	public void fromXML(XmlReader xmr, Element elem)
	{
		if (xmr.hasAttribute(elem, ATTR_NAME))
			overrideName.set(xmr.getAttribute(elem, ATTR_NAME));

		xmr.requiresAttribute(elem, ATTR_TYPE);
		type.set(EntranceType.valueOf(xmr.getAttribute(elem, ATTR_TYPE)));

		if (xmr.hasAttribute(elem, ATTR_MARKER))
			markerName.set(xmr.getAttribute(elem, ATTR_MARKER));

		if (xmr.hasAttribute(elem, ATTR_HAS_CALLBACK))
			hasCallback.set(xmr.readBoolean(elem, ATTR_HAS_CALLBACK));

		switch (type.get()) {
			case SingleDoor:
				if (xmr.hasAttribute(elem, ATTR_DOOR1))
					door1Name.set(xmr.getAttribute(elem, ATTR_DOOR1));

				if (xmr.hasAttribute(elem, ATTR_DOOR_DIR)) {
					int doorSwingDir = xmr.readInt(elem, ATTR_DOOR_DIR);
					doorSwing.set(ProjectDatabase.EDoorSwings.getName(doorSwingDir));
				}

				if (xmr.hasAttribute(elem, ATTR_DOOR_SFX)) {
					int doorSoundID = xmr.readHex(elem, ATTR_DOOR_SFX);
					doorSound.set(ProjectDatabase.EDoorSounds.getName(doorSoundID));
				}
				break;

			case DoubleDoor:
				if (xmr.hasAttribute(elem, ATTR_DOOR1))
					door1Name.set(xmr.getAttribute(elem, ATTR_DOOR1));

				if (xmr.hasAttribute(elem, ATTR_DOOR2))
					door2Name.set(xmr.getAttribute(elem, ATTR_DOOR2));

				if (xmr.hasAttribute(elem, ATTR_DOOR_SFX)) {
					int doorSoundID = xmr.readHex(elem, ATTR_DOOR_SFX);
					doorSound.set(ProjectDatabase.EDoorSounds.getName(doorSoundID));
				}
				break;

			case HorizontalPipe:
				if (xmr.hasAttribute(elem, ATTR_PIPE_COLLIDER))
					pipeCollider.set(xmr.getAttribute(elem, ATTR_PIPE_COLLIDER));
				break;

			case BlueWarpPipe:
				if (xmr.hasAttribute(elem, ATTR_WARP_PIPE))
					warpPipeEntity.set(xmr.getAttribute(elem, ATTR_WARP_PIPE));
				break;

			default:
		}
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag exitTag = xmw.createTag(TAG_ENTRANCE, true);

		if (overrideName.get() != null && !overrideName.get().isEmpty())
			xmw.addAttribute(exitTag, ATTR_NAME, overrideName.get());

		xmw.addAttribute(exitTag, ATTR_TYPE, type.get().name());
		xmw.addBoolean(exitTag, ATTR_HAS_CALLBACK, hasCallback.get());

		if (markerName.get() != null && !markerName.get().isEmpty())
			xmw.addAttribute(exitTag, ATTR_MARKER, markerName.get());

		switch (type.get()) {
			case SingleDoor:
				if (door1Name.get() != null && !door1Name.get().isEmpty())
					xmw.addAttribute(exitTag, ATTR_DOOR1, door1Name.get());
				xmw.addInt(exitTag, ATTR_DOOR_DIR, ProjectDatabase.EDoorSwings.getID(doorSwing.get()));
				xmw.addHex(exitTag, ATTR_DOOR_SFX, ProjectDatabase.EDoorSounds.getID(doorSound.get()));
				break;

			case DoubleDoor:
				if (door1Name.get() != null && !door1Name.get().isEmpty())
					xmw.addAttribute(exitTag, ATTR_DOOR1, door1Name.get());
				if (door2Name.get() != null && !door2Name.get().isEmpty())
					xmw.addAttribute(exitTag, ATTR_DOOR2, door2Name.get());
				xmw.addHex(exitTag, ATTR_DOOR_SFX, ProjectDatabase.EDoorSounds.getID(doorSound.get()));
				break;

			case HorizontalPipe:
				if (pipeCollider.get() != null && !pipeCollider.get().isEmpty())
					xmw.addAttribute(exitTag, ATTR_PIPE_COLLIDER, pipeCollider.get());
				break;

			case BlueWarpPipe:
				if (warpPipeEntity.get() != null && !warpPipeEntity.get().isEmpty())
					xmw.addAttribute(exitTag, ATTR_WARP_PIPE, warpPipeEntity.get());
				break;

			default:
		}

		xmw.printTag(exitTag);
	}

	@Override
	public String toString()
	{
		if (markerName.get() == null || markerName.get().isEmpty())
			return "Invalid Entrance";

		return type + " from " + markerName.get();
	}

	public String getName()
	{
		if (overrideName.get() == null || overrideName.get().isEmpty()) {
			if (markerName.get() == null || markerName.get().isEmpty())
				return null;

			return "Entrance_" + markerName.get().replaceAll("\\s+", "_");
		}
		return overrideName.get().replaceAll("\\s+", "_");
	}
}
