package game.sprite.editor.animators.command;

import java.awt.Color;
import java.awt.Component;
import java.util.List;

import javax.swing.DefaultListModel;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import app.SwingUtils;
import common.commands.AbstractCommand;
import game.sprite.editor.SpriteEditor;
import game.sprite.editor.animators.keyframe.Keyframe;
import net.miginfocom.swing.MigLayout;
import util.ui.ListAdapterComboboxModel;

//7VVV UUUU
// loop
public class Loop extends AnimCommand
{
	public Label label;
	public transient int fixedPos; // only used for generating commands
	public int count;

	// used during conversion from Keyframes
	public transient Keyframe target;

	public Loop(CommandAnimator animator)
	{
		this(animator, null, -1, (short) 3);
	}

	// used during conversion from Keyframes
	public Loop(CommandAnimator animator, Keyframe target, int count)
	{
		this(animator, null, -1, (short) count);

		this.target = target;
	}

	public Loop(CommandAnimator animator, Label label, int fixedPos, short s1)
	{
		super(animator);

		this.label = label;
		this.fixedPos = fixedPos;
		count = s1;
	}

	@Override
	public Loop copy()
	{
		return new Loop(animator, label, -1, (short) count);
	}

	@Override
	public AdvanceResult apply()
	{
		if (animator.comp.repeatCount == 0) {
			animator.comp.repeatCount = count;
			animator.gotoLabel(label);
			return AdvanceResult.JUMP;
		}
		else {
			animator.comp.repeatCount--;
			if (animator.comp.repeatCount != 0) {
				animator.gotoLabel(label);
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
		if (label == null)
			return "Repeat: (missing) (x" + count + ")";
		else if (animator.findCommand(label) < 0)
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
		LoopPanel.instance().bind(this, animator.labels);
		return LoopPanel.instance();
	}

	@Override
	public void addTo(List<Short> seq)
	{
		int pos = 0;

		if (label.labelName.startsWith("#"))
			pos = Integer.parseInt(label.labelName.substring(1), 16);
		else {
			pos = animator.getCommandOffset(label);
			if (pos < 0)
				throw new RuntimeException("Repeat is missing label: " + label.labelName);
		}

		seq.add((short) (0x7000 | (pos & 0xFFF)));
		seq.add((short) count);
	}

	@Override
	public String checkErrorMsg()
	{
		if (label == null || animator.findCommand(label) < 0)
			return "Loop Command: missing label";

		return null;
	}

	private static class LoopPanel extends JPanel
	{
		private static LoopPanel instance;
		private Loop cmd;

		private JComboBox<Label> labelComboBox;
		private JSpinner countSpinner;
		private boolean ignoreChanges = false;

		private static LoopPanel instance()
		{
			if (instance == null)
				instance = new LoopPanel();
			return instance;
		}

		private LoopPanel()
		{
			super(new MigLayout(CommandAnimatorEditor.PANEL_LAYOUT_PROPERTIES));

			labelComboBox = new JComboBox<>();
			SwingUtils.setFontSize(labelComboBox, 14);
			labelComboBox.setMaximumRowCount(16);
			labelComboBox.addActionListener((e) -> {
				if (!ignoreChanges) {
					Label label = (Label) labelComboBox.getSelectedItem();
					SpriteEditor.execute(new SetCommandLoopLabel(cmd, label));
				}
			});

			countSpinner = new JSpinner();
			countSpinner.setModel(new SpinnerNumberModel(1, 0, 300, 1));
			countSpinner.addChangeListener((e) -> {
				if (!ignoreChanges) {
					int loopCount = (int) countSpinner.getValue();
					SpriteEditor.execute(new SetCommandLoopCount(cmd, loopCount));
				}
			});

			SwingUtils.setFontSize(countSpinner, 12);
			SwingUtils.centerSpinnerText(countSpinner);
			SwingUtils.addBorderPadding(countSpinner);

			add(SwingUtils.getLabel("Repeat from Label", 14), "gapbottom 4");
			add(labelComboBox, "growx, pushx");
			add(SwingUtils.getLabel("Repetitions: ", 12), "split 2");
			add(countSpinner, "w 30%");
		}

		private void bind(Loop cmd, DefaultListModel<Label> labels)
		{
			this.cmd = cmd;

			//TODO could be a problem when labels are deleted!
			ignoreChanges = true;
			labelComboBox.setModel(new ListAdapterComboboxModel<>(labels));
			labelComboBox.setSelectedItem(cmd.label);
			ignoreChanges = false;

			countSpinner.setValue(cmd.count);
		}

		private class SetCommandLoopCount extends AbstractCommand
		{
			private final Loop cmd;
			private final int next;
			private final int prev;

			private SetCommandLoopCount(Loop cmd, int next)
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

				cmd.incrementModified();
				cmd.owner.calculateTiming();
				CommandAnimatorEditor.repaintCommandList();
			}

			@Override
			public void undo()
			{
				super.undo();

				cmd.count = prev;

				ignoreChanges = true;
				countSpinner.setValue(prev);
				ignoreChanges = false;

				cmd.decrementModified();
				cmd.owner.calculateTiming();
				CommandAnimatorEditor.repaintCommandList();
			}
		}

		private class SetCommandLoopLabel extends AbstractCommand
		{
			private final Loop cmd;
			private final Label next;
			private final Label prev;

			private SetCommandLoopLabel(Loop cmd, Label next)
			{
				super("Set Loop Label");

				this.cmd = cmd;
				this.next = next;
				this.prev = cmd.label;
			}

			@Override
			public void exec()
			{
				super.exec();

				cmd.label = next;

				ignoreChanges = true;
				labelComboBox.setSelectedItem(next);
				ignoreChanges = false;

				cmd.incrementModified();
				cmd.owner.calculateTiming();
				CommandAnimatorEditor.repaintCommandList();
			}

			@Override
			public void undo()
			{
				super.undo();

				cmd.label = prev;

				ignoreChanges = true;
				labelComboBox.setSelectedItem(prev);
				ignoreChanges = false;

				cmd.decrementModified();
				cmd.owner.calculateTiming();
				CommandAnimatorEditor.repaintCommandList();
			}
		}
	}
}
