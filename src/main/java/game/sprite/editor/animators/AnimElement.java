package game.sprite.editor.animators;

import java.awt.Color;
import java.awt.Component;

import game.sprite.SpriteComponent;

public abstract class AnimElement
{
	public final SpriteComponent ownerComp;
	protected boolean highlighted = false;

	// first occurance frame of this element during animation playback
	public int animTime = -1;

	public AnimElement(SpriteComponent c)
	{
		this.ownerComp = c;
	}

	public abstract AnimElement copy();

	public abstract String getName();

	public Color getTextColor()
	{
		return null;
	}

	//TODO
	//	public abstract boolean hasError();

	//TODO
	//	public abstract String getErrorMessage();

	// returns TRUE if the next keyframe should be executed
	public abstract boolean advance();

	public abstract Component getPanel();
}
