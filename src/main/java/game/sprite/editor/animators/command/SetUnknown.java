package game.sprite.editor.animators.command;

import java.awt.Component;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import app.SwingUtils;
import common.commands.AbstractCommand;
import game.sprite.editor.SpriteEditor;
import net.miginfocom.swing.MigLayout;

//80XX
public class SetUnknown extends AnimCommand
{
	public int value;

	public SetUnknown(CommandAnimator animator)
	{
		this(animator, (short) 0);
	}

	public SetUnknown(CommandAnimator animator, short s0)
	{
		super(animator);

		value = (s0 & 0xFF);
	}

	@Override
	public SetUnknown copy()
	{
		SetUnknown clone = new SetUnknown(animator);
		clone.value = value;
		return clone;
	}

	@Override
	public AdvanceResult apply()
	{
		return AdvanceResult.NEXT;
	}

	@Override
	public String getName()
	{
		return "Set Unknown";
	}

	@Override
	public String toString()
	{
		return "Set Unknown: " + value;
	}

	@Override
	public int length()
	{
		return 1;
	}

	@Override
	public Component getPanel()
	{
		SetUnknownPanel.instance().bind(this);
		return SetUnknownPanel.instance();
	}

	@Override
	public void addTo(List<Short> seq)
	{
		seq.add((short) (0x8000 | (value & 0xFF)));
	}

	private static class SetUnknownPanel extends JPanel
	{
		private static SetUnknownPanel instance;
		private SetUnknown cmd;

		private JSpinner valueSpinner;
		private boolean ignoreChanges = false;

		private static SetUnknownPanel instance()
		{
			if (instance == null)
				instance = new SetUnknownPanel();
			return instance;
		}

		private SetUnknownPanel()
		{
			super(new MigLayout(CommandAnimatorEditor.PANEL_LAYOUT_PROPERTIES));

			valueSpinner = new JSpinner();
			valueSpinner.setModel(new SpinnerNumberModel(0, 0, 255, 1));
			valueSpinner.addChangeListener((e) -> {
				if (!ignoreChanges)
					SpriteEditor.execute(new SetCommandUnknown(cmd, (int) valueSpinner.getValue()));
			});

			SwingUtils.setFontSize(valueSpinner, 12);
			SwingUtils.centerSpinnerText(valueSpinner);
			SwingUtils.addBorderPadding(valueSpinner);

			add(SwingUtils.getLabel("Set Notify", 14), "gapbottom 4");
			add(valueSpinner, "w 30%");
		}

		private void bind(SetUnknown cmd)
		{
			this.cmd = cmd;

			ignoreChanges = true;
			valueSpinner.setValue(cmd.value);
			ignoreChanges = false;
		}

		private class SetCommandUnknown extends AbstractCommand
		{
			private final SetUnknown cmd;
			private final int next;
			private final int prev;

			private SetCommandUnknown(SetUnknown cmd, int next)
			{
				super("Set Unknown");

				this.cmd = cmd;
				this.next = next;
				this.prev = cmd.value;
			}

			@Override
			public void exec()
			{
				super.exec();

				cmd.value = next;

				ignoreChanges = true;
				valueSpinner.setValue(next);
				ignoreChanges = false;

				cmd.incrementModified();
				CommandAnimatorEditor.repaintCommandList();
			}

			@Override
			public void undo()
			{
				super.undo();

				cmd.value = prev;

				ignoreChanges = true;
				valueSpinner.setValue(prev);
				ignoreChanges = false;

				cmd.decrementModified();
				CommandAnimatorEditor.repaintCommandList();
			}
		}
	}
}
