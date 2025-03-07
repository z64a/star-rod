package game.sprite.editor.animators.keyframe;

import java.awt.Color;
import java.awt.Component;
import java.util.List;

import app.SwingUtils;
import game.sprite.editor.animators.command.Label;
import game.sprite.editor.animators.keyframe.KeyframeAnimatorEditor.GotoPanel;

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
}
