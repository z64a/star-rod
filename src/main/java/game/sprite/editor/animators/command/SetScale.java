package game.sprite.editor.animators.command;

import static game.sprite.SpriteKey.*;

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

import org.w3c.dom.Element;

import app.SwingUtils;
import common.commands.AbstractCommand;
import game.sprite.editor.SpriteEditor;
import game.sprite.editor.animators.ScaleMode;
import net.miginfocom.swing.MigLayout;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

//5VVV UUUU
// set scale (%)
public class SetScale extends AnimCommand
{
	public ScaleMode mode;
	public int percent;

	public SetScale(CommandAnimator animator)
	{
		this(animator, (short) 0, (short) 100);
	}

	public SetScale(CommandAnimator animator, short s0, short s1)
	{
		super(animator);

		mode = ScaleMode.get(s0 & 0xFFF);
		percent = s1;
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag tag = xmw.createTag(TAG_CMD_SET_SCALE, true);
		xmw.addAttribute(tag, ATTR_MODE, mode.name);
		xmw.addInt(tag, ATTR_PERCENT, percent);
		xmw.printTag(tag);
	}

	@Override
	public void fromXML(XmlReader xmr, Element elem)
	{
		if (xmr.hasAttribute(elem, ATTR_MODE))
			mode = ScaleMode.get(xmr.getAttribute(elem, ATTR_MODE));

		if (xmr.hasAttribute(elem, ATTR_PERCENT))
			percent = xmr.readInt(elem, ATTR_PERCENT);
	}

	@Override
	public SetScale copy()
	{
		return new SetScale(animator, (short) mode.value, (short) percent);
	}

	@Override
	public AdvanceResult apply()
	{
		switch (mode) {
			case UNIFORM:
				owner.scaleX = percent;
				owner.scaleY = percent;
				owner.scaleZ = percent;
				break;
			case X:
				owner.scaleX = percent;
				break;
			case Y:
				owner.scaleY = percent;
			case Z:
				owner.scaleZ = percent;
				break;
		}
		return AdvanceResult.NEXT;
	}

	@Override
	public String getName()
	{
		return "Set Scale";
	}

	@Override
	public String getFormattedText()
	{
		return toString();
	}

	@Override
	public String toString()
	{
		String typeName = "error";
		switch (mode) {
			case UNIFORM:
				typeName = "All";
				break;
			case X:
				typeName = "X";
				break;
			case Y:
				typeName = "Y";
			case Z:
				typeName = "Z";
				break;
		}
		return "Scale " + typeName + ": " + percent + "%";
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
		seq.add((short) (0x5000 | (mode.value & 0xFFF)));
		seq.add((short) percent);
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
						SpriteEditor.execute(new SetCommandScaleType(cmd, ScaleMode.get(i)));
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
			scaleSpinner.setValue(cmd.percent);

			switch (cmd.mode) {
				case UNIFORM:
					allButton.setSelected(true);
					break;
				case X:
					xButton.setSelected(true);
					break;
				case Y:
					yButton.setSelected(true);
					break;
				case Z:
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
				this.prev = cmd.percent;
			}

			@Override
			public void exec()
			{
				super.exec();

				cmd.percent = next;

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

				cmd.percent = prev;

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
			private final ScaleMode next;
			private final ScaleMode prev;

			private SetCommandScaleType(SetScale cmd, ScaleMode next)
			{
				super("Set Scale Type");

				this.cmd = cmd;
				this.next = next;
				this.prev = cmd.mode;
			}

			@Override
			public void exec()
			{
				super.exec();

				cmd.mode = next;

				ignoreChanges = true;
				switch (cmd.mode) {
					case UNIFORM:
						allButton.setSelected(true);
						break;
					case X:
						xButton.setSelected(true);
						break;
					case Y:
						yButton.setSelected(true);
						break;
					case Z:
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

				cmd.mode = prev;

				ignoreChanges = true;
				switch (cmd.mode) {
					case UNIFORM:
						allButton.setSelected(true);
						break;
					case X:
						xButton.setSelected(true);
						break;
					case Y:
						yButton.setSelected(true);
						break;
					case Z:
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
