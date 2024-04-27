package game.globals.editor;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;

import app.SwingUtils;
import net.miginfocom.swing.MigLayout;

public final class ListConfirmDialog<T> extends JDialog
{
	private DialogResult result = DialogResult.NONE;

	public ListConfirmDialog(String message, Iterable<T> elements)
	{
		this(message, elements, null);
	}

	public ListConfirmDialog(String message, Iterable<T> elements, ListCellRenderer<T> cellRenderer)
	{
		super(null, java.awt.Dialog.ModalityType.TOOLKIT_MODAL);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		DefaultListModel<T> model = new DefaultListModel<>();
		for (T elem : elements)
			model.addElement(elem);

		JList<T> list = new JList<>();
		list.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		if (cellRenderer != null)
			list.setCellRenderer(cellRenderer);

		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setModel(model);

		JScrollPane listScrollPane = new JScrollPane(list);
		listScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		listScrollPane.setWheelScrollingEnabled(true);

		JButton selectButton = new JButton("Confirm");
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
		add(SwingUtils.getLabel(message, 12), "growx");
		add(listScrollPane, "growx, growy, push, gapbottom 8");

		add(new JLabel(), "split 3, grow, pushx 2");
		add(selectButton, "w 120!");
		add(cancelButton, "w 120!");
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
