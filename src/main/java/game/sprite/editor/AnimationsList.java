package game.sprite.editor;

import java.awt.Component;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;

import app.SwingUtils;
import common.commands.CommandBatch;
import game.sprite.Sprite;
import game.sprite.SpriteAnimation;
import game.sprite.editor.commands.CreateAnimation;
import game.sprite.editor.commands.DeleteAnimation;
import game.sprite.editor.commands.ReorderAnimation;
import game.sprite.editor.commands.SelectAnimation;
import net.miginfocom.swing.MigLayout;
import util.Logger;
import util.ui.DragReorderList;
import util.ui.DragReorderTransferHandle;

public class AnimationsList extends DragReorderList<SpriteAnimation>
{
	private final SpriteEditor editor;
	private SpriteAnimation animClipboard = null;

	public boolean ignoreSelectionChange = false;

	public AnimationsList(SpriteEditor editor)
	{
		this.editor = editor;
		editor.registerDragList(this);

		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		setCellRenderer(new SpriteAnimCellRenderer());

		setTransferHandler(new SpriteAnimTransferHandle());

		addListSelectionListener((e) -> {
			if (e.getValueIsAdjusting())
				return;

			AnimationsList list = AnimationsList.this;
			if (!ignoreSelectionChange)
				SpriteEditor.execute(new SelectAnimation(list, list.getSelectedValue()));
		});

		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e)
			{
				// double click to rename
				if (e.getClickCount() == 2) {
					int index = locationToIndex(e.getPoint());
					if (index != -1) {
						SpriteAnimation anim = getModel().getElementAt(index);
						promptRenameAnim(anim);
					}
				}
			}
		});

		addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e)
			{
				// rename with 'F2' key
				if (e.getKeyCode() == KeyEvent.VK_F2) {
					int index = getSelectedIndex();
					if (index != -1) {
						SpriteAnimation anim = getModel().getElementAt(index);
						promptRenameAnim(anim);
					}
				}
			}
		});

		InputMap im = getInputMap(JComponent.WHEN_FOCUSED);
		ActionMap am = getActionMap();

		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK), "copy");
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK), "paste");
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK), "duplicate");
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete");

		am.put("copy", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				SpriteAnimation cur = getSelectedValue();
				if (cur == null) {
					Toolkit.getDefaultToolkit().beep();
					return;
				}
				animClipboard = cur.copy();
			}
		});

		am.put("paste", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				int i = getSelectedIndex();
				if (i == -1 || animClipboard == null || animClipboard.parentSprite != editor.getSprite()) {
					Toolkit.getDefaultToolkit().beep();
					return;
				}

				if (getDefaultModel().size() >= Sprite.MAX_ANIMATIONS) {
					Logger.logError("Cannot have more than " + Sprite.MAX_ANIMATIONS + " animations!");
					Toolkit.getDefaultToolkit().beep();
					return;
				}

				SpriteAnimation copy = animClipboard.copy();
				if (!copy.assignUniqueName(copy.name)) {
					Logger.logError("Could not generate unique name for " + copy.name);
					Toolkit.getDefaultToolkit().beep();
					return;
				}

				SpriteEditor.execute(new CreateAnimation("Paste Animation", editor.getSprite(), copy, i + 1));

			}
		});

		am.put("duplicate", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				SpriteAnimation cur = getSelectedValue();
				if (cur == null) {
					Toolkit.getDefaultToolkit().beep();
					return;
				}

				if (getDefaultModel().size() >= Sprite.MAX_ANIMATIONS) {
					Logger.logError("Cannot have more than " + Sprite.MAX_ANIMATIONS + " animations!");
					Toolkit.getDefaultToolkit().beep();
					return;
				}

				int i = getSelectedIndex();
				SpriteAnimation copy = cur.copy();

				if (!copy.assignUniqueName(copy.name)) {
					Logger.logError("Could not generate unique name for " + copy.name);
					Toolkit.getDefaultToolkit().beep();
					return;
				}

				SpriteEditor.execute(new CreateAnimation("Duplicate Animation", editor.getSprite(), copy, i + 1));
			}
		});

		am.put("delete", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				int i = getSelectedIndex();
				if (i == -1) {
					Toolkit.getDefaultToolkit().beep();
					return;
				}

				CommandBatch batch = new CommandBatch("Delete Component");
				batch.addCommand(new SelectAnimation(AnimationsList.this, null));
				batch.addCommand(new DeleteAnimation(editor.getSprite(), i));
				SpriteEditor.execute(batch);
			}
		});
	}

	private void promptRenameAnim(SpriteAnimation anim)
	{
		String name = SwingUtils.getInputDialog()
			.setParent(editor.getFrame())
			.setTitle("Set Animation Name")
			.setMessage("Choose a unique animation name")
			.setMessageType(JOptionPane.QUESTION_MESSAGE)
			.setDefault(anim.name)
			.prompt();

		if (name == null) {
			// prompt canceled
			return;
		}

		name = name.trim();

		if (name.isBlank() || name.equals(anim.name)) {
			// invalid name provided
			return;
		}

		boolean success = anim.assignUniqueName(name);
		if (!success) {
			Toolkit.getDefaultToolkit().beep();
		}
	}

	private class SpriteAnimTransferHandle extends DragReorderTransferHandle<SpriteAnimation>
	{
		@Override
		public void dropAction(SpriteAnimation anim, int dropIndex)
		{
			SpriteEditor.execute(new ReorderAnimation(AnimationsList.this, anim, dropIndex));
		}
	}

	private static class SpriteAnimCellRenderer extends JPanel implements ListCellRenderer<SpriteAnimation>
	{
		private JLabel nameLabel;
		private JLabel idLabel;

		public SpriteAnimCellRenderer()
		{
			idLabel = new JLabel();
			nameLabel = new JLabel();

			setLayout(new MigLayout("ins 0, fillx"));
			add(idLabel, "gapleft 16, w 32!");
			add(nameLabel, "growx, pushx, gapright push");

			setOpaque(true);
		}

		@Override
		public Component getListCellRendererComponent(
			JList<? extends SpriteAnimation> list,
			SpriteAnimation anim,
			int index,
			boolean isSelected,
			boolean cellHasFocus)
		{
			if (isSelected) {
				setBackground(list.getSelectionBackground());
				setForeground(list.getSelectionForeground());
			}
			else {
				setBackground(list.getBackground());
				setForeground(list.getForeground());
			}

			setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
			if (anim != null) {
				idLabel.setText(String.format("%02X", anim.getIndex()));
				nameLabel.setText(anim.name);
			}
			else {
				idLabel.setText("XXX");
				nameLabel.setText("error!");
			}

			return this;
		}
	}
}
