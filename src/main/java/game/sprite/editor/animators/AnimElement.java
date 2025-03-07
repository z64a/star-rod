package game.sprite.editor.animators;

import java.awt.Color;
import java.awt.Component;
import java.util.List;

import app.SwingUtils;
import game.sprite.SpriteComponent;
import game.sprite.editor.Editable;

public abstract class AnimElement implements Editable
{
	public static enum AdvanceResult
	{
		BLOCK, NEXT, JUMP
	}

	public final SpriteComponent owner;
	public boolean highlighted = false;

	// first occurance frame of this element during animation playback
	public int animTime = -1;

	public AnimElement(SpriteComponent c)
	{
		this.owner = c;
	}

	public abstract AnimElement copy();

	/**
	 * Perform the action for a single command
	 * @return AdvanceResult indicating how to handle advancing the next command
	 */
	public abstract AdvanceResult apply();

	public abstract Component getPanel();

	public abstract String getName();

	public Color getTextColor()
	{
		// highlight invalid values as errors
		if (hasError())
			return SwingUtils.getRedTextColor();
		else
			return null;
	}

	private final EditableData editableData = new EditableData(this);

	@Override
	public EditableData getEditableData()
	{
		return editableData;
	}

	@Override
	public void addEditableDownstream(List<Editable> downstream)
	{}

	@Override
	public String checkErrorMsg()
	{
		// override to add error reporting
		return null;
	}
}
