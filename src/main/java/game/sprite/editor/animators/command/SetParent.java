package game.sprite.editor.animators.command;

import java.awt.Component;
import java.util.List;

import game.sprite.SpriteComponent;
import game.sprite.editor.Editable;
import game.sprite.editor.animators.command.CommandAnimatorEditor.SetParentPanel;
import util.Logger;

//81XX parent to component XX
public class SetParent extends AnimCommand
{
	public SpriteComponent parent;

	public SetParent(CommandAnimator animator)
	{
		this(animator, (short) 0);
	}

	public SetParent(CommandAnimator animator, short s0)
	{
		super(animator);

		int id = (s0 & 0xFF);
		if (id < owner.parentAnimation.components.size())
			parent = owner.parentAnimation.components.get(id);
	}

	public SetParent(CommandAnimator animator, SpriteComponent parent)
	{
		super(animator);

		this.parent = parent;
	}

	@Override
	public SetParent copy()
	{
		SetParent clone = new SetParent(animator);
		clone.parent = parent;
		return clone;
	}

	@Override
	public AdvanceResult apply()
	{
		owner.parent = parent;
		return AdvanceResult.NEXT;
	}

	@Override
	public String getName()
	{
		return "Set Parent";
	}

	@Override
	public String toString()
	{
		if (parent == null)
			return "Parent: (missing)";
		else if (parent.deleted)
			return "Parent: " + parent.name + " (missing)";
		else
			return "Parent: " + parent.name;
	}

	@Override
	public int length()
	{
		return 1;
	}

	@Override
	public Component getPanel()
	{
		SetParentPanel.instance().bind(this);
		return SetParentPanel.instance();
	}

	@Override
	public void addTo(List<Short> seq)
	{
		if (parent != null)
			seq.add((short) (0x8100 | (parent.getIndex() & 0xFF)));
		else {
			Logger.logError("No parent selected for SetParent in " + owner);
			seq.add((short) (0x8100));
		}
	}

	@Override
	public void addEditableDownstream(List<Editable> downstream)
	{
		if (parent != null)
			downstream.add(parent);
	}

	@Override
	public String checkErrorMsg()
	{
		if (parent == null)
			return "SetParent Command: undefined parent";

		if (parent == owner)
			return "SetParent Command: parented to itself";

		return null;
	}
}
