package game.sprite.editor.animators.command;

import java.awt.Container;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.TreeMap;

import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

import game.sprite.RawAnimation;
import game.sprite.SpriteComponent;
import game.sprite.editor.SpriteEditor;
import game.sprite.editor.animators.AnimElement;
import game.sprite.editor.animators.AnimElement.AdvanceResult;
import game.sprite.editor.animators.ComponentAnimator;
import game.sprite.editor.animators.ToKeyframesConverter;
import game.sprite.editor.animators.keyframe.AnimKeyframe;
import game.sprite.editor.animators.keyframe.KeyframeAnimator;
import util.IterableListModel;

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

	private void findAllLabels()
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
		ignoreListDataChanges = false;

		findAllLabels();
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
			if (listPosition < 0 || listPosition > commands.size())
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
			comp.keyframeCount = 0;
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
				lbl.labelName = String.format("#%X", streamPos);
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

				if (j > 0 || !lbl.labelName.equals("Start"))
					rawAnim.setLabel(lbl.listPos, lbl.labelName);
			}
			j += cmd.length();
		}

		return rawAnim;
	}
}
