package game.sprite.editor.animators.keyframe;

import java.awt.Container;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.TreeMap;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import game.sprite.RawAnimation;
import game.sprite.SpriteComponent;
import game.sprite.SpritePalette;
import game.sprite.SpriteRaster;
import game.sprite.editor.SpriteEditor;
import game.sprite.editor.animators.AnimElement;
import game.sprite.editor.animators.AnimElement.AdvanceResult;
import game.sprite.editor.animators.ComponentAnimator;
import game.sprite.editor.animators.ToCommandsConverter;
import game.sprite.editor.animators.command.AnimCommand;
import game.sprite.editor.animators.command.CommandAnimator;
import util.IterableListModel;
import util.Logger;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlWriter;

public class KeyframeAnimator implements ComponentAnimator
{
	// prevent infinite loops from crashing the editor
	private static final int MAX_ITERATIONS = 1024;

	public IterableListModel<AnimKeyframe> keyframes;
	public final SpriteComponent comp;

	private SpriteEditor editor;

	// command list control state
	private int listPosition;

	public KeyframeAnimator(SpriteComponent comp)
	{
		this.comp = comp;
		keyframes = new IterableListModel<>();
	}

	/**
	 * Creates a copy of another KeyframeAnimator, along with all commands
	 * Updates references within commands to point to corresponding (copied) commands/components
	 * @param comp SpriteComponent which this new animator belongs to
	 * @param other KeyframeAnimator to copy commands from
	 */
	public KeyframeAnimator(SpriteComponent comp, KeyframeAnimator other)
	{
		this(comp);

		// easier to compile to bytes and reconstruct a copy than to deal with the web of
		// object references that would need to be properly updated in the new list
		RawAnimation raw = other.getCommandList();
		generateFrom(raw);
	}

	public List<Keyframe> getKeyframesList()
	{
		ArrayList<Keyframe> keys = new ArrayList<>();

		for (AnimKeyframe other : keyframes) {
			if (other instanceof Keyframe kf) {
				keys.add(kf);
			}
		}

		return keys;
	}

	@Override
	public void bind(SpriteEditor editor, Container commandListContainer, Container commandEditContainer)
	{
		this.editor = editor;

		KeyframeAnimatorEditor.bind(editor, this, commandListContainer, commandEditContainer);

		if (comp != null)
			KeyframeAnimatorEditor.instance().restoreSelection(comp.lastSelectedCommand);
	}

	@Override
	public void unbind()
	{
		if (comp != null)
			comp.lastSelectedCommand = KeyframeAnimatorEditor.instance().getSelection();
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
			if (listPosition < 0 || listPosition >= keyframes.size())
				return;

			AnimElement cmd = keyframes.get(listPosition);
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
		if ((listPosition >= 0) && (listPosition < keyframes.size()))
			keyframes.get(listPosition).highlighted = false;

		listPosition = newPos;

		if ((listPosition >= 0) && (listPosition < keyframes.size()))
			keyframes.get(listPosition).highlighted = true;
	}

	protected boolean gotoKeyframe(AnimKeyframe kf)
	{
		int i = findKeyframe(kf);
		if (i >= 0) {
			setListPosition(i);
			comp.gotoTime = 0;
		}
		return (i >= 0);
	}

	// get the list position of a command
	protected int findKeyframe(AnimKeyframe kf)
	{
		if (kf == null)
			return -1;

		for (int i = 0; i < keyframes.size(); i++) {
			AnimKeyframe other = keyframes.get(i);
			if (kf == other)
				return i;
		}
		return -1;
	}

	// get the compiled position of a command
	protected int getKeyframeOffset(AnimKeyframe kf)
	{
		if (kf == null)
			return -1;

		int j = 0;
		for (int i = 0; i < keyframes.size(); i++) {
			AnimKeyframe other = keyframes.get(i);
			if (kf == other)
				return j;
			j += other.length();
		}
		return -1;
	}

	public void useKeyframes(List<AnimKeyframe> newKeyframes)
	{
		keyframes.clear();
		for (AnimKeyframe kf : newKeyframes)
			keyframes.addElement(kf);

		reset();
	}

	public List<AnimCommand> toCommands(CommandAnimator animator)
	{
		ToCommandsConverter converter = new ToCommandsConverter(animator, this);
		return converter.getCommands();
	}

	@Override
	public boolean surpassed(AnimElement elem)
	{
		return listPosition >= keyframes.indexOf(elem);
	}

	@Override
	public void calculateTiming()
	{
		if (keyframes.size() == 0)
			return;

		for (AnimKeyframe cmd : keyframes) {
			cmd.animTime = -1;
			cmd.isTarget = false;
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

			AnimKeyframe cmd = keyframes.get(pos);
			if ((cmd.animTime != -1) && loopCount == 0)
				break;

			if (cmd instanceof LoopKey l) {
				if (l.target == null) {
					break;
				}
				l.target.isTarget = true;

				int i = keyframes.indexOf(l.target);
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
			else if (cmd instanceof GotoKey g) {
				if (g.target == null) {
					break;
				}
				g.target.isTarget = true;

				int i = keyframes.indexOf(g.target);
				// label not found
				if (i < 0)
					break;
				// decorate goto with current anim time
				g.animTime = time;
				// goto label
				pos = i;
				continue;
			}
			else if (cmd instanceof Keyframe kf) {
				// decorate wait with current anim time
				kf.animTime = time;
				time += kf.duration;
			}

			// next command, but be careful not to overrun the list if it has no proper return
			pos++;
			if (pos == keyframes.size())
				break;
		}
	}

	@Override
	public RawAnimation getCommandList()
	{
		RawAnimation rawAnim = new RawAnimation();

		for (int i = 0; i < keyframes.size(); i++) {
			AnimKeyframe cmd = keyframes.get(i);
			cmd.addTo(rawAnim);
		}

		for (int i = 0; i < keyframes.size(); i++) {
			AnimKeyframe cmd = keyframes.get(i);
			if (cmd instanceof Keyframe kf) {
				if (!kf.name.isEmpty())
					rawAnim.setLabel(i, kf.name);
			}
		}

		return rawAnim;
	}

	@Override
	public boolean generateFrom(RawAnimation rawAnim)
	{
		keyframes.clear();

		TreeMap<Integer, Keyframe> listPositionMap = new TreeMap<>();
		Queue<Short> cmdQueue = new LinkedList<>(rawAnim);
		int listPos = 0;

		// initial keyframe
		int numKeyframes = 1;
		Keyframe curKf = new Keyframe(this);
		curKf.name = "Keyframe " + numKeyframes;

		while (!cmdQueue.isEmpty()) {
			int opcode = (cmdQueue.peek() >> 12) & 0xF;
			switch (opcode) {
				// wait command ends current keyframe
				case 0:
					curKf.listLen += curKf.setDuration(cmdQueue);
					listPositionMap.put(listPos, curKf);
					listPos += curKf.listLen;
					keyframes.addElement(curKf);

					// next keyframe
					curKf = new Keyframe(this);
					numKeyframes++;
					curKf.name = "Keyframe " + numKeyframes;
					curKf.listPos = listPos;
					break;

				// 2VVV
				// goto -- jump to another position in the list
				case 2:
					int gotoPos = cmdQueue.poll() & 0xFFF;

					Keyframe gotoTarget = listPositionMap.get(gotoPos);
					GotoKey jump = new GotoKey(this, gotoTarget);
					listPos++;

					keyframes.addElement(jump);
					break;

				// 7VVV UUUU
				// loop
				case 7:
					int loopPos = cmdQueue.poll() & 0xFFF;

					Keyframe loopTarget = listPositionMap.get(loopPos);
					LoopKey loop = new LoopKey(this, loopTarget, cmdQueue.poll());
					listPos += 2;

					keyframes.addElement(loop);
					break;

				case 8:
					int type = (cmdQueue.peek() >> 8) & 0xF;
					int value = cmdQueue.poll() & 0xFF;
					switch (type) {
						case 0: // 80XX
							// setUnknown
							break;
						case 1: // 81XX
							keyframes.addElement(new ParentKey(this, (short) value));
							listPos++;
							break;
						case 2: // 82XX
							keyframes.addElement(new NotifyKey(this, (short) value));
							listPos++;
							break;
					}
					break;

				// keyframe-building commands
				case 1:
					curKf.listLen += curKf.setImage(cmdQueue);
					break;
				case 3:
					curKf.listLen += curKf.setPosition(cmdQueue);
					break;
				case 4:
					curKf.listLen += curKf.setRotation(cmdQueue);
					break;
				case 5:
					curKf.listLen += curKf.setScale(cmdQueue);
					break;
				case 6:
					curKf.listLen += curKf.setPalette(cmdQueue);
					break;
				default:
					throw new IllegalStateException(String.format("Unknown animation command: %04X", cmdQueue.peek()));
			}
		}

		for (int i = 0; i < keyframes.size(); i++) {
			AnimKeyframe cmd = keyframes.get(i);

			if (cmd instanceof Keyframe kf) {
				if (rawAnim.hasLabel(i))
					kf.name = rawAnim.getLabel(i);
			}
		}

		reset();
		return true;
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		for (AnimKeyframe cmd : keyframes)
			cmd.toXML(xmw);
	}

	@Override
	public void fromXML(XmlReader xmr, Element compElem)
	{
		for (Node child = compElem.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (child instanceof Element elem) {
				switch (elem.getNodeName()) {
					case "Goto":
						GotoKey go2 = new GotoKey(this);
						go2.fromXML(xmr, elem);
						keyframes.addElement(go2);
						break;
					case "Loop":
						LoopKey loop = new LoopKey(this);
						loop.fromXML(xmr, elem);
						keyframes.addElement(loop);
						break;
					case "SetParent":
						ParentKey setPar = new ParentKey(this);
						setPar.fromXML(xmr, elem);
						keyframes.addElement(setPar);
						break;
					case "SetNotify":
						NotifyKey setNotify = new NotifyKey(this);
						setNotify.fromXML(xmr, elem);
						keyframes.addElement(setNotify);
						break;
					case "Keyframe":
						Keyframe setUnknown = new Keyframe(this);
						setUnknown.fromXML(xmr, elem);
						keyframes.addElement(setUnknown);
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
		// create hashmaps for lookup-by-name
		HashMap<String, Keyframe> keyframeMap = new HashMap<>();

		for (AnimKeyframe cmd : keyframes) {
			if (cmd instanceof Keyframe lbl) {
				if (lbl.name != null && !lbl.name.isBlank())
					keyframeMap.putIfAbsent(lbl.name, lbl);
			}
		}

		// determine position of each command in the stream
		TreeMap<Integer, AnimKeyframe> posMap = new TreeMap<>();
		int pos = 0;
		for (AnimKeyframe cmd : keyframes) {
			posMap.put(pos, cmd);
			pos += cmd.length();
		}

		// create a copy of the command list we can iterate over so we can safely modify the original during iteration
		ArrayList<AnimKeyframe> listCopy = new ArrayList<>(keyframes.size());
		for (AnimKeyframe cmd : keyframes)
			listCopy.add(cmd);

		// iterate through commands and replace temporary stringy references with object references
		for (AnimKeyframe cmd : listCopy) {
			if (cmd instanceof GotoKey go2) {
				go2.target = keyframeMap.get(go2.targetName);
			}
			else if (cmd instanceof LoopKey loop) {
				loop.target = keyframeMap.get(loop.targetName);
			}
			else if (cmd instanceof Keyframe kf) {
				if (kf.imgName != null) {
					kf.img = imgMap.get(kf.imgName);
				}
				if (kf.palName != null) {
					kf.pal = palMap.get(kf.palName);
				}
			}
			else if (cmd instanceof ParentKey setParent) {
				// match implementation from CommandAnimationEditor
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
