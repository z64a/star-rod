package game.sprite.editor.animators.command;

import java.awt.Color;
import java.awt.Component;
import java.util.List;

import javax.swing.DefaultListModel;
import javax.swing.JComboBox;
import javax.swing.JPanel;

import app.SwingUtils;
import common.commands.AbstractCommand;
import game.sprite.editor.SpriteEditor;
import game.sprite.editor.animators.keyframe.Keyframe;
import net.miginfocom.swing.MigLayout;
import util.ui.ListAdapterComboboxModel;

//2VVV
// goto -- jump to another position in the list
public class Goto extends AnimCommand
{
	public Label label;
	public transient int fixedPos; // only used for generating commands

	// used during conversion from Keyframes
	public transient Keyframe target;

	public Goto(CommandAnimator animator)
	{
		this(animator, null, -1);
	}

	// used during conversion from Keyframes
	public Goto(CommandAnimator animator, Keyframe target)
	{
		this(animator);

		this.target = target;
	}

	public Goto(CommandAnimator animator, Label label, int fixedPos)
	{
		super(animator);

		this.label = label;
		this.fixedPos = fixedPos;
	}

	@Override
	public Goto copy()
	{
		return new Goto(animator, label, -1);
	}

	@Override
	public AdvanceResult apply()
	{
		if (label == null)
			return AdvanceResult.NEXT;

		// goto: self infinite loops add a 1 frame delay
		if (animator.findCommand(label) < animator.findCommand(this))
			owner.complete = (owner.keyframeCount < 2);

		animator.gotoLabel(label);
		return AdvanceResult.JUMP;
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
		else if (animator.findCommand(label) < 0)
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
		if (!animator.labels.contains(label))
			animator.labels.addElement(label);

		GotoPanel.instance().bind(this, animator.labels);
		return GotoPanel.instance();
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
				throw new RuntimeException("Goto is missing label: " + label.labelName);
		}

		seq.add((short) (0x2000 | (pos & 0xFFF)));
	}

	@Override
	public String checkErrorMsg()
	{
		if (label == null || animator.findCommand(label) < 0)
			return "Goto Command: missing label";

		return null;
	}

	private static class GotoPanel extends JPanel
	{
		private static GotoPanel instance;
		private Goto cmd;

		private JComboBox<Label> labelComboBox;
		private boolean ignoreChanges = false;

		private static GotoPanel instance()
		{
			if (instance == null)
				instance = new GotoPanel();
			return instance;
		}

		private GotoPanel()
		{
			super(new MigLayout(CommandAnimatorEditor.PANEL_LAYOUT_PROPERTIES));

			labelComboBox = new JComboBox<>();
			SwingUtils.setFontSize(labelComboBox, 14);
			labelComboBox.setMaximumRowCount(16);
			labelComboBox.addActionListener((e) -> {
				if (!ignoreChanges) {
					Label label = (Label) labelComboBox.getSelectedItem();
					SpriteEditor.execute(new SetCommandGotoLabel(cmd, label));
				}
			});

			add(SwingUtils.getLabel("Goto Label", 14), "gapbottom 4");
			add(labelComboBox, "growx, pushx");
		}

		private void bind(Goto cmd, DefaultListModel<Label> labels)
		{
			this.cmd = cmd;

			//TODO could be a problem when labels are deleted!
			ignoreChanges = true;
			labelComboBox.setModel(new ListAdapterComboboxModel<>(labels));
			labelComboBox.setSelectedItem(cmd.label);
			ignoreChanges = false;
		}

		private class SetCommandGotoLabel extends AbstractCommand
		{
			private final Goto cmd;
			private final Label next;
			private final Label prev;

			private SetCommandGotoLabel(Goto cmd, Label next)
			{
				super("Set Goto Label");

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
