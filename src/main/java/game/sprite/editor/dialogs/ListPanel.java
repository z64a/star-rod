package game.sprite.editor.dialogs;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

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

public abstract class ListPanel<T> extends JPanel
{
	public final DragReorderList<T> list;

	public ListPanel()
	{
		list = new DragReorderList<>();
		list.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.addListSelectionListener((e) -> {
			if (e.getValueIsAdjusting())
				return;

			onSelectEDT(list.getSelectedValue());
		});

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

		//TODO ?
		/*
		list.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (e.getClickCount() == 2)
				{
					int index = list.locationToIndex(e.getPoint());
					promptRenameAt(index, "");
				}
			}
		});
		*/

		JScrollPane listScrollPane = new JScrollPane(list);
		listScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

		setPreferredSize(new Dimension(300, 400));
		setLayout(new MigLayout("fill, wrap, ins 0"));
		add(listScrollPane, "grow, pushy");
	}

	public DefaultListModel<T> getListModel()
	{
		return (DefaultListModel<T>) list.getModel();
	}

	protected boolean canDelete(T item)
	{
		return true;
	}

	protected void onDelete(T item)
	{}

	protected void onSelectEDT(T item)
	{}

	public void promptRenameAt(int index, String oldName)
	{
		String input = (String) JOptionPane.showInputDialog(
			ListPanel.this,
			"Enter a new name", "Rename",
			JOptionPane.PLAIN_MESSAGE,
			null,
			null,
			oldName);

		String newName = null;
		if (input != null)
			newName = input.trim();
		else
			return;

		if (!newName.isEmpty())
			rename(index, newName);
	}

	public abstract void rename(int index, String newName);
}
