package game.sprite.editor;

import java.awt.Component;
import java.awt.Font;
import java.awt.Rectangle;
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
import game.sprite.SpriteComponent;
import game.sprite.editor.commands.CreateComponent;
import game.sprite.editor.commands.DeleteComponent;
import game.sprite.editor.commands.RenameComponent;
import game.sprite.editor.commands.ReorderComponent;
import game.sprite.editor.commands.SelectComponent;
import game.sprite.editor.commands.ToggleComponentHidden;
import net.miginfocom.swing.MigLayout;
import util.EnableCounter;
import util.Logger;
import util.ui.DragReorderList;
import util.ui.DragReorderTransferHandle;
import util.ui.ThemedIcon;

public class ComponentsList extends DragReorderList<SpriteComponent>
{
	private static final int EYE_ICON_WIDTH = 16;

	private final SpriteEditor editor;
	private SpriteComponent compClipboard = null;

	public EnableCounter ignoreChanges = new EnableCounter();

	public ComponentsList(SpriteEditor editor)
	{
		this.editor = editor;
		editor.registerDragList(this);
		editor.registerEditableListener(SpriteComponent.class, () -> this.repaint());

		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		setCellRenderer(new SpriteCompCellRenderer());

		setTransferHandler(new SpriteCompTransferHandle());

		// visibility toggles in the component list are not really functional, we implment them
		// via a mouse listener on the list and calculate whether mouse click locations overlap
		// with the open/closed eye icons
		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e)
			{
				int index = locationToIndex(e.getPoint());
				if (index != -1) {
					// double click to rename
					if (e.getClickCount() == 2) {
						SpriteComponent comp = getModel().getElementAt(index);
						promptRenameComp(comp);
					}
					else {
						// test for clicking on visibility toggle icons
						Rectangle cellBounds = getCellBounds(index, index);
						int min = cellBounds.x + 8;
						int max = min + EYE_ICON_WIDTH;
						if (cellBounds != null && e.getX() > min && e.getX() < max) {
							SpriteComponent comp = getModel().getElementAt(index);
							if (comp != null)
								SpriteEditor.execute(new ToggleComponentHidden(ComponentsList.this, comp));
						}
					}
				}
			}
		});

		addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e)
			{
				int index = getSelectedIndex();

				switch (e.getKeyCode()) {
					// rename with 'F2' key
					case KeyEvent.VK_F2:
						if (index != -1) {
							SpriteComponent comp = getModel().getElementAt(index);
							promptRenameComp(comp);
						}
						break;
					// toggle visibility with 'H' key
					case KeyEvent.VK_H:
						if (index != -1) {
							SpriteComponent comp = getModel().getElementAt(index);
							if (comp != null)
								SpriteEditor.execute(new ToggleComponentHidden(ComponentsList.this, comp));
						}
						break;
				}
			}
		});

		addListSelectionListener((e) -> {
			if (e.getValueIsAdjusting())
				return;

			ComponentsList list = ComponentsList.this;
			if (ignoreChanges.disabled())
				SpriteEditor.execute(new SelectComponent(list, list.getSelectedValue()));
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
				SpriteComponent cur = getSelectedValue();
				if (cur == null) {
					Toolkit.getDefaultToolkit().beep();
					return;
				}
				compClipboard = cur.copy();
			}
		});

		am.put("paste", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				int i = getSelectedIndex();
				if (i == -1 || compClipboard == null) {
					Toolkit.getDefaultToolkit().beep();
					return;
				}

				SpriteAnimation curAnim = editor.getAnimation();
				if (curAnim == null) {
					Logger.logError("Can't paste component while animation is null");
					Toolkit.getDefaultToolkit().beep();
					return;
				}

				if (compClipboard.parentAnimation.parentSprite != curAnim.parentSprite) {
					Logger.logError("Can't paste component " + compClipboard.name + " from sprite " + compClipboard.parentAnimation.parentSprite);
					Toolkit.getDefaultToolkit().beep();
					return;
				}

				if (getDefaultModel().size() >= Sprite.MAX_COMPONENTS) {
					Logger.logError("Cannot have more than " + Sprite.MAX_COMPONENTS + " components!");
					Toolkit.getDefaultToolkit().beep();
					return;
				}

				SpriteComponent copy = new SpriteComponent(curAnim, compClipboard);
				String newName = copy.createUniqueName(copy.name);

				if (newName == null) {
					Logger.logError("Could not generate unique name for " + copy.name);
					Toolkit.getDefaultToolkit().beep();
					return;
				}

				copy.name = newName;
				SpriteEditor.execute(new CreateComponent("Paste Component", curAnim, copy, i + 1));
			}
		});

		am.put("duplicate", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				SpriteComponent cur = getSelectedValue();
				if (cur == null) {
					Toolkit.getDefaultToolkit().beep();
					return;
				}

				SpriteAnimation curAnim = editor.getAnimation();
				if (curAnim == null) {
					Logger.logError("Can't duplicate component while animation is null");
					Toolkit.getDefaultToolkit().beep();
					return;
				}

				if (getDefaultModel().size() >= Sprite.MAX_COMPONENTS) {
					Logger.logError("Cannot have more than " + Sprite.MAX_COMPONENTS + " components!");
					Toolkit.getDefaultToolkit().beep();
					return;
				}

				int i = getSelectedIndex();
				SpriteComponent copy = cur.copy();
				String newName = copy.createUniqueName(copy.name);

				if (newName == null) {
					Logger.logError("Could not generate unique name for " + copy.name);
					Toolkit.getDefaultToolkit().beep();
					return;
				}

				copy.name = newName;
				SpriteEditor.execute(new CreateComponent("Duplicate Component", curAnim, copy, i + 1));
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

				SpriteAnimation curAnim = editor.getAnimation();
				if (curAnim == null) {
					Logger.logError("Can't delete component while animation is null");
					Toolkit.getDefaultToolkit().beep();
					return;
				}

				CommandBatch batch = new CommandBatch("Delete Component");
				batch.addCommand(new SelectComponent(ComponentsList.this, null));
				batch.addCommand(new DeleteComponent(curAnim, i));
				SpriteEditor.execute(batch);
			}
		});
	}

	private void promptRenameComp(SpriteComponent comp)
	{
		String name = SwingUtils.getInputDialog()
			.setParent(editor.getFrame())
			.setTitle("Set Component Name")
			.setMessage("Choose a unique component name")
			.setMessageType(JOptionPane.QUESTION_MESSAGE)
			.setDefault(comp.name)
			.prompt();

		if (name == null) {
			// prompt canceled
			return;
		}

		name = name.trim();

		if (name.isBlank() || name.equals(comp.name)) {
			// invalid name provided
			return;
		}

		String newName = comp.createUniqueName(name);
		if (newName == null) {
			Logger.logError("Could not generate valid name from input!");
			Toolkit.getDefaultToolkit().beep();
			return;
		}

		SpriteEditor.execute(new RenameComponent(this, comp, newName));
	}

	private class SpriteCompTransferHandle extends DragReorderTransferHandle<SpriteComponent>
	{
		@Override
		public void dropAction(SpriteComponent comp, int dropIndex)
		{
			SpriteEditor.execute(new ReorderComponent(ComponentsList.this, comp, dropIndex));
		}
	}

	private static class SpriteCompCellRenderer extends JPanel implements ListCellRenderer<SpriteComponent>
	{
		private JLabel iconLabel;
		private JLabel nameLabel;

		public SpriteCompCellRenderer()
		{
			iconLabel = new JLabel();
			nameLabel = new JLabel();

			setLayout(new MigLayout("ins 0, fillx", "8[" + EYE_ICON_WIDTH + "]8[grow]"));
			add(iconLabel, "growx");
			add(nameLabel, "growx");

			setOpaque(true);
		}

		@Override
		public Component getListCellRendererComponent(
			JList<? extends SpriteComponent> list,
			SpriteComponent comp,
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
			if (comp != null) {
				iconLabel.setIcon(comp.hidden ? ThemedIcon.VISIBILITY_OFF_16 : ThemedIcon.VISIBILITY_ON_16);
				nameLabel.setFont(getFont().deriveFont(comp.hidden ? Font.ITALIC : Font.PLAIN));

				if (comp.isModified())
					nameLabel.setText(comp.name + " *");
				else
					nameLabel.setText(comp.name);

				if (comp.hasError())
					nameLabel.setForeground(SwingUtils.getRedTextColor());
				else
					nameLabel.setForeground(null);
			}
			else {
				iconLabel.setIcon(null);
				nameLabel.setText("NULL");
				nameLabel.setForeground(SwingUtils.getRedTextColor());
			}

			return this;
		}
	}
}
