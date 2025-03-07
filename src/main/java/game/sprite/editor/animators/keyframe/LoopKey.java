package game.sprite.editor.animators.keyframe;

import java.awt.Color;
import java.awt.Component;
import java.util.List;

import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import app.SwingUtils;
import common.commands.AbstractCommand;
import game.sprite.editor.SpriteEditor;
import game.sprite.editor.animators.command.Label;
import net.miginfocom.swing.MigLayout;
import util.ui.ListAdapterComboboxModel;

public class LoopKey extends AnimKeyframe
{
	public int count;

	public Keyframe target;

	// used during conversion from commands
	public transient Label label;

	// used during conversion from commands
	public LoopKey(KeyframeAnimator animator, Label lbl, int loopCount)
	{
		super(animator);

		this.label = lbl;
		this.count = loopCount;
	}

	protected LoopKey(KeyframeAnimator animator, Keyframe kf, int loopCount)
	{
		super(animator);

		this.target = kf;
		this.count = loopCount;
	}

	@Override
	public LoopKey copy()
	{
		return new LoopKey(animator, target, count);
	}

	@Override
	public AdvanceResult apply()
	{
		if (animator.comp.repeatCount == 0) {
			animator.comp.repeatCount = count;
			animator.gotoKeyframe(target);
			return AdvanceResult.JUMP;
		}
		else {
			animator.comp.repeatCount--;
			if (animator.comp.repeatCount != 0) {
				animator.gotoKeyframe(target);
				return AdvanceResult.JUMP;
			}
		}
		return AdvanceResult.NEXT;
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
		if (target == null)
			return "Repeat: (missing) (x" + count + ")";
		else if (!animator.keyframes.contains(target))
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
		int pos = animator.getKeyframeOffset(target);
		if (pos < 0)
			throw new RuntimeException("Repeat is missing target: " + target.name);

		seq.add((short) (0x7000 | (pos & 0xFFF)));
		seq.add((short) count);
	}

	@Override
	public Component getPanel()
	{
		LoopPanel.instance().set(this, animator.keyframes);
		return LoopPanel.instance();
	}

	@Override
	public String checkErrorMsg()
	{
		if (target == null || !animator.keyframes.contains(target))
			return "Goto Keyframe: missing label";

		return null;
	}

	private static class LoopPanel extends JPanel
	{
		private static LoopPanel instance;
		private boolean ignoreChanges = false;

		private LoopKey cmd;

		private JComboBox<Keyframe> keyframeComboBox;
		private JSpinner countSpinner;

		protected static LoopPanel instance()
		{
			if (instance == null)
				instance = new LoopPanel();
			return instance;
		}

		private LoopPanel()
		{
			super(new MigLayout(KeyframeAnimatorEditor.PANEL_LAYOUT_PROPERTIES));

			keyframeComboBox = new JComboBox<>();
			SwingUtils.setFontSize(keyframeComboBox, 14);
			keyframeComboBox.setMaximumRowCount(16);
			keyframeComboBox.addActionListener((e) -> {
				if (!ignoreChanges) {
					Keyframe target = (Keyframe) keyframeComboBox.getSelectedItem();
					SpriteEditor.execute(new SetKeyframeLoopTarget(cmd, target));
				}
			});

			countSpinner = new JSpinner();
			SwingUtils.setFontSize(countSpinner, 12);
			countSpinner.setModel(new SpinnerNumberModel(1, 0, 300, 1));
			SwingUtils.centerSpinnerText(countSpinner);
			countSpinner.addChangeListener((e) -> {
				if (!ignoreChanges) {
					int count = (int) countSpinner.getValue();
					SpriteEditor.execute(new SetKeyframeLoopCount(cmd, count));
				}
			});

			add(SwingUtils.getLabel("Repeat Properties", 14), "gapbottom 4");
			add(keyframeComboBox, "w 200!");
			add(SwingUtils.getLabel("Repetitions: ", 12), "sg lbl, split 2");
			add(countSpinner, "sg etc, grow");
		}

		private void set(LoopKey cmd, DefaultListModel<AnimKeyframe> animKeyframes)
		{
			this.cmd = cmd;

			DefaultComboBoxModel<Keyframe> keyframes = new DefaultComboBoxModel<>();
			for (int i = 0; i < animKeyframes.size(); i++) {
				AnimKeyframe animFrame = animKeyframes.getElementAt(i);
				if (animFrame instanceof Keyframe kf)
					keyframes.addElement(kf);
			}

			ignoreChanges = true;
			keyframeComboBox.setModel(new ListAdapterComboboxModel<>(keyframes));
			keyframeComboBox.setSelectedItem(cmd.target);
			ignoreChanges = false;

			countSpinner.setValue(cmd.count);
		}

		private class SetKeyframeLoopTarget extends AbstractCommand
		{
			private final LoopKey cmd;
			private final Keyframe next;
			private final Keyframe prev;

			private SetKeyframeLoopTarget(LoopKey cmd, Keyframe next)
			{
				super("Set Loop Target");

				this.cmd = cmd;
				this.next = next;
				this.prev = cmd.target;
			}

			@Override
			public void exec()
			{
				super.exec();

				cmd.target = next;

				ignoreChanges = true;
				keyframeComboBox.setSelectedItem(next);
				ignoreChanges = false;

				cmd.owner.calculateTiming();
				cmd.incrementModified();
				KeyframeAnimatorEditor.repaintCommandList();
			}

			@Override
			public void undo()
			{
				super.undo();

				cmd.target = prev;

				ignoreChanges = true;
				keyframeComboBox.setSelectedItem(prev);
				ignoreChanges = false;

				cmd.owner.calculateTiming();
				cmd.decrementModified();
				KeyframeAnimatorEditor.repaintCommandList();
			}
		}

		private class SetKeyframeLoopCount extends AbstractCommand
		{
			private final LoopKey cmd;
			private final int next;
			private final int prev;

			private SetKeyframeLoopCount(LoopKey cmd, int next)
			{
				super("Set Loop Count");

				this.cmd = cmd;
				this.next = next;
				this.prev = cmd.count;
			}

			@Override
			public void exec()
			{
				super.exec();

				cmd.count = next;

				ignoreChanges = true;
				countSpinner.setValue(next);
				ignoreChanges = false;

				cmd.owner.calculateTiming();
				cmd.incrementModified();
				KeyframeAnimatorEditor.repaintCommandList();
			}

			@Override
			public void undo()
			{
				super.undo();

				cmd.count = prev;

				ignoreChanges = true;
				countSpinner.setValue(prev);
				ignoreChanges = false;

				cmd.owner.calculateTiming();
				cmd.decrementModified();
				KeyframeAnimatorEditor.repaintCommandList();
			}
		}
	}

}
