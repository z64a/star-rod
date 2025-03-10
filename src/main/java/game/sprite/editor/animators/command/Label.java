package game.sprite.editor.animators.command;

import static game.sprite.SpriteKey.ATTR_NAME;
import static game.sprite.SpriteKey.TAG_CMD_LABEL;

import java.awt.Component;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

import org.w3c.dom.Element;

import app.SwingUtils;
import common.commands.AbstractCommand;
import game.sprite.editor.SpriteEditor;
import net.miginfocom.swing.MigLayout;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

//Used with Goto and Loop to specify targets
public class Label extends AnimCommand
{
	public String name;

	// serialization only
	public int listPos;

	// only used during conversion from Keyframes
	public transient boolean inUse;

	public Label(CommandAnimator animator)
	{
		this(animator, "New Label");
	}

	public Label(CommandAnimator animator, String name)
	{
		super(animator);

		this.name = name;
		animator.labels.addElement(this);
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag tag = xmw.createTag(TAG_CMD_LABEL, true);
		xmw.addAttribute(tag, ATTR_NAME, name);
		xmw.printTag(tag);
	}

	@Override
	public void fromXML(XmlReader xmr, Element elem)
	{
		if (xmr.hasAttribute(elem, ATTR_NAME))
			name = xmr.getAttribute(elem, ATTR_NAME);
	}

	@Override
	public Label copy()
	{
		return new Label(animator, name);
	}

	@Override
	public AdvanceResult apply()
	{
		return AdvanceResult.NEXT;
	}

	@Override
	public String getName()
	{
		return "Label";
	}

	@Override
	public String toString()
	{
		return "<html>" + SwingUtils.makeFontTag(SwingUtils.getGreenTextColor())
			+ "Label: <i>" + name + "</i></font></html>";
	}

	@Override
	public int length()
	{
		return 0;
	}

	@Override
	public Component getPanel()
	{
		LabelPanel.instance().bind(this);
		return LabelPanel.instance();
	}

	@Override
	public void addTo(List<Short> seq)
	{
		throw new RuntimeException("Tried to add label to command sequence.");
	}

	private static class LabelPanel extends JPanel
	{
		private static LabelPanel instance;
		private Label cmd;

		private LabelTextField labelNameField;
		private boolean ignoreChanges = false;

		private static LabelPanel instance()
		{
			if (instance == null)
				instance = new LabelPanel();
			return instance;
		}

		private LabelPanel()
		{
			super(new MigLayout(CommandAnimatorEditor.PANEL_LAYOUT_PROPERTIES));

			labelNameField = new LabelTextField(() -> {
				if (ignoreChanges)
					return;

				// need to delay the command so we dont call setText while document is still processing events
				SwingUtilities.invokeLater(() -> {
					SpriteEditor.execute(new SetCommandLabelName(cmd, labelNameField.getText()));
				});
			});

			labelNameField.setMargin(SwingUtils.TEXTBOX_INSETS);
			labelNameField.setBorder(BorderFactory.createCompoundBorder(
				labelNameField.getBorder(),
				BorderFactory.createEmptyBorder(2, 4, 2, 4)));
			SwingUtils.setFontSize(labelNameField, 14);

			add(SwingUtils.getLabel("Label Name", 14), "gapbottom 4");
			add(labelNameField, "growx, pushx");
		}

		private void bind(Label cmd)
		{
			this.cmd = cmd;

			ignoreChanges = true;
			labelNameField.setText(cmd.name);
			ignoreChanges = false;
		}

		private class SetCommandLabelName extends AbstractCommand
		{
			private final Label cmd;
			private final String next;
			private final String prev;

			private SetCommandLabelName(Label cmd, String next)
			{
				super("Set Label Name");

				this.cmd = cmd;
				this.next = next;
				this.prev = cmd.name;
			}

			@Override
			public void exec()
			{
				super.exec();

				cmd.name = next;

				ignoreChanges = true;
				labelNameField.setText(next);
				ignoreChanges = false;

				cmd.incrementModified();
				CommandAnimatorEditor.repaintCommandList();
			}

			@Override
			public void undo()
			{
				super.undo();

				cmd.name = prev;

				ignoreChanges = true;
				labelNameField.setText(prev);
				ignoreChanges = false;

				cmd.decrementModified();
				CommandAnimatorEditor.repaintCommandList();
			}
		}
	}

	private static class LabelTextField extends JTextField
	{
		private static class LimitedDocumentFilter extends DocumentFilter
		{
			private int limit;

			public LimitedDocumentFilter(int limit)
			{
				if (limit <= 0) {
					throw new IllegalArgumentException("Limit can not be <= 0");
				}
				this.limit = limit;
			}

			@Override
			public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException
			{
				int currentLength = fb.getDocument().getLength();
				int overLimit = (currentLength + text.length()) - limit - length;
				if (overLimit > 0) {
					text = text.substring(0, text.length() - overLimit);
				}
				if (text.length() > 0) {
					super.replace(fb, offset, length, text, attrs);
				}
			}
		}

		private static class MyDocumentListener implements DocumentListener
		{
			Runnable runnable;

			public MyDocumentListener(Runnable r)
			{
				this.runnable = r;
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				runnable.run();
			}

			@Override
			public void insertUpdate(DocumentEvent e)
			{
				runnable.run();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				runnable.run();
			}
		}

		public LabelTextField(Runnable r)
		{
			setColumns(12);
			((AbstractDocument) getDocument()).setDocumentFilter(new LimitedDocumentFilter(20));
			getDocument().addDocumentListener(new MyDocumentListener(r));
		}
	}
}
