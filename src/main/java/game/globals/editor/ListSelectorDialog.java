package game.globals.editor;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import app.SwingUtils;
import net.miginfocom.swing.MigLayout;
import util.ui.FilteredListModel;
import util.ui.FilteredListModel.ListFilter;

public final class ListSelectorDialog<T extends GlobalsListable> extends JDialog
{
	private final FilteredListModel<T> filteredListModel;
	private final JTextField filterTextField;

	private DialogResult result = DialogResult.NONE;
	private T selectedObject;

	public ListSelectorDialog(DefaultListModel<T> listModel)
	{
		this(listModel, null);
	}

	public ListSelectorDialog(DefaultListModel<T> listModel, ListCellRenderer<T> cellRenderer)
	{
		super(null, java.awt.Dialog.ModalityType.TOOLKIT_MODAL);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		JList<T> list = new JList<>();
		list.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		if (cellRenderer != null)
			list.setCellRenderer(cellRenderer);

		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.addListSelectionListener((e) -> {
			if (e.getValueIsAdjusting())
				return;
			setValue(list.getSelectedValue());
		});

		list.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent evt)
			{
				if (SwingUtilities.isLeftMouseButton(evt) && evt.getClickCount() == 1) {
					if (list.getSelectedIndex() != -1) {
						int index = list.locationToIndex(evt.getPoint());
						if (index >= 0)
							setValue(list.getModel().getElementAt(index));
					}
				}
			}
		});

		filteredListModel = new FilteredListModel<>(listModel);
		list.setModel(filteredListModel);

		filterTextField = new JTextField(20);
		filterTextField.setMargin(SwingUtils.TEXTBOX_INSETS);
		filterTextField.getDocument().addDocumentListener(new DocumentListener() {
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
		SwingUtils.addBorderPadding(filterTextField);

		JScrollPane listScrollPane = new JScrollPane(list);
		listScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		listScrollPane.setWheelScrollingEnabled(true);

		JButton selectButton = new JButton("Select");
		SwingUtils.addBorderPadding(selectButton);
		selectButton.addActionListener((e) -> {
			result = DialogResult.ACCEPT;
			dispose();
		});

		JButton cancelButton = new JButton("Cancel");
		SwingUtils.addBorderPadding(cancelButton);
		cancelButton.addActionListener((e) -> {
			result = DialogResult.CANCEL;
			dispose();
		});

		setLayout(new MigLayout("ins 16, fill, wrap"));
		add(SwingUtils.getLabel("Filter:", 12), "w 40!, split 2");
		add(filterTextField, "growx");
		add(listScrollPane, "w 400!, h 600!, push, gapbottom 8");

		add(new JLabel(), "split 3, grow, pushx 2");
		add(selectButton, "w 25%!");
		add(cancelButton, "w 25%!");

		if (listModel.size() > 0)
			list.setSelectedIndex(0);
		else
			setValue(null);
	}

	private void updateListFilter()
	{
		filteredListModel.setFilter(new ListFilter() {
			@Override
			@SuppressWarnings("unchecked")
			public boolean accept(Object element)
			{
				return ((T) element).getFilterableString().toUpperCase().contains(filterTextField.getText().toUpperCase());
			}
		});
	}

	public void setValue(T object)
	{
		selectedObject = object;
	}

	public T getValue()
	{
		return selectedObject;
	}

	public DialogResult getResult()
	{
		return result;
	}

	public boolean isResultAccepted()
	{
		return result == DialogResult.ACCEPT;
	}
}
