package game.sprite.editor.animators;

import java.awt.Component;

import game.sprite.SpriteComponent;

public abstract class AnimElement
{
	protected final SpriteComponent ownerComp;
	protected boolean highlighted = false;

	public AnimElement(SpriteComponent c)
	{
		this.ownerComp = c;
	}

	public abstract String getName();

	public abstract AnimElement copy();

	// returns TRUE if the next keyframe should be executed
	public abstract boolean advance();

	public abstract Component getPanel();
}
