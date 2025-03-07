package game.sprite.editor.animators.command;

import java.awt.Component;
import java.util.List;

import javax.swing.ComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JPanel;

import app.SwingUtils;
import common.commands.AbstractCommand;
import game.sprite.SpritePalette;
import game.sprite.editor.Editable;
import game.sprite.editor.PaletteCellRenderer;
import game.sprite.editor.SpriteEditor;
import net.miginfocom.swing.MigLayout;

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

	protected static class SetPalettePanel extends JPanel
	{
		private static SetPalettePanel instance;
		private SetPalette cmd;

		private JComboBox<SpritePalette> paletteComboBox;
		private boolean ignoreChanges = false;

		protected static SetPalettePanel instance()
		{
			if (instance == null)
				instance = new SetPalettePanel();
			return instance;
		}

		private SetPalettePanel()
		{
			super(new MigLayout(CommandAnimatorEditor.PANEL_LAYOUT_PROPERTIES));

			paletteComboBox = new JComboBox<>();
			SwingUtils.setFontSize(paletteComboBox, 14);
			paletteComboBox.setMaximumRowCount(24);
			paletteComboBox.setRenderer(new PaletteCellRenderer("Use Raster Default"));
			paletteComboBox.addActionListener((e) -> {
				if (!ignoreChanges) {
					SpritePalette pal = (SpritePalette) paletteComboBox.getSelectedItem();
					SpriteEditor.execute(new SetCommandPalette(cmd, pal));
				}
			});

			add(SwingUtils.getLabel("Set Palette", 14), "gapbottom 4");
			add(paletteComboBox, "growx, pushx");
		}

		private void bind(SetPalette cmd)
		{
			this.cmd = cmd;

			ignoreChanges = true;
			paletteComboBox.setSelectedItem(cmd.pal);
			ignoreChanges = false;
		}

		protected void setModel(ComboBoxModel<SpritePalette> model)
		{
			ignoreChanges = true;
			paletteComboBox.setModel(model);
			ignoreChanges = false;
		}

		private class SetCommandPalette extends AbstractCommand
		{
			private final SetPalette cmd;
			private final SpritePalette next;
			private final SpritePalette prev;

			public SetCommandPalette(SetPalette cmd, SpritePalette next)
			{
				super("Set Palette");

				this.cmd = cmd;
				this.next = next;
				this.prev = cmd.pal;
			}

			@Override
			public void exec()
			{
				super.exec();

				cmd.pal = next;

				ignoreChanges = true;
				paletteComboBox.setSelectedItem(next);
				ignoreChanges = false;

				cmd.incrementModified();
				CommandAnimatorEditor.repaintCommandList();
			}

			@Override
			public void undo()
			{
				super.undo();

				cmd.pal = prev;

				ignoreChanges = true;
				paletteComboBox.setSelectedItem(prev);
				ignoreChanges = false;

				cmd.decrementModified();
				CommandAnimatorEditor.repaintCommandList();
			}
		}
	}
}
