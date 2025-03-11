package game.sprite.editor.animators.keyframe;

import static game.sprite.SpriteKey.ATTR_DEST;
import static game.sprite.SpriteKey.TAG_CMD_GOTO;

import java.awt.Color;
import java.awt.Component;
import java.util.List;

import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JPanel;

import org.w3c.dom.Element;

import app.SwingUtils;
import common.commands.AbstractCommand;
import game.sprite.editor.SpriteEditor;
import game.sprite.editor.animators.command.Label;
import net.miginfocom.swing.MigLayout;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class GotoKey extends AnimKeyframe
{
	public Keyframe target;

	// used during deserialization
	public transient String targetName;

	// used during conversion from commands
	public transient Label label;

	// for XML deserialization
	public GotoKey(KeyframeAnimator animator)
	{
		super(animator);
	}

	// used during conversion from commands
	public GotoKey(KeyframeAnimator animator, Label lbl)
	{
		super(animator);

		this.label = lbl;
	}

	protected GotoKey(KeyframeAnimator animator, Keyframe kf)
	{
		super(animator);

		this.target = kf;
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag tag = xmw.createTag(TAG_CMD_GOTO, true);
		if (target != null)
			xmw.addAttribute(tag, ATTR_DEST, target.name);
		xmw.printTag(tag);
	}

	@Override
	public void fromXML(XmlReader xmr, Element elem)
	{
		if (xmr.hasAttribute(elem, ATTR_DEST))
			targetName = xmr.getAttribute(elem, ATTR_DEST);
	}

	@Override
	public GotoKey copy()
	{
		return new GotoKey(animator, target);
	}

	@Override
	public AdvanceResult apply()
	{
		if (target == null)
			return AdvanceResult.NEXT;

		// goto: self infinite loops add a 1 frame delay
		if (animator.findKeyframe(target) < animator.findKeyframe(this))
			owner.complete = (owner.gotoTime == 0);

		animator.gotoKeyframe(target);
		return AdvanceResult.JUMP;
	}

	@Override
	public int length()
	{
		return 1;
	}

	@Override
	public void addTo(List<Short> seq)
	{
		if (target == null)
			return;

		int pos = animator.getKeyframeOffset(target);
		if (pos < 0)
			throw new RuntimeException("Goto is missing target: " + target.name);

		seq.add((short) (0x2000 | (pos & 0xFFF)));
	}

	@Override
	public String getName()
	{
		return "Goto";
	}

	@Override
	public Color getTextColor()
	{
		if (hasError())
			return SwingUtils.getRedTextColor();
		else
			return SwingUtils.getBlueTextColor();
	}

	@Override
	public String getFormattedText()
	{
		if (target == null)
			return "Goto: (missing)";
		else if (!animator.keyframes.contains(target))
			return "<html>Goto: <i>" + target.name + "</i>  (missing)</html>";
		else
			return "<html>Goto: <i>" + target.name + "</i></html>";
	}

	@Override
	public String toString()
	{
		if (target == null)
			return "Goto: (missing)";
		else if (!animator.keyframes.contains(target))
			return "Goto: " + target.name + " (missing)";
		else
			return "Goto: " + target.name;
	}

	@Override
	public Component getPanel()
	{
		List<Keyframe> keyframes = animator.getKeyframesList();

		DefaultComboBoxModel<Keyframe> comboBoxModel = new DefaultComboBoxModel<>();

		// if the target is missing from the command, add it
		if (!keyframes.contains(target))
			comboBoxModel.addElement(target);

		// add all the keyframes for this animator
		comboBoxModel.addAll(keyframes);

		GotoKeyPanel.instance().bind(this, comboBoxModel);
		return GotoKeyPanel.instance();
	}

	@Override
	public String checkErrorMsg()
	{
		if (target == null || !animator.keyframes.contains(target))
			return "Goto Keyframe: missing label";

		return null;
	}

	protected static class GotoKeyPanel extends JPanel
	{
		protected static GotoKeyPanel instance;
		private boolean ignoreChanges = false;

		private GotoKey cmd;

		private JComboBox<Keyframe> keyframeComboBox;

		private static GotoKeyPanel instance()
		{
			if (instance == null)
				instance = new GotoKeyPanel();
			return instance;
		}

		private GotoKeyPanel()
		{
			super(new MigLayout(KeyframeAnimatorEditor.PANEL_LAYOUT_PROPERTIES));

			keyframeComboBox = new JComboBox<>();
			SwingUtils.setFontSize(keyframeComboBox, 14);
			keyframeComboBox.setMaximumRowCount(16);
			keyframeComboBox.addActionListener((e) -> {
				if (!ignoreChanges) {
					Keyframe target = (Keyframe) keyframeComboBox.getSelectedItem();
					SpriteEditor.execute(new SetKeyframeGotoTarget(cmd, target));
				}
			});

			add(SwingUtils.getLabel("Goto Keyframe", 14), "gapbottom 4");
			add(keyframeComboBox, "w 200!");
		}

		private void bind(GotoKey cmd, ComboBoxModel<Keyframe> keyframes)
		{
			this.cmd = cmd;

			ignoreChanges = true;
			keyframeComboBox.setModel(keyframes);
			keyframeComboBox.setSelectedItem(cmd.target);
			ignoreChanges = false;
		}

		private class SetKeyframeGotoTarget extends AbstractCommand
		{
			private final GotoKey cmd;
			private final Keyframe next;
			private final Keyframe prev;

			private SetKeyframeGotoTarget(GotoKey cmd, Keyframe next)
			{
				super("Set Goto Target");

				this.cmd = cmd;
				this.next = next;
				this.prev = cmd.target;
			}

			@Override
			public void exec()
			{
				super.exec();

				cmd.target = next;

				ignoreChanges = true;
				keyframeComboBox.setSelectedItem(next);
				ignoreChanges = false;

				cmd.owner.calculateTiming();
				cmd.incrementModified();
				KeyframeAnimatorEditor.repaintCommandList();
			}

			@Override
			public void undo()
			{
				super.undo();

				cmd.target = prev;

				ignoreChanges = true;
				keyframeComboBox.setSelectedItem(prev);
				ignoreChanges = false;

				cmd.owner.calculateTiming();
				cmd.decrementModified();
				KeyframeAnimatorEditor.repaintCommandList();
			}
		}
	}
}
