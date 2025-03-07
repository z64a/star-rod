package game.sprite.editor.animators.command;

import java.awt.Component;
import java.util.List;

import game.sprite.Sprite;
import game.sprite.SpriteRaster;
import game.sprite.editor.Editable;
import game.sprite.editor.animators.command.CommandAnimatorEditor.SetImagePanel;

//1VVV
// set image -- FFF is valid value for "no image" (may actually need to be < 100`)
public class SetImage extends AnimCommand
{
	public SpriteRaster img;

	public SetImage(CommandAnimator animator)
	{
		this(animator, (short) 0);
	}

	public SetImage(CommandAnimator animator, short s0)
	{
		super(animator);

		Sprite sprite = animator.comp.parentAnimation.parentSprite;

		// FFF is valid, so sign extend (implicit cast to int)
		int id = (s0 << 20) >> 20;
		if (id < 0 || id >= sprite.rasters.size())
			img = null;
		else
			img = sprite.rasters.get(id);
	}

	public SetImage(CommandAnimator animator, SpriteRaster img)
	{
		super(animator);

		this.img = img;
	}

	@Override
	public SetImage copy()
	{
		SetImage clone = new SetImage(animator);
		clone.img = img;
		return clone;
	}

	@Override
	public AdvanceResult apply()
	{
		owner.sr = img;
		owner.sp = null;
		return AdvanceResult.NEXT;
	}

	@Override
	public String getName()
	{
		return "Set Raster";
	}

	@Override
	public String toString()
	{
		if (img == null)
			return "Clear Raster";
		else if (img.deleted)
			return "Raster: " + img.name + " (missing)";
		else
			return "Raster: " + img.name;
	}

	@Override
	public int length()
	{
		return 1;
	}

	@Override
	public Component getPanel()
	{
		SetImagePanel.instance().bind(this);
		return SetImagePanel.instance();
	}

	@Override
	public void addTo(List<Short> seq)
	{
		int id = (img == null) ? -1 : img.getIndex();
		seq.add((short) (0x1000 | (id & 0xFFF)));
	}

	@Override
	public void addEditableDownstream(List<Editable> downstream)
	{
		if (img != null)
			downstream.add(img);
	}
}
