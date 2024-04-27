package game.globals.editor.tabs;

import java.awt.Toolkit;
import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

import com.alexandriasoftware.swing.JSplitButton;

import app.SwingUtils;
import assets.ExpectedAsset;
import game.globals.ItemRecord;
import game.globals.MoveRecord;
import game.globals.editor.GlobalsData.GlobalsCategory;
import game.globals.editor.GlobalsEditor;
import game.globals.editor.renderers.MessageCellRenderer;
import game.globals.editor.renderers.MoveListRenderer;
import game.globals.editor.renderers.PaddedCellRenderer;
import game.map.editor.ui.SwingGUI;
import game.message.Message;
import net.miginfocom.swing.MigLayout;
import util.Logger;
import util.MathUtil;
import util.ui.FlagEditorPanel;
import util.ui.IntTextField;
import util.ui.ListSelectorDialog;
import util.ui.StringField;

public class MoveTab extends SingleListTab<MoveRecord>
{
	private JComboBox<String> actionTipBox;
	private JComboBox<String> moveTypeBox;

	private JLabel titleNameLabel;
	private StringField nameField;

	private StringField nameMsgField;
	private StringField shortDescMsgField;
	private StringField fullDescMsgField;

	private JLabel nameMsgPreview;
	private JLabel fullDescMsgPreview;
	private JLabel shortDescMsgPreview;

	private JTextArea flagsTextArea;

	private IntTextField fpCostField;
	private IntTextField bpCostField;

	public MoveTab(GlobalsEditor editor, int tabIndex)
	{
		super(editor, tabIndex, editor.data.moves);

		InputMap im = list.getInputMap();
		im.put(KeyStroke.getKeyStroke("control C"), "copy_data");
		im.put(KeyStroke.getKeyStroke("control V"), "paste_data");

		ActionMap am = list.getActionMap();
		am.put("copy_data", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				copyFromSelected();
			}
		});
		am.put("paste_data", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				pasteIntoSelected();
			}
		});
	}

	@Override
	protected String getTabName()
	{
		return "Moves";
	}

	@Override
	protected ExpectedAsset getIcon()
	{
		return ExpectedAsset.ICON_POWER_JUMP;
	}

	@Override
	protected GlobalsCategory getDataType()
	{
		return GlobalsCategory.MOVE_TABLE;
	}

	@Override
	protected void notifyDataChange(GlobalsCategory type)
	{
		if (type != GlobalsCategory.MOVE_TABLE)
			return;

		reacquireSelection();
		repaintList();
	}

	private void copyFromSelected()
	{
		clipboard = getSelected();
	}

	private void pasteIntoSelected()
	{
		MoveRecord item = getSelected();
		if (item == null || clipboard == null) {
			Toolkit.getDefaultToolkit().beep();
			return;
		}
		item.copyFrom(clipboard);
		onModelDataChange();
		setModified();

		updateInfoPanel(item);
		repaintList();
	}

	private Message chooseMessage(String title)
	{
		ListSelectorDialog<Message> chooser = new ListSelectorDialog<>(editor.messageListModel, new MessageCellRenderer(48));

		SwingUtils.showModalDialog(chooser, title);
		if (!chooser.isResultAccepted())
			return null;

		Message selected = chooser.getValue();
		if (selected == null)
			return null;

		return selected;
	}

	private void updateMessageField(StringField field, JLabel lbl, String identifier)
	{
		field.setText(identifier);

		Message msg = editor.getMessage(identifier);
		if (msg == null) {
			field.setForeground(SwingUtils.getRedTextColor());
			lbl.setForeground(SwingUtils.getRedTextColor());
			lbl.setText("Unknown identifier");
		}
		else {
			field.setForeground(null);
			lbl.setForeground(null);

			String s = msg.toString();
			if (s.length() > 60) {
				s = s.substring(0, 60);
				int lastSpace = s.lastIndexOf(" ");
				if (lastSpace > 48)
					s = s.substring(0, lastSpace);
				else
					s = s.substring(0, 56);
				s += " ...";
			}
			lbl.setText(s);
		}
	}

	@Override
	protected void addListButtons(JPanel listPanel)
	{
		JButton addButton = new JButton("Add Move");
		addButton.addActionListener((e) -> {
			if (listModel.getSize() > 0xFF) {
				Logger.log("Can't add any more moves!");
				Toolkit.getDefaultToolkit().beep();
				return;
			}

			MoveRecord newMove = new MoveRecord(listModel.size());
			newMove.setName("NameMissing");
			listModel.addElement(newMove);
			onModelDataChange();

			updateListFilter();
			list.setSelectedValue(newMove, false);
			list.ensureIndexIsVisible(list.getSelectedIndex());
			setModified();
		});
		SwingUtils.addBorderPadding(addButton);

		JSplitButton actionsButton = new JSplitButton("Actions  ");
		JPopupMenu actionsPopup = new JPopupMenu();
		actionsButton.setPopupMenu(actionsPopup);
		actionsButton.setAlwaysPopup(true);
		SwingUtils.addBorderPadding(actionsButton);
		JMenuItem menuItem;

		menuItem = new JMenuItem("Copy Data");
		menuItem.setPreferredSize(POPUP_OPTION_SIZE);
		menuItem.addActionListener(e -> {
			copyFromSelected();
		});
		actionsPopup.add(menuItem);

		menuItem = new JMenuItem("Paste Data");
		menuItem.setPreferredSize(POPUP_OPTION_SIZE);
		menuItem.addActionListener(e -> {
			pasteIntoSelected();
		});
		actionsPopup.add(menuItem);

		menuItem = new JMenuItem("Clear Data");
		menuItem.setPreferredSize(POPUP_OPTION_SIZE);
		menuItem.addActionListener(e -> {
			MoveRecord move = getSelected();
			if (move == null) {
				Toolkit.getDefaultToolkit().beep();
				return;
			}
			MoveRecord newMove = new MoveRecord(move.listIndex);
			newMove.setName("NameMissing");
			move.copyFrom(newMove);
			onModelDataChange();
			setModified();

			updateInfoPanel(move);
			repaintList();
		});
		actionsPopup.add(menuItem);

		menuItem = new JMenuItem("Discard Changes");
		menuItem.setPreferredSize(POPUP_OPTION_SIZE);
		menuItem.addActionListener(e -> {
			MoveRecord move = getSelected();
			if (move == null) {
				Toolkit.getDefaultToolkit().beep();
				return;
			}
			if (move.getModified()) {
				move.restoreBackup();
				onModelDataChange();
				updateInfoPanel(move);
				repaintList();
			}
		});
		actionsPopup.add(menuItem);

		listPanel.add(addButton, "span, split 2, grow, sg but");
		listPanel.add(actionsButton, "grow, sg but");
	}

	@Override
	protected JPanel createInfoPanel(JLabel infoLabel)
	{
		titleNameLabel = SwingUtils.getLabel("???", 14);
		titleNameLabel.setIconTextGap(12);

		nameMsgPreview = new JLabel();
		fullDescMsgPreview = new JLabel();
		shortDescMsgPreview = new JLabel();

		nameField = new StringField(SwingConstants.LEADING, (s) -> {
			if (hasSelected() && !s.isBlank()) {
				MoveRecord move = getSelected();
				if (!s.equals(move.name)) {
					String oldValue = move.name;
					move.setName(s);
					updateInfoPanel(move);
					setModified();
					repaintList();

					listModel.rebuildNameCache();

					for (ItemRecord item : editor.data.items) {
						if (item.moveName != null && item.moveName.equals(oldValue))
							item.moveName = s;
					}
				}
			}
		});
		SwingUtils.addTextFieldFilter(nameField, "\\W+");

		nameMsgField = new StringField(SwingConstants.LEADING, (s) -> {
			MoveRecord move = getSelected();
			if (move != null && !s.equals(move.msgName)) {
				move.msgName = s;
				updateInfoPanel(move);
				setModified();
			}
		});
		SwingUtils.addTextFieldFilter(nameMsgField, "\\s+");

		shortDescMsgField = new StringField(SwingConstants.LEADING, (s) -> {
			MoveRecord move = getSelected();
			if (move != null && !s.equals(move.msgShortDesc)) {
				move.msgShortDesc = s;
				updateInfoPanel(move);
				setModified();
			}
		});
		SwingUtils.addTextFieldFilter(shortDescMsgField, "\\s+");

		fullDescMsgField = new StringField(SwingConstants.LEADING, (s) -> {
			MoveRecord move = getSelected();
			if (move != null && !s.equals(move.msgFullDesc)) {
				move.msgFullDesc = s;
				updateInfoPanel(move);
				setModified();
			}
		});
		SwingUtils.addTextFieldFilter(fullDescMsgField, "\\s+");

		JButton chooseNameButton = new JButton("Choose");
		chooseNameButton.addActionListener((e) -> {
			if (hasSelected()) {
				MoveRecord move = getSelected();
				Message msg = chooseMessage("Choose Name");
				if (msg != null) {
					String newValue = "MSG_" + msg.getIdentifier();
					if (!newValue.equals(move.msgName)) {
						move.msgName = newValue;
						updateInfoPanel(move);
						setModified();
					}
				}
			}
		});
		SwingUtils.addBorderPadding(chooseNameButton);

		JButton chooseFullDescButton = new JButton("Choose");
		chooseFullDescButton.addActionListener((e) -> {
			if (hasSelected()) {
				MoveRecord move = getSelected();
				Message msg = chooseMessage("Choose Full Description");
				if (msg != null) {
					String newValue = "MSG_" + msg.getIdentifier();
					if (!newValue.equals(move.msgFullDesc)) {
						move.msgFullDesc = newValue;
						updateInfoPanel(move);
						setModified();
					}
				}
			}
		});
		SwingUtils.addBorderPadding(chooseFullDescButton);

		JButton chooseShortDescButton = new JButton("Choose");
		chooseShortDescButton.addActionListener((e) -> {
			if (hasSelected()) {
				MoveRecord move = getSelected();
				Message msg = chooseMessage("Choose Short Description");
				if (msg != null) {
					String newValue = "MSG_" + msg.getIdentifier();
					if (!newValue.equals(move.msgShortDesc)) {
						move.msgShortDesc = newValue;
						updateInfoPanel(move);
						setModified();
					}
				}
			}
		});
		SwingUtils.addBorderPadding(chooseShortDescButton);

		flagsTextArea = new JTextArea();
		flagsTextArea.setEditable(false);
		flagsTextArea.setLineWrap(true);
		flagsTextArea.setWrapStyleWord(true);
		flagsTextArea.setBorder(UIManager.getLookAndFeel().getDefaults().getBorder("TextField.border"));
		JButton flagsButton = new JButton("Choose");
		flagsButton.addActionListener((e) -> {
			if (hasSelected()) {
				MoveRecord move = getSelected();
				FlagEditorPanel flagPanel = new FlagEditorPanel(move.targetFlags);

				int choice = SwingUtils.getConfirmDialog()
					.setParent(SwingGUI.instance())
					.setTitle("Set Flags")
					.setMessage(flagPanel)
					.setOptionsType(JOptionPane.OK_CANCEL_OPTION)
					.choose();

				if (choice == JOptionPane.YES_OPTION) {
					int newValue = flagPanel.getValue();
					if (newValue != move.targetFlags.getBits()) {
						move.targetFlags.setBits(newValue);
						setModified();

						flagsTextArea.setText(String.join(", ", move.targetFlags.getSelectedDisp()));
					}
				}
			}
		});
		SwingUtils.addBorderPadding(flagsButton);

		fpCostField = new IntTextField((v) -> {
			if (hasSelected()) {
				MoveRecord move = getSelected();
				byte newValue = (byte) (int) v;
				if (newValue != move.fpCost) {
					move.fpCost = newValue;
					setModified();
				}
			}
		});
		SwingUtils.addBorderPadding(fpCostField);

		bpCostField = new IntTextField((v) -> {
			if (hasSelected()) {
				MoveRecord move = getSelected();
				byte newValue = (byte) (int) v;
				if (newValue != move.bpCost) {
					move.bpCost = newValue;
					setModified();
				}
			}
		});
		SwingUtils.addBorderPadding(bpCostField);

		actionTipBox = new JComboBox<>();
		actionTipBox.setModel(editor.actionTips);
		actionTipBox.setRenderer(new PaddedCellRenderer<String>(128));
		actionTipBox.setMaximumRowCount(16);
		actionTipBox.addActionListener((e) -> {
			if (!hasSelected() || shouldIgnoreChanges())
				return;
			MoveRecord move = getSelected();
			String displayed = (String) actionTipBox.getSelectedItem();
			String real = editor.getRealActionTip(displayed);
			if (!move.actionTip.equals(real)) {
				move.actionTip = real;
				setModified();
			}
		});
		SwingUtils.addBorderPadding(actionTipBox);

		moveTypeBox = new JComboBox<>();
		moveTypeBox.setModel(editor.moveTypes);
		moveTypeBox.setRenderer(new PaddedCellRenderer<String>());
		moveTypeBox.setMaximumRowCount(16);
		moveTypeBox.addActionListener((e) -> {
			if (!hasSelected() || shouldIgnoreChanges())
				return;
			MoveRecord move = getSelected();
			String newValue = (String) moveTypeBox.getSelectedItem();
			if (!move.category.equals(newValue)) {
				move.category = newValue;
				setModified();
			}

		});
		SwingUtils.addBorderPadding(moveTypeBox);

		JPanel infoPanel = new JPanel(new MigLayout("ins 0, fill, hidemode 3", "[80!]5[280!]5[90!][grow]"));
		infoPanel.add(titleNameLabel, "span, h 32!");

		infoPanel.add(SwingUtils.getLabel("Name", 12));
		infoPanel.add(nameField, "grow, wrap");

		infoPanel.add(new JLabel(""));
		infoPanel.add(nameMsgField, "grow");
		infoPanel.add(chooseNameButton, "grow, wrap");
		infoPanel.add(nameMsgPreview, "skip 1, span, grow, wrap");

		infoPanel.add(SwingUtils.getLabelWithTooltip("Short Desc", "Message used in shops"));
		infoPanel.add(shortDescMsgField, "grow");
		infoPanel.add(chooseShortDescButton, "grow, wrap");
		infoPanel.add(shortDescMsgPreview, "skip 1, span, grow, wrap");

		infoPanel.add(SwingUtils.getLabelWithTooltip("Full Desc", "Message used in most menus"));
		infoPanel.add(fullDescMsgField, "grow");
		infoPanel.add(chooseFullDescButton, "grow, wrap");
		infoPanel.add(fullDescMsgPreview, "skip 1, span, grow, wrap");

		infoPanel.add(new JLabel(), "span, h 4!, wrap");

		infoPanel.add(SwingUtils.getLabel("Target Flags", 12));
		infoPanel.add(flagsTextArea, "grow");
		infoPanel.add(flagsButton, "grow, wrap");

		infoPanel.add(new JLabel(), "span, h 4!, wrap");

		infoPanel.add(SwingUtils.getLabelWithTooltip("Input Popup", "Sets the message explaining the action command controls."));
		infoPanel.add(actionTipBox, "grow, wrap");

		infoPanel.add(SwingUtils.getLabelWithTooltip("Move Type", "Mostly determines where this move will appear in the battle menu."));
		infoPanel.add(moveTypeBox, "grow, wrap");

		infoPanel.add(SwingUtils.getLabel("FP Cost", 12));
		infoPanel.add(fpCostField, "w 132!, wrap");

		infoPanel.add(SwingUtils.getLabel("BP Cost", 12));
		infoPanel.add(bpCostField, "w 132!, wrap");

		JPanel embedPanel = new JPanel(new MigLayout("ins 8 16 8 16, fill, wrap"));
		embedPanel.add(infoPanel, "growx");
		embedPanel.add(new JLabel(), "growy, pushy");
		embedPanel.add(infoLabel, "growx");

		return embedPanel;
	}

	@Override
	protected void updateInfoPanel(MoveRecord move, boolean fromSet)
	{
		titleNameLabel.setText(move.enumName);
		nameField.setText(move.name);

		updateMessageField(nameMsgField, nameMsgPreview, move.msgName);
		updateMessageField(shortDescMsgField, shortDescMsgPreview, move.msgShortDesc);
		updateMessageField(fullDescMsgField, fullDescMsgPreview, move.msgFullDesc);

		flagsTextArea.setText(String.join(", ", move.targetFlags.getSelectedDisp()));

		String displayed = editor.getDispActionTip(move.actionTip);
		actionTipBox.setSelectedItem(displayed);

		moveTypeBox.setSelectedItem(move.category);

		fpCostField.setValue(MathUtil.clamp(move.fpCost, 0, 255));
		bpCostField.setValue(MathUtil.clamp(move.bpCost, 0, 255));
	}

	@Override
	protected ListCellRenderer<MoveRecord> getCellRenderer()
	{
		return new MoveListRenderer();
	}
}
