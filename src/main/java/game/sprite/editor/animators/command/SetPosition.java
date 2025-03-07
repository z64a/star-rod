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

//3VVV XXXX YYYY ZZZZ
// set position -- flag: doesn't do anything
public class SetPosition extends AnimCommand
{
	public boolean unknown;
	public int x, y, z;

	public SetPosition(CommandAnimator animator)
	{
		this(animator, (short) 0, (short) 0, (short) 0, (short) 0);
	}

	public SetPosition(CommandAnimator animator, short s0, short s1, short s2, short s3)
	{
		super(animator);

		unknown = (s0 & 0xFFF) == 1;
		x = s1;
		y = s2;
		z = s3;
	}

	@Override
	public SetPosition copy()
	{
		SetPosition clone = new SetPosition(animator);
		clone.x = x;
		clone.y = y;
		clone.z = z;
		clone.unknown = unknown;
		return clone;
	}

	@Override
	public AdvanceResult apply()
	{
		owner.dx = x;
		owner.dy = y;
		owner.dz = z;
		return AdvanceResult.NEXT;
	}

	@Override
	public String getName()
	{
		return "Set Position";
	}

	@Override
	public String toString()
	{
		return String.format("Position: (%d, %d, %d)", x, y, z);
	}

	@Override
	public int length()
	{
		return 4;
	}

	@Override
	public Component getPanel()
	{
		SetPositionPanel.instance().bind(this);
		return SetPositionPanel.instance();
	}

	@Override
	public void addTo(List<Short> seq)
	{
		seq.add(unknown ? (short) 0x3001 : (short) 0x3000);
		seq.add((short) x);
		seq.add((short) y);
		seq.add((short) z);
	}

	private static class SetPositionPanel extends JPanel
	{
		private static SetPositionPanel instance;
		private SetPosition cmd;

		private boolean ignoreChanges = false;
		private JSpinner xSpinner, ySpinner, zSpinner;

		private static SetPositionPanel instance()
		{
			if (instance == null)
				instance = new SetPositionPanel();
			return instance;
		}

		private SetPositionPanel()
		{
			super(new MigLayout(CommandAnimatorEditor.PANEL_LAYOUT_PROPERTIES));

			xSpinner = new JSpinner();
			xSpinner.setModel(new SpinnerNumberModel(0, -256, 256, 1));
			xSpinner.addChangeListener((e) -> {
				if (!ignoreChanges)
					SpriteEditor.execute(new SetCommandPosition(cmd, 0, (int) xSpinner.getValue()));
			});

			SwingUtils.setFontSize(xSpinner, 12);
			SwingUtils.centerSpinnerText(xSpinner);
			SwingUtils.addBorderPadding(xSpinner);

			ySpinner = new JSpinner();
			ySpinner.setModel(new SpinnerNumberModel(0, -256, 256, 1));
			ySpinner.addChangeListener((e) -> {
				if (!ignoreChanges)
					SpriteEditor.execute(new SetCommandPosition(cmd, 1, (int) ySpinner.getValue()));
			});

			SwingUtils.setFontSize(ySpinner, 12);
			SwingUtils.centerSpinnerText(ySpinner);
			SwingUtils.addBorderPadding(ySpinner);

			zSpinner = new JSpinner();
			zSpinner.setModel(new SpinnerNumberModel(0, -256, 256, 1));
			zSpinner.addChangeListener((e) -> {
				if (!ignoreChanges)
					SpriteEditor.execute(new SetCommandPosition(cmd, 2, (int) zSpinner.getValue()));
			});

			SwingUtils.setFontSize(zSpinner, 12);
			SwingUtils.centerSpinnerText(zSpinner);
			SwingUtils.addBorderPadding(zSpinner);

			JPanel coordPanel = new JPanel(new MigLayout("fill, ins 0", "[sg spin]4[sg spin]4[sg spin]"));
			coordPanel.add(xSpinner);
			coordPanel.add(ySpinner);
			coordPanel.add(zSpinner);

			add(SwingUtils.getLabel("Set Position Offset", 14), "gapbottom 4");
			add(coordPanel, "growx");
		}

		private void bind(SetPosition cmd)
		{
			this.cmd = cmd;

			ignoreChanges = true;
			xSpinner.setValue(cmd.x);
			ySpinner.setValue(cmd.y);
			zSpinner.setValue(cmd.z);
			ignoreChanges = false;
		}

		private class SetCommandPosition extends AbstractCommand
		{
			private final SetPosition cmd;
			private final int coord;
			private final int next;
			private final int prev;

			private SetCommandPosition(SetPosition cmd, int coord, int next)
			{
				super("Set Position");

				this.cmd = cmd;
				this.coord = coord;
				this.next = next;

				switch (coord) {
					case 0:
						this.prev = cmd.x;
						break;
					case 1:
						this.prev = cmd.y;
						break;
					default:
						this.prev = cmd.z;
						break;
				}
			}

			@Override
			public void exec()
			{
				super.exec();

				switch (coord) {
					case 0:
						cmd.x = next;
						break;
					case 1:
						cmd.y = next;
						break;
					default:
						cmd.z = next;
						break;
				}

				ignoreChanges = true;
				switch (coord) {
					case 0:
						xSpinner.setValue(next);
						break;
					case 1:
						ySpinner.setValue(next);
						break;
					default:
						zSpinner.setValue(next);
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

				switch (coord) {
					case 0:
						cmd.x = prev;
						break;
					case 1:
						cmd.y = prev;
						break;
					default:
						cmd.z = prev;
						break;
				}

				ignoreChanges = true;
				switch (coord) {
					case 0:
						xSpinner.setValue(prev);
						break;
					case 1:
						ySpinner.setValue(prev);
						break;
					default:
						zSpinner.setValue(prev);
						break;
				}
				ignoreChanges = false;

				cmd.decrementModified();
				CommandAnimatorEditor.repaintCommandList();
			}
		}
	}
}
