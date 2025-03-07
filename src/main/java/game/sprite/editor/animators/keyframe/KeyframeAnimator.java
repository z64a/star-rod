package game.sprite.editor.animators.keyframe;

import java.awt.Container;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.TreeMap;

import game.sprite.RawAnimation;
import game.sprite.SpriteComponent;
import game.sprite.editor.SpriteEditor;
import game.sprite.editor.animators.AnimElement;
import game.sprite.editor.animators.AnimElement.AdvanceResult;
import game.sprite.editor.animators.ComponentAnimator;
import game.sprite.editor.animators.ToCommandsConverter;
import game.sprite.editor.animators.command.AnimCommand;
import game.sprite.editor.animators.command.CommandAnimator;
import util.IterableListModel;

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

	@Override
	public void bind(SpriteEditor editor, Container commandListContainer, Container commandEditContainer)
	{
		this.editor = editor;

		KeyframeAnimatorEditor.bind(editor, this, commandListContainer, commandEditContainer);
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
			if (listPosition < 0 || listPosition > keyframes.size())
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
			comp.keyframeCount = 0;
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
	public boolean generateFrom(RawAnimation rawAnim)
	{
		keyframes.clear();

		TreeMap<Integer, Keyframe> listPositionMap = new TreeMap<>();
		List<Integer> keyframePositions = getKeyframePositions(new ArrayList<>(rawAnim));

		Queue<Short> cmdQueue = new LinkedList<>(rawAnim);
		Keyframe curKf = null;
		int listPos = 0;
		int numKeyframes = 0;

		while (!cmdQueue.isEmpty()) {
			int opcode = (cmdQueue.peek() >> 12) & 0xF;
			if (opcode != 2 && opcode != 7) {
				if (keyframePositions.contains(listPos)) {
					// next keyframe
					curKf = new Keyframe(this);
					curKf.name = "Keyframe " + numKeyframes++;
					curKf.listPos = listPos;

					listPositionMap.put(curKf.listPos, curKf);
					keyframes.addElement(curKf);
				}
			}

			switch (opcode) {
				// wait command ends current keyframe
				case 0:
					listPos += curKf.setDuration(cmdQueue);
					break;

				// 2VVV
				// goto -- jump to another position in the list
				case 2: {
					int pos = cmdQueue.poll() & 0xFFF;

					Keyframe target = listPositionMap.get(pos);
					GotoKey jump = new GotoKey(this, target);
					listPos++;

					keyframes.addElement(jump);
				}
					break;

				// 7VVV UUUU
				// loop
				case 7: {
					int pos = cmdQueue.poll() & 0xFFF;

					Keyframe target = listPositionMap.get(pos);
					LoopKey loop = new LoopKey(this, target, cmdQueue.poll());
					listPos += 2;

					keyframes.addElement(loop);
				}
					break;

				// keyframe-building commands
				case 1:
					listPos += curKf.setImage(cmdQueue);
					break;
				case 3:
					listPos += curKf.setPosition(cmdQueue);
					break;
				case 4:
					listPos += curKf.setRotation(cmdQueue);
					break;
				case 5:
					listPos += curKf.setScale(cmdQueue);
					break;
				case 6:
					listPos += curKf.setPalette(cmdQueue);
					break;
				case 8:
					int type = (cmdQueue.peek() >> 8) & 0xF;
					int value = cmdQueue.peek() & 0xFF;
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
				default:
					throw new IllegalStateException(String.format("Unknown animation command: %04X", cmdQueue.peek()));
			}
		}

		for (int i = 0; i < keyframes.size(); i++) {
			AnimKeyframe cmd = keyframes.get(i);

			if (cmd instanceof Keyframe kf) {
				if (rawAnim.hasLabel(listPos))
					kf.name = rawAnim.getLabel(listPos);
			}
		}

		reset();
		return true;
	}

	private List<Integer> getKeyframePositions(ArrayList<Short> copyQueue)
	{
		List<Integer> keyframePositions = new ArrayList<>();

		int kfPos = 0;
		int pos = 0;
		while (pos < copyQueue.size()) {
			Short s = copyQueue.get(pos);

			int opcode = (s >> 12) & 0xF;
			switch (opcode) {
				case 0:
					pos += 1;
					keyframePositions.add(kfPos);
					kfPos = pos;
					break;
				case 1:
					pos += 1;
					break;
				case 2:
					pos += 1;
					keyframePositions.add(s & 0xFFF);
					kfPos = pos;
					break;
				case 3:
					pos += 4;
					break;
				case 4:
					pos += 3;
					break;
				case 5:
					pos += 2;
					break;
				case 6:
					pos += 1;
					break;
				case 7:
					pos += 2;
					keyframePositions.add(s & 0xFFF);
					kfPos = pos;
					break;
				case 8:
					pos += 1;
					break;
				default:
					throw new IllegalStateException(String.format("Unknown animation command: %04X", s));
			}
		}

		// always have at least one
		if (keyframePositions.isEmpty())
			keyframePositions.add(0);

		return keyframePositions;
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
					rawAnim.setLabel(kf.listPos, kf.name);
			}
		}

		return rawAnim;
	}
}
