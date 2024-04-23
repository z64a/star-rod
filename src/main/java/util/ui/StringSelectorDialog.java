package util.ui;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import app.SwingUtils;
import net.miginfocom.swing.MigLayout;
import util.ui.FilteredListModel.ListFilter;

public final class StringSelectorDialog extends JDialog
{
	private final FilteredListModel<String> filteredListModel;
	private final JTextField filterTextField;

	private DialogResult result = DialogResult.NONE;
	private String selectedObject;

	public StringSelectorDialog(List<String> strings)
	{
		super(null, java.awt.Dialog.ModalityType.TOOLKIT_MODAL);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		JList<String> list = new JList<>();
		list.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

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

		DefaultListModel<String> listModel = new DefaultListModel<>();
		listModel.addAll(strings);

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
		add(listScrollPane, "w 400!, h 400!, push, gapbottom 8");

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
			public boolean accept(Object element)
			{
				return ((String) element).toUpperCase().contains(filterTextField.getText().toUpperCase());
			}
		});
	}

	public void setValue(String s)
	{ selectedObject = s; }

	public String getValue()
	{ return selectedObject; }

	public DialogResult getResult()
	{ return result; }

	public boolean isResultAccepted()
	{ return result == DialogResult.ACCEPT; }
}
