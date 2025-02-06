package game.sprite.editor.animators;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Enumeration;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.InputMap;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

import com.alexandriasoftware.swing.JSplitButton;

import app.SwingUtils;
import game.sprite.Sprite;
import game.sprite.SpriteAnimation;
import game.sprite.SpriteComponent;
import game.sprite.SpritePalette;
import game.sprite.SpriteRaster;
import game.sprite.editor.IndexableComboBoxRenderer;
import game.sprite.editor.SpriteEditor;
import game.sprite.editor.animators.CommandAnimator.AnimCommand;
import game.sprite.editor.animators.CommandAnimator.Goto;
import game.sprite.editor.animators.CommandAnimator.Label;
import game.sprite.editor.animators.CommandAnimator.Loop;
import game.sprite.editor.animators.CommandAnimator.SetImage;
import game.sprite.editor.animators.CommandAnimator.SetNotify;
import game.sprite.editor.animators.CommandAnimator.SetPalette;
import game.sprite.editor.animators.CommandAnimator.SetParent;
import game.sprite.editor.animators.CommandAnimator.SetPosition;
import game.sprite.editor.animators.CommandAnimator.SetRotation;
import game.sprite.editor.animators.CommandAnimator.SetScale;
import game.sprite.editor.animators.CommandAnimator.SetUnknown;
import game.sprite.editor.animators.CommandAnimator.Wait;
import net.miginfocom.swing.MigLayout;
import util.ui.DragReorderList;
import util.ui.ListAdapterComboboxModel;

public class CommandAnimatorEditor
{
	private static final String PANEL_LAYOUT_PROPERTIES = "ins 0 10 0 10, wrap";

	private static CommandAnimatorEditor instance;

	private DragReorderList<AnimCommand> commandList;
	private ListDataListener commandListListener;

	private JPanel commandListPanel;
	private JPanel commandEditPanel;

	private SpriteEditor editor;
	private CommandAnimator animator;

	private AnimCommand clipboard;

	public static void bind(SpriteEditor editor, CommandAnimator animator, Container commandListContainer, Container commandEditContainer)
	{
		instance().editor = editor;
		instance().animator = animator;

		commandListContainer.removeAll();
		commandListContainer.add(instance().commandListPanel, "grow");

		commandEditContainer.removeAll();
		commandEditContainer.add(instance().commandEditPanel, "grow");

		instance().commandList.setModel(animator.commandListModel);

		animator.commandListModel.removeListDataListener(instance().commandListListener);
		animator.commandListModel.addListDataListener(instance().commandListListener);
	}

	public static void setModels(Sprite sprite)
	{
		DefaultComboBoxModel<SpriteRaster> rasterModel = new DefaultComboBoxModel<>();
		rasterModel.addElement(null);
		for (int i = 0; i < sprite.rasters.size(); i++) {
			SpriteRaster sr = sprite.rasters.get(i);
			rasterModel.addElement(sr);
		}

		DefaultComboBoxModel<SpritePalette> paletteModel = new DefaultComboBoxModel<>();
		paletteModel.addElement(null);
		for (int i = 0; i < sprite.palettes.size(); i++) {
			SpritePalette sr = sprite.palettes.get(i);
			paletteModel.addElement(sr);
		}

		SetImagePanel.instance().setModel(new ListAdapterComboboxModel<>(rasterModel));
		SetPalettePanel.instance().setModel(new ListAdapterComboboxModel<>(paletteModel));
	}

	private static CommandAnimatorEditor instance()
	{
		if (instance == null)
			instance = new CommandAnimatorEditor();
		return instance;
	}

	private static void repaintCommandList()
	{
		instance().commandList.repaint();
	}

	private CommandAnimatorEditor()
	{
		commandList = new DragReorderList<>();
		commandList.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		commandList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		commandListPanel = new JPanel(new MigLayout("fill, ins 0, wrap"));
		commandEditPanel = new JPanel(new MigLayout("fill, ins 0, wrap"));

		commandList.addListSelectionListener((e) -> {
			if (e.getValueIsAdjusting())
				return;

			AnimCommand cmd = commandList.getSelectedValue();
			commandEditPanel.removeAll();
			if (cmd != null)
				commandEditPanel.add(cmd.getPanel(), "grow, pushy");
			commandEditPanel.revalidate();
			commandEditPanel.repaint();
		});

		InputMap im = commandList.getInputMap(JComponent.WHEN_FOCUSED);
		ActionMap am = commandList.getActionMap();

		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK), "copy");
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK), "paste");
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK), "duplicate");
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete");

		am.put("copy", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				AnimCommand cmd = commandList.getSelectedValue();
				if (cmd == null) {
					Toolkit.getDefaultToolkit().beep();
					return;
				}
				clipboard = (AnimCommand) cmd.copy();
			}
		});

		am.put("paste", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				int i = commandList.getSelectedIndex();
				if (i == -1 || clipboard == null) {
					Toolkit.getDefaultToolkit().beep();
					return;
				}
				AnimCommand copy = (AnimCommand) clipboard.copy();
				commandList.getDefaultModel().add(i + 1, copy);
			}
		});

		am.put("duplicate", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				AnimCommand cmd = commandList.getSelectedValue();
				if (cmd == null) {
					Toolkit.getDefaultToolkit().beep();
					return;
				}
				int i = commandList.getSelectedIndex();
				AnimCommand copy = (AnimCommand) cmd.copy();
				commandList.getDefaultModel().add(i + 1, copy);
			}
		});

		am.put("delete", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				int i = commandList.getSelectedIndex();
				if (i == -1) {
					Toolkit.getDefaultToolkit().beep();
					return;
				}
				commandList.getDefaultModel().remove(i);
			}
		});

		commandList.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e)
			{
				int row = commandList.locationToIndex(e.getPoint());
				if (row < 0)
					return;
				AnimElement elem = commandList.getModel().getElementAt(row);

				switch (e.getButton()) {
					case MouseEvent.BUTTON3: // right click
						animator.advanceTo(elem);
						commandListPanel.repaint();
						editor.updatePlaybackStatus();
						break;
				}
			}
		});

		JPopupMenu createMenu = new JPopupMenu();
		JSplitButton createButton = new JSplitButton("Add Command");
		createButton.setPopupMenu(createMenu);
		createButton.setAlwaysPopup(true);

		JMenuItem waitItem = new JMenuItem("Wait");
		waitItem.addActionListener((e) -> create(animator.new Wait()));
		createMenu.add(waitItem);

		JMenuItem imgItem = new JMenuItem("Set Raster");
		imgItem.addActionListener((e) -> create(animator.new SetImage()));
		createMenu.add(imgItem);

		JMenuItem palItem = new JMenuItem("Set Palette");
		palItem.addActionListener((e) -> create(animator.new SetPalette()));
		createMenu.add(palItem);

		createMenu.addSeparator();

		JMenuItem labelItem = new JMenuItem("Label");
		labelItem.addActionListener((e) -> create(animator.new Label()));
		createMenu.add(labelItem);

		JMenuItem gotoItem = new JMenuItem("Goto");
		gotoItem.addActionListener((e) -> create(animator.new Goto()));
		createMenu.add(gotoItem);

		JMenuItem loopItem = new JMenuItem("Repeat");
		loopItem.addActionListener((e) -> create(animator.new Loop()));
		createMenu.add(loopItem);

		createMenu.addSeparator();

		JMenuItem posItem = new JMenuItem("Set Position");
		posItem.addActionListener((e) -> create(animator.new SetPosition()));
		createMenu.add(posItem);

		JMenuItem rotItem = new JMenuItem("Set Rotation");
		rotItem.addActionListener((e) -> create(animator.new SetRotation()));
		createMenu.add(rotItem);

		JMenuItem scaleItem = new JMenuItem("Set Scale");
		scaleItem.addActionListener((e) -> create(animator.new SetScale()));
		createMenu.add(scaleItem);

		createMenu.addSeparator();

		JMenuItem parentItem = new JMenuItem("Set Parent");
		parentItem.addActionListener((e) -> create(animator.new SetParent()));
		createMenu.add(parentItem);

		JMenuItem notifyItem = new JMenuItem("Set Notify");
		notifyItem.addActionListener((e) -> create(animator.new SetNotify()));
		createMenu.add(notifyItem);

		JScrollPane listScrollPane = new JScrollPane(commandList);
		listScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		commandListPanel.add(SwingUtils.getLabel("Commands", 14));
		commandListPanel.add(listScrollPane, "grow, push");
		commandListPanel.add(createButton, "growx");

		commandListListener = new ListDataListener() {
			@Override
			public void contentsChanged(ListDataEvent e)
			{
				animator.resetAnimation();
			}

			@Override
			public void intervalAdded(ListDataEvent e)
			{
				animator.resetAnimation();
			}

			@Override
			public void intervalRemoved(ListDataEvent e)
			{
				animator.resetAnimation();
			}
		};
	}

	private static void create(AnimCommand cmd)
	{
		DefaultListModel<AnimCommand> model = instance().commandList.getDefaultModel();

		if (instance().commandList.isSelectionEmpty())
			model.addElement(cmd);
		else
			model.add(instance().commandList.getSelectedIndex() + 1, cmd);
	}

	protected static class LabelPanel extends JPanel
	{
		private static LabelPanel instance;
		private Label cmd;

		private LabelTextField labelNameField;

		protected static LabelPanel instance()
		{
			if (instance == null)
				instance = new LabelPanel();
			return instance;
		}

		private LabelPanel()
		{
			super(new MigLayout(PANEL_LAYOUT_PROPERTIES));

			labelNameField = new LabelTextField(() -> {
				cmd.labelName = labelNameField.getText();
				repaintCommandList();
			});

			labelNameField.setMargin(SwingUtils.TEXTBOX_INSETS);
			labelNameField.setBorder(BorderFactory.createCompoundBorder(
				labelNameField.getBorder(),
				BorderFactory.createEmptyBorder(2, 4, 2, 4)));

			add(SwingUtils.getLabel("Label Name", 14), "gapbottom 4");
			add(labelNameField, "grow");
		}

		protected void set(Label cmd)
		{
			this.cmd = cmd;
			labelNameField.setText(cmd.labelName);
		}
	}

	protected static class GotoPanel extends JPanel
	{
		private static GotoPanel instance;
		private Goto cmd;

		private JComboBox<Label> labelComboBox;

		protected static GotoPanel instance()
		{
			if (instance == null)
				instance = new GotoPanel();
			return instance;
		}

		private GotoPanel()
		{
			super(new MigLayout(PANEL_LAYOUT_PROPERTIES));

			labelComboBox = new JComboBox<>();
			SwingUtils.setFontSize(labelComboBox, 14);
			labelComboBox.setMaximumRowCount(16);
			labelComboBox.addActionListener((e) -> {
				if (ignoreChanges)
					return;
				cmd.label = (Label) labelComboBox.getSelectedItem();
				repaintCommandList();
			});

			add(SwingUtils.getLabel("Goto Label", 14), "gapbottom 4");
			add(labelComboBox, "w 200!");
		}

		private boolean ignoreChanges = false;

		protected void set(Goto cmd, DefaultListModel<Label> labels)
		{
			this.cmd = cmd;

			ignoreChanges = true;
			labelComboBox.setModel(new ListAdapterComboboxModel<>(labels));
			labelComboBox.setSelectedItem(cmd.label);
			ignoreChanges = false;
		}
	}

	protected static class LoopPanel extends JPanel
	{
		private static LoopPanel instance;
		private Loop cmd;

		private JComboBox<Label> labelComboBox;
		private JSpinner countSpinner;

		protected static LoopPanel instance()
		{
			if (instance == null)
				instance = new LoopPanel();
			return instance;
		}

		private LoopPanel()
		{
			super(new MigLayout(PANEL_LAYOUT_PROPERTIES));

			labelComboBox = new JComboBox<>();
			SwingUtils.setFontSize(labelComboBox, 14);
			labelComboBox.setMaximumRowCount(16);
			labelComboBox.addActionListener((e) -> {
				if (ignoreChanges)
					return;
				cmd.label = (Label) labelComboBox.getSelectedItem();
				repaintCommandList();
			});

			countSpinner = new JSpinner();
			SwingUtils.setFontSize(countSpinner, 12);
			countSpinner.setModel(new SpinnerNumberModel(1, 0, 300, 1));
			SwingUtils.centerSpinnerText(countSpinner);
			countSpinner.addChangeListener((e) -> {
				cmd.count = (int) countSpinner.getValue();
				repaintCommandList();
			});

			add(SwingUtils.getLabel("Repeat Properties", 14), "gapbottom 4");
			add(labelComboBox, "w 200!");
			add(SwingUtils.getLabel("Repetitions: ", 12), "sg lbl, split 2");
			add(countSpinner, "sg etc, grow");
		}

		private boolean ignoreChanges = false;

		protected void set(Loop cmd, DefaultListModel<Label> labels)
		{
			this.cmd = cmd;

			ignoreChanges = true;
			labelComboBox.setModel(new ListAdapterComboboxModel<>(labels));
			labelComboBox.setSelectedItem(cmd.label);
			ignoreChanges = false;

			countSpinner.setValue(cmd.count);
		}
	}

	protected static class WaitPanel extends JPanel
	{
		private static WaitPanel instance;
		private Wait cmd;

		private JSpinner countSpinner;

		protected static WaitPanel instance()
		{
			if (instance == null)
				instance = new WaitPanel();
			return instance;
		}

		private WaitPanel()
		{
			super(new MigLayout(PANEL_LAYOUT_PROPERTIES));

			countSpinner = new JSpinner();
			SwingUtils.setFontSize(countSpinner, 12);
			countSpinner.setModel(new SpinnerNumberModel(1, 0, 300, 1)); // longest used = 260
			SwingUtils.centerSpinnerText(countSpinner);
			countSpinner.addChangeListener((e) -> {
				cmd.count = (int) countSpinner.getValue();
				repaintCommandList();
			});

			add(SwingUtils.getLabel("Wait Duration", 14), "gapbottom 4");
			add(countSpinner, "w 30%, split 2");
			add(SwingUtils.getLabel(" frames", 12));
		}

		protected void set(Wait cmd)
		{
			this.cmd = cmd;
			countSpinner.setValue(cmd.count);
		}
	}

	protected static class SetImagePanel extends JPanel
	{
		private static SetImagePanel instance;
		private SetImage cmd;

		private JComboBox<SpriteRaster> imageComboBox;
		private boolean ignoreChanges = false;

		protected static SetImagePanel instance()
		{
			if (instance == null)
				instance = new SetImagePanel();
			return instance;
		}

		private SetImagePanel()
		{
			super(new MigLayout(PANEL_LAYOUT_PROPERTIES));

			imageComboBox = new JComboBox<>();
			imageComboBox.setUI(new BlankArrowUI());

			SpriteRasterRenderer renderer = new SpriteRasterRenderer();
			renderer.setMinimumSize(new Dimension(80, 80));
			renderer.setPreferredSize(new Dimension(80, 80));
			imageComboBox.setRenderer(renderer);
			imageComboBox.setMaximumRowCount(5);
			imageComboBox.addActionListener((e) -> {
				if (ignoreChanges || cmd == null)
					return;

				cmd.img = (SpriteRaster) imageComboBox.getSelectedItem();
				repaintCommandList();
			});

			add(SwingUtils.getLabel("Choose Raster", 14), "gapbottom 4");
			add(imageComboBox, "w 60%, h 120!");
		}

		protected void set(SetImage cmd)
		{
			this.cmd = cmd;

			ignoreChanges = true;
			imageComboBox.setSelectedItem(cmd.img);
			ignoreChanges = false;
		}

		protected void setModel(ComboBoxModel<SpriteRaster> model)
		{
			ignoreChanges = true;
			imageComboBox.setModel(model);
			ignoreChanges = false;
		}
	}

	protected static class SetPalettePanel extends JPanel
	{
		private static SetPalettePanel instance;
		private SetPalette cmd;

		private JComboBox<SpritePalette> paletteComboBox;
		private boolean ignoreChanges = false;

		protected static SetPalettePanel instance()
		{
			if (instance == null)
				instance = new SetPalettePanel();
			return instance;
		}

		private SetPalettePanel()
		{
			super(new MigLayout(PANEL_LAYOUT_PROPERTIES));

			paletteComboBox = new JComboBox<>();
			SwingUtils.setFontSize(paletteComboBox, 14);
			paletteComboBox.setMaximumRowCount(24);
			paletteComboBox.setRenderer(new IndexableComboBoxRenderer("Use Default"));
			paletteComboBox.addActionListener((e) -> {
				if (ignoreChanges || cmd == null)
					return;
				cmd.pal = (SpritePalette) paletteComboBox.getSelectedItem();
				repaintCommandList();
			});

			add(SwingUtils.getLabel("Choose Palette", 14), "gapbottom 4");
			add(paletteComboBox, "w 200!");
		}

		protected void set(SetPalette cmd)
		{
			this.cmd = cmd;

			ignoreChanges = true;
			paletteComboBox.setSelectedItem(cmd.pal);
			ignoreChanges = false;
		}

		protected void setModel(ComboBoxModel<SpritePalette> model)
		{
			ignoreChanges = true;
			paletteComboBox.setModel(model);
			ignoreChanges = false;
		}
	}

	protected static class SetParentPanel extends JPanel
	{
		private static SetParentPanel instance;
		private SetParent cmd;

		private JComboBox<SpriteComponent> componentComboBox;
		private boolean ignoreChanges = false;

		protected static SetParentPanel instance()
		{
			if (instance == null)
				instance = new SetParentPanel();
			return instance;
		}

		private SetParentPanel()
		{
			super(new MigLayout(PANEL_LAYOUT_PROPERTIES));

			componentComboBox = new JComboBox<>();
			SwingUtils.setFontSize(componentComboBox, 14);
			componentComboBox.setMaximumRowCount(24);
			componentComboBox.setRenderer(new IndexableComboBoxRenderer("None"));
			componentComboBox.addActionListener((e) -> {
				if (ignoreChanges)
					return;
				cmd.comp = (SpriteComponent) componentComboBox.getSelectedItem();
				repaintCommandList();
			});

			add(SwingUtils.getLabel("Set Parent", 14), "gapbottom 4");
			add(componentComboBox, "w 200!");
		}

		protected void set(SetParent cmd)
		{
			this.cmd = cmd;
			SpriteAnimation anim = cmd.ownerComp.parentAnimation;

			ignoreChanges = true;
			componentComboBox.setModel(new ListAdapterComboboxModel<>(anim.components));
			componentComboBox.setSelectedItem(cmd.comp);
			ignoreChanges = false;
		}
	}

	protected static class SetNotifyPanel extends JPanel
	{
		private static SetNotifyPanel instance;
		private SetNotify cmd;

		private JSpinner valueSpinner;
		private boolean ignoreChanges = false;

		protected static SetNotifyPanel instance()
		{
			if (instance == null)
				instance = new SetNotifyPanel();
			return instance;
		}

		private SetNotifyPanel()
		{
			super(new MigLayout(PANEL_LAYOUT_PROPERTIES));

			valueSpinner = new JSpinner();
			SwingUtils.setFontSize(valueSpinner, 12);
			valueSpinner.setModel(new SpinnerNumberModel(0, 0, 255, 1));
			SwingUtils.centerSpinnerText(valueSpinner);
			valueSpinner.addChangeListener((e) -> {
				if (ignoreChanges)
					return;
				cmd.value = (int) valueSpinner.getValue();
				repaintCommandList();
			});

			add(SwingUtils.getLabel("Set Notify", 14), "gapbottom 4");
			add(valueSpinner, "w 30%!");
		}

		protected void set(SetNotify cmd)
		{
			this.cmd = cmd;

			ignoreChanges = true;
			valueSpinner.setValue(cmd.value);
			ignoreChanges = false;
		}
	}

	protected static class SetUnknownPanel extends JPanel
	{
		private static SetUnknownPanel instance;
		private SetUnknown cmd;

		private JSpinner valueSpinner;
		private boolean ignoreChanges = false;

		protected static SetUnknownPanel instance()
		{
			if (instance == null)
				instance = new SetUnknownPanel();
			return instance;
		}

		private SetUnknownPanel()
		{
			super(new MigLayout(PANEL_LAYOUT_PROPERTIES));

			valueSpinner = new JSpinner();
			SwingUtils.setFontSize(valueSpinner, 12);
			valueSpinner.setModel(new SpinnerNumberModel(0, 0, 255, 1));
			SwingUtils.centerSpinnerText(valueSpinner);
			valueSpinner.addChangeListener((e) -> {
				if (ignoreChanges)
					return;
				cmd.value = (int) valueSpinner.getValue();
				repaintCommandList();
			});

			add(SwingUtils.getLabel("Set Notify", 14), "gapbottom 4");
			add(valueSpinner, "w 30%");
		}

		protected void set(SetUnknown cmd)
		{
			this.cmd = cmd;

			ignoreChanges = true;
			valueSpinner.setValue(cmd.value);
			ignoreChanges = false;
		}
	}

	protected static class SetPositionPanel extends JPanel
	{
		private static SetPositionPanel instance;
		private SetPosition cmd;

		private boolean ignoreChanges = false;
		private JSpinner xSpinner, ySpinner, zSpinner;
		private JCheckBox unknownCheckbox;

		protected static SetPositionPanel instance()
		{
			if (instance == null)
				instance = new SetPositionPanel();
			return instance;
		}

		private SetPositionPanel()
		{
			super(new MigLayout(PANEL_LAYOUT_PROPERTIES));

			xSpinner = new JSpinner();
			SwingUtils.setFontSize(xSpinner, 12);
			xSpinner.setModel(new SpinnerNumberModel(0, -256, 256, 1));
			SwingUtils.centerSpinnerText(xSpinner);
			xSpinner.addChangeListener((e) -> {
				if (ignoreChanges)
					return;
				cmd.x = (int) xSpinner.getValue();
				repaintCommandList();
			});

			ySpinner = new JSpinner();
			SwingUtils.setFontSize(ySpinner, 12);
			ySpinner.setModel(new SpinnerNumberModel(0, -256, 256, 1));
			SwingUtils.centerSpinnerText(ySpinner);
			ySpinner.addChangeListener((e) -> {
				if (ignoreChanges)
					return;
				cmd.y = (int) ySpinner.getValue();
				repaintCommandList();
			});

			zSpinner = new JSpinner();
			SwingUtils.setFontSize(zSpinner, 12);
			zSpinner.setModel(new SpinnerNumberModel(0, -256, 256, 1));
			SwingUtils.centerSpinnerText(zSpinner);
			zSpinner.addChangeListener((e) -> {
				if (ignoreChanges)
					return;
				cmd.z = (int) zSpinner.getValue();
				repaintCommandList();
			});

			unknownCheckbox = new JCheckBox("Unknown purpose");
			unknownCheckbox.addActionListener((e) -> {
				if (ignoreChanges)
					return;
				cmd.unknown = unknownCheckbox.isSelected();
				repaintCommandList();
			});
			unknownCheckbox.setToolTipText("Flag of unknown purpose. I don't think it does anything. Toggle it if you like surprises.");

			add(SwingUtils.getLabel("Component Position Offset", 14), "gapbottom 4");
			add(xSpinner, "split 3, sg xyz");
			add(ySpinner, "sg xyz");
			add(zSpinner, "sg xyz");
			add(unknownCheckbox, "gapbottom push");
		}

		protected void set(SetPosition cmd)
		{
			this.cmd = cmd;
			ignoreChanges = true;
			xSpinner.setValue(cmd.x);
			ySpinner.setValue(cmd.y);
			zSpinner.setValue(cmd.z);
			unknownCheckbox.setSelected(cmd.unknown);
			ignoreChanges = false;
		}
	}

	protected static class SetRotationPanel extends JPanel
	{
		private static SetRotationPanel instance;
		private SetRotation cmd;

		private boolean ignoreChanges = false;
		private JSpinner xSpinner, ySpinner, zSpinner;

		protected static SetRotationPanel instance()
		{
			if (instance == null)
				instance = new SetRotationPanel();
			return instance;
		}

		private SetRotationPanel()
		{
			super(new MigLayout(PANEL_LAYOUT_PROPERTIES));

			xSpinner = new JSpinner();
			SwingUtils.setFontSize(xSpinner, 12);
			xSpinner.setModel(new SpinnerNumberModel(0, -180, 180, 1));
			SwingUtils.centerSpinnerText(xSpinner);
			xSpinner.addChangeListener((e) -> {
				if (ignoreChanges)
					return;
				cmd.x = (int) xSpinner.getValue();
				repaintCommandList();
			});

			ySpinner = new JSpinner();
			SwingUtils.setFontSize(ySpinner, 12);
			ySpinner.setModel(new SpinnerNumberModel(0, -180, 180, 1));
			SwingUtils.centerSpinnerText(ySpinner);
			ySpinner.addChangeListener((e) -> {
				if (ignoreChanges)
					return;
				cmd.y = (int) ySpinner.getValue();
				repaintCommandList();
			});

			zSpinner = new JSpinner();
			SwingUtils.setFontSize(zSpinner, 12);
			zSpinner.setModel(new SpinnerNumberModel(0, -180, 180, 1));
			SwingUtils.centerSpinnerText(zSpinner);
			zSpinner.addChangeListener((e) -> {
				if (ignoreChanges)
					return;
				cmd.z = (int) zSpinner.getValue();
				repaintCommandList();
			});

			add(SwingUtils.getLabel("Component Rotation Angles", 14), "gapbottom 4");
			add(xSpinner, "split 3, sg xyz");
			add(ySpinner, "sg xyz");
			add(zSpinner, "sg xyz");
		}

		protected void set(SetRotation cmd)
		{
			this.cmd = cmd;
			ignoreChanges = true;
			xSpinner.setValue(cmd.x);
			ySpinner.setValue(cmd.y);
			zSpinner.setValue(cmd.z);
			ignoreChanges = false;
		}
	}

	protected static class SetScalePanel extends JPanel
	{
		private static SetScalePanel instance;
		private SetScale cmd;

		private JSpinner scaleSpinner;
		private JRadioButton allButton, xButton, yButton, zButton;

		protected static SetScalePanel instance()
		{
			if (instance == null)
				instance = new SetScalePanel();
			return instance;
		}

		private SetScalePanel()
		{
			super(new MigLayout(PANEL_LAYOUT_PROPERTIES));

			scaleSpinner = new JSpinner();
			SwingUtils.setFontSize(scaleSpinner, 12);
			scaleSpinner.setModel(new SpinnerNumberModel(1, 0, 500, 1));
			SwingUtils.centerSpinnerText(scaleSpinner);
			scaleSpinner.addChangeListener((e) -> {
				cmd.scalePercent = (int) scaleSpinner.getValue();
				repaintCommandList();
			});

			ButtonGroup scaleButtons = new ButtonGroup();

			ActionListener buttonListener = e -> {
				int i = 0;
				for (Enumeration<AbstractButton> buttons = scaleButtons.getElements(); buttons.hasMoreElements(); i++) {
					AbstractButton button = buttons.nextElement();
					if (button.isSelected())
						cmd.type = i;
				}
			};

			allButton = new JRadioButton("Uniform");
			xButton = new JRadioButton("Only X");
			yButton = new JRadioButton("Only Y");
			zButton = new JRadioButton("Only Z");
			allButton.setSelected(true);
			allButton.addActionListener(buttonListener);
			xButton.addActionListener(buttonListener);
			yButton.addActionListener(buttonListener);
			zButton.addActionListener(buttonListener);

			scaleButtons.add(allButton);
			scaleButtons.add(xButton);
			scaleButtons.add(yButton);
			scaleButtons.add(zButton);

			add(SwingUtils.getLabel("Component Scale", 14), "gapbottom 4");
			add(scaleSpinner, "w 30%, split 2");
			add(SwingUtils.getLabel(" percent", 12));
			add(allButton, "sg but, split 4");
			add(xButton, "sg but");
			add(yButton, "sg but");
			add(zButton, "sg but, grow");
		}

		protected void set(SetScale cmd)
		{
			this.cmd = cmd;
			scaleSpinner.setValue(cmd.scalePercent);

			switch (cmd.type) {
				case 0:
					allButton.setSelected(true);
					break;
				case 1:
					xButton.setSelected(true);
					break;
				case 2:
					yButton.setSelected(true);
					break;
				case 3:
					zButton.setSelected(true);
					break;
			}
		}
	}

	private static class LabelTextField extends JTextField
	{
		private static class LimitedDocumentFilter extends DocumentFilter
		{
			private int limit;

			public LimitedDocumentFilter(int limit)
			{
				if (limit <= 0) {
					throw new IllegalArgumentException("Limit can not be <= 0");
				}
				this.limit = limit;
			}

			@Override
			public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException
			{
				int currentLength = fb.getDocument().getLength();
				int overLimit = (currentLength + text.length()) - limit - length;
				if (overLimit > 0) {
					text = text.substring(0, text.length() - overLimit);
				}
				if (text.length() > 0) {
					super.replace(fb, offset, length, text, attrs);
				}
			}
		}

		private static class MyDocumentListener implements DocumentListener
		{
			Runnable runnable;

			public MyDocumentListener(Runnable r)
			{
				this.runnable = r;
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				runnable.run();
			}

			@Override
			public void insertUpdate(DocumentEvent e)
			{
				runnable.run();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				runnable.run();
			}
		}

		public LabelTextField(Runnable r)
		{
			setColumns(12);
			((AbstractDocument) getDocument()).setDocumentFilter(new LimitedDocumentFilter(20));
			getDocument().addDocumentListener(new MyDocumentListener(r));
		}
	}
}
