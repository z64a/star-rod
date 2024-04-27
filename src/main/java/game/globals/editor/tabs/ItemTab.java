package game.globals.editor.tabs;

import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
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

import app.IconResource;
import app.SwingUtils;
import assets.ExpectedAsset;
import game.globals.IconRecord;
import game.globals.ItemRecord;
import game.globals.MoveRecord;
import game.globals.editor.BadgeReorderDialog;
import game.globals.editor.GlobalsData.GlobalsCategory;
import game.globals.editor.GlobalsEditor;
import game.globals.editor.renderers.BadgeCellRenderer;
import game.globals.editor.renderers.IconBoxRenderer;
import game.globals.editor.renderers.IconListRenderer;
import game.globals.editor.renderers.ItemListRenderer;
import game.globals.editor.renderers.MessageCellRenderer;
import game.globals.editor.renderers.PaddedCellRenderer;
import game.map.editor.ui.SwingGUI;
import game.message.Message;
import net.miginfocom.swing.MigLayout;
import util.Logger;
import util.MathUtil;
import util.ui.FlagEditorPanel;
import util.ui.HexTextField;
import util.ui.IntTextField;
import util.ui.ListSelectorDialog;
import util.ui.StringField;

public class ItemTab extends SingleListTab<ItemRecord>
{
	private JLabel titleIconLabel;
	private JLabel titleNameLabel;
	private StringField nameField;

	private StringField nameMsgField;
	private StringField fullDescMsgField;
	private StringField shortDescMsgField;

	private JLabel nameMsgPreview;
	private JLabel fullDescMsgPreview;
	private JLabel shortDescMsgPreview;

	private JComboBox<String> iconPreviewBox;
	private StringField hudElementField;
	private StringField itemEntityField;

	private JTextArea typeFlagsTextArea;
	private JTextArea targetFlagsTextArea;

	private JLabel moveLabel;
	private JComboBox<String> moveBox;
	private JButton chooseMoveButton;

	private JLabel sellValueLabel;
	private IntTextField sellValueField;

	private JButton editSortValueButton;
	private JLabel sortValueLabel;
	private HexTextField sortValueField;

	private JLabel potencyALabel;
	private IntTextField potencyAField;

	private JLabel potencyBLabel;
	private IntTextField potencyBField;

	public ItemTab(GlobalsEditor editor, int tabIndex)
	{
		super(editor, tabIndex, editor.data.items);

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
		return "Items";
	}

	@Override
	protected ExpectedAsset getIcon()
	{
		return ExpectedAsset.ICON_FIRE_FLOWER;
	}

	@Override
	protected GlobalsCategory getDataType()
	{
		return GlobalsCategory.ITEM_TABLE;
	}

	@Override
	protected void notifyDataChange(GlobalsCategory type)
	{
		if (type != GlobalsCategory.ITEM_TABLE)
			return;

		repaintList();
	}

	private void copyFromSelected()
	{
		clipboard = getSelected();
	}

	private void pasteIntoSelected()
	{
		ItemRecord item = getSelected();
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

	@Override
	public void onSelectTab()
	{
		DefaultComboBoxModel<String> moveModel = new DefaultComboBoxModel<>();
		for (MoveRecord rec : editor.data.moves)
			moveModel.addElement(rec.enumName);
		moveBox.setModel(moveModel);

		DefaultComboBoxModel<String> imagesModel = new DefaultComboBoxModel<>();
		for (IconRecord rec : editor.data.icons)
			imagesModel.addElement(rec.getIdentifier());
		iconPreviewBox.setModel(imagesModel);

		updateInfoPanel(getSelected(), false);
	}

	@Override
	protected void addListButtons(JPanel listPanel)
	{
		JButton addButton = new JButton("Add Item");
		addButton.addActionListener((e) -> {
			if (listModel.getSize() > 920) {
				Logger.log("Can't add any more items!");
				Toolkit.getDefaultToolkit().beep();
				return;
			}
			ItemRecord newItem = new ItemRecord(listModel.size());
			newItem.setName("NameMissing");
			listModel.addElement(newItem);
			onModelDataChange();

			updateListFilter();
			list.setSelectedValue(newItem, false);
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
			ItemRecord item = getSelected();
			if (item == null) {
				Toolkit.getDefaultToolkit().beep();
				return;
			}
			ItemRecord newItem = new ItemRecord(item.listIndex);
			newItem.setName("NameMissing");
			item.copyFrom(newItem);
			onModelDataChange();
			setModified();

			updateInfoPanel(item);
			repaintList();
		});
		actionsPopup.add(menuItem);

		menuItem = new JMenuItem("Discard Changes");
		menuItem.setPreferredSize(POPUP_OPTION_SIZE);
		menuItem.addActionListener(e -> {
			ItemRecord item = getSelected();
			if (item == null) {
				Toolkit.getDefaultToolkit().beep();
				return;
			}
			if (item.getModified()) {
				item.restoreBackup();
				onModelDataChange();
				updateInfoPanel(item);
				repaintList();
			}
		});
		actionsPopup.add(menuItem);

		listPanel.add(addButton, "span, split 2, grow, sg but");
		listPanel.add(actionsButton, "grow, sg but");
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
	protected JPanel createInfoPanel(JLabel infoLabel)
	{
		titleIconLabel = new JLabel(IconResource.CROSS_24, SwingConstants.CENTER);
		titleNameLabel = SwingUtils.getLabel("???", 14);

		nameField = new StringField(SwingConstants.LEADING, (s) -> {
			if (hasSelected() && !s.isBlank()) {
				ItemRecord item = getSelected();
				if (!s.equals(item.name)) {
					item.setName(s);
					updateInfoPanel(item);
					setModified();
					repaintList();

					listModel.rebuildNameCache();
				}
			}
		});
		SwingUtils.addTextFieldFilter(nameField, "\\W+");

		nameMsgPreview = new JLabel();
		fullDescMsgPreview = new JLabel();
		shortDescMsgPreview = new JLabel();

		nameMsgField = new StringField(SwingConstants.LEADING, (s) -> {
			ItemRecord item = getSelected();
			if (item != null && !s.equals(item.msgName)) {
				item.msgName = s;
				updateInfoPanel(item);
				setModified();
			}
		});
		SwingUtils.addTextFieldFilter(nameMsgField, "\\s+");

		fullDescMsgField = new StringField(SwingConstants.LEADING, (s) -> {
			ItemRecord item = getSelected();
			if (item != null && !s.equals(item.msgFullDesc)) {
				item.msgFullDesc = s;
				updateInfoPanel(item);
				setModified();
			}
		});
		SwingUtils.addTextFieldFilter(fullDescMsgField, "\\s+");

		shortDescMsgField = new StringField(SwingConstants.LEADING, (s) -> {
			ItemRecord item = getSelected();
			if (item != null && !s.equals(item.msgShortDesc)) {
				item.msgShortDesc = s;
				updateInfoPanel(item);
				setModified();
			}
		});
		SwingUtils.addTextFieldFilter(shortDescMsgField, "\\s+");

		JButton chooseNameButton = new JButton("Choose");
		chooseNameButton.addActionListener((e) -> {
			if (hasSelected()) {
				ItemRecord item = getSelected();
				Message msg = chooseMessage("Choose Name");
				if (msg != null) {
					String s = msg.getIdentifier();
					if (!s.equals(item.msgName)) {
						item.msgName = s;
						updateInfoPanel(item);
						setModified();
					}
				}
			}
		});

		JButton chooseFullDescButton = new JButton("Choose");
		chooseFullDescButton.addActionListener((e) -> {
			if (hasSelected()) {
				ItemRecord item = getSelected();
				Message msg = chooseMessage("Choose Full Description");
				if (msg != null) {
					String s = msg.getIdentifier();
					if (!s.equals(item.msgFullDesc)) {
						item.msgFullDesc = s;
						updateInfoPanel(item);
						setModified();
					}
				}
			}
		});

		JButton chooseShortDescButton = new JButton("Choose");
		chooseShortDescButton.addActionListener((e) -> {
			if (hasSelected()) {
				ItemRecord item = getSelected();
				Message msg = chooseMessage("Choose Short Description");
				if (msg != null) {
					String s = msg.getIdentifier();
					if (!s.equals(item.msgShortDesc)) {
						item.msgShortDesc = s;
						updateInfoPanel(item);
						setModified();
					}
				}
			}
		});

		typeFlagsTextArea = new JTextArea();
		typeFlagsTextArea.setEditable(false);
		typeFlagsTextArea.setLineWrap(true);
		typeFlagsTextArea.setWrapStyleWord(true);
		typeFlagsTextArea.setBorder(UIManager.getLookAndFeel().getDefaults().getBorder("TextField.border"));
		JButton typeFlagsButton = new JButton("Edit");
		typeFlagsButton.addActionListener((e) -> {
			if (hasSelected()) {
				ItemRecord item = getSelected();
				FlagEditorPanel flagPanel = new FlagEditorPanel(item.typeFlags);

				int choice = SwingUtils.getConfirmDialog()
					.setParent(SwingGUI.instance())
					.setTitle("Set Type Flags")
					.setMessage(flagPanel)
					.setOptionsType(JOptionPane.OK_CANCEL_OPTION)
					.choose();

				if (choice == JOptionPane.YES_OPTION) {
					int newValue = flagPanel.getValue();
					if (newValue != item.typeFlags.getBits()) {
						item.typeFlags.setBits(newValue);
						setModified();

						typeFlagsTextArea.setText(String.join(", ", item.typeFlags.getSelectedDisp()));
						updateInfoPanel(item);
					}
				}
			}
		});
		SwingUtils.addBorderPadding(typeFlagsButton);

		targetFlagsTextArea = new JTextArea();
		targetFlagsTextArea.setEditable(false);
		targetFlagsTextArea.setLineWrap(true);
		targetFlagsTextArea.setWrapStyleWord(true);
		targetFlagsTextArea.setBorder(UIManager.getLookAndFeel().getDefaults().getBorder("TextField.border"));
		JButton targetFlagsButton = new JButton("Edit");
		targetFlagsButton.addActionListener((e) -> {
			if (hasSelected()) {
				ItemRecord item = getSelected();
				FlagEditorPanel flagPanel = new FlagEditorPanel(item.targetFlags);

				int choice = SwingUtils.getConfirmDialog()
					.setParent(SwingGUI.instance())
					.setTitle("Set Target Flags")
					.setMessage(flagPanel)
					.setOptionsType(JOptionPane.OK_CANCEL_OPTION)
					.choose();

				if (choice == JOptionPane.YES_OPTION) {
					int newValue = flagPanel.getValue();
					if (newValue != item.targetFlags.getBits()) {
						item.targetFlags.setBits(newValue);
						setModified();

						targetFlagsTextArea.setText(String.join(", ", item.targetFlags.getSelectedDisp()));
						updateInfoPanel(item);
					}
				}
			}
		});
		SwingUtils.addBorderPadding(targetFlagsButton);

		iconPreviewBox = new JComboBox<>();
		iconPreviewBox.setRenderer(new IconBoxRenderer(editor.data));
		iconPreviewBox.setMaximumRowCount(32);
		iconPreviewBox.addActionListener((e) -> {
			if (!shouldIgnoreChanges() && hasSelected()) {
				ItemRecord item = getSelected();
				String newValue = (String) iconPreviewBox.getSelectedItem();
				if (item.iconName == null || !item.iconName.equals(newValue)) {
					item.iconName = (newValue == null) ? "" : newValue;
					updateInfoPanel(item);
					setModified();
				}
			}
		});
		iconPreviewBox.setEditable(true);

		hudElementField = new StringField(SwingConstants.LEADING, (s) -> {
			if (hasSelected() && !s.isBlank()) {
				ItemRecord item = getSelected();
				if (!s.equals(item.hudElemName)) {
					item.hudElemName = s;
					setModified();
				}
			}
		});
		SwingUtils.addTextFieldFilter(hudElementField, "\\W+");

		itemEntityField = new StringField(SwingConstants.LEADING, (s) -> {
			if (hasSelected() && !s.isBlank()) {
				ItemRecord item = getSelected();
				if (!s.equals(item.itemEntityName)) {
					item.itemEntityName = s;
					setModified();
				}
			}
		});
		SwingUtils.addTextFieldFilter(itemEntityField, "\\W+");

		moveBox = new JComboBox<>();
		moveBox.setRenderer(new PaddedCellRenderer<String>(24));
		moveBox.setMaximumRowCount(16);
		moveBox.addActionListener((e) -> {
			if (!shouldIgnoreChanges() && hasSelected()) {
				ItemRecord item = getSelected();
				String newValue = (String) moveBox.getSelectedItem();
				if (item.moveName == null || !item.moveName.equals(newValue)) {
					item.moveName = (newValue == null) ? "" : newValue;
					updateInfoPanel(item);
					setModified();
				}
			}
		});
		moveBox.setEditable(true);

		JButton chooseImageAssetButton = new JButton("Choose");
		chooseImageAssetButton.addActionListener((e) -> {
			if (hasSelected()) {
				ItemRecord item = getSelected();
				ListSelectorDialog<IconRecord> chooser = new ListSelectorDialog<>(
					editor.data.icons, new IconListRenderer());
				chooser.setValue(editor.data.icons.getElement(item.iconName));
				SwingUtils.showModalDialog(chooser, "Choose Image");
				if (chooser.isResultAccepted())
					iconPreviewBox.setSelectedItem(chooser.getValue() == null ? "" : chooser.getValue().getIdentifier());
			}
		});
		SwingUtils.addBorderPadding(chooseImageAssetButton);

		chooseMoveButton = new JButton("Choose");
		chooseMoveButton.addActionListener((e) -> {
			if (hasSelected()) {
				ItemRecord item = getSelected();
				ListSelectorDialog<MoveRecord> chooser = new ListSelectorDialog<>(editor.data.moves);
				chooser.setValue(editor.data.moves.getElement(item.moveName));
				SwingUtils.showModalDialog(chooser, "Choose Associated Move");
				if (chooser.isResultAccepted())
					moveBox.setSelectedItem(chooser.getValue() == null ? "" : chooser.getValue().enumName);
			}
		});
		SwingUtils.addBorderPadding(chooseMoveButton);

		sortValueField = new HexTextField(4, (v) -> {
			if (hasSelected()) {
				ItemRecord item = getSelected();
				short newValue = (short) (int) v;
				if (newValue != item.sortValue) {
					item.sortValue = newValue;
					setModified();
				}
			}
		});
		SwingUtils.addBorderPadding(sortValueField);

		editSortValueButton = new JButton("Reorder");
		editSortValueButton.addActionListener((e) -> {
			// get all badges
			List<ItemRecord> badges = new ArrayList<>();
			for (ItemRecord rec : listModel) {
				if (rec.typeFlags.testBit(0x40)) // isBadge
					badges.add(rec);
			}
			// sort by menu sort value
			badges.sort(Comparator.comparing(item -> item.sortValue));
			DefaultListModel<ItemRecord> sortedBadgeModel = new DefaultListModel<>();
			sortedBadgeModel.addAll(badges);

			BadgeReorderDialog chooser = new BadgeReorderDialog(sortedBadgeModel, new BadgeCellRenderer(editor.data));

			SwingUtils.showModalDialog(chooser, "Change Badge Menu Order");
			if (chooser.isResultAccepted()) {
				short currentValue = 0;
				short prevOldValue = 0;
				for (int i = 0; i < sortedBadgeModel.size(); i++) {
					ItemRecord item = sortedBadgeModel.get(i);
					if (i > 0 && item.sortValue != prevOldValue)
						currentValue++;
					prevOldValue = item.sortValue;
					if (item.sortValue != currentValue) {
						item.sortValue = currentValue;
						item.setModified(true);
						super.setModified();
					}
				}
				updateInfoPanel(getSelected());
			}
		});
		SwingUtils.addBorderPadding(editSortValueButton);

		potencyAField = new IntTextField((v) -> {
			if (hasSelected()) {
				ItemRecord item = getSelected();
				byte newValue = (byte) (int) v;
				if (newValue != item.potencyA) {
					item.potencyA = newValue;
					setModified();
				}
			}
		});
		SwingUtils.addBorderPadding(potencyAField);

		potencyBField = new IntTextField((v) -> {
			if (hasSelected()) {
				ItemRecord item = getSelected();
				byte newValue = (byte) (int) v;
				if (newValue != item.potencyB) {
					item.potencyB = newValue;
					setModified();
				}
			}
		});
		SwingUtils.addBorderPadding(potencyBField);

		sellValueField = new IntTextField((v) -> {
			if (hasSelected()) {
				ItemRecord item = getSelected();
				short newValue = (short) (int) v;
				if (newValue != item.sellValue) {
					item.sellValue = newValue;
					setModified();
				}
			}
		});
		SwingUtils.addBorderPadding(sellValueField);

		JPanel infoPanel = new JPanel(new MigLayout("ins 0, fill, hidemode 3", "[80!]5[280!]5[90!][grow]"));
		infoPanel.add(titleIconLabel, "span, split 2, w 32!, h 32!");
		infoPanel.add(titleNameLabel, "gapleft 8, growx, pushx, center");

		infoPanel.add(SwingUtils.getLabel("Name", 12));
		infoPanel.add(nameField, "grow, wrap");

		// add message fields

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

		// add icon and template fields

		infoPanel.add(SwingUtils.getLabel("Icon", 12));
		infoPanel.add(iconPreviewBox, "grow");
		infoPanel.add(chooseImageAssetButton, "growx, wrap");

		infoPanel.add(SwingUtils.getLabel("HUD Element", 12));
		infoPanel.add(hudElementField, "grow, wrap");

		infoPanel.add(SwingUtils.getLabel("Item Entity", 12));
		infoPanel.add(itemEntityField, "grow, wrap");

		infoPanel.add(new JLabel(), "span, wrap, h 4!");

		// add flag fields

		infoPanel.add(SwingUtils.getLabel("Type Flags", 12));
		infoPanel.add(typeFlagsTextArea, "grow");
		infoPanel.add(typeFlagsButton, "grow, wrap");

		infoPanel.add(SwingUtils.getLabel("Target Flags", 12));
		infoPanel.add(targetFlagsTextArea, "grow");
		infoPanel.add(targetFlagsButton, "grow, wrap");

		infoPanel.add(new JLabel(), "span, h 4!, wrap");

		// add various other fields

		moveLabel = SwingUtils.getLabelWithTooltip("Move", "Associated move for badge");
		infoPanel.add(moveLabel);
		infoPanel.add(moveBox, "grow");
		infoPanel.add(chooseMoveButton, "grow, wrap");

		sortValueLabel = SwingUtils.getLabelWithTooltip("Menu Order", "Used to sort badges in the pause menu. Higher is further down.");
		infoPanel.add(sortValueLabel);
		infoPanel.add(sortValueField, "w 132!");
		infoPanel.add(editSortValueButton, "grow, wrap");

		potencyALabel = SwingUtils.getLabel("Potency A", 12);
		infoPanel.add(potencyALabel);
		infoPanel.add(potencyAField, "w 132!, wrap");

		potencyBLabel = SwingUtils.getLabel("Potency B", 12);
		infoPanel.add(potencyBLabel);
		infoPanel.add(potencyBField, "w 132!, wrap");

		sellValueLabel = SwingUtils.getLabelWithTooltip("Sell Value", "Default coin value in shops and Refund.");
		infoPanel.add(sellValueLabel);
		infoPanel.add(sellValueField, "w 132!, wrap");

		JPanel embedPanel = new JPanel(new MigLayout("ins 8 16 8 16, fill, wrap"));
		embedPanel.add(infoPanel, "growx");
		embedPanel.add(new JLabel(), "growy, pushy");
		embedPanel.add(infoLabel, "growx");

		return embedPanel;
	}

	@Override
	protected void updateInfoPanel(ItemRecord item, boolean fromSet)
	{
		Icon preview = editor.data.getLargeIcon(item.iconName);
		titleIconLabel.setIcon((preview != null) ? preview : IconResource.CROSS_24);
		iconPreviewBox.setForeground((preview != null) ? null : SwingUtils.getRedTextColor());

		moveBox.setForeground(editor.data.hasMoveEnum(item.moveName) ? null : SwingUtils.getRedTextColor());

		titleNameLabel.setText(item.enumName);
		nameField.setText(item.name);

		updateMessageField(nameMsgField, nameMsgPreview, item.msgName);
		updateMessageField(shortDescMsgField, shortDescMsgPreview, item.msgShortDesc);
		updateMessageField(fullDescMsgField, fullDescMsgPreview, item.msgFullDesc);

		typeFlagsTextArea.setText(String.join(", ", item.typeFlags.getSelectedDisp()));
		targetFlagsTextArea.setText(String.join(", ", item.targetFlags.getSelectedDisp()));

		moveBox.setSelectedItem(item.moveName);
		hudElementField.setText(item.hudElemName);
		itemEntityField.setText(item.itemEntityName);
		iconPreviewBox.setSelectedItem(item.iconName);

		sortValueField.setValue(MathUtil.clamp(item.sortValue, 0, 0xFFFF));
		potencyAField.setValue(MathUtil.clamp(item.potencyA, -128, 127));
		potencyBField.setValue(MathUtil.clamp(item.potencyB, -128, 127));
		sellValueField.setValue(MathUtil.clamp(item.sellValue, -1, 999));

		boolean isWeapon = item.typeFlags.testBit(0x2);
		boolean isKey = item.typeFlags.testBit(0x8);
		boolean isBadge = item.typeFlags.testBit(0x40);
		boolean isFood = item.typeFlags.testBit(0x80);

		sortValueLabel.setVisible(isBadge);
		sortValueField.setVisible(isBadge);
		editSortValueButton.setVisible(isBadge);

		moveLabel.setVisible(isBadge);
		moveBox.setVisible(isBadge);
		chooseMoveButton.setVisible(isBadge);

		potencyALabel.setVisible(isFood || isWeapon);
		potencyAField.setVisible(isFood || isWeapon);
		potencyALabel.setText(isFood ? "HP Gain" : "Power");

		potencyBLabel.setVisible(isFood);
		potencyBField.setVisible(isFood);
		potencyBLabel.setText("FP Gain");

		sellValueLabel.setVisible(!isKey);
		sellValueField.setVisible(!isKey);
	}

	@Override
	protected ListCellRenderer<ItemRecord> getCellRenderer()
	{
		return new ItemListRenderer(editor.data);
	}
}
