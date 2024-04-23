package game.sprite.editor;

import java.awt.Component;
import java.util.Collection;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
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
import net.miginfocom.swing.MigLayout;
import util.ui.FilteredListModel;

public class SpriteList extends JPanel
{
	private JList<SpriteMetadata> list;
	private DefaultListModel<SpriteMetadata> listModel;
	private FilteredListModel<SpriteMetadata> filteredListModel;

	private JTextField filterField;

	public SpriteList(SpriteEditor editor)
	{
		super(new MigLayout("fill, ins 0, wrap"));

		list = new JList<>();
		list.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		list.setCellRenderer(new SpriteMetaCellRenderer());

		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.addListSelectionListener((e) -> {
			if (e.getValueIsAdjusting())
				return;

			editor.invokeLater(() -> {
				if (getSelected() == null)
					editor.setSprite(-1, true);
				else
					editor.setSprite(getSelected().id, false);
			});
		});

		listModel = new DefaultListModel<>();
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

		JScrollPane listScrollPane = new JScrollPane(list);
		listScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		listScrollPane.setWheelScrollingEnabled(true);

		add(SwingUtils.getLabel("Filter:", 12), "w 40!, split 2");
		add(filterField, "growx");
		add(listScrollPane, "grow, push, gaptop 8");
		//add(new JPanel(), "growx, sg but");
	}

	public void setSprites(Collection<SpriteMetadata> sprites)
	{
		listModel.clear();

		for (SpriteMetadata sprite : sprites) {
			listModel.addElement(sprite);
		}

		updateListFilter();
		//   list.repaint();
	}

	public void setSelected(SpriteMetadata sprite)
	{
		list.setSelectedValue(sprite, true);
	}

	public void setSelectedIndex(int id)
	{
		list.setSelectedIndex(id);
	}

	public SpriteMetadata getSelected()
	{ return list.getSelectedValue(); }

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
