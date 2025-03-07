package game.sprite.editor.animators.command;

import java.awt.Color;
import java.awt.Component;
import java.util.List;

import app.SwingUtils;
import game.sprite.editor.animators.command.CommandAnimatorEditor.LoopPanel;
import game.sprite.editor.animators.keyframe.Keyframe;

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
}
