package game.sprite.editor.animators.command;

import static game.sprite.SpriteKey.ATTR_XYZ;
import static game.sprite.SpriteKey.TAG_CMD_SET_ROT;

import java.awt.Component;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import org.w3c.dom.Element;

import app.SwingUtils;
import common.commands.AbstractCommand;
import game.sprite.editor.SpriteEditor;
import net.miginfocom.swing.MigLayout;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

//4xxx yyyy zzzz
// set rotation (euler angles)
public class SetRotation extends AnimCommand
{
	public int x, y, z;

	public SetRotation(CommandAnimator animator)
	{
		this(animator, (short) 0, (short) 0, (short) 0);
	}

	public SetRotation(CommandAnimator animator, short s0, short s1, short s2)
	{
		super(animator);

		// sign extend (implicit cast to int)
		x = ((s0 << 20) >> 20);
		y = s1;
		z = s2;
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag tag = xmw.createTag(TAG_CMD_SET_ROT, true);
		xmw.addIntArray(tag, ATTR_XYZ, x, y, z);
		xmw.printTag(tag);
	}

	@Override
	public void fromXML(XmlReader xmr, Element elem)
	{
		if (xmr.hasAttribute(elem, ATTR_XYZ)) {
			int[] pos = xmr.readIntArray(elem, ATTR_XYZ, 3);
			x = pos[0];
			y = pos[1];
			z = pos[2];
		}
	}

	@Override
	public SetRotation copy()
	{
		return new SetRotation(animator, (short) x, (short) y, (short) z);
	}

	@Override
	public AdvanceResult apply()
	{
		owner.rx = x;
		owner.ry = y;
		owner.rz = z;
		return AdvanceResult.NEXT;
	}

	@Override
	public String getName()
	{
		return "Set Rotation";
	}

	@Override
	public String toString()
	{
		return String.format("Rotation: (%d, %d, %d)", x, y, z);
	}

	@Override
	public int length()
	{
		return 3;
	}

	@Override
	public Component getPanel()
	{
		SetRotationPanel.instance().bind(this);
		return SetRotationPanel.instance();
	}

	@Override
	public void addTo(List<Short> seq)
	{
		seq.add((short) (0x4000 | (x & 0xFFF)));
		seq.add((short) y);
		seq.add((short) z);
	}

	private static class SetRotationPanel extends JPanel
	{
		private static SetRotationPanel instance;
		private SetRotation cmd;

		private boolean ignoreChanges = false;
		private JSpinner xSpinner, ySpinner, zSpinner;

		private static SetRotationPanel instance()
		{
			if (instance == null)
				instance = new SetRotationPanel();
			return instance;
		}

		private SetRotationPanel()
		{
			super(new MigLayout(CommandAnimatorEditor.PANEL_LAYOUT_PROPERTIES));

			xSpinner = new JSpinner();
			xSpinner.setModel(new SpinnerNumberModel(0, -180, 180, 1));
			xSpinner.addChangeListener((e) -> {
				if (!ignoreChanges)
					SpriteEditor.execute(new SetCommandRotation(cmd, 0, (int) xSpinner.getValue()));
			});

			SwingUtils.setFontSize(xSpinner, 12);
			SwingUtils.centerSpinnerText(xSpinner);
			SwingUtils.addBorderPadding(xSpinner);

			ySpinner = new JSpinner();
			ySpinner.setModel(new SpinnerNumberModel(0, -180, 180, 1));
			ySpinner.addChangeListener((e) -> {
				if (!ignoreChanges)
					SpriteEditor.execute(new SetCommandRotation(cmd, 1, (int) ySpinner.getValue()));
			});

			SwingUtils.setFontSize(ySpinner, 12);
			SwingUtils.centerSpinnerText(ySpinner);
			SwingUtils.addBorderPadding(ySpinner);

			zSpinner = new JSpinner();
			zSpinner.setModel(new SpinnerNumberModel(0, -180, 180, 1));
			zSpinner.addChangeListener((e) -> {
				if (!ignoreChanges)
					SpriteEditor.execute(new SetCommandRotation(cmd, 2, (int) zSpinner.getValue()));
			});

			SwingUtils.setFontSize(zSpinner, 12);
			SwingUtils.centerSpinnerText(zSpinner);
			SwingUtils.addBorderPadding(zSpinner);

			JPanel coordPanel = new JPanel(new MigLayout("fill, ins 0", "[sg spin]4[sg spin]4[sg spin]"));
			coordPanel.add(xSpinner);
			coordPanel.add(ySpinner);
			coordPanel.add(zSpinner);

			add(SwingUtils.getLabel("Set Rotation Angles", 14), "gapbottom 4");
			add(coordPanel, "growx");
		}

		private void bind(SetRotation cmd)
		{
			this.cmd = cmd;

			ignoreChanges = true;
			xSpinner.setValue(cmd.x);
			ySpinner.setValue(cmd.y);
			zSpinner.setValue(cmd.z);
			ignoreChanges = false;
		}

		private class SetCommandRotation extends AbstractCommand
		{
			private final SetRotation cmd;
			private final int coord;
			private final int next;
			private final int prev;

			private SetCommandRotation(SetRotation cmd, int coord, int next)
			{
				super("Set Rotation");

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
