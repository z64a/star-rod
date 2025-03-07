package game.sprite.editor.animators.keyframe;

import java.awt.Color;
import java.awt.Component;
import java.util.List;

import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JComboBox;
import javax.swing.JPanel;

import app.SwingUtils;
import common.commands.AbstractCommand;
import game.sprite.editor.SpriteEditor;
import game.sprite.editor.animators.command.Label;
import net.miginfocom.swing.MigLayout;
import util.ui.ListAdapterComboboxModel;

public class GotoKey extends AnimKeyframe
{
	public Keyframe target;

	// used during conversion from commands
	public transient Label label;

	// used during conversion from commands
	public GotoKey(KeyframeAnimator animator, Label lbl)
	{
		super(animator);

		this.label = lbl;
	}

	protected GotoKey(KeyframeAnimator animator, Keyframe kf)
	{
		super(animator);

		this.target = kf;
	}

	@Override
	public GotoKey copy()
	{
		return new GotoKey(animator, target);
	}

	@Override
	public AdvanceResult apply()
	{
		if (label == null)
			return AdvanceResult.NEXT;

		// goto: self infinite loops add a 1 frame delay
		if (animator.findKeyframe(target) < animator.findKeyframe(this))
			owner.complete = (owner.keyframeCount < 2);

		animator.gotoKeyframe(target);
		return AdvanceResult.JUMP;
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

		int pos = animator.getKeyframeOffset(target);
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
		if (hasError())
			return SwingUtils.getRedTextColor();
		else
			return SwingUtils.getBlueTextColor();
	}

	@Override
	public String toString()
	{
		if (target == null)
			return "Goto: (missing)";
		else if (!animator.keyframes.contains(target))
			return "<html>Goto: <i>" + target.name + "</i>  (missing)</html>";
		else
			return "<html>Goto: <i>" + target.name + "</i></html>";
	}

	@Override
	public Component getPanel()
	{
		GotoPanel.instance().set(this, animator.keyframes);
		return GotoPanel.instance();
	}

	@Override
	public String checkErrorMsg()
	{
		if (target == null || !animator.keyframes.contains(target))
			return "Goto Keyframe: missing label";

		return null;
	}

	private static class GotoPanel extends JPanel
	{
		private static GotoPanel instance;
		private boolean ignoreChanges = false;

		private GotoKey cmd;

		private JComboBox<Keyframe> keyframeComboBox;

		private static GotoPanel instance()
		{
			if (instance == null)
				instance = new GotoPanel();
			return instance;
		}

		private GotoPanel()
		{
			super(new MigLayout(KeyframeAnimatorEditor.PANEL_LAYOUT_PROPERTIES));

			keyframeComboBox = new JComboBox<>();
			SwingUtils.setFontSize(keyframeComboBox, 14);
			keyframeComboBox.setMaximumRowCount(16);
			keyframeComboBox.addActionListener((e) -> {
				if (!ignoreChanges) {
					Keyframe target = (Keyframe) keyframeComboBox.getSelectedItem();
					SpriteEditor.execute(new SetKeyframeGotoTarget(cmd, target));
				}
			});

			add(SwingUtils.getLabel("Goto Keyframe", 14), "gapbottom 4");
			add(keyframeComboBox, "w 200!");
		}

		private void set(GotoKey cmd, DefaultListModel<AnimKeyframe> animKeyframes)
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
		}

		private class SetKeyframeGotoTarget extends AbstractCommand
		{
			private final GotoKey cmd;
			private final Keyframe next;
			private final Keyframe prev;

			private SetKeyframeGotoTarget(GotoKey cmd, Keyframe next)
			{
				super("Set Goto Target");

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
	}
}
