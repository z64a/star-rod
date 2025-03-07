package game.sprite.editor.animators.keyframe;

import java.awt.Component;
import java.util.List;

import game.sprite.SpriteComponent;
import game.sprite.editor.Editable;
import game.sprite.editor.animators.keyframe.KeyframeAnimatorEditor.SetParentPanel;
import util.Logger;

public class ParentKey extends AnimKeyframe
{
	public SpriteComponent parent;

	public ParentKey(KeyframeAnimator animator)
	{
		this(animator, (short) 0);
	}

	public ParentKey(KeyframeAnimator animator, short s0)
	{
		super(animator);

		int id = (s0 & 0xFF);
		if (id < owner.parentAnimation.components.size())
			parent = owner.parentAnimation.components.get(id);
	}

	public ParentKey(KeyframeAnimator animator, SpriteComponent parent)
	{
		super(animator);

		this.parent = parent;
	}

	@Override
	public ParentKey copy()
	{
		ParentKey clone = new ParentKey(animator);
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
			return "SetParent: undefined parent";

		if (parent == owner)
			return "SetParent: parented to itself";

		return null;
	}
}
