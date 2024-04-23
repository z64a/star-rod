package game.globals.editor;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ScrollPaneConstants;

import app.SwingUtils;
import game.globals.ItemRecord;
import net.miginfocom.swing.MigLayout;
import util.ui.DragReorderList;

public final class BadgeReorderDialog extends JDialog
{
	private DialogResult result = DialogResult.NONE;

	public BadgeReorderDialog(DefaultListModel<ItemRecord> listModel, ListCellRenderer<ItemRecord> cellRenderer)
	{
		super(null, java.awt.Dialog.ModalityType.TOOLKIT_MODAL);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		DragReorderList<ItemRecord> list = new DragReorderList<>();
		list.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		list.setCellRenderer(cellRenderer);
		list.setModel(listModel);

		JScrollPane listScrollPane = new JScrollPane(list);
		listScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		listScrollPane.setWheelScrollingEnabled(true);

		JButton selectButton = new JButton("Set Order");
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
		add(SwingUtils.getLabel("Arrange pause menu order by dragging", 14));
		add(listScrollPane, "w 400!, h 600!, push, gapbottom 8");

		add(new JLabel(), "split 3, grow, pushx 2");
		add(selectButton, "w 25%!");
		add(cancelButton, "w 25%!");
	}

	public DialogResult getResult()
	{ return result; }

	public boolean isResultAccepted()
	{ return result == DialogResult.ACCEPT; }
}
