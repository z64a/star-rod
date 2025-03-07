package game.sprite.editor.animators.keyframe;

import java.util.List;

import game.sprite.editor.animators.AnimElement;

public abstract class AnimKeyframe extends AnimElement
{
	public final KeyframeAnimator animator;

	protected AnimKeyframe(KeyframeAnimator animator)
	{
		super(animator.comp);

		this.animator = animator;
	}

	protected abstract int length();

	protected abstract void addTo(List<Short> seq);
}
