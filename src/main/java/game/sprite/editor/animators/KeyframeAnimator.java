package game.sprite.editor.animators;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.TreeMap;

import app.SwingUtils;
import game.sprite.RawAnimation;
import game.sprite.SpriteComponent;
import game.sprite.SpritePalette;
import game.sprite.SpriteRaster;
import game.sprite.editor.SpriteEditor;
import game.sprite.editor.animators.KeyframeAnimatorEditor.GotoPanel;
import game.sprite.editor.animators.KeyframeAnimatorEditor.KeyframePanel;
import game.sprite.editor.animators.KeyframeAnimatorEditor.LoopPanel;
import util.IterableListModel;
import util.Logger;

public class KeyframeAnimator implements ComponentAnimator
{
	public IterableListModel<AnimKeyframe> keyframeListModel = new IterableListModel<>();
	private final SpriteComponent comp;

	private SpriteEditor editor;

	// command list control state
	public AnimKeyframe currentKeyframe;

	public KeyframeAnimator(SpriteComponent comp)
	{
		this.comp = comp;
	}

	@Override
	public void bind(SpriteEditor editor, Container commandListContainer, Container commandEditContainer)
	{
		this.editor = editor;

		KeyframeAnimatorEditor.bind(editor, this, commandListContainer, commandEditContainer);
	}

	public void recalculateLinks()
	{
		for (int i = 0; i < keyframeListModel.size(); i++) {
			AnimKeyframe cmd = keyframeListModel.get(i);
			cmd.nextKeyframe = ((i + 1) == keyframeListModel.size()) ? null : keyframeListModel.get(i + 1);
		}
	}

	private void setCurrentKeyframe(AnimKeyframe kf)
	{
		if (currentKeyframe != null)
			currentKeyframe.highlighted = false;

		currentKeyframe = kf;

		if (currentKeyframe != null)
			currentKeyframe.highlighted = true;
	}

	public void resetAnimation()
	{
		SpriteEditor.instance().resetAnimation();
	}

	@Override
	public void reset()
	{
		setCurrentKeyframe(null);
		comp.sr = null;
		comp.sp = null;
		comp.parent = null;

		comp.complete = false;
		comp.keyframeCount = 0;
		comp.repeatCount = 0;
	}

	@Override
	public void step()
	{
		if (keyframeListModel.isEmpty()) {
			setCurrentKeyframe(null);
			return;
		}

		if (currentKeyframe == null) {
			setCurrentKeyframe(keyframeListModel.firstElement());
			currentKeyframe.exec();
		}

		if (comp.delayCount > 0) {
			comp.delayCount -= 2;

			if (comp.delayCount > 0)
				return;
		}

		int iterations = 0;
		int maxIterations = 1024;

		// prevent infinite loops from freezing the editor
		while (comp.delayCount <= 0 && currentKeyframe != null && iterations < maxIterations) {
			currentKeyframe.advance();

			if (currentKeyframe != null) {
				currentKeyframe.exec();
			}
			iterations++;
		}
	}

	public void advanceTo(AnimElement elem)
	{
		comp.parentAnimation.advanceTo(this, elem);
	}

	@Override
	public boolean surpassed(AnimElement elem)
	{
		if (currentKeyframe == null)
			return true;

		return keyframeListModel.indexOf(currentKeyframe) >= keyframeListModel.indexOf(elem);
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
	public void calculateTiming()
	{
		for (AnimKeyframe cmd : keyframeListModel) {
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

			AnimKeyframe cmd = keyframeListModel.get(pos);
			if ((cmd.animTime != -1) && loopCount == 0)
				break;

			if (cmd instanceof Loop l) {
				int i = keyframeListModel.indexOf(l.target);
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
				int i = keyframeListModel.indexOf(g.target);
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
			if (pos == keyframeListModel.size())
				break;
		}
	}

	@Override
	public boolean generate(RawAnimation rawAnim)
	{
		keyframeListModel.clear();

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
					curKf = new Keyframe();
					curKf.name = "Keyframe " + numKeyframes++;
					curKf.listPos = listPos;

					listPositionMap.put(curKf.listPos, curKf);
					keyframeListModel.addElement(curKf);
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
					Goto jump = new Goto(target);
					listPos++;

					keyframeListModel.addElement(jump);
				}
					break;

				// 7VVV UUUU
				// loop
				case 7: {
					int pos = cmdQueue.poll() & 0xFFF;

					Keyframe target = listPositionMap.get(pos);
					Loop loop = new Loop(target, cmdQueue.poll());
					listPos += 2;

					keyframeListModel.addElement(loop);
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
					switch (type) {
						case 0:
							break; // setUnknown
						case 1:
							listPos += curKf.setParent(cmdQueue);
							break;
						case 2:
							listPos += curKf.setNotify(cmdQueue);
							break;
					}
					break;
				default:
					throw new IllegalStateException(String.format("Unknown animation command: %04X", cmdQueue.peek()));
			}
		}

		for (int i = 0; i < keyframeListModel.size(); i++) {
			AnimKeyframe cmd = keyframeListModel.get(i);
			cmd.nextKeyframe = ((i + 1) == keyframeListModel.size()) ? null : keyframeListModel.get(i + 1);

			if (cmd instanceof Keyframe kf) {
				if (rawAnim.hasLabel(listPos))
					kf.name = rawAnim.getLabel(listPos);
			}
		}

		reset();
		return true;
	}

	@Override
	public void cleanDeletedRasters()
	{
		for (int i = 0; i < keyframeListModel.size(); i++) {
			AnimKeyframe cmd = keyframeListModel.get(i);
			if (cmd instanceof Keyframe kf) {
				if (kf.img != null && kf.img.deleted)
					kf.img = null;
			}
		}

		if (comp.sr != null && comp.sr.deleted)
			comp.sr = null;
	}

	@Override
	public void cleanDeletedPalettes()
	{
		for (int i = 0; i < keyframeListModel.size(); i++) {
			AnimKeyframe cmd = keyframeListModel.get(i);
			if (cmd instanceof Keyframe kf) {
				if (kf.pal != null && kf.pal.deleted)
					kf.pal = null;
			}
		}

		if (comp.sp != null && comp.sp.deleted)
			comp.sp = null;
	}

	@Override
	public RawAnimation getCommandList()
	{
		RawAnimation rawAnim = new RawAnimation();

		for (int i = 0; i < keyframeListModel.size(); i++) {
			AnimKeyframe cmd = keyframeListModel.get(i);
			cmd.addTo(rawAnim);
		}

		for (int i = 0; i < keyframeListModel.size(); i++) {
			AnimKeyframe cmd = keyframeListModel.get(i);
			if (cmd instanceof Keyframe kf) {
				if (!kf.name.isEmpty())
					rawAnim.setLabel(kf.listPos, kf.name);
			}
		}

		return rawAnim;
	}

	// get the list position of a command
	private int findKeyframe(AnimKeyframe kf)
	{
		if (kf == null)
			return -1;

		for (int i = 0; i < keyframeListModel.size(); i++) {
			AnimKeyframe other = keyframeListModel.get(i);
			if (kf == other)
				return i;
		}
		return -1;
	}

	// get the compiled position of a command
	private int getKeyframeOffset(AnimKeyframe kf)
	{
		if (kf == null)
			return -1;

		int j = 0;
		for (int i = 0; i < keyframeListModel.size(); i++) {
			AnimKeyframe other = keyframeListModel.get(i);
			if (kf == other)
				return j;
			j += other.length();
		}
		return -1;
	}

	public abstract class AnimKeyframe extends AnimElement
	{
		AnimKeyframe nextKeyframe;

		private AnimKeyframe()
		{
			super(comp);
		}

		public abstract int length();

		// returns TRUE if the next keyframe should be executed
		public abstract void exec();

		@Override
		public abstract boolean advance();

		protected abstract void addTo(List<Short> seq);
	}

	public class Goto extends AnimKeyframe
	{
		protected Keyframe target;

		protected Goto(Keyframe kf)
		{
			this.target = kf;
		}

		@Override
		public Goto copy()
		{
			return new Goto(target);
		}

		@Override
		public void exec()
		{}

		@Override
		public boolean advance()
		{
			if (findKeyframe(target) < findKeyframe(currentKeyframe))
				ownerComp.complete = (ownerComp.keyframeCount < 2);
			ownerComp.keyframeCount = 0;

			setCurrentKeyframe(target);
			return true;
		}

		@Override
		public int length()
		{
			return 1;
		}

		@Override
		public void addTo(List<Short> seq)
		{
			if (target == null)
				return;

			int pos = getKeyframeOffset(target);
			if (pos < 0)
				throw new RuntimeException("Goto is missing target: " + target.name);

			seq.add((short) (0x2000 | (pos & 0xFFF)));
		}

		@Override
		public String getName()
		{
			return "Goto";
		}

		@Override
		public Color getTextColor()
		{
			if (target == null || !keyframeListModel.contains(target))
				return SwingUtils.getRedTextColor();
			else
				return SwingUtils.getBlueTextColor();
		}

		@Override
		public String toString()
		{
			if (target == null)
				return "Goto: (missing)";
			else if (!keyframeListModel.contains(target))
				return "<html>Goto: <i>" + target.name + "</i>  (missing)</html>";
			else
				return "<html>Goto: <i>" + target.name + "</i></html>";
		}

		@Override
		public Component getPanel()
		{
			GotoPanel.instance().set(this, keyframeListModel);
			return GotoPanel.instance();
		}
	}

	public class Loop extends AnimKeyframe
	{
		protected Keyframe target;
		protected int count;

		protected Loop(Keyframe kf, int loopCount)
		{
			this.target = kf;
			this.count = loopCount;
		}

		@Override
		public Loop copy()
		{
			return new Loop(target, count);
		}

		@Override
		public void exec()
		{}

		@Override
		public boolean advance()
		{
			if (comp.repeatCount == 0) {
				comp.repeatCount = count;
				setCurrentKeyframe(target);
			}
			else {
				if (--comp.repeatCount == 0)
					setCurrentKeyframe(nextKeyframe);
				else
					setCurrentKeyframe(target);
			}
			return true;
		}

		@Override
		public String getName()
		{
			return "Loop";
		}

		@Override
		public Color getTextColor()
		{
			if (target == null || !keyframeListModel.contains(target))
				return SwingUtils.getRedTextColor();
			else
				return SwingUtils.getBlueTextColor();
		}

		@Override
		public String toString()
		{
			if (target == null)
				return "Repeat: (missing) (x" + count + ")";
			else if (!keyframeListModel.contains(target))
				return "<html>Repeat: <i>" + target.name + "</i>  (missing) (x" + count + ")</html>";
			else
				return "<html>Repeat: <i>" + target.name + "</i>  (x" + count + ")</html>";
		}

		@Override
		public int length()
		{
			return 2;
		}

		@Override
		public void addTo(List<Short> seq)
		{
			int pos = getKeyframeOffset(target);
			if (pos < 0)
				throw new RuntimeException("Repeat is missing target: " + target.name);

			seq.add((short) (0x7000 | (pos & 0xFFF)));
			seq.add((short) count);
		}

		@Override
		public Component getPanel()
		{
			LoopPanel.instance().set(this, keyframeListModel);
			return LoopPanel.instance();
		}
	}

	public class Keyframe extends AnimKeyframe
	{
		protected Keyframe()
		{
			name = "New Keyframe";
		}

		@Override
		public Keyframe copy()
		{
			Keyframe clone = new Keyframe();
			clone.name = name;
			clone.duration = duration;
			clone.setParent = setParent;
			clone.parentComp = parentComp;
			clone.setImage = setImage;
			clone.img = img;
			clone.setPalette = setPalette;
			clone.pal = pal;
			clone.unknown = unknown;
			clone.dx = dx;
			clone.dy = dy;
			clone.dz = dz;
			clone.rx = rx;
			clone.ry = ry;
			clone.rz = rz;
			clone.sx = sx;
			clone.sy = sy;
			clone.sz = sz;
			return clone;
		}

		String name = "";
		int listPos = -1;

		public int duration;

		boolean setNotify = false;
		public int notifyValue;

		boolean setParent = false;
		public SpriteComponent parentComp;

		boolean setImage = false;
		public SpriteRaster img = null;

		boolean setPalette = false;
		public SpritePalette pal = null;

		public boolean unknown;
		public int dx = 0, dy = 0, dz = 0;
		public int rx = 0, ry = 0, rz = 0;
		public int sx = 100, sy = 100, sz = 100;

		@Override
		public void exec()
		{
			if (setImage) {
				ownerComp.sr = img;
				ownerComp.sp = null;
			}

			if (setPalette) {
				ownerComp.sp = pal;
			}

			if (setParent) {
				ownerComp.parent = parentComp;
			}

			// these all use default values if not set by the keyframe
			if (duration > 0) {
				ownerComp.dx = dx;
				ownerComp.dy = dy;
				ownerComp.dz = dz;

				ownerComp.rx = rx;
				ownerComp.ry = ry;
				ownerComp.rz = rz;

				ownerComp.scaleX = sx;
				ownerComp.scaleY = sy;
				ownerComp.scaleZ = sz;
			}

			ownerComp.delayCount = duration;
		}

		@Override
		public boolean advance()
		{
			ownerComp.keyframeCount++;
			setCurrentKeyframe(nextKeyframe);
			return true;
		}

		@Override
		public int length()
		{
			// zero IQ method is fine and less prone to bugs
			List<Short> dummySeq = new LinkedList<>();
			addTo(dummySeq);
			return dummySeq.size();
		}

		@Override
		protected void addTo(List<Short> seq)
		{
			if (setParent) {
				if (parentComp != null)
					seq.add((short) (0x8100 | (parentComp.getIndex() & 0xFF)));
				else {
					Logger.logError("No parent selected for SetParent in " + ownerComp);
					seq.add((short) (0x8100));
				}
			}

			if (duration > 0 && dx != 0 || dy != 0 || dz != 0) {
				seq.add(unknown ? (short) 0x3001 : (short) 0x3000);
				seq.add((short) dx);
				seq.add((short) dy);
				seq.add((short) dz);
			}

			if (duration > 0 && rx != 0 || ry != 0 || rz != 0) {
				seq.add((short) (0x4000 | (rx & 0xFFF)));
				seq.add((short) ry);
				seq.add((short) rz);
			}

			if (duration > 0 && sx != 100 || sy != 100 || sz != 100) {
				if (sx == sy && sy == sz) {
					seq.add((short) 0x5000);
					seq.add((short) sx);
				}
				else {
					if (sx != 100) {
						seq.add((short) 0x5001);
						seq.add((short) sx);
					}
					if (sy != 100) {
						seq.add((short) 0x5002);
						seq.add((short) sy);
					}
					if (sz != 100) {
						seq.add((short) 0x5003);
						seq.add((short) sz);
					}
				}
			}

			if (notifyValue > 0) {
				seq.add((short) (0x8200 | (notifyValue & 0xFF)));
			}

			if (setImage) {
				int id = (img == null) ? -1 : img.getIndex();
				seq.add((short) (0x1000 | (id & 0xFFF)));
			}

			if (setPalette) {
				int id = (pal == null) ? -1 : pal.getIndex();
				seq.add((short) (0x6000 | (id & 0xFFF)));
			}

			if (duration > 0)
				seq.add((short) (0x0000 | (duration & 0xFFF)));
		}

		// 0VVV : 0 is valid, leads to a duration of 4095
		public int setDuration(Queue<Short> cmdQueue)
		{
			duration = (cmdQueue.poll() & 0xFFF);

			if (duration == 0)
				duration = 4095;

			return 1;
		}

		// 1VVV : FFF is valid value for "no image"
		public int setImage(Queue<Short> cmdQueue)
		{
			// ensure FFF -> FFFF with sign extension
			int id = (cmdQueue.poll() << 20) >> 20;
			img = (id < 0) ? null : ownerComp.parentAnimation.parentSprite.rasters.get(id);
			setImage = true;
			return 1;
		}

		// 3VVV XXXX YYYY ZZZZ
		public int setPosition(Queue<Short> cmdQueue)
		{
			//XXX flag doesnt seem to do anything?
			unknown = (cmdQueue.poll() & 0xFFF) == 1;
			dx = cmdQueue.poll();
			dy = cmdQueue.poll();
			dz = cmdQueue.poll();
			return 4;
		}

		// 4XXX YYYY ZZZZ
		// set rotation (euler angles)
		public int setRotation(Queue<Short> cmdQueue)
		{
			// ensure FFF -> FFFF with sign extension
			rx = ((cmdQueue.poll() << 20) >> 20);
			ry = cmdQueue.poll();
			rz = cmdQueue.poll();
			return 3;
		}

		// 5VVV UUUU : scale (%)
		public int setScale(Queue<Short> cmdQueue)
		{
			int type = (cmdQueue.poll() & 0xFFF);
			int scale = cmdQueue.poll();
			switch (type) {
				case 0:
					sx = scale;
					sy = scale;
					sz = scale;
					break;
				case 1:
					sx = scale;
					break;
				case 2:
					sy = scale;
					break;
				case 3:
					sz = scale;
					break;
			}
			return 2;
		}

		// 6VVV
		public int setPalette(Queue<Short> cmdQueue)
		{
			// ensure FFF -> FFFF with sign extension
			int id = (cmdQueue.poll() << 20) >> 20;
			pal = (id < 0) ? null : ownerComp.parentAnimation.parentSprite.palettes.get(id);
			setPalette = true;
			return 1;
		}

		// 81XX
		public int setParent(Queue<Short> cmdQueue)
		{
			int s0 = cmdQueue.poll();
			int id = (s0 & 0xFF);

			parentComp = ownerComp.parentAnimation.components.get(id);
			setParent = true;
			return 1;
		}

		// 82XX
		public int setNotify(Queue<Short> cmdQueue)
		{
			int s0 = cmdQueue.poll();
			notifyValue = (s0 & 0xFF);
			return 1;
		}

		@Override
		public String getName()
		{
			return "Keyframe";
		}

		@Override
		public Color getTextColor()
		{
			if (duration == 0)
				return SwingUtils.getRedTextColor();
			else
				return null;
		}

		@Override
		public String toString()
		{
			SpriteEditor editor = SpriteEditor.instance();
			boolean highlight = (highlighted && editor != null && editor.highlightCommand);

			StringBuilder sb = new StringBuilder("<html>");
			if (highlight)
				sb.append("<b>");
			sb.append(name);
			if (highlight)
				sb.append("</b>");
			sb.append("</html>");
			return sb.toString();
		}

		@Override
		public Component getPanel()
		{
			KeyframePanel.instance().set(this);
			return KeyframePanel.instance();
		}
	}
}
