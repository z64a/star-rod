package game.sprite.editor.animators.command;

import static game.sprite.SpriteKey.*;

import java.awt.Component;
import java.util.List;

import javax.swing.JComboBox;
import javax.swing.JPanel;

import org.w3c.dom.Element;

import app.SwingUtils;
import common.commands.AbstractCommand;
import game.sprite.SpriteAnimation;
import game.sprite.SpriteComponent;
import game.sprite.editor.Editable;
import game.sprite.editor.SpriteEditor;
import net.miginfocom.swing.MigLayout;
import util.Logger;
import util.ui.ListAdapterComboboxModel;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

//81XX parent to component XX
public class SetParent extends AnimCommand
{
	public SpriteComponent parent;

	// used during deserialization
	public transient String parName = null;
	public transient int parIndex = -1;

	public SetParent(CommandAnimator animator)
	{
		this(animator, (short) 0);
	}

	public SetParent(CommandAnimator animator, short s0)
	{
		super(animator);

		parIndex = (s0 & 0xFF);

		if (parIndex < owner.parentAnimation.components.size())
			parent = owner.parentAnimation.components.get(parIndex);
	}

	public SetParent(CommandAnimator animator, SpriteComponent parent)
	{
		super(animator);

		this.parent = parent;
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag tag = xmw.createTag(TAG_CMD_SET_PAR, true);

		if (SpriteEditor.instance().optOutputNames) {
			if (parent == null)
				xmw.addAttribute(tag, ATTR_NAME, "");
			else
				xmw.addAttribute(tag, ATTR_NAME, parent.name);
		}
		else {
			if (parent == null)
				xmw.addHex(tag, ATTR_INDEX, -1);
			else
				xmw.addHex(tag, ATTR_INDEX, parent.getIndex());
		}

		xmw.printTag(tag);
	}

	@Override
	public void fromXML(XmlReader xmr, Element elem)
	{
		if (xmr.hasAttribute(elem, ATTR_NAME))
			parName = xmr.getAttribute(elem, ATTR_NAME);
		else if (xmr.hasAttribute(elem, ATTR_INDEX))
			parIndex = xmr.readHex(elem, ATTR_INDEX);
	}

	@Override
	public SetParent copy()
	{
		SetParent clone = new SetParent(animator);
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
	public String getFormattedText()
	{
		return toString();
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
			return "SetParent Command: undefined parent";

		if (parent == owner && SpriteEditor.instance().optStrictErrorChecking)
			return "SetParent Command: parented to itself";

		return null;
	}

	protected static class SetParentPanel extends JPanel
	{
		private static SetParentPanel instance;
		private SetParent cmd;

		private JComboBox<SpriteComponent> componentComboBox;
		private boolean ignoreChanges = false;

		protected static SetParentPanel instance()
		{
			if (instance == null)
				instance = new SetParentPanel();
			return instance;
		}

		private SetParentPanel()
		{
			super(new MigLayout(CommandAnimatorEditor.PANEL_LAYOUT_PROPERTIES));

			componentComboBox = new JComboBox<>();
			SwingUtils.setFontSize(componentComboBox, 14);
			componentComboBox.setMaximumRowCount(24);
			componentComboBox.addActionListener((e) -> {
				if (!ignoreChanges) {
					SpriteComponent comp = (SpriteComponent) componentComboBox.getSelectedItem();
					SpriteEditor.execute(new SetCommandParent(cmd, comp));
				}
			});

			add(SwingUtils.getLabel("Set Parent", 14), "gapbottom 4");
			add(componentComboBox, "growx, pushx");
		}

		private void bind(SetParent cmd)
		{
			this.cmd = cmd;
			SpriteAnimation anim = cmd.owner.parentAnimation;

			ignoreChanges = true;
			componentComboBox.setModel(new ListAdapterComboboxModel<>(anim.components));
			componentComboBox.setSelectedItem(cmd.parent);
			ignoreChanges = false;
		}

		private class SetCommandParent extends AbstractCommand
		{
			private final SetParent cmd;
			private final SpriteComponent next;
			private final SpriteComponent prev;

			private SetCommandParent(SetParent cmd, SpriteComponent next)
			{
				super("Set Parent");

				this.cmd = cmd;
				this.next = next;
				this.prev = cmd.parent;
			}

			@Override
			public void exec()
			{
				super.exec();

				cmd.parent = next;

				ignoreChanges = true;
				componentComboBox.setSelectedItem(next);
				ignoreChanges = false;

				cmd.incrementModified();
				CommandAnimatorEditor.repaintCommandList();
			}

			@Override
			public void undo()
			{
				super.undo();

				cmd.parent = prev;

				ignoreChanges = true;
				componentComboBox.setSelectedItem(prev);
				ignoreChanges = false;

				cmd.decrementModified();
				CommandAnimatorEditor.repaintCommandList();
			}
		}
	}
}
