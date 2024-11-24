package game.sprite.editor.animators;

import java.awt.Component;
import java.awt.Container;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.TreeMap;

import javax.swing.DefaultListModel;

import app.SwingUtils;
import game.sprite.RawAnimation;
import game.sprite.SpriteComponent;
import game.sprite.SpritePalette;
import game.sprite.SpriteRaster;
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
import util.Logger;

public class CommandAnimator implements ComponentAnimator
{
	public DefaultListModel<AnimCommand> commandListModel = new DefaultListModel<>();
	public DefaultListModel<Label> labels = new DefaultListModel<>();
	private final SpriteComponent comp;

	private SpriteEditor editor;

	// command list control state
	private int listPosition;

	public CommandAnimator(SpriteComponent comp)
	{
		this.comp = comp;
	}

	@Override
	public void bind(SpriteEditor editor, Container commandListContainer, Container commandEditContainer)
	{
		this.editor = editor;
		CommandAnimatorEditor.bind(editor, this, commandListContainer, commandEditContainer);
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
	public boolean generate(RawAnimation rawAnim)
	{
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
					int pos = cmdQueue.poll() & 0xFFF;
					if (!labels.containsKey(pos))
						labels.put(pos, new Label(getLabelName(rawAnim, pos)));

					Goto go2 = new Goto(labels.get(pos), pos);
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
					int pos = cmdQueue.poll() & 0xFFF;
					if (!labels.containsKey(pos))
						labels.put(pos, new Label(getLabelName(rawAnim, pos)));

					Loop loop = new Loop(labels.get(pos), pos, cmdQueue.poll());
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
			int absPos = e.getKey();
			int listPos = getListIndex(absPos);
			if (listPos >= 0) {
				commandListModel.add(listPos, lbl);
			}
			else {
				lbl.labelName = String.format("#%X", absPos);
				commandListModel.add(getFloorListIndex(absPos), lbl);
			}
		}

		reset();
		return true;
	}

	@Override
	public void cleanDeletedRasters()
	{
		for (int i = 0; i < commandListModel.size(); i++) {
			AnimCommand cmd = commandListModel.get(i);
			if (cmd instanceof SetImage setImg) {
				if (setImg.img != null && setImg.img.deleted)
					setImg.img = null;
			}
		}

		if (comp.sr != null && comp.sr.deleted)
			comp.sr = null;
	}

	@Override
	public void cleanDeletedPalettes()
	{
		for (int i = 0; i < commandListModel.size(); i++) {
			AnimCommand cmd = commandListModel.get(i);
			if (cmd instanceof SetPalette setPal) {
				if (setPal.pal != null && setPal.pal.deleted)
					setPal.pal = null;
			}
		}

		if (comp.sp != null && comp.sp.deleted)
			comp.sp = null;
	}

	// gets the (logical) command list index for the real position in the command list
	private int getListIndex(int pos)
	{
		int j = 0;
		for (int i = 0; i < commandListModel.size(); i++) {
			AnimCommand cmd = commandListModel.get(i);
			if (j == pos)
				return i;
			j += cmd.length();
		}
		return -1;
	}

	private int getFloorListIndex(int pos)
	{
		int floor = 0;
		int j = 0;
		for (int i = 0; i < commandListModel.size(); i++) {
			AnimCommand cmd = commandListModel.get(i);
			if (j > floor)
				return floor;
			floor = j;
			j += cmd.length();
		}
		return floor;
	}

	private String getLabelName(RawAnimation rawAnim, int pos)
	{
		String s = rawAnim.getLabel(pos);
		if (s != null && !s.isEmpty())
			return s;
		if (pos == 0)
			return "Start";
		return String.format("Pos %X", pos);
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

		protected abstract Component getPanel();
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
			LabelPanel.instance().set(this);
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
			WaitPanel.instance().set(this);
			return WaitPanel.instance();
		}

		@Override
		public void addTo(List<Short> seq)
		{
			seq.add((short) (0x0000 | (count & 0xFFF)));
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
		public String toString()
		{
			return (img == null) ? "Clear Raster" : "Use Raster: " + img;
		}

		@Override
		public int length()
		{
			return 1;
		}

		@Override
		public Component getPanel()
		{
			SetImagePanel.instance().set(this);
			return SetImagePanel.instance();
		}

		@Override
		public void addTo(List<Short> seq)
		{
			int id = (img == null) ? -1 : img.getIndex();
			seq.add((short) (0x1000 | (id & 0xFFF)));
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
		public String toString()
		{
			if (label == null)
				return "<html>" + SwingUtils.makeFontTag(SwingUtils.getRedTextColor())
					+ "Goto:  (missing)</font></html>";
			else if (findCommand(label) < 0)
				return "<html>" + SwingUtils.makeFontTag(SwingUtils.getRedTextColor())
					+ "Goto: <i>" + label.labelName + "</i>  (missing)</font></html>";
			else
				return "<html>" + SwingUtils.makeFontTag(SwingUtils.getBlueTextColor())
					+ "Goto: <i>" + label.labelName + "</i></font></html>";
		}

		@Override
		public int length()
		{
			return 1;
		}

		@Override
		public Component getPanel()
		{
			GotoPanel.instance().set(this, labels);
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
	}

	// 3VVV XXXX YYYY ZZZZ
	// set position -- flag: doesn't seem to do ANYTHING! TODO
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
		public String toString()
		{
			return "Offset Position";
		}

		@Override
		public int length()
		{
			return 4;
		}

		@Override
		public Component getPanel()
		{
			SetPositionPanel.instance().set(this);
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
		public String toString()
		{
			return "Set Rotation";
		}

		@Override
		public int length()
		{
			return 3;
		}

		@Override
		public Component getPanel()
		{
			SetRotationPanel.instance().set(this);
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
		public String toString()
		{
			return "Set Scale";
		}

		@Override
		public int length()
		{
			return 2;
		}

		@Override
		public Component getPanel()
		{
			SetScalePanel.instance().set(this);
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
			assert (pal != null);
			ownerComp.sp = pal;
			return false;
		}

		@Override
		public String toString()
		{
			return (pal == null) ? "Use Default Palette" : "Use Palette: " + pal;
		}

		@Override
		public int length()
		{
			return 1;
		}

		@Override
		public Component getPanel()
		{
			SetPalettePanel.instance().set(this);
			return SetPalettePanel.instance();
		}

		@Override
		public void addTo(List<Short> seq)
		{
			int id = (pal == null) ? -1 : pal.getIndex();
			seq.add((short) (0x6000 | (id & 0xFFF)));
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
		public String toString()
		{
			if (label == null)
				return "<html>" + SwingUtils.makeFontTag(SwingUtils.getRedTextColor())
					+ "Repeat:  (missing) (x" + count + ")</font></html>";
			else if (findCommand(label) < 0)
				return "<html>" + SwingUtils.makeFontTag(SwingUtils.getRedTextColor())
					+ "Repeat: <i>" + label.labelName + "</i>  (missing) (x" + count + ")</font></html>";
			else
				return "<html>" + SwingUtils.makeFontTag(SwingUtils.getBlueTextColor())
					+ "Repeat: <i>" + label.labelName + "</i>  (x" + count + ")</font></html>";
		}

		@Override
		public int length()
		{
			return 2;
		}

		@Override
		public Component getPanel()
		{
			LoopPanel.instance().set(this, labels);
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
		public String toString()
		{
			return "Set unknown: " + value;
		}

		@Override
		public int length()
		{
			return 1;
		}

		@Override
		public Component getPanel()
		{
			SetUnknownPanel.instance().set(this);
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
		public String toString()
		{
			return "Set parent: " + comp;
		}

		@Override
		public int length()
		{
			return 1;
		}

		@Override
		public Component getPanel()
		{
			SetParentPanel.instance().set(this);
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
			SetNotifyPanel.instance().set(this);
			return SetNotifyPanel.instance();
		}

		@Override
		public void addTo(List<Short> seq)
		{
			seq.add((short) (0x8200 | (value & 0xFF)));
		}
	}
}
