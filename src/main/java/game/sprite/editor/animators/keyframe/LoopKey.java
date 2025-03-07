package game.sprite.editor.animators.keyframe;

import java.awt.Color;
import java.awt.Component;
import java.util.List;

import app.SwingUtils;
import game.sprite.editor.animators.command.Label;
import game.sprite.editor.animators.keyframe.KeyframeAnimatorEditor.LoopPanel;

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
}
