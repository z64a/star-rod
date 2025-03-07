package game.sprite.editor.animators.command;

import java.util.List;

import game.sprite.editor.animators.AnimElement;

public abstract class AnimCommand extends AnimElement
{
	public final CommandAnimator animator;

	protected AnimCommand(CommandAnimator animator)
	{
		super(animator.comp);

		this.animator = animator;
	}

	protected abstract int length();

	protected abstract void addTo(List<Short> seq);
}
