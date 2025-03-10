package game.sprite.editor.animators.command;

import java.awt.Container;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.TreeMap;

import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import game.sprite.RawAnimation;
import game.sprite.Sprite;
import game.sprite.SpriteComponent;
import game.sprite.SpritePalette;
import game.sprite.SpriteRaster;
import game.sprite.editor.SpriteEditor;
import game.sprite.editor.animators.AnimElement;
import game.sprite.editor.animators.AnimElement.AdvanceResult;
import game.sprite.editor.animators.ComponentAnimator;
import game.sprite.editor.animators.ToKeyframesConverter;
import game.sprite.editor.animators.keyframe.AnimKeyframe;
import game.sprite.editor.animators.keyframe.KeyframeAnimator;
import util.IterableListModel;
import util.Logger;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlWriter;

public class CommandAnimator implements ComponentAnimator
{
	// prevent infinite loops from crashing the editor
	private static final int MAX_ITERATIONS = 1024;

	public final IterableListModel<AnimCommand> commands;
	public final IterableListModel<Label> labels;
	public final SpriteComponent comp;

	private SpriteEditor editor;

	// command list control state
	private int listPosition;

	private boolean ignoreListDataChanges = false;

	public CommandAnimator(SpriteComponent comp)
	{
		this.comp = comp;

		commands = new IterableListModel<>();
		labels = new IterableListModel<>();

		commands.addListDataListener(new ListDataListener() {
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

	/**
	 * Creates a copy of another CommandAnimator, along with all commands
	 * Updates references within commands to point to corresponding (copied) commands/components
	 * @param comp SpriteComponent which this new animator belongs to
	 * @param other CommandAnimator to copy commands from
	 */
	public CommandAnimator(SpriteComponent comp, CommandAnimator other)
	{
		this(comp);

		// easier to compile to bytes and reconstruct a copy than to deal with the web of
		// object references that would need to be properly updated in the new list
		RawAnimation raw = other.getCommandList();
		generateFrom(raw);
	}

	public void findAllLabels()
	{
		labels.clear();
		for (AnimCommand other : commands) {
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

		if (comp != null)
			CommandAnimatorEditor.instance().restoreSelection(comp.lastSelectedCommand);

		ignoreListDataChanges = false;

		findAllLabels();
	}

	@Override
	public void unbind()
	{
		if (comp != null)
			comp.lastSelectedCommand = CommandAnimatorEditor.instance().getSelection();
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
		comp.gotoTime = 0;
	}

	@Override
	public void step()
	{
		// wait for delay to end
		if (comp.delayCount > 0) {
			comp.delayCount -= 2;

			if (comp.delayCount <= 0)
				setListPosition(listPosition + 1);
			else
				return;
		}

		// after done waiting, continue to next set of commands
		comp.dx = 0;
		comp.dy = 0;
		comp.dz = 0;
		comp.rx = 0;
		comp.ry = 0;
		comp.rz = 0;
		comp.scaleX = 100;
		comp.scaleY = 100;
		comp.scaleZ = 100;

		for (int iterations = 0; iterations < MAX_ITERATIONS; iterations++) {
			// safety check: out of range list position
			if (listPosition < 0 || listPosition >= commands.size())
				return;

			AnimElement cmd = commands.get(listPosition);
			AdvanceResult result = cmd.apply();
			if (result == AdvanceResult.BLOCK)
				return;

			if (result == AdvanceResult.NEXT)
				setListPosition(listPosition + 1);
		}
	}

	protected void advanceTo(AnimElement elem)
	{
		comp.parentAnimation.advanceTo(this, elem);
	}

	private void setListPosition(int newPos)
	{
		if ((listPosition >= 0) && (listPosition < commands.size()))
			commands.get(listPosition).highlighted = false;

		listPosition = newPos;

		if ((listPosition >= 0) && (listPosition < commands.size()))
			commands.get(listPosition).highlighted = true;
	}

	protected boolean gotoLabel(Label label)
	{
		int i = findCommand(label);
		if (i >= 0) {
			setListPosition(i);
			comp.gotoTime = 0;
		}
		return (i >= 0);
	}

	// get the list position of a command
	protected int findCommand(AnimCommand cmd)
	{
		if (cmd == null)
			return -1;

		for (int i = 0; i < commands.size(); i++) {
			AnimCommand other = commands.get(i);
			if (cmd == other)
				return i;
		}
		return -1;
	}

	// get the compiled position of a command
	protected int getCommandOffset(AnimCommand cmd)
	{
		if (cmd == null)
			return -1;

		int j = 0;
		for (int i = 0; i < commands.size(); i++) {
			AnimCommand other = commands.get(i);
			if (cmd == other)
				return j;
			j += other.length();
		}
		return -1;
	}

	public void useCommands(List<AnimCommand> newCommands)
	{
		commands.clear();
		for (AnimCommand cmd : newCommands)
			commands.addElement(cmd);

		reset();
		findAllLabels();
	}

	public List<AnimKeyframe> toKeyframes(KeyframeAnimator animator)
	{
		ToKeyframesConverter converter = new ToKeyframesConverter(this, animator);
		return converter.getKeyframes();
	}

	@Override
	public boolean surpassed(AnimElement elem)
	{
		return listPosition >= commands.indexOf(elem);
	}

	@Override
	public void calculateTiming()
	{
		if (commands.size() == 0)
			return;

		for (AnimCommand cmd : commands) {
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

			AnimCommand cmd = commands.get(pos);
			if ((cmd.animTime != -1) && loopCount == 0)
				break;

			if (cmd instanceof Loop l) {
				int i = commands.indexOf(l.label);
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
				int i = commands.indexOf(g.label);
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
			if (pos == commands.size())
				break;
		}
	}

	@Override
	public RawAnimation getCommandList()
	{
		RawAnimation rawAnim = new RawAnimation();

		for (int i = 0; i < commands.size(); i++) {
			AnimCommand cmd = commands.get(i);
			if (cmd instanceof Label)
				continue;
			cmd.addTo(rawAnim);
		}

		rawAnim.setLabel(0, "Start");

		int j = 0;
		for (int i = 0; i < commands.size(); i++) {
			AnimCommand cmd = commands.get(i);
			if (cmd instanceof Label lbl) {
				lbl.listPos = j;

				if (j > 0 || !lbl.name.equals("Start"))
					rawAnim.setLabel(lbl.listPos, lbl.name);
			}
			j += cmd.length();
		}

		return rawAnim;
	}

	@Override
	public boolean generateFrom(RawAnimation rawAnim)
	{
		ignoreListDataChanges = true;
		commands.clear();

		TreeMap<Integer, Label> labels = new TreeMap<>();
		Queue<Short> cmdQueue = new LinkedList<>(rawAnim);

		while (!cmdQueue.isEmpty()) {
			switch ((cmdQueue.peek() >> 12) & 0xF) {
				case 0:
					commands.addElement(new Wait(this, cmdQueue.poll()));
					break;
				case 1:
					commands.addElement(new SetImage(this, cmdQueue.poll()));
					break;
				case 2: {
					int streamPos = cmdQueue.poll() & 0xFFF;
					if (!labels.containsKey(streamPos))
						labels.put(streamPos, new Label(this, getLabelName(rawAnim, streamPos)));

					Goto go2 = new Goto(this, labels.get(streamPos), streamPos);
					commands.addElement(go2);
				}
					break;
				case 3:
					commands.addElement(new SetPosition(this, cmdQueue.poll(), cmdQueue.poll(), cmdQueue.poll(), cmdQueue.poll()));
					break;
				case 4:
					commands.addElement(new SetRotation(this, cmdQueue.poll(), cmdQueue.poll(), cmdQueue.poll()));
					break;
				case 5:
					commands.addElement(new SetScale(this, cmdQueue.poll(), cmdQueue.poll()));
					break;
				case 6:
					commands.addElement(new SetPalette(this, cmdQueue.poll()));
					break;
				case 7: {
					int streamPos = cmdQueue.poll() & 0xFFF;
					if (!labels.containsKey(streamPos))
						labels.put(streamPos, new Label(this, getLabelName(rawAnim, streamPos)));

					Loop loop = new Loop(this, labels.get(streamPos), streamPos, cmdQueue.poll());
					commands.addElement(loop);
				}
					break;
				case 8: {
					short cmd = cmdQueue.poll();
					int type = (cmd & 0xFFF) >> 8;

					switch (type) {
						case 0:
							commands.addElement(new SetUnknown(this, cmd));
							break;
						case 1:
							commands.addElement(new SetParent(this, cmd));
							break;
						case 2:
							commands.addElement(new SetNotify(this, cmd));
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
				commands.add(listPos, lbl);
			}
			else {
				lbl.name = String.format("#%X", streamPos);
				commands.add(getFloorListIndex(streamPos), lbl);
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
		for (int i = 0; i < commands.size(); i++) {
			if (streamPos == pos)
				return i;

			AnimCommand cmd = commands.get(i);
			pos += cmd.length();
		}
		return -1;
	}

	// gets the index of the first command preceding or equal to a certain position in the raw stream of shorts
	private int getFloorListIndex(int streamPos)
	{
		int pos = 0;
		for (int i = 0; i < commands.size(); i++) {
			AnimCommand cmd = commands.get(i);
			pos += cmd.length();

			if (streamPos < pos)
				return i;
		}
		return commands.size();
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
	public void toXML(XmlWriter xmw)
	{
		for (AnimCommand cmd : commands)
			cmd.toXML(xmw);
	}

	@Override
	public void fromXML(XmlReader xmr, Element compElem)
	{
		for (Node child = compElem.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (child instanceof Element elem) {
				switch (elem.getNodeName()) {
					case "Label":
						Label lbl = new Label(this);
						lbl.fromXML(xmr, elem);
						commands.addElement(lbl);
						break;
					case "Goto":
						Goto go2 = new Goto(this);
						go2.fromXML(xmr, elem);
						commands.addElement(go2);
						break;
					case "Loop":
						Loop loop = new Loop(this);
						loop.fromXML(xmr, elem);
						commands.addElement(loop);
						break;
					case "Wait":
						Wait wait = new Wait(this);
						wait.fromXML(xmr, elem);
						commands.addElement(wait);
						break;
					case "SetRaster":
						SetImage setImg = new SetImage(this);
						setImg.fromXML(xmr, elem);
						commands.addElement(setImg);
						break;
					case "SetPalette":
						SetPalette setPal = new SetPalette(this);
						setPal.fromXML(xmr, elem);
						commands.addElement(setPal);
						break;
					case "SetParent":
						SetParent setPar = new SetParent(this);
						setPar.fromXML(xmr, elem);
						commands.addElement(setPar);
						break;
					case "SetPos":
						SetPosition setPos = new SetPosition(this);
						setPos.fromXML(xmr, elem);
						commands.addElement(setPos);
						break;
					case "SetRot":
						SetRotation setRot = new SetRotation(this);
						setRot.fromXML(xmr, elem);
						commands.addElement(setRot);
						break;
					case "SetScale":
						SetScale setScale = new SetScale(this);
						setScale.fromXML(xmr, elem);
						commands.addElement(setScale);
						break;
					case "SetNotify":
						SetNotify setNotify = new SetNotify(this);
						setNotify.fromXML(xmr, elem);
						commands.addElement(setNotify);
						break;
					case "SetUnknown":
						SetUnknown setUnknown = new SetUnknown(this);
						setUnknown.fromXML(xmr, elem);
						commands.addElement(setUnknown);
						break;
				}
			}
		}
	}

	@Override
	public void updateReferences(
		HashMap<String, SpriteRaster> imgMap,
		HashMap<String, SpritePalette> palMap,
		HashMap<String, SpriteComponent> compMap)
	{
		Sprite spr = comp.parentAnimation.parentSprite;

		// create hashmaps for lookup-by-name
		HashMap<String, Label> labelMap = new HashMap<>();

		for (AnimCommand cmd : commands) {
			if (cmd instanceof Label lbl) {
				if (lbl.name != null && !lbl.name.isBlank())
					labelMap.putIfAbsent(lbl.name, lbl);
			}
		}

		// determine position of each command in the stream
		TreeMap<Integer, AnimCommand> posMap = new TreeMap<>();
		int pos = 0;
		for (AnimCommand cmd : commands) {
			posMap.put(pos, cmd);
			pos += cmd.length();
		}

		// create a copy of the command list we can iterate over so we can safely modify the original during iteration
		ArrayList<AnimCommand> listCopy = new ArrayList<>(commands.size());
		for (AnimCommand cmd : commands)
			listCopy.add(cmd);

		// iterate through commands and replace temporary stringy references with object references
		for (AnimCommand cmd : listCopy) {
			if (cmd instanceof Goto go2) {
				if (go2.fixedPos != Goto.NO_FIXED_POS) {
					Entry<Integer, AnimCommand> e = posMap.floorEntry(go2.fixedPos);
					if (e != null) {
						AnimCommand target = e.getValue();
						if (target instanceof Label lbl) {
							go2.label = lbl;
						}
						else {
							int targetIndex = commands.indexOf(target);

							Label lbl = new Label(this);
							lbl.name = String.format("#%X", go2.fixedPos);
							commands.add(targetIndex, lbl);

							go2.label = lbl;
						}
					}
				}
				else {
					go2.label = labelMap.get(go2.labelName);
				}
			}
			else if (cmd instanceof Loop loop) {
				if (loop.fixedPos != Loop.NO_FIXED_POS) {
					Entry<Integer, AnimCommand> e = posMap.floorEntry(loop.fixedPos);
					if (e != null) {
						AnimCommand target = e.getValue();
						if (target instanceof Label lbl) {
							loop.label = lbl;
						}
						else {
							int targetIndex = commands.indexOf(target);

							Label lbl = new Label(this);
							lbl.name = String.format("#%X", loop.fixedPos);
							commands.add(targetIndex, lbl);

							loop.label = lbl;
						}
					}
				}
				else {
					loop.label = labelMap.get(loop.labelName);
				}
			}
			else if (cmd instanceof SetImage setImg) {
				if (setImg.imgName != null) {
					setImg.img = imgMap.get(setImg.imgName);
				}
				else if (setImg.imgIndex == -1) {
					setImg.img = null;
				}
				else if (setImg.imgIndex >= 0 && setImg.imgIndex < spr.rasters.size()) {
					setImg.img = spr.rasters.get(setImg.imgIndex);
				}
				else {
					Logger.logfWarning("SetRaster command references out of bounds index: %02X", setImg.imgIndex);
				}
			}
			else if (cmd instanceof SetPalette setPal) {
				if (setPal.palName != null) {
					setPal.pal = palMap.get(setPal.palName);
				}
				else if (setPal.palIndex == -1) {
					setPal.pal = null;
				}
				else if (setPal.palIndex >= 0 && setPal.palIndex < spr.palettes.size()) {
					setPal.pal = spr.palettes.get(setPal.palIndex);
				}
				else {
					Logger.logfWarning("SetPalette command references out of bounds index: %02X", setPal.palIndex);
				}
			}
			else if (cmd instanceof SetParent setParent) {
				if (setParent.parName != null) {
					setParent.parent = compMap.get(setParent.parName);
				}
				else if (setParent.parIndex == -1) {
					setParent.parent = null;
				}
				else if (setParent.parIndex >= 0 && setParent.parIndex < comp.parentAnimation.components.size()) {
					setParent.parent = comp.parentAnimation.components.get(setParent.parIndex);
				}
				else {
					Logger.logfWarning("SetParent command references out of bounds index: %02X", setParent.parIndex);
				}
			}
		}
	}
}
