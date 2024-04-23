package game.sprite.editor.dialogs;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;

import net.miginfocom.swing.MigLayout;
import util.ui.DragReorderList;

public abstract class ListEditPanel<T> extends JPanel
{
	protected final DragReorderList<T> list;
	protected final DefaultListModel<T> listModel;

	public ListEditPanel(DefaultListModel<T> listModel)
	{
		this.listModel = listModel;

		list = new DragReorderList<>();
		list.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		list.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "DeleteCommands");
		list.getActionMap().put("DeleteCommands", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				if (!list.isSelectionEmpty()) {
					T obj = list.getSelectedValue();
					if (canDelete(obj)) {
						DefaultListModel<?> model = (DefaultListModel<?>) list.getModel();
						model.removeElement(obj);
						onDelete(obj);
					}
				}
			}
		});

		list.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (e.getClickCount() == 2) {
					int index = list.locationToIndex(e.getPoint());
					String input = JOptionPane.showInputDialog(
						ListEditPanel.this,
						"Enter a new name", "Rename Item",
						JOptionPane.PLAIN_MESSAGE);

					String newName = null;
					if (input != null)
						newName = input.trim();
					else
						return;

					if (!newName.isEmpty())
						rename(index, newName);
				}
			}
		});

		list.setModel(listModel);

		JScrollPane listScrollPane = new JScrollPane(list);
		listScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

		setPreferredSize(new Dimension(300, 400));
		setLayout(new MigLayout("fill, wrap"));
		add(listScrollPane, "grow, pushy, gapbottom 4");
	}

	protected boolean canDelete(T item)
	{
		return true;
	}

	protected void onDelete(T item)
	{}

	public abstract void rename(int index, String newName);
}
