package game.sprite.editor.animators;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Enumeration;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

import app.SwingUtils;
import common.commands.AbstractCommand;
import game.sprite.Sprite;
import game.sprite.SpriteAnimation;
import game.sprite.SpriteComponent;
import game.sprite.SpritePalette;
import game.sprite.SpriteRaster;
import game.sprite.editor.PaletteCellRenderer;
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
import game.sprite.editor.commands.CreateCommand;
import net.miginfocom.swing.MigLayout;
import util.ui.EvenSpinner;
import util.ui.ListAdapterComboboxModel;

public class CommandAnimatorEditor extends AnimationEditor
{
	private static final String PANEL_LAYOUT_PROPERTIES = "ins 0 10 0 10, wrap";

	private static CommandAnimatorEditor instance;

	private AnimElementsList<AnimCommand> commandList;
	private ListDataListener commandListListener;

	private JPanel commandListPanel;
	private JPanel commandEditPanel;

	private CommandAnimator animator;

	private AnimElement selected;

	public static void bind(SpriteEditor editor, CommandAnimator animator, Container commandListContainer, Container commandEditContainer)
	{
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

	public static void init()
	{
		// allow SpriteEditor to pre-load at startup
		instance();
	}

	private static void repaintCommandList()
	{
		instance().commandList.repaint();
	}

	private CommandAnimatorEditor()
	{
		commandList = new AnimElementsList<>(this);
		commandList.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		commandList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		commandListPanel = new JPanel(new MigLayout("fill, ins 0, wrap 3",
			"[grow, sg col][grow, sg col][grow, sg col]"));
		commandEditPanel = new JPanel(new MigLayout("fill, ins 0, wrap"));

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
						SpriteEditor.instance().updatePlaybackStatus();
						break;
				}
			}
		});

		JButton waitBtn = new JButton("Wait");
		waitBtn.addActionListener((e) -> create(animator.new Wait()));

		JButton imgBtn = new JButton("Raster");
		imgBtn.addActionListener((e) -> create(animator.new SetImage()));

		JButton palBtn = new JButton("Palette");
		palBtn.addActionListener((e) -> create(animator.new SetPalette()));

		JButton labelBtn = new JButton("Label");
		labelBtn.addActionListener((e) -> create(animator.new Label()));

		JButton gotoBtn = new JButton("Goto");
		gotoBtn.addActionListener((e) -> create(animator.new Goto()));

		JButton loopBtn = new JButton("Repeat");
		loopBtn.addActionListener((e) -> create(animator.new Loop()));

		JButton posBtn = new JButton("Position");
		posBtn.addActionListener((e) -> create(animator.new SetPosition()));

		JButton rotBtn = new JButton("Rotation");
		rotBtn.addActionListener((e) -> create(animator.new SetRotation()));

		JButton scaleBtn = new JButton("Scale");
		scaleBtn.addActionListener((e) -> create(animator.new SetScale()));

		JButton parentBtn = new JButton("Parent");
		parentBtn.addActionListener((e) -> create(animator.new SetParent()));

		JButton notifyBtn = new JButton("Notify");
		notifyBtn.addActionListener((e) -> create(animator.new SetNotify()));

		JScrollPane listScrollPane = new JScrollPane(commandList);
		listScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		commandListPanel.add(SwingUtils.getLabel("Commands", 14), "growx, span");
		commandListPanel.add(listScrollPane, "grow, span, push");

		commandListPanel.add(waitBtn, "growx");
		commandListPanel.add(imgBtn, "growx");
		commandListPanel.add(palBtn, "growx");

		commandListPanel.add(posBtn, "growx");
		commandListPanel.add(rotBtn, "growx");
		commandListPanel.add(scaleBtn, "growx");

		commandListPanel.add(labelBtn, "growx");
		commandListPanel.add(gotoBtn, "growx");
		commandListPanel.add(loopBtn, "growx");

		commandListPanel.add(parentBtn, "growx");
		commandListPanel.add(notifyBtn, "growx");

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

	@Override
	public AnimElement getSelected()
	{
		return selected;
	}

	@Override
	public void setSelected(AnimElement elem)
	{
		if (elem == selected)
			return;

		selected = elem;

		commandEditPanel.removeAll();
		if (elem != null)
			commandEditPanel.add(elem.getPanel(), "grow, pushy");
		commandEditPanel.revalidate();
		commandEditPanel.repaint();
	}

	private static void create(AnimCommand cmd)
	{
		AnimElementsList<AnimCommand> list = instance().commandList;
		DefaultListModel<AnimCommand> model = instance().commandList.getDefaultModel();

		int pos;
		if (list.isSelectionEmpty())
			pos = model.getSize();
		else
			pos = list.getSelectedIndex() + 1;

		SpriteEditor.execute(new CreateCommand("Create " + cmd.getName(), list, cmd, pos));
	}

	protected static class LabelPanel extends JPanel
	{
		private static LabelPanel instance;
		private Label cmd;

		private LabelTextField labelNameField;
		private boolean ignoreChanges = false;

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
				if (ignoreChanges)
					return;

				// need to delay the command so we dont call setText while document is still processing events
				SwingUtilities.invokeLater(() -> {
					SpriteEditor.execute(new SetLabelNameCommand(cmd, labelNameField.getText()));
				});
			});

			labelNameField.setMargin(SwingUtils.TEXTBOX_INSETS);
			labelNameField.setBorder(BorderFactory.createCompoundBorder(
				labelNameField.getBorder(),
				BorderFactory.createEmptyBorder(2, 4, 2, 4)));
			SwingUtils.setFontSize(labelNameField, 14);

			add(SwingUtils.getLabel("Label Name", 14), "gapbottom 4");
			add(labelNameField, "growx, pushx");
		}

		protected void bind(Label cmd)
		{
			this.cmd = cmd;

			ignoreChanges = true;
			labelNameField.setText(cmd.labelName);
			ignoreChanges = false;
		}

		public class SetLabelNameCommand extends AbstractCommand
		{
			private final Label cmd;
			private final String next;
			private final String prev;

			public SetLabelNameCommand(Label cmd, String next)
			{
				super("Set Label Name");

				this.cmd = cmd;
				this.next = next;
				this.prev = cmd.labelName;
			}

			@Override
			public void exec()
			{
				cmd.labelName = next;

				ignoreChanges = true;
				labelNameField.setText(next);
				ignoreChanges = false;

				repaintCommandList();
			}

			@Override
			public void undo()
			{
				cmd.labelName = prev;

				ignoreChanges = true;
				labelNameField.setText(prev);
				ignoreChanges = false;

				repaintCommandList();
			}
		}
	}

	protected static class GotoPanel extends JPanel
	{
		private static GotoPanel instance;
		private Goto cmd;

		private JComboBox<Label> labelComboBox;
		private boolean ignoreChanges = false;

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

				Label label = (Label) labelComboBox.getSelectedItem();
				SpriteEditor.execute(new SetGotoLabelCommand(cmd, label));
			});

			add(SwingUtils.getLabel("Goto Label", 14), "gapbottom 4");
			add(labelComboBox, "growx, pushx");
		}

		protected void bind(Goto cmd, DefaultListModel<Label> labels)
		{
			this.cmd = cmd;

			//TODO could be a problem when labels are deleted!
			ignoreChanges = true;
			labelComboBox.setModel(new ListAdapterComboboxModel<>(labels));
			labelComboBox.setSelectedItem(cmd.label);
			ignoreChanges = false;
		}

		public class SetGotoLabelCommand extends AbstractCommand
		{
			private final Goto cmd;
			private final Label next;
			private final Label prev;

			public SetGotoLabelCommand(Goto cmd, Label next)
			{
				super("Set Goto Label");

				this.cmd = cmd;
				this.next = next;
				this.prev = cmd.label;
			}

			@Override
			public void exec()
			{
				cmd.label = next;

				ignoreChanges = true;
				labelComboBox.setSelectedItem(next);
				ignoreChanges = false;

				cmd.ownerComp.calculateTiming();
				repaintCommandList();
			}

			@Override
			public void undo()
			{
				cmd.label = prev;

				ignoreChanges = true;
				labelComboBox.setSelectedItem(prev);
				ignoreChanges = false;

				cmd.ownerComp.calculateTiming();
				repaintCommandList();
			}
		}
	}

	protected static class LoopPanel extends JPanel
	{
		private static LoopPanel instance;
		private Loop cmd;

		private JComboBox<Label> labelComboBox;
		private JSpinner countSpinner;
		private boolean ignoreChanges = false;

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

				Label label = (Label) labelComboBox.getSelectedItem();
				SpriteEditor.execute(new SetLoopLabelCommand(cmd, label));
			});

			countSpinner = new JSpinner();
			countSpinner.setModel(new SpinnerNumberModel(1, 0, 300, 1));
			countSpinner.addChangeListener((e) -> {
				if (ignoreChanges)
					return;

				int loopCount = (int) countSpinner.getValue();
				SpriteEditor.execute(new SetLoopCountCommand(cmd, loopCount));
			});

			SwingUtils.setFontSize(countSpinner, 12);
			SwingUtils.centerSpinnerText(countSpinner);
			SwingUtils.addBorderPadding(countSpinner);

			add(SwingUtils.getLabel("Repeat from Label", 14), "gapbottom 4");
			add(labelComboBox, "growx, pushx");
			add(SwingUtils.getLabel("Repetitions: ", 12), "split 2");
			add(countSpinner, "w 30%");
		}

		protected void bind(Loop cmd, DefaultListModel<Label> labels)
		{
			this.cmd = cmd;

			//TODO could be a problem when labels are deleted!
			ignoreChanges = true;
			labelComboBox.setModel(new ListAdapterComboboxModel<>(labels));
			labelComboBox.setSelectedItem(cmd.label);
			ignoreChanges = false;

			countSpinner.setValue(cmd.count);
		}

		public class SetLoopCountCommand extends AbstractCommand
		{
			private final Loop cmd;
			private final int next;
			private final int prev;

			public SetLoopCountCommand(Loop cmd, int next)
			{
				super("Set Loop Count");

				this.cmd = cmd;
				this.next = next;
				this.prev = cmd.count;
			}

			@Override
			public void exec()
			{
				cmd.count = next;

				ignoreChanges = true;
				countSpinner.setValue(next);
				ignoreChanges = false;

				cmd.ownerComp.calculateTiming();
				repaintCommandList();
			}

			@Override
			public void undo()
			{
				cmd.count = prev;

				ignoreChanges = true;
				countSpinner.setValue(prev);
				ignoreChanges = false;

				cmd.ownerComp.calculateTiming();
				repaintCommandList();
			}
		}

		public class SetLoopLabelCommand extends AbstractCommand
		{
			private final Loop cmd;
			private final Label next;
			private final Label prev;

			public SetLoopLabelCommand(Loop cmd, Label next)
			{
				super("Set Loop Label");

				this.cmd = cmd;
				this.next = next;
				this.prev = cmd.label;
			}

			@Override
			public void exec()
			{
				cmd.label = next;

				ignoreChanges = true;
				labelComboBox.setSelectedItem(next);
				ignoreChanges = false;

				cmd.ownerComp.calculateTiming();
				repaintCommandList();
			}

			@Override
			public void undo()
			{
				cmd.label = prev;

				ignoreChanges = true;
				labelComboBox.setSelectedItem(prev);
				ignoreChanges = false;

				cmd.ownerComp.calculateTiming();
				repaintCommandList();
			}
		}
	}

	protected static class WaitPanel extends JPanel
	{
		private static WaitPanel instance;
		private Wait cmd;

		private JSpinner countSpinner;
		private boolean ignoreChanges = false;

		protected static WaitPanel instance()
		{
			if (instance == null)
				instance = new WaitPanel();
			return instance;
		}

		private WaitPanel()
		{
			super(new MigLayout(PANEL_LAYOUT_PROPERTIES));

			countSpinner = new EvenSpinner();
			countSpinner.setModel(new SpinnerNumberModel(1, 0, 300, 1)); // longest used = 260
			countSpinner.addChangeListener((e) -> {
				if (ignoreChanges)
					return;

				SpriteEditor.execute(new SetDelayCommand(cmd, (int) countSpinner.getValue()));
			});

			SwingUtils.setFontSize(countSpinner, 12);
			SwingUtils.centerSpinnerText(countSpinner);
			SwingUtils.addBorderPadding(countSpinner);

			add(SwingUtils.getLabel("Wait Duration", 14), "gapbottom 4");
			add(countSpinner, "w 30%, split 2");
			add(SwingUtils.getLabel(" frames", 12));
		}

		protected void bind(Wait cmd)
		{
			this.cmd = cmd;

			ignoreChanges = true;
			countSpinner.setValue(cmd.count);
			ignoreChanges = false;
		}

		public class SetDelayCommand extends AbstractCommand
		{
			private final Wait cmd;
			private final int next;
			private final int prev;

			public SetDelayCommand(Wait cmd, int next)
			{
				super("Set Wait Delay");

				this.cmd = cmd;
				this.next = next;
				this.prev = cmd.count;
			}

			@Override
			public void exec()
			{
				cmd.count = next;

				ignoreChanges = true;
				countSpinner.setValue(next);
				ignoreChanges = false;

				cmd.ownerComp.calculateTiming();
				repaintCommandList();
			}

			@Override
			public void undo()
			{
				cmd.count = prev;

				ignoreChanges = true;
				countSpinner.setValue(prev);
				ignoreChanges = false;

				cmd.ownerComp.calculateTiming();
				repaintCommandList();
			}
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

				SpriteRaster img = (SpriteRaster) imageComboBox.getSelectedItem();
				SpriteEditor.execute(new SetImageCommand(cmd, img));
			});

			JButton btnChoose = new JButton("Select");
			SwingUtils.addBorderPadding(btnChoose);

			btnChoose.addActionListener((e) -> {
				Sprite sprite = cmd.ownerComp.parentAnimation.parentSprite;
				SpriteRaster raster = SpriteEditor.instance().promptForRaster(sprite);
				if (raster != null)
					SpriteEditor.execute(new SetImageCommand(cmd, raster));
			});

			JButton btnClear = new JButton("Clear");
			SwingUtils.addBorderPadding(btnClear);

			btnClear.addActionListener((e) -> {
				SpriteEditor.execute(new SetImageCommand(cmd, null));
			});

			add(SwingUtils.getLabel("Set Raster", 14), "gapbottom 4");
			add(imageComboBox, "w 60%, h 120!");
			add(btnChoose, "split 2, growx");
			add(btnClear, "growx");
		}

		protected void bind(SetImage cmd)
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

		public class SetImageCommand extends AbstractCommand
		{
			private final SetImage cmd;
			private final SpriteRaster next;
			private final SpriteRaster prev;

			public SetImageCommand(SetImage cmd, SpriteRaster next)
			{
				super("Set Raster");

				this.cmd = cmd;
				this.next = next;
				this.prev = cmd.img;
			}

			@Override
			public void exec()
			{
				cmd.img = next;

				ignoreChanges = true;
				imageComboBox.setSelectedItem(next);
				ignoreChanges = false;

				repaintCommandList();
			}

			@Override
			public void undo()
			{
				cmd.img = prev;

				ignoreChanges = true;
				imageComboBox.setSelectedItem(prev);
				ignoreChanges = false;

				repaintCommandList();
			}
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
			paletteComboBox.setRenderer(new PaletteCellRenderer("Use Raster Default"));
			paletteComboBox.addActionListener((e) -> {
				if (ignoreChanges || cmd == null)
					return;

				SpritePalette pal = (SpritePalette) paletteComboBox.getSelectedItem();
				SpriteEditor.execute(new SetPaletteCommand(cmd, pal));
			});

			add(SwingUtils.getLabel("Set Palette", 14), "gapbottom 4");
			add(paletteComboBox, "growx, pushx");
		}

		protected void bind(SetPalette cmd)
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

		public class SetPaletteCommand extends AbstractCommand
		{
			private final SetPalette cmd;
			private final SpritePalette next;
			private final SpritePalette prev;

			public SetPaletteCommand(SetPalette cmd, SpritePalette next)
			{
				super("Set Palette");

				this.cmd = cmd;
				this.next = next;
				this.prev = cmd.pal;
			}

			@Override
			public void exec()
			{
				cmd.pal = next;

				ignoreChanges = true;
				paletteComboBox.setSelectedItem(next);
				ignoreChanges = false;

				repaintCommandList();
			}

			@Override
			public void undo()
			{
				cmd.pal = prev;

				ignoreChanges = true;
				paletteComboBox.setSelectedItem(prev);
				ignoreChanges = false;

				repaintCommandList();
			}
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
			componentComboBox.addActionListener((e) -> {
				if (ignoreChanges)
					return;

				SpriteComponent comp = (SpriteComponent) componentComboBox.getSelectedItem();
				SpriteEditor.execute(new SetParentCommand(cmd, comp));

				cmd.comp = (SpriteComponent) componentComboBox.getSelectedItem();
				repaintCommandList();
			});

			add(SwingUtils.getLabel("Set Parent", 14), "gapbottom 4");
			add(componentComboBox, "growx, pushx");
		}

		protected void bind(SetParent cmd)
		{
			this.cmd = cmd;
			SpriteAnimation anim = cmd.ownerComp.parentAnimation;

			ignoreChanges = true;
			componentComboBox.setModel(new ListAdapterComboboxModel<>(anim.components));
			componentComboBox.setSelectedItem(cmd.comp);
			ignoreChanges = false;
		}

		public class SetParentCommand extends AbstractCommand
		{
			private final SetParent cmd;
			private final SpriteComponent next;
			private final SpriteComponent prev;

			public SetParentCommand(SetParent cmd, SpriteComponent next)
			{
				super("Set Parent");

				this.cmd = cmd;
				this.next = next;
				this.prev = cmd.comp;
			}

			@Override
			public void exec()
			{
				cmd.comp = next;

				ignoreChanges = true;
				componentComboBox.setSelectedItem(next);
				ignoreChanges = false;

				repaintCommandList();
			}

			@Override
			public void undo()
			{
				cmd.comp = prev;

				ignoreChanges = true;
				componentComboBox.setSelectedItem(prev);
				ignoreChanges = false;

				repaintCommandList();
			}
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
			valueSpinner.setModel(new SpinnerNumberModel(0, 0, 255, 1));
			valueSpinner.addChangeListener((e) -> {
				if (ignoreChanges)
					return;

				SpriteEditor.execute(new SetNotifyCommand(cmd, (int) valueSpinner.getValue()));
			});

			SwingUtils.setFontSize(valueSpinner, 12);
			SwingUtils.centerSpinnerText(valueSpinner);
			SwingUtils.addBorderPadding(valueSpinner);

			add(SwingUtils.getLabel("Set Notify", 14), "gapbottom 4");
			add(valueSpinner, "w 30%!");
		}

		protected void bind(SetNotify cmd)
		{
			this.cmd = cmd;

			ignoreChanges = true;
			valueSpinner.setValue(cmd.value);
			ignoreChanges = false;
		}

		public class SetNotifyCommand extends AbstractCommand
		{
			private final SetNotify cmd;
			private final int next;
			private final int prev;

			public SetNotifyCommand(SetNotify cmd, int next)
			{
				super("Set Notify");

				this.cmd = cmd;
				this.next = next;
				this.prev = cmd.value;
			}

			@Override
			public void exec()
			{
				cmd.value = next;

				ignoreChanges = true;
				valueSpinner.setValue(next);
				ignoreChanges = false;

				repaintCommandList();
			}

			@Override
			public void undo()
			{
				cmd.value = prev;

				ignoreChanges = true;
				valueSpinner.setValue(prev);
				ignoreChanges = false;

				repaintCommandList();
			}
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
			valueSpinner.setModel(new SpinnerNumberModel(0, 0, 255, 1));
			valueSpinner.addChangeListener((e) -> {
				if (ignoreChanges)
					return;

				SpriteEditor.execute(new SetUnknownCommand(cmd, (int) valueSpinner.getValue()));
			});

			SwingUtils.setFontSize(valueSpinner, 12);
			SwingUtils.centerSpinnerText(valueSpinner);
			SwingUtils.addBorderPadding(valueSpinner);

			add(SwingUtils.getLabel("Set Notify", 14), "gapbottom 4");
			add(valueSpinner, "w 30%");
		}

		protected void bind(SetUnknown cmd)
		{
			this.cmd = cmd;

			ignoreChanges = true;
			valueSpinner.setValue(cmd.value);
			ignoreChanges = false;
		}

		public class SetUnknownCommand extends AbstractCommand
		{
			private final SetUnknown cmd;
			private final int next;
			private final int prev;

			public SetUnknownCommand(SetUnknown cmd, int next)
			{
				super("Set Unknown");

				this.cmd = cmd;
				this.next = next;
				this.prev = cmd.value;
			}

			@Override
			public void exec()
			{
				cmd.value = next;

				ignoreChanges = true;
				valueSpinner.setValue(next);
				ignoreChanges = false;

				repaintCommandList();
			}

			@Override
			public void undo()
			{
				cmd.value = prev;

				ignoreChanges = true;
				valueSpinner.setValue(prev);
				ignoreChanges = false;

				repaintCommandList();
			}
		}
	}

	protected static class SetPositionPanel extends JPanel
	{
		private static SetPositionPanel instance;
		private SetPosition cmd;

		private boolean ignoreChanges = false;
		private JSpinner xSpinner, ySpinner, zSpinner;

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
			xSpinner.setModel(new SpinnerNumberModel(0, -256, 256, 1));
			xSpinner.addChangeListener((e) -> {
				if (ignoreChanges)
					return;
				SpriteEditor.execute(new SetPositionCommand(cmd, 0, (int) xSpinner.getValue()));
			});

			SwingUtils.setFontSize(xSpinner, 12);
			SwingUtils.centerSpinnerText(xSpinner);
			SwingUtils.addBorderPadding(xSpinner);

			ySpinner = new JSpinner();
			ySpinner.setModel(new SpinnerNumberModel(0, -256, 256, 1));
			ySpinner.addChangeListener((e) -> {
				if (ignoreChanges)
					return;
				SpriteEditor.execute(new SetPositionCommand(cmd, 1, (int) ySpinner.getValue()));
			});

			SwingUtils.setFontSize(ySpinner, 12);
			SwingUtils.centerSpinnerText(ySpinner);
			SwingUtils.addBorderPadding(ySpinner);

			zSpinner = new JSpinner();
			zSpinner.setModel(new SpinnerNumberModel(0, -256, 256, 1));
			zSpinner.addChangeListener((e) -> {
				if (ignoreChanges)
					return;
				SpriteEditor.execute(new SetPositionCommand(cmd, 2, (int) zSpinner.getValue()));
			});

			SwingUtils.setFontSize(zSpinner, 12);
			SwingUtils.centerSpinnerText(zSpinner);
			SwingUtils.addBorderPadding(zSpinner);

			JPanel coordPanel = new JPanel(new MigLayout("fill, ins 0", "[sg spin]4[sg spin]4[sg spin]"));
			coordPanel.add(xSpinner);
			coordPanel.add(ySpinner);
			coordPanel.add(zSpinner);

			add(SwingUtils.getLabel("Set Position Offset", 14), "gapbottom 4");
			add(coordPanel, "growx");
		}

		protected void bind(SetPosition cmd)
		{
			this.cmd = cmd;

			ignoreChanges = true;
			xSpinner.setValue(cmd.x);
			ySpinner.setValue(cmd.y);
			zSpinner.setValue(cmd.z);
			ignoreChanges = false;
		}

		public class SetPositionCommand extends AbstractCommand
		{
			private final SetPosition cmd;
			private final int coord;
			private final int next;
			private final int prev;

			public SetPositionCommand(SetPosition cmd, int coord, int next)
			{
				super("Set Position");

				this.cmd = cmd;
				this.coord = coord;
				this.next = next;

				switch (coord) {
					case 0:
						this.prev = cmd.x;
						break;
					case 1:
						this.prev = cmd.y;
						break;
					default:
						this.prev = cmd.z;
						break;
				}
			}

			@Override
			public void exec()
			{
				switch (coord) {
					case 0:
						cmd.x = next;
						break;
					case 1:
						cmd.y = next;
						break;
					default:
						cmd.z = next;
						break;
				}

				ignoreChanges = true;
				switch (coord) {
					case 0:
						xSpinner.setValue(next);
						break;
					case 1:
						ySpinner.setValue(next);
						break;
					default:
						zSpinner.setValue(next);
						break;
				}
				ignoreChanges = false;

				repaintCommandList();
			}

			@Override
			public void undo()
			{
				switch (coord) {
					case 0:
						cmd.x = prev;
						break;
					case 1:
						cmd.y = prev;
						break;
					default:
						cmd.z = prev;
						break;
				}

				ignoreChanges = true;
				switch (coord) {
					case 0:
						xSpinner.setValue(prev);
						break;
					case 1:
						ySpinner.setValue(prev);
						break;
					default:
						zSpinner.setValue(prev);
						break;
				}
				ignoreChanges = false;

				repaintCommandList();
			}
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
			xSpinner.setModel(new SpinnerNumberModel(0, -180, 180, 1));
			xSpinner.addChangeListener((e) -> {
				if (ignoreChanges)
					return;
				SpriteEditor.execute(new SetRotationCommand(cmd, 0, (int) xSpinner.getValue()));
			});

			SwingUtils.setFontSize(xSpinner, 12);
			SwingUtils.centerSpinnerText(xSpinner);
			SwingUtils.addBorderPadding(xSpinner);

			ySpinner = new JSpinner();
			ySpinner.setModel(new SpinnerNumberModel(0, -180, 180, 1));
			ySpinner.addChangeListener((e) -> {
				if (ignoreChanges)
					return;
				SpriteEditor.execute(new SetRotationCommand(cmd, 1, (int) ySpinner.getValue()));
			});

			SwingUtils.setFontSize(ySpinner, 12);
			SwingUtils.centerSpinnerText(ySpinner);
			SwingUtils.addBorderPadding(ySpinner);

			zSpinner = new JSpinner();
			zSpinner.setModel(new SpinnerNumberModel(0, -180, 180, 1));
			zSpinner.addChangeListener((e) -> {
				if (ignoreChanges)
					return;
				SpriteEditor.execute(new SetRotationCommand(cmd, 2, (int) zSpinner.getValue()));
			});

			SwingUtils.setFontSize(zSpinner, 12);
			SwingUtils.centerSpinnerText(zSpinner);
			SwingUtils.addBorderPadding(zSpinner);

			JPanel coordPanel = new JPanel(new MigLayout("fill, ins 0", "[sg spin]4[sg spin]4[sg spin]"));
			coordPanel.add(xSpinner);
			coordPanel.add(ySpinner);
			coordPanel.add(zSpinner);

			add(SwingUtils.getLabel("Set Rotation Angles", 14), "gapbottom 4");
			add(coordPanel, "growx");
		}

		protected void bind(SetRotation cmd)
		{
			this.cmd = cmd;

			ignoreChanges = true;
			xSpinner.setValue(cmd.x);
			ySpinner.setValue(cmd.y);
			zSpinner.setValue(cmd.z);
			ignoreChanges = false;
		}

		public class SetRotationCommand extends AbstractCommand
		{
			private final SetRotation cmd;
			private final int coord;
			private final int next;
			private final int prev;

			public SetRotationCommand(SetRotation cmd, int coord, int next)
			{
				super("Set Rotation");

				this.cmd = cmd;
				this.coord = coord;
				this.next = next;

				switch (coord) {
					case 0:
						this.prev = cmd.x;
						break;
					case 1:
						this.prev = cmd.y;
						break;
					default:
						this.prev = cmd.z;
						break;
				}
			}

			@Override
			public void exec()
			{
				switch (coord) {
					case 0:
						cmd.x = next;
						break;
					case 1:
						cmd.y = next;
						break;
					default:
						cmd.z = next;
						break;
				}

				ignoreChanges = true;
				switch (coord) {
					case 0:
						xSpinner.setValue(next);
						break;
					case 1:
						ySpinner.setValue(next);
						break;
					default:
						zSpinner.setValue(next);
						break;
				}
				ignoreChanges = false;

				repaintCommandList();
			}

			@Override
			public void undo()
			{
				switch (coord) {
					case 0:
						cmd.x = prev;
						break;
					case 1:
						cmd.y = prev;
						break;
					default:
						cmd.z = prev;
						break;
				}

				ignoreChanges = true;
				switch (coord) {
					case 0:
						xSpinner.setValue(prev);
						break;
					case 1:
						ySpinner.setValue(prev);
						break;
					default:
						zSpinner.setValue(prev);
						break;
				}
				ignoreChanges = false;

				repaintCommandList();
			}
		}
	}

	protected static class SetScalePanel extends JPanel
	{
		private static SetScalePanel instance;
		private SetScale cmd;

		private boolean ignoreChanges = false;
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
			scaleSpinner.setModel(new SpinnerNumberModel(1, 0, 500, 1));
			scaleSpinner.addChangeListener((e) -> {
				if (ignoreChanges)
					return;
				SpriteEditor.execute(new SetScalePercentCommand(cmd, (int) scaleSpinner.getValue()));
			});

			SwingUtils.setFontSize(scaleSpinner, 12);
			SwingUtils.centerSpinnerText(scaleSpinner);
			SwingUtils.addBorderPadding(scaleSpinner);

			ButtonGroup scaleButtons = new ButtonGroup();

			ActionListener buttonListener = e -> {
				int i = 0;
				for (Enumeration<AbstractButton> buttons = scaleButtons.getElements(); buttons.hasMoreElements(); i++) {
					AbstractButton button = buttons.nextElement();
					if (button.isSelected())
						SpriteEditor.execute(new SetScaleTypeCommand(cmd, i));
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

			add(SwingUtils.getLabel("Set Scale Percent", 14), "gapbottom 4");
			add(scaleSpinner);
			add(allButton);
			add(xButton);
			add(yButton);
			add(zButton);
		}

		protected void bind(SetScale cmd)
		{
			this.cmd = cmd;

			ignoreChanges = true;
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
			ignoreChanges = false;
		}

		public class SetScalePercentCommand extends AbstractCommand
		{
			private final SetScale cmd;
			private final int next;
			private final int prev;

			public SetScalePercentCommand(SetScale cmd, int next)
			{
				super("Set Scale Percent");

				this.cmd = cmd;
				this.next = next;
				this.prev = cmd.scalePercent;
			}

			@Override
			public void exec()
			{
				cmd.scalePercent = next;

				ignoreChanges = true;
				scaleSpinner.setValue(next);
				ignoreChanges = false;

				repaintCommandList();
			}

			@Override
			public void undo()
			{
				cmd.scalePercent = prev;

				ignoreChanges = true;
				scaleSpinner.setValue(prev);
				ignoreChanges = false;

				repaintCommandList();
			}
		}

		public class SetScaleTypeCommand extends AbstractCommand
		{
			private final SetScale cmd;
			private final int next;
			private final int prev;

			public SetScaleTypeCommand(SetScale cmd, int next)
			{
				super("Set Scale Type");

				this.cmd = cmd;
				this.next = next;
				this.prev = cmd.type;
			}

			@Override
			public void exec()
			{
				cmd.type = next;

				ignoreChanges = true;
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
				ignoreChanges = false;

				repaintCommandList();
			}

			@Override
			public void undo()
			{
				cmd.type = prev;

				ignoreChanges = true;
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
				ignoreChanges = false;

				repaintCommandList();
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
