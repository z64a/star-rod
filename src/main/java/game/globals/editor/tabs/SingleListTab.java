package game.globals.editor.tabs;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

import app.SwingUtils;
import game.globals.editor.GlobalsEditor;
import game.globals.editor.GlobalsEditor.GlobalEditorTab;
import game.globals.editor.GlobalsListModel;
import game.globals.editor.GlobalsRecord;
import net.miginfocom.swing.MigLayout;
import util.Logger;
import util.Logger.Message;
import util.ui.FadingLabel;
import util.ui.FilteredListModel;
import util.ui.FilteredListModel.ListFilter;

public abstract class SingleListTab<T extends GlobalsRecord> extends GlobalEditorTab implements Logger.Listener
{
	public static final Dimension POPUP_OPTION_SIZE = new Dimension(150, 24);
	private static final float MESSAGE_HOLD_TIME = 4.0f;
	private static final float MESSAGE_FADE_TIME = 0.5f;

	protected JList<T> list;
	protected GlobalsListModel<T> listModel;
	private FilteredListModel<T> filteredListModel;

	private boolean ignoreChanges = false;
	private T selectedObject;
	protected T clipboard;

	private FadingLabel infoLabel;
	private JTextField filterTextField;
	private final JPanel listPanel;
	private final JPanel infoPanel;

	protected SingleListTab(GlobalsEditor editor, int tabIndex, GlobalsListModel<T> listModel)
	{
		super(editor, tabIndex);

		this.listModel = listModel;
		onModelDataChange();

		listPanel = new JPanel(new MigLayout("fill, ins 0, wrap"));
		infoLabel = new FadingLabel(true, SwingConstants.LEFT, MESSAGE_HOLD_TIME, MESSAGE_FADE_TIME);

		createListPanel(listModel);
		infoPanel = createInfoPanel(infoLabel);

		if (listModel.size() > 0)
			list.setSelectedIndex(0);
		else
			setSelected(null);

		setLayout(new MigLayout("fill"));
		add(listPanel, "w 270!, growy");
		add(infoPanel, "grow, push");

		Logger.addListener(this);
	}

	private final void createListPanel(DefaultListModel<T> listModel)
	{
		list = new JList<>();
		list.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		ListCellRenderer<T> cellRenderer = getCellRenderer();
		if (cellRenderer != null)
			list.setCellRenderer(cellRenderer);

		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.addListSelectionListener((e) -> {
			if (e.getValueIsAdjusting())
				return;
			setSelected(list.getSelectedValue());
		});

		list.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent evt)
			{
				if (SwingUtilities.isLeftMouseButton(evt) && evt.getClickCount() == 1) {
					if (list.getSelectedIndex() != -1) {
						int index = list.locationToIndex(evt.getPoint());
						if (index >= 0)
							setSelected(list.getModel().getElementAt(index));
					}
				}
			}
		});

		list.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "Delete");
		list.getActionMap().put("Delete", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent evt)
			{
				if (!list.isSelectionEmpty() && listModel.size() > 1) {
					int selectedIndex = list.getSelectedIndex();
					T selectedObject = list.getSelectedValue();
					if (!selectedObject.canDeleteFromList()) {
						Toolkit.getDefaultToolkit().beep();
						Logger.log("Can't delete " + selectedObject.toString());
						return;
					}

					try {
						listModel.removeElement(selectedObject);
						selectedObject.setIndex(GlobalsRecord.DELETED);
						onModelDataChange();
						updateListFilter();
						setModified();

						int newIndex = selectedIndex;
						if (filteredListModel.getSize() == selectedIndex)
							newIndex = selectedIndex - 1;

						list.setSelectedIndex(newIndex);
					}
					catch (IndexOutOfBoundsException e) {}
				}
			}
		});

		filteredListModel = new FilteredListModel<>(listModel);
		list.setModel(filteredListModel);

		listModel.addListDataListener(new ListDataListener() {
			@Override
			public void intervalAdded(ListDataEvent e)
			{
				onModelDataChange();
			}

			@Override
			public void intervalRemoved(ListDataEvent e)
			{
				onModelDataChange();
			}

			@Override
			public void contentsChanged(ListDataEvent e)
			{
				onModelDataChange();
			}
		});

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

		listPanel.add(SwingUtils.getLabel("Filter:", 12), "w 40!, split 2");
		listPanel.add(filterTextField, "growx");
		listPanel.add(listScrollPane, "grow, push");
		addListButtons(listPanel);
	}

	protected final void updateListFilter()
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

	@Override
	public void setModified()
	{
		super.setModified();

		if (selectedObject != null) {
			selectedObject.setModified(true);
			list.repaint();
		}
	}

	@Override
	public void clearModified()
	{
		super.clearModified();

		for (T obj : listModel)
			obj.setModified(false);

		list.repaint();
	}

	@Override
	public void post(Message msg)
	{
		Color c = null;
		switch (msg.priority) {
			case WARNING:
			case ERROR:
				c = SwingUtils.getRedTextColor();
				break;
			default:
				c = SwingUtils.getTextColor();
				break;
		}
		infoLabel.setMessage(msg.text, c);
	}

	protected void onModelDataChange()
	{
		for (int i = 0; i < listModel.getSize(); i++)
			listModel.get(i).setIndex(i);
	}

	protected final void reacquireSelection()
	{
		boolean foundMatch = false;
		for (int i = 0; i < listModel.size(); i++) {
			T newObject = listModel.get(i);
			T selected = getSelected();
			if (selected != null && selected.getIndex() == newObject.getIndex()) {
				setSelected(newObject);
				foundMatch = true;
			}
		}

		if (!foundMatch) {
			if (listModel.size() > 0)
				setSelected(listModel.get(0));
			else
				setSelected(null);
		}
	}

	// for children to access

	protected final void repaintList()
	{
		list.repaint();
	}

	protected final void setSelected(T object)
	{
		selectedObject = object;

		if (object == null) {
			infoPanel.setVisible(false);
			return;
		}

		infoPanel.setVisible(true);

		ignoreChanges = true;
		updateInfoPanel(object, true);
		ignoreChanges = false;
	}

	protected final boolean hasSelected()
	{
		return selectedObject != null;
	}

	protected final T getSelected()
	{ return selectedObject; }

	protected final boolean shouldIgnoreChanges()
	{
		return ignoreChanges;
	}

	protected final void updateInfoPanel(T object)
	{
		updateInfoPanel(object, false);
	}

	// for children to implement

	protected abstract void addListButtons(JPanel listPanel);

	protected abstract JPanel createInfoPanel(JLabel infoLabel);

	protected abstract void updateInfoPanel(T object, boolean fromSet);

	protected ListCellRenderer<T> getCellRenderer()
	{ return null; }
}
