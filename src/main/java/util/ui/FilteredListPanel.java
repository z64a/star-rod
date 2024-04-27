package util.ui;

import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
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
import net.miginfocom.swing.MigLayout;

public abstract class FilteredListPanel<T> extends JPanel
{
	private JTextField filterField;
	private JList<T> list;

	private DefaultListModel<T> listModel;
	private FilteredListModel<T> filteredListModel;

	private boolean ignoreChanges = false;

	public abstract String getFilterableText(T element);

	public abstract void handleSelection(T element);

	public FilteredListPanel(ListCellRenderer<T> cellRenderer)
	{
		super(new MigLayout("fill, ins 0, wrap"));

		list = new JList<>();

		list.addListSelectionListener((e) -> {
			if (ignoreChanges || e.getValueIsAdjusting())
				return;
			handleSelection(list.getSelectedValue());
		});

		/*
		list.addMouseListener(new MouseAdapter()
		{
		    public void mouseClicked(MouseEvent evt)
		    {
		    	if (evt.getButton() != MouseEvent.BUTTON1) {
		    		return;
		    	}
		
		    	if (evt.getClickCount() == 1) {
		    		Rectangle rect = list.getCellBounds(0, list.getLastVisibleIndex());
		        	if (rect != null && !rect.contains(evt.getPoint())) {
		        		list.clearSelection();
		        	}
		    	}
		
		        if (evt.getClickCount() == 2) {
		        	Rectangle rect = list.getCellBounds(0, list.getLastVisibleIndex());
		        	if (rect != null && rect.contains(evt.getPoint())) {
		        		int index = list.locationToIndex(evt.getPoint());
		        	}
		        }
		    }
		});
		*/

		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		if (cellRenderer != null) {
			list.setCellRenderer(cellRenderer);
		}

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
		add(filterField, "growx, gaptop 12");
		add(listScrollPane, "grow, push, gaptop 8");

		setContent(null);
	}

	public void setContent(List<T> content)
	{
		if (content == null) {
			content = new ArrayList<>();
		}

		ignoreChanges = true;
		filteredListModel.setIgnoreChanges(true);

		listModel.clear();
		for (T element : content) {
			listModel.addElement(element);
		}

		ignoreChanges = false;
		filteredListModel.setIgnoreChanges(false);

		updateListFilter();

		list.clearSelection();
		if (filteredListModel.getSize() > 0) {
			list.addSelectionInterval(0, 0);
		}
	}

	private void updateListFilter()
	{
		filteredListModel.setFilter(element -> {
			if (element == null)
				return false;

			@SuppressWarnings("unchecked")
			String itemText = getFilterableText((T) element);
			String filterText = filterField.getText();

			return itemText.toUpperCase().contains(filterText.toUpperCase());
		});
	}

	public T getSelected()
	{ return list.getSelectedValue(); }

	public void setSelected(T value)
	{
		list.setSelectedValue(value, true);
	}
}
