package game.sprite.editor;

import java.awt.Component;
import java.util.Collection;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import app.SwingUtils;
import game.sprite.SpriteLoader.SpriteMetadata;
import game.sprite.editor.commands.SelectSprite;
import net.miginfocom.swing.MigLayout;
import util.IterableListModel;
import util.ui.FilteredListModel;

public class SpriteList extends JPanel
{
	private JList<SpriteMetadata> list;
	private IterableListModel<SpriteMetadata> listModel;
	private FilteredListModel<SpriteMetadata> filteredListModel;

	private JTextField filterField;

	public boolean ignoreSelectionChange = false;

	public SpriteList(SpriteEditor editor)
	{
		super(new MigLayout("fill, ins 0, wrap"));

		list = new JList<>();
		list.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		list.setCellRenderer(new SpriteMetaCellRenderer());

		listModel = new IterableListModel<>();
		filteredListModel = new FilteredListModel<>(listModel);
		list.setModel(filteredListModel);

		filterField = new JTextField(20);
		filterField.setMargin(SwingUtils.TEXTBOX_INSETS);
		filterField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void changedUpdate(DocumentEvent e)
			{
				updateListFilter();
			}

			@Override
			public void insertUpdate(DocumentEvent e)
			{
				updateListFilter();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				updateListFilter();
			}
		});
		SwingUtils.addBorderPadding(filterField);

		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.addListSelectionListener((e) -> {
			if (e.getValueIsAdjusting())
				return;

			if (!ignoreSelectionChange) {
				SpriteList list = SpriteList.this;
				int newID = list.getSelected() == null ? -1 : list.getSelected().id;
				SpriteEditor.execute(new SelectSprite(list, newID));
			}
		});

		JScrollPane listScrollPane = new JScrollPane(list);
		listScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

		add(SwingUtils.getLabel("Filter:", 12), "w 40!, split 2");
		add(filterField, "growx");
		add(listScrollPane, "grow, push, gaptop 8");
	}

	public void setSprites(Collection<SpriteMetadata> sprites)
	{
		listModel.clear();

		for (SpriteMetadata sprite : sprites) {
			listModel.addElement(sprite);
		}

		updateListFilter();
	}

	public void setSelectedID(int nextID)
	{
		SpriteMetadata match = null;

		for (SpriteMetadata sprite : listModel) {
			if (sprite.id == nextID) {
				match = sprite;
				break;
			}
		}

		list.setSelectedValue(match, true);
	}

	public void setSelectedIndex(int id)
	{
		list.setSelectedIndex(id);
	}

	public int getSelectedIndex()
	{
		return list.getSelectedIndex();
	}

	public SpriteMetadata getSelected()
	{
		return list.getSelectedValue();
	}

	public int getInitialSelection(String name)
	{
		if (name != null && !name.isBlank()) {
			for (int i = 0; i < listModel.size(); i++) {
				SpriteMetadata cur = listModel.get(i);
				if (cur.name.equals(name))
					return i;
			}
		}
		return -1;
	}

	private void updateListFilter()
	{
		filteredListModel.setFilter(element -> {
			SpriteMetadata sprite = (SpriteMetadata) element;
			String needle = String.format("%02X ", sprite.id) + " " + sprite.name.toUpperCase();
			return (needle.contains(filterField.getText().toUpperCase()));
		});
	}

	private static class SpriteMetaCellRenderer extends JPanel implements ListCellRenderer<SpriteMetadata>
	{
		private JLabel nameLabel;
		private JLabel idLabel;

		public SpriteMetaCellRenderer()
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
			JList<? extends SpriteMetadata> list,
			SpriteMetadata sprite,
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
			if (sprite != null) {
				idLabel.setText(String.format("%02X", sprite.id));
				nameLabel.setText(sprite.name);
			}
			else {
				idLabel.setText("XXX");
				nameLabel.setText("error!");
			}

			return this;
		}
	}
}
