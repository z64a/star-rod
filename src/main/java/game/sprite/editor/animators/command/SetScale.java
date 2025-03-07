package game.sprite.editor.animators.command;

import java.awt.Component;
import java.awt.event.ActionListener;
import java.util.Enumeration;
import java.util.List;

import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import app.SwingUtils;
import common.commands.AbstractCommand;
import game.sprite.editor.SpriteEditor;
import net.miginfocom.swing.MigLayout;

//5VVV UUUU
// set scale (%)
public class SetScale extends AnimCommand
{
	public int type;
	public int scalePercent;

	public SetScale(CommandAnimator animator)
	{
		this(animator, (short) 0, (short) 100);
	}

	public SetScale(CommandAnimator animator, short s0, short s1)
	{
		super(animator);

		type = (s0 & 0xFFF);
		scalePercent = s1;
	}

	@Override
	public SetScale copy()
	{
		return new SetScale(animator, (short) type, (short) scalePercent);
	}

	@Override
	public AdvanceResult apply()
	{
		switch (type) {
			case 0:
				owner.scaleX = scalePercent;
				owner.scaleY = scalePercent;
				owner.scaleZ = scalePercent;
				break;
			case 1:
				owner.scaleX = scalePercent;
				break;
			case 2:
				owner.scaleY = scalePercent;
			case 3:
				owner.scaleZ = scalePercent;
				break;
			default:
				throw new RuntimeException(String.format("Invalid scale command type: %X", type));
		}
		return AdvanceResult.NEXT;
	}

	@Override
	public String getName()
	{
		return "Set Scale";
	}

	@Override
	public String toString()
	{
		String typeName;
		switch (type) {
			case 0:
				typeName = "All";
				break;
			case 1:
				typeName = "X";
				break;
			case 2:
				typeName = "Y";
			case 3:
				typeName = "Z";
				break;
			default:
				throw new RuntimeException(String.format("Invalid scale command type: %X", type));
		}
		return "Scale " + typeName + ": " + scalePercent + "%";
	}

	@Override
	public int length()
	{
		return 2;
	}

	@Override
	public Component getPanel()
	{
		SetScalePanel.instance().bind(this);
		return SetScalePanel.instance();
	}

	@Override
	public void addTo(List<Short> seq)
	{
		seq.add((short) (0x5000 | (type & 0xFFF)));
		seq.add((short) scalePercent);
	}

	private static class SetScalePanel extends JPanel
	{
		private static SetScalePanel instance;
		private SetScale cmd;

		private boolean ignoreChanges = false;
		private JSpinner scaleSpinner;
		private JRadioButton allButton, xButton, yButton, zButton;

		private static SetScalePanel instance()
		{
			if (instance == null)
				instance = new SetScalePanel();
			return instance;
		}

		private SetScalePanel()
		{
			super(new MigLayout(CommandAnimatorEditor.PANEL_LAYOUT_PROPERTIES));

			scaleSpinner = new JSpinner();
			scaleSpinner.setModel(new SpinnerNumberModel(1, 0, 500, 1));
			scaleSpinner.addChangeListener((e) -> {
				if (!ignoreChanges)
					SpriteEditor.execute(new SetCommandScalePercent(cmd, (int) scaleSpinner.getValue()));
			});

			SwingUtils.setFontSize(scaleSpinner, 12);
			SwingUtils.centerSpinnerText(scaleSpinner);
			SwingUtils.addBorderPadding(scaleSpinner);

			ButtonGroup scaleButtons = new ButtonGroup();

			ActionListener buttonListener = e -> {
				int i = 0;
				for (Enumeration<AbstractButton> buttons = scaleButtons.getElements(); buttons.hasMoreElements(); i++) {
					AbstractButton button = buttons.nextElement();
					if (button.isSelected())
						SpriteEditor.execute(new SetCommandScaleType(cmd, i));
				}
			};

			allButton = new JRadioButton("Uniform");
			xButton = new JRadioButton("Only X");
			yButton = new JRadioButton("Only Y");
			zButton = new JRadioButton("Only Z");
			allButton.setSelected(true);
			allButton.addActionListener(buttonListener);
			xButton.addActionListener(buttonListener);
			yButton.addActionListener(buttonListener);
			zButton.addActionListener(buttonListener);

			scaleButtons.add(allButton);
			scaleButtons.add(xButton);
			scaleButtons.add(yButton);
			scaleButtons.add(zButton);

			add(SwingUtils.getLabel("Set Scale Percent", 14), "gapbottom 4");
			add(scaleSpinner);
			add(allButton);
			add(xButton);
			add(yButton);
			add(zButton);
		}

		private void bind(SetScale cmd)
		{
			this.cmd = cmd;

			ignoreChanges = true;
			scaleSpinner.setValue(cmd.scalePercent);

			switch (cmd.type) {
				case 0:
					allButton.setSelected(true);
					break;
				case 1:
					xButton.setSelected(true);
					break;
				case 2:
					yButton.setSelected(true);
					break;
				case 3:
					zButton.setSelected(true);
					break;
			}
			ignoreChanges = false;
		}

		private class SetCommandScalePercent extends AbstractCommand
		{
			private final SetScale cmd;
			private final int next;
			private final int prev;

			private SetCommandScalePercent(SetScale cmd, int next)
			{
				super("Set Scale Percent");

				this.cmd = cmd;
				this.next = next;
				this.prev = cmd.scalePercent;
			}

			@Override
			public void exec()
			{
				super.exec();

				cmd.scalePercent = next;

				ignoreChanges = true;
				scaleSpinner.setValue(next);
				ignoreChanges = false;

				cmd.incrementModified();
				CommandAnimatorEditor.repaintCommandList();
			}

			@Override
			public void undo()
			{
				super.undo();

				cmd.scalePercent = prev;

				ignoreChanges = true;
				scaleSpinner.setValue(prev);
				ignoreChanges = false;

				cmd.decrementModified();
				CommandAnimatorEditor.repaintCommandList();
			}
		}

		private class SetCommandScaleType extends AbstractCommand
		{
			private final SetScale cmd;
			private final int next;
			private final int prev;

			private SetCommandScaleType(SetScale cmd, int next)
			{
				super("Set Scale Type");

				this.cmd = cmd;
				this.next = next;
				this.prev = cmd.type;
			}

			@Override
			public void exec()
			{
				super.exec();

				cmd.type = next;

				ignoreChanges = true;
				switch (cmd.type) {
					case 0:
						allButton.setSelected(true);
						break;
					case 1:
						xButton.setSelected(true);
						break;
					case 2:
						yButton.setSelected(true);
						break;
					case 3:
						zButton.setSelected(true);
						break;
				}
				ignoreChanges = false;

				cmd.incrementModified();
				CommandAnimatorEditor.repaintCommandList();
			}

			@Override
			public void undo()
			{
				super.undo();

				cmd.type = prev;

				ignoreChanges = true;
				switch (cmd.type) {
					case 0:
						allButton.setSelected(true);
						break;
					case 1:
						xButton.setSelected(true);
						break;
					case 2:
						yButton.setSelected(true);
						break;
					case 3:
						zButton.setSelected(true);
						break;
				}
				ignoreChanges = false;

				cmd.decrementModified();
				CommandAnimatorEditor.repaintCommandList();
			}
		}
	}
}
