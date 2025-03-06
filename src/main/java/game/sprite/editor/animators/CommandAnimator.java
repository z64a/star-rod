package game.sprite.editor.animators;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.TreeMap;

import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

import app.SwingUtils;
import game.sprite.RawAnimation;
import game.sprite.SpriteComponent;
import game.sprite.SpritePalette;
import game.sprite.SpriteRaster;
import game.sprite.editor.Editable;
import game.sprite.editor.SpriteEditor;
import game.sprite.editor.animators.CommandAnimatorEditor.GotoPanel;
import game.sprite.editor.animators.CommandAnimatorEditor.LabelPanel;
import game.sprite.editor.animators.CommandAnimatorEditor.LoopPanel;
import game.sprite.editor.animators.CommandAnimatorEditor.SetImagePanel;
import game.sprite.editor.animators.CommandAnimatorEditor.SetNotifyPanel;
import game.sprite.editor.animators.CommandAnimatorEditor.SetPalettePanel;
import game.sprite.editor.animators.CommandAnimatorEditor.SetParentPanel;
import game.sprite.editor.animators.CommandAnimatorEditor.SetPositionPanel;
import game.sprite.editor.animators.CommandAnimatorEditor.SetRotationPanel;
import game.sprite.editor.animators.CommandAnimatorEditor.SetScalePanel;
import game.sprite.editor.animators.CommandAnimatorEditor.SetUnknownPanel;
import game.sprite.editor.animators.CommandAnimatorEditor.WaitPanel;
import util.IterableListModel;
import util.Logger;

public class CommandAnimator implements ComponentAnimator
{
	public final IterableListModel<AnimCommand> commandListModel;
	public final IterableListModel<Label> labels;
	private final SpriteComponent comp;

	private SpriteEditor editor;

	// command list control state
	private int listPosition;

	private boolean ignoreListDataChanges = false;

	public CommandAnimator(SpriteComponent comp)
	{
		this.comp = comp;

		commandListModel = new IterableListModel<>();
		labels = new IterableListModel<>();

		commandListModel.addListDataListener(new ListDataListener() {
			@Override
			public void intervalAdded(ListDataEvent e)
			{
				onListChanged(e);
			}

			@Override
			public void intervalRemoved(ListDataEvent e)
			{
				onListChanged(e);
			}

			@Override
			public void contentsChanged(ListDataEvent e)
			{
				onListChanged(e);
			}

			private void onListChanged(ListDataEvent e)
			{
				if (ignoreListDataChanges)
					return;
				findAllLabels();
			}
		});
	}

	private void findAllLabels()
	{
		labels.clear();
		for (AnimCommand other : commandListModel) {
			if (other instanceof Label lbl) {
				labels.addElement(lbl);
			}
		}
	}

	@Override
	public void bind(SpriteEditor editor, Container commandListContainer, Container commandEditContainer)
	{
		this.editor = editor;

		ignoreListDataChanges = true;
		CommandAnimatorEditor.bind(editor, this, commandListContainer, commandEditContainer);
		ignoreListDataChanges = false;

		findAllLabels();
	}

	private AnimElement setListPosition(int pos)
	{
		boolean validPos = ((0 <= listPosition) && (listPosition < commandListModel.size()));
		if (validPos)
			commandListModel.get(listPosition).highlighted = false;

		listPosition = pos;

		validPos = ((0 <= listPosition) && (listPosition < commandListModel.size()));
		if (validPos) {
			AnimElement elem = commandListModel.get(listPosition);
			elem.highlighted = true;
			return elem;
		}

		return null;
	}

	public void resetAnimation()
	{
		editor.resetAnimation();
	}

	@Override
	public void reset()
	{
		setListPosition(0);

		comp.delayCount = 0;
		comp.repeatCount = 0;

		comp.parent = null;
		comp.sr = null;
		comp.sp = null;
		comp.dx = 0;
		comp.dy = 0;
		comp.dz = 0;
		comp.rx = 0;
		comp.ry = 0;
		comp.rz = 0;
		comp.scaleX = 100;
		comp.scaleY = 100;
		comp.scaleZ = 100;

		comp.complete = false;
		comp.keyframeCount = 0;
	}

	@Override
	public void step()
	{
		if (comp.delayCount > 0) {
			comp.delayCount -= 2;

			if (comp.delayCount > 0)
				return;
		}

		// finished waiting, continue to next frame
		comp.dx = 0;
		comp.dy = 0;
		comp.dz = 0;
		comp.rx = 0;
		comp.ry = 0;
		comp.rz = 0;
		comp.scaleX = 100;
		comp.scaleY = 100;
		comp.scaleZ = 100;

		boolean done = false;
		if (listPosition == 0 && commandListModel.size() > 0) {
			AnimElement cmd = commandListModel.get(listPosition);
			done = cmd.advance();
		}

		// prevent infinite loops from crashing the editor
		// ex: 1002 2000 -- sets the component image forever
		int cmdCounter = 0;
		int maxCommands = 1024;

		while (!done && (listPosition < commandListModel.size()) && cmdCounter++ < maxCommands) {
			AnimElement cmd = setListPosition(listPosition + 1);
			done = (cmd == null) ? true : cmd.advance();
		}
	}

	public void advanceTo(AnimElement elem)
	{
		comp.parentAnimation.advanceTo(this, elem);
	}

	@Override
	public boolean surpassed(AnimElement elem)
	{
		return listPosition >= commandListModel.indexOf(elem);
	}

	@Override
	public void calculateTiming()
	{
		if (commandListModel.size() == 0)
			return;

		for (AnimCommand cmd : commandListModel) {
			cmd.animTime = -1;
		}

		int pos = 0;
		int time = 0;
		int iter = 0;
		boolean done = false;
		int loopCount = 0;

		while (!done) {
			// prevent deadlocking from infinite loops
			if (iter++ > 1024) {
				break;
			}

			AnimCommand cmd = commandListModel.get(pos);
			if ((cmd.animTime != -1) && loopCount == 0)
				break;

			if (cmd instanceof Loop l) {
				int i = commandListModel.indexOf(l.label);
				// label not found
				if (i < 0)
					break;

				if (loopCount == 0) {
					loopCount = l.count;
					// goto label
					pos = i;
					continue;
				}
				else {
					loopCount--;
					if (loopCount != 0) {
						// goto label
						pos = i;
						continue;
					}
				}
			}
			else if (cmd instanceof Goto g) {
				int i = commandListModel.indexOf(g.label);
				// label not found
				if (i < 0)
					break;
				// decorate goto with current anim time
				g.animTime = time;
				// goto label
				pos = i;
				continue;
			}
			else if (cmd instanceof Wait w) {
				// decorate wait with current anim time
				w.animTime = time;
				time += w.count;
			}

			// next command, but be careful not to overrun the list if it has no proper return
			pos++;
			if (pos == commandListModel.size())
				break;
		}
	}

	@Override
	public boolean generate(RawAnimation rawAnim)
	{
		ignoreListDataChanges = true;
		commandListModel.clear();

		TreeMap<Integer, Label> labels = new TreeMap<>();
		Queue<Short> cmdQueue = new LinkedList<>(rawAnim);

		while (!cmdQueue.isEmpty()) {
			switch ((cmdQueue.peek() >> 12) & 0xF) {
				case 0:
					commandListModel.addElement(new Wait(cmdQueue.poll()));
					break;
				case 1:
					commandListModel.addElement(new SetImage(cmdQueue.poll()));
					break;
				case 2: {
					int streamPos = cmdQueue.poll() & 0xFFF;
					if (!labels.containsKey(streamPos))
						labels.put(streamPos, new Label(getLabelName(rawAnim, streamPos)));

					Goto go2 = new Goto(labels.get(streamPos), streamPos);
					commandListModel.addElement(go2);
				}
					break;
				case 3:
					commandListModel.addElement(new SetPosition(cmdQueue.poll(), cmdQueue.poll(), cmdQueue.poll(), cmdQueue.poll()));
					break;
				case 4:
					commandListModel.addElement(new SetRotation(cmdQueue.poll(), cmdQueue.poll(), cmdQueue.poll()));
					break;
				case 5:
					commandListModel.addElement(new SetScale(cmdQueue.poll(), cmdQueue.poll()));
					break;
				case 6:
					commandListModel.addElement(new SetPalette(cmdQueue.poll()));
					break;
				case 7: {
					int streamPos = cmdQueue.poll() & 0xFFF;
					if (!labels.containsKey(streamPos))
						labels.put(streamPos, new Label(getLabelName(rawAnim, streamPos)));

					Loop loop = new Loop(labels.get(streamPos), streamPos, cmdQueue.poll());
					commandListModel.addElement(loop);
				}
					break;
				case 8: {
					short cmd = cmdQueue.poll();
					int type = (cmd & 0xFFF) >> 8;

					switch (type) {
						case 0:
							commandListModel.addElement(new SetUnknown(cmd));
							break;
						case 1:
							commandListModel.addElement(new SetParent(cmd));
							break;
						case 2:
							commandListModel.addElement(new SetNotify(cmd));
							break;
					}
				}
					break;
				default:
					throw new RuntimeException(String.format("Unknown animation command: %04X", cmdQueue.poll()));
			}
		}

		// insert labels
		for (Entry<Integer, Label> e : labels.entrySet()) {
			Label lbl = e.getValue();
			int streamPos = e.getKey();
			int listPos = getListIndex(streamPos);

			if (listPos >= 0) {
				commandListModel.add(listPos, lbl);
			}
			else {
				lbl.labelName = String.format("#%X", streamPos);
				commandListModel.add(getFloorListIndex(streamPos), lbl);
			}
		}

		reset();

		ignoreListDataChanges = false;
		findAllLabels();

		return true;
	}

	// gets the index of the command matching a certain position in the raw stream of shorts
	private int getListIndex(int streamPos)
	{
		int pos = 0;
		for (int i = 0; i < commandListModel.size(); i++) {
			if (streamPos == pos)
				return i;

			AnimCommand cmd = commandListModel.get(i);
			pos += cmd.length();
		}
		return -1;
	}

	// gets the index of the first command preceding or equal to a certain position in the raw stream of shorts
	private int getFloorListIndex(int streamPos)
	{
		int pos = 0;
		for (int i = 0; i < commandListModel.size(); i++) {
			AnimCommand cmd = commandListModel.get(i);
			pos += cmd.length();

			if (streamPos < pos)
				return i;
		}
		return commandListModel.size();
	}

	private String getLabelName(RawAnimation rawAnim, int pos)
	{
		String s = rawAnim.getLabel(pos);
		if (s != null && !s.isEmpty())
			return s;
		if (pos == 0)
			return "Start";
		return String.format("Pos_%X", pos);
	}

	@Override
	public RawAnimation getCommandList()
	{
		RawAnimation rawAnim = new RawAnimation();

		for (int i = 0; i < commandListModel.size(); i++) {
			AnimCommand cmd = commandListModel.get(i);
			if (cmd instanceof Label)
				continue;
			cmd.addTo(rawAnim);
		}

		rawAnim.setLabel(0, "Start");

		int j = 0;
		for (int i = 0; i < commandListModel.size(); i++) {
			AnimCommand cmd = commandListModel.get(i);
			if (cmd instanceof Label lbl) {
				lbl.listPos = j;

				if (j > 0 || !lbl.labelName.equals("Start"))
					rawAnim.setLabel(lbl.listPos, lbl.labelName);
			}
			j += cmd.length();
		}

		return rawAnim;
	}

	// get the list position of a command
	private int findCommand(AnimCommand cmd)
	{
		if (cmd == null)
			return -1;

		for (int i = 0; i < commandListModel.size(); i++) {
			AnimCommand other = commandListModel.get(i);
			if (cmd == other)
				return i;
		}
		return -1;
	}

	// get the compiled position of a command
	private int getCommandOffset(AnimCommand cmd)
	{
		if (cmd == null)
			return -1;

		int j = 0;
		for (int i = 0; i < commandListModel.size(); i++) {
			AnimCommand other = commandListModel.get(i);
			if (cmd == other)
				return j;
			j += other.length();
		}
		return -1;
	}

	private boolean gotoLabel(Label label)
	{
		int i = findCommand(label);
		if (i >= 0) {
			setListPosition(i);
			comp.keyframeCount = 0;
		}
		return (i >= 0);
	}

	public abstract class AnimCommand extends AnimElement
	{
		public AnimCommand()
		{
			super(comp);
		}

		protected abstract int length();

		protected abstract void addTo(List<Short> seq);
	}

	// Used with Goto and Loop to specify targets
	public class Label extends AnimCommand
	{
		public String labelName;

		// serialization only
		public int listPos;

		public Label()
		{
			this("New Label");
		}

		public Label(String name)
		{
			this.labelName = name;
			labels.addElement(this);
		}

		@Override
		public Label copy()
		{
			return new Label(labelName);
		}

		@Override
		public boolean advance()
		{
			return false;
		}

		@Override
		public String getName()
		{
			return "Label";
		}

		@Override
		public String toString()
		{
			return "<html>" + SwingUtils.makeFontTag(SwingUtils.getGreenTextColor())
				+ "Label: <i>" + labelName + "</i></font></html>";
		}

		@Override
		public int length()
		{
			return 0;
		}

		@Override
		public Component getPanel()
		{
			LabelPanel.instance().bind(this);
			return LabelPanel.instance();
		}

		@Override
		public void addTo(List<Short> seq)
		{
			throw new RuntimeException("Tried to add label to command sequence.");
		}
	}

	// 0VVV
	public class Wait extends AnimCommand
	{
		public int count;

		public Wait()
		{
			this((short) 2);
		}

		public Wait(short s0)
		{
			count = (s0 & 0xFFF);
			if (count == 0)
				count = 4095;
		}

		@Override
		public Wait copy()
		{
			return new Wait((short) count);
		}

		@Override
		public boolean advance()
		{
			comp.delayCount = count;
			if (count > 0)
				ownerComp.keyframeCount++;

			return true;
		}

		@Override
		public String getName()
		{
			return "Wait";
		}

		@Override
		public Color getTextColor()
		{
			if (hasError())
				return SwingUtils.getRedTextColor();
			else if (count % 2 == 1)
				return SwingUtils.getYellowTextColor();
			else
				return null;
		}

		@Override
		public String toString()
		{
			if (highlighted && editor.highlightCommand)
				return "<html><b>Wait " + count + "</b></html>";
			else
				return "Wait " + count;
		}

		@Override
		public int length()
		{
			return 1;
		}

		@Override
		public Component getPanel()
		{
			WaitPanel.instance().bind(this);
			return WaitPanel.instance();
		}

		@Override
		public void addTo(List<Short> seq)
		{
			seq.add((short) (0x0000 | (count & 0xFFF)));
		}

		@Override
		public String checkErrorMsg()
		{
			if (count == 0)
				return "Wait Command: invalid duration";

			return null;
		}
	}

	// 1VVV
	// set image -- FFF is valid value for "no image" (may actually need to be < 100`)
	public class SetImage extends AnimCommand
	{
		public SpriteRaster img;

		public SetImage()
		{
			this((short) 0);
		}

		public SetImage(short s0)
		{
			// FFF is valid, so sign extend (implicit cast to int)
			int id = (s0 << 20) >> 20;
			if (id < 0 || id >= comp.parentAnimation.parentSprite.rasters.size())
				img = null;
			else
				img = comp.parentAnimation.parentSprite.rasters.get(id);
		}

		@Override
		public SetImage copy()
		{
			SetImage clone = new SetImage();
			clone.img = img;
			return clone;
		}

		@Override
		public boolean advance()
		{
			ownerComp.sr = img;
			ownerComp.sp = null;
			return false;
		}

		@Override
		public String getName()
		{
			return "Set Raster";
		}

		@Override
		public String toString()
		{
			if (img == null)
				return "Clear Raster";
			else if (img.deleted)
				return "Raster: " + img.name + " (missing)";
			else
				return "Raster: " + img.name;
		}

		@Override
		public int length()
		{
			return 1;
		}

		@Override
		public Component getPanel()
		{
			SetImagePanel.instance().bind(this);
			return SetImagePanel.instance();
		}

		@Override
		public void addTo(List<Short> seq)
		{
			int id = (img == null) ? -1 : img.getIndex();
			seq.add((short) (0x1000 | (id & 0xFFF)));
		}

		@Override
		public void addEditableDownstream(List<Editable> downstream)
		{
			if (img != null)
				downstream.add(img);
		}
	}

	// 2VVV
	// goto -- jump to another position in the list
	public class Goto extends AnimCommand
	{
		public Label label;
		public transient int fixedPos; // only used for generating commands

		public Goto()
		{
			this(null, -1);
		}

		public Goto(Label label, int fixedPos)
		{
			this.label = label;
			this.fixedPos = fixedPos;
		}

		@Override
		public Goto copy()
		{
			return new Goto(label, -1);
		}

		@Override
		public boolean advance()
		{
			if (label == null)
				return true;

			if (findCommand(label) < findCommand(this))
				ownerComp.complete = (ownerComp.keyframeCount < 2);

			gotoLabel(label);
			return false;
		}

		@Override
		public String getName()
		{
			return "Goto";
		}

		@Override
		public Color getTextColor()
		{
			if (hasError())
				return SwingUtils.getRedTextColor();
			else
				return SwingUtils.getBlueTextColor();
		}

		@Override
		public String toString()
		{
			if (label == null)
				return "Goto: (missing)";
			else if (findCommand(label) < 0)
				return "<html>Goto: <i>" + label.labelName + "</i>  (missing)</html>";
			else
				return "<html>Goto: <i>" + label.labelName + "</i></html>";
		}

		@Override
		public int length()
		{
			return 1;
		}

		@Override
		public Component getPanel()
		{
			// if the label is missing from the command, add it
			if (!labels.contains(label))
				labels.addElement(label);

			GotoPanel.instance().bind(this, labels);
			return GotoPanel.instance();
		}

		@Override
		public void addTo(List<Short> seq)
		{
			int pos = 0;

			if (label.labelName.startsWith("#"))
				pos = Integer.parseInt(label.labelName.substring(1), 16);
			else {
				pos = getCommandOffset(label);
				if (pos < 0)
					throw new RuntimeException("Goto is missing label: " + label.labelName);
			}

			seq.add((short) (0x2000 | (pos & 0xFFF)));
		}

		@Override
		public String checkErrorMsg()
		{
			if (label == null || findCommand(label) < 0)
				return "Goto Command: missing label";

			return null;
		}
	}

	// 3VVV XXXX YYYY ZZZZ
	// set position -- flag: doesn't do anything
	public class SetPosition extends AnimCommand
	{
		public boolean unknown;
		public int x, y, z;

		public SetPosition()
		{
			this((short) 0, (short) 0, (short) 0, (short) 0);
		}

		public SetPosition(short s0, short s1, short s2, short s3)
		{
			unknown = (s0 & 0xFFF) == 1;
			x = s1;
			y = s2;
			z = s3;
		}

		@Override
		public SetPosition copy()
		{
			SetPosition clone = new SetPosition();
			clone.x = x;
			clone.y = y;
			clone.z = z;
			clone.unknown = unknown;
			return clone;
		}

		@Override
		public boolean advance()
		{
			ownerComp.dx = x;
			ownerComp.dy = y;
			ownerComp.dz = z;
			return false;
		}

		@Override
		public String getName()
		{
			return "Set Position";
		}

		@Override
		public String toString()
		{
			return String.format("Position: (%d, %d, %d)", x, y, z);
		}

		@Override
		public int length()
		{
			return 4;
		}

		@Override
		public Component getPanel()
		{
			SetPositionPanel.instance().bind(this);
			return SetPositionPanel.instance();
		}

		@Override
		public void addTo(List<Short> seq)
		{
			seq.add(unknown ? (short) 0x3001 : (short) 0x3000);
			seq.add((short) x);
			seq.add((short) y);
			seq.add((short) z);
		}
	}

	// 4XXX YYYY ZZZZ
	// set rotation (euler angles)
	public class SetRotation extends AnimCommand
	{
		public int x, y, z;

		public SetRotation()
		{
			this((short) 0, (short) 0, (short) 0);
		}

		public SetRotation(short s0, short s1, short s2)
		{
			// sign extend (implicit cast to int)
			x = ((s0 << 20) >> 20);
			y = s1;
			z = s2;
		}

		@Override
		public SetRotation copy()
		{
			return new SetRotation((short) x, (short) y, (short) z);
		}

		@Override
		public boolean advance()
		{
			ownerComp.rx = x;
			ownerComp.ry = y;
			ownerComp.rz = z;
			return false;
		}

		@Override
		public String getName()
		{
			return "Set Rotation";
		}

		@Override
		public String toString()
		{
			return String.format("Rotation: (%d, %d, %d)", x, y, z);
		}

		@Override
		public int length()
		{
			return 3;
		}

		@Override
		public Component getPanel()
		{
			SetRotationPanel.instance().bind(this);
			return SetRotationPanel.instance();
		}

		@Override
		public void addTo(List<Short> seq)
		{
			seq.add((short) (0x4000 | (x & 0xFFF)));
			seq.add((short) y);
			seq.add((short) z);
		}
	}

	// 5VVV UUUU
	// set scale (%)
	public class SetScale extends AnimCommand
	{
		public int type;
		public int scalePercent;

		public SetScale()
		{
			this((short) 0, (short) 100);
		}

		public SetScale(short s0, short s1)
		{
			type = (s0 & 0xFFF);
			scalePercent = s1;
		}

		@Override
		public SetScale copy()
		{
			return new SetScale((short) type, (short) scalePercent);
		}

		@Override
		public boolean advance()
		{
			switch (type) {
				case 0:
					ownerComp.scaleX = scalePercent;
					ownerComp.scaleY = scalePercent;
					ownerComp.scaleZ = scalePercent;
					break;
				case 1:
					ownerComp.scaleX = scalePercent;
					break;
				case 2:
					ownerComp.scaleY = scalePercent;
				case 3:
					ownerComp.scaleZ = scalePercent;
					break;
				default:
					throw new RuntimeException(String.format("Invalid scale command type: %X", type));
			}
			return false;
		}

		@Override
		public String getName()
		{
			return "Set Scale";
		}

		@Override
		public String toString()
		{
			String typeName;
			switch (type) {
				case 0:
					typeName = "All";
					break;
				case 1:
					typeName = "X";
					break;
				case 2:
					typeName = "Y";
				case 3:
					typeName = "Z";
					break;
				default:
					throw new RuntimeException(String.format("Invalid scale command type: %X", type));
			}
			return "Scale " + typeName + ": " + scalePercent + "%";
		}

		@Override
		public int length()
		{
			return 2;
		}

		@Override
		public Component getPanel()
		{
			SetScalePanel.instance().bind(this);
			return SetScalePanel.instance();
		}

		@Override
		public void addTo(List<Short> seq)
		{
			seq.add((short) (0x5000 | (type & 0xFFF)));
			seq.add((short) scalePercent);
		}
	}

	// 6VVV
	// use palette
	public class SetPalette extends AnimCommand
	{
		public SpritePalette pal;

		public SetPalette()
		{
			this((short) 0);
		}

		public SetPalette(short s0)
		{
			// FFF is valid, so sign extend (implicit cast to int)
			int id = (s0 << 20) >> 20;
			pal = (id < 0) ? null : comp.parentAnimation.parentSprite.palettes.get(id);
		}

		@Override
		public SetPalette copy()
		{
			SetPalette clone = new SetPalette();
			clone.pal = pal;
			return clone;
		}

		@Override
		public boolean advance()
		{
			ownerComp.sp = pal;
			return false;
		}

		@Override
		public String getName()
		{
			return "Set Palette";
		}

		@Override
		public String toString()
		{
			if (pal == null)
				return "Default Palette";
			else if (pal.deleted)
				return "Palette: " + pal + " (missing)";
			else
				return "Palette: " + pal;
		}

		@Override
		public int length()
		{
			return 1;
		}

		@Override
		public Component getPanel()
		{
			SetPalettePanel.instance().bind(this);
			return SetPalettePanel.instance();
		}

		@Override
		public void addTo(List<Short> seq)
		{
			int id = (pal == null) ? -1 : pal.getIndex();
			seq.add((short) (0x6000 | (id & 0xFFF)));
		}

		@Override
		public void addEditableDownstream(List<Editable> downstream)
		{
			if (pal != null)
				downstream.add(pal);
		}
	}

	// 7VVV UUUU
	// loop
	public class Loop extends AnimCommand
	{
		public Label label;
		public transient int fixedPos; // only used for generating commands
		public int count;

		public Loop()
		{
			this(null, -1, (short) 3);
		}

		public Loop(Label label, int fixedPos, short s1)
		{
			this.label = label;
			this.fixedPos = fixedPos;
			count = s1;
		}

		@Override
		public Loop copy()
		{
			return new Loop(label, -1, (short) count);
		}

		@Override
		public boolean advance()
		{
			if (comp.repeatCount == 0) {
				comp.repeatCount = count;
				gotoLabel(label);
			}
			else {
				comp.repeatCount--;
				if (comp.repeatCount != 0)
					gotoLabel(label);
			}
			return false;
		}

		@Override
		public String getName()
		{
			return "Loop";
		}

		@Override
		public Color getTextColor()
		{
			if (hasError())
				return SwingUtils.getRedTextColor();
			else
				return SwingUtils.getBlueTextColor();
		}

		@Override
		public String toString()
		{
			if (label == null)
				return "Repeat: (missing) (x" + count + ")";
			else if (findCommand(label) < 0)
				return "<html>Repeat: <i>" + label.labelName + "</i>  (missing) (x" + count + ")</html>";
			else
				return "<html>Repeat: <i>" + label.labelName + "</i>  (x" + count + ")</html>";
		}

		@Override
		public int length()
		{
			return 2;
		}

		@Override
		public Component getPanel()
		{
			LoopPanel.instance().bind(this, labels);
			return LoopPanel.instance();
		}

		@Override
		public void addTo(List<Short> seq)
		{
			int pos = 0;

			if (label.labelName.startsWith("#"))
				pos = Integer.parseInt(label.labelName.substring(1), 16);
			else {
				pos = getCommandOffset(label);
				if (pos < 0)
					throw new RuntimeException("Repeat is missing label: " + label.labelName);
			}

			seq.add((short) (0x7000 | (pos & 0xFFF)));
			seq.add((short) count);
		}

		@Override
		public String checkErrorMsg()
		{
			if (label == null || findCommand(label) < 0)
				return "Loop Command: missing label";

			return null;
		}
	}

	// 80XX
	public class SetUnknown extends AnimCommand
	{
		public int value;

		public SetUnknown()
		{
			this((short) 0);
		}

		public SetUnknown(short s0)
		{
			value = (s0 & 0xFF);
		}

		@Override
		public SetUnknown copy()
		{
			SetUnknown clone = new SetUnknown();
			clone.value = value;
			return clone;
		}

		@Override
		public boolean advance()
		{
			return false;
		}

		@Override
		public String getName()
		{
			return "Set Unknown";
		}

		@Override
		public String toString()
		{
			return "Set Unknown: " + value;
		}

		@Override
		public int length()
		{
			return 1;
		}

		@Override
		public Component getPanel()
		{
			SetUnknownPanel.instance().bind(this);
			return SetUnknownPanel.instance();
		}

		@Override
		public void addTo(List<Short> seq)
		{
			seq.add((short) (0x8000 | (value & 0xFF)));
		}
	}

	// 81XX parent to component XX
	public class SetParent extends AnimCommand
	{
		public SpriteComponent comp;

		public SetParent()
		{
			this((short) 0);
		}

		public SetParent(short s0)
		{
			int id = (s0 & 0xFF);
			if (id < ownerComp.parentAnimation.components.size())
				comp = ownerComp.parentAnimation.components.get(id);
		}

		@Override
		public SetParent copy()
		{
			SetParent clone = new SetParent();
			clone.comp = comp;
			return clone;
		}

		@Override
		public boolean advance()
		{
			ownerComp.parent = comp;
			return false;
		}

		@Override
		public String getName()
		{
			return "Set Parent";
		}

		@Override
		public String toString()
		{
			if (comp == null)
				return "Parent: (missing)";
			else if (comp.deleted)
				return "Parent: " + comp.name + " (missing)";
			else
				return "Parent: " + comp.name;
		}

		@Override
		public int length()
		{
			return 1;
		}

		@Override
		public Component getPanel()
		{
			SetParentPanel.instance().bind(this);
			return SetParentPanel.instance();
		}

		@Override
		public void addTo(List<Short> seq)
		{
			if (comp != null)
				seq.add((short) (0x8100 | (comp.getIndex() & 0xFF)));
			else {
				Logger.logError("No parent selected for SetParent in " + ownerComp);
				seq.add((short) (0x8100));
			}
		}

		@Override
		public void addEditableDownstream(List<Editable> downstream)
		{
			if (comp != null)
				downstream.add(comp);
		}

		@Override
		public String checkErrorMsg()
		{
			if (comp == null)
				return "SetParent Command: undefined parent";

			if (comp == ownerComp)
				return "SetParent Command: parented to itself";

			return null;
		}
	}

	// 82VV
	public class SetNotify extends AnimCommand
	{
		public int value;

		public SetNotify()
		{
			this((short) 0);
		}

		public SetNotify(short s0)
		{
			value = (s0 & 0xFF);
		}

		@Override
		public SetNotify copy()
		{
			SetNotify clone = new SetNotify();
			clone.value = value;
			return clone;
		}

		@Override
		public boolean advance()
		{
			return false;
		}

		@Override
		public String getName()
		{
			return "Set Notify";
		}

		@Override
		public String toString()
		{
			return "Set Notify: " + value;
		}

		@Override
		public int length()
		{
			return 1;
		}

		@Override
		public Component getPanel()
		{
			SetNotifyPanel.instance().bind(this);
			return SetNotifyPanel.instance();
		}

		@Override
		public void addTo(List<Short> seq)
		{
			seq.add((short) (0x8200 | (value & 0xFF)));
		}
	}
}
