package game.sprite.editor.animators.command;

import java.awt.Component;
import java.util.List;

import game.sprite.SpritePalette;
import game.sprite.editor.Editable;
import game.sprite.editor.animators.command.CommandAnimatorEditor.SetPalettePanel;

//6VVV
// use palette
public class SetPalette extends AnimCommand
{
	public SpritePalette pal;

	public SetPalette(CommandAnimator animator)
	{
		this(animator, (short) 0);
	}

	public SetPalette(CommandAnimator animator, short s0)
	{
		super(animator);

		// FFF is valid, so sign extend (implicit cast to int)
		int id = (s0 << 20) >> 20;
		pal = (id < 0) ? null : animator.comp.parentAnimation.parentSprite.palettes.get(id);
	}

	public SetPalette(CommandAnimator animator, SpritePalette pal)
	{
		super(animator);

		this.pal = pal;
	}

	@Override
	public SetPalette copy()
	{
		SetPalette clone = new SetPalette(animator);
		clone.pal = pal;
		return clone;
	}

	@Override
	public AdvanceResult apply()
	{
		owner.sp = pal;
		return AdvanceResult.NEXT;
	}

	@Override
	public String getName()
	{
		return "Set Palette";
	}

	@Override
	public String toString()
	{
		if (pal == null)
			return "Default Palette";
		else if (pal.deleted)
			return "Palette: " + pal + " (missing)";
		else
			return "Palette: " + pal;
	}

	@Override
	public int length()
	{
		return 1;
	}

	@Override
	public Component getPanel()
	{
		SetPalettePanel.instance().bind(this);
		return SetPalettePanel.instance();
	}

	@Override
	public void addTo(List<Short> seq)
	{
		int id = (pal == null) ? -1 : pal.getIndex();
		seq.add((short) (0x6000 | (id & 0xFFF)));
	}

	@Override
	public void addEditableDownstream(List<Editable> downstream)
	{
		if (pal != null)
			downstream.add(pal);
	}
}
