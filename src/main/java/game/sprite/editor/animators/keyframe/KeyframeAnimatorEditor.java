package game.sprite.editor.animators.keyframe;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

import app.SwingUtils;
import common.commands.AbstractCommand;
import game.sprite.Sprite;
import game.sprite.SpriteAnimation;
import game.sprite.SpriteComponent;
import game.sprite.SpritePalette;
import game.sprite.SpriteRaster;
import game.sprite.editor.SpriteEditor;
import game.sprite.editor.animators.AnimElement;
import game.sprite.editor.animators.AnimElementsList;
import game.sprite.editor.animators.BlankArrowUI;
import game.sprite.editor.animators.ComponentAnimationEditor;
import game.sprite.editor.animators.SpriteRasterRenderer;
import game.sprite.editor.commands.CreateCommand;
import game.sprite.editor.commands.keyframe.SetKeyframeDuration;
import game.sprite.editor.commands.keyframe.SetKeyframeEnablePalette;
import game.sprite.editor.commands.keyframe.SetKeyframeEnableRaster;
import game.sprite.editor.commands.keyframe.SetKeyframeName;
import game.sprite.editor.commands.keyframe.SetKeyframePalette;
import game.sprite.editor.commands.keyframe.SetKeyframePosition;
import game.sprite.editor.commands.keyframe.SetKeyframeRaster;
import game.sprite.editor.commands.keyframe.SetKeyframeRotation;
import game.sprite.editor.commands.keyframe.SetKeyframeScale;
import net.miginfocom.swing.MigLayout;
import util.ui.EvenSpinner;
import util.ui.ListAdapterComboboxModel;
import util.ui.NameTextField;

public class KeyframeAnimatorEditor extends ComponentAnimationEditor
{
	private static final String PANEL_LAYOUT_PROPERTIES = "ins 0 10 0 10, wrap";

	private static KeyframeAnimatorEditor instance;

	private AnimElementsList<AnimKeyframe> commandList;
	private ListDataListener commandListListener;

	private JPanel commandListPanel;
	private JPanel commandEditPanel;

	private SpriteEditor editor;
	private KeyframeAnimator animator;

	private AnimElement selected;

	public static void bind(SpriteEditor editor, KeyframeAnimator animator, Container commandListContainer, Container commandEditContainer)
	{
		instance().editor = editor;
		instance().animator = animator;

		commandListContainer.removeAll();
		commandListContainer.add(instance().commandListPanel, "grow");

		commandEditContainer.removeAll();
		commandEditContainer.add(instance().commandEditPanel, "grow");

		instance().commandList.setModel(animator.keyframes);

		animator.keyframes.removeListDataListener(instance().commandListListener);
		animator.keyframes.addListDataListener(instance().commandListListener);
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

		KeyframePanel.instance().setModel(rasterModel, paletteModel);
	}

	private static KeyframeAnimatorEditor instance()
	{
		if (instance == null)
			instance = new KeyframeAnimatorEditor();
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

	private KeyframeAnimatorEditor()
	{
		commandList = new AnimElementsList<AnimKeyframe>(this);
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
						editor.updatePlaybackStatus();
						break;
				}
			}
		});

		JButton newBtn = new JButton("Keyframe");
		newBtn.addActionListener((e) -> create(new Keyframe(animator)));

		JButton repeatBtn = new JButton("Repeat");
		repeatBtn.addActionListener((e) -> create(new LoopKey(animator, (Keyframe) null, 3)));

		JButton gotoBtn = new JButton("Goto");
		gotoBtn.addActionListener((e) -> create(new GotoKey(animator, (Keyframe) null)));

		JButton parentBtn = new JButton("Parent");
		parentBtn.addActionListener((e) -> create(new ParentKey(animator, (SpriteComponent) null)));

		JButton notifyBtn = new JButton("Notify");
		notifyBtn.addActionListener((e) -> create(new NotifyKey(animator, 1)));

		JScrollPane listScrollPane = new JScrollPane(commandList);
		listScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

		commandListPanel.add(SwingUtils.getLabel("Commands", 14), "growx, span");
		commandListPanel.add(listScrollPane, "grow, span, push");

		commandListPanel.add(newBtn, "growx");
		commandListPanel.add(repeatBtn, "growx");
		commandListPanel.add(gotoBtn, "growx");
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

	private static void create(AnimKeyframe cmd)
	{
		AnimElementsList<AnimKeyframe> list = instance().commandList;
		DefaultListModel<AnimKeyframe> model = instance().commandList.getDefaultModel();

		int pos;
		if (list.isSelectionEmpty())
			pos = model.getSize();
		else
			pos = list.getSelectedIndex() + 1;

		SpriteEditor.execute(new CreateCommand("Create " + cmd.getName(), list, cmd, pos));
	}

	protected static class GotoPanel extends JPanel
	{
		private static GotoPanel instance;
		private GotoKey cmd;

		private JComboBox<Keyframe> keyframeComboBox;

		protected static GotoPanel instance()
		{
			if (instance == null)
				instance = new GotoPanel();
			return instance;
		}

		private GotoPanel()
		{
			super(new MigLayout(PANEL_LAYOUT_PROPERTIES));

			keyframeComboBox = new JComboBox<>();
			SwingUtils.setFontSize(keyframeComboBox, 14);
			keyframeComboBox.setMaximumRowCount(16);
			keyframeComboBox.addActionListener((e) -> {
				if (ignoreChanges)
					return;
				cmd.target = (Keyframe) keyframeComboBox.getSelectedItem();
				repaintCommandList();
			});

			add(SwingUtils.getLabel("Goto Keyframe", 14), "gapbottom 4");
			add(keyframeComboBox, "w 200!");
		}

		private boolean ignoreChanges = false;

		protected void set(GotoKey cmd, DefaultListModel<AnimKeyframe> animKeyframes)
		{
			this.cmd = cmd;

			DefaultComboBoxModel<Keyframe> keyframes = new DefaultComboBoxModel<>();
			for (int i = 0; i < animKeyframes.size(); i++) {
				AnimKeyframe animFrame = animKeyframes.getElementAt(i);
				if (animFrame instanceof Keyframe kf)
					keyframes.addElement(kf);
			}

			ignoreChanges = true;
			keyframeComboBox.setModel(new ListAdapterComboboxModel<>(keyframes));
			keyframeComboBox.setSelectedItem(cmd.target);
			ignoreChanges = false;
		}
	}

	protected static class LoopPanel extends JPanel
	{
		private static LoopPanel instance;
		private LoopKey cmd;

		private JComboBox<Keyframe> keyframeComboBox;
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

			keyframeComboBox = new JComboBox<>();
			SwingUtils.setFontSize(keyframeComboBox, 14);
			keyframeComboBox.setMaximumRowCount(16);
			keyframeComboBox.addActionListener((e) -> {
				if (ignoreChanges)
					return;
				cmd.target = (Keyframe) keyframeComboBox.getSelectedItem();
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
			add(keyframeComboBox, "w 200!");
			add(SwingUtils.getLabel("Repetitions: ", 12), "sg lbl, split 2");
			add(countSpinner, "sg etc, grow");
		}

		private boolean ignoreChanges = false;

		protected void set(LoopKey cmd, DefaultListModel<AnimKeyframe> animKeyframes)
		{
			this.cmd = cmd;

			DefaultComboBoxModel<Keyframe> keyframes = new DefaultComboBoxModel<>();
			for (int i = 0; i < animKeyframes.size(); i++) {
				AnimKeyframe animFrame = animKeyframes.getElementAt(i);
				if (animFrame instanceof Keyframe kf)
					keyframes.addElement(kf);
			}

			ignoreChanges = true;
			keyframeComboBox.setModel(new ListAdapterComboboxModel<>(keyframes));
			keyframeComboBox.setSelectedItem(cmd.target);
			ignoreChanges = false;

			countSpinner.setValue(cmd.count);
		}
	}

	protected static class SetParentPanel extends JPanel
	{
		private static SetParentPanel instance;
		private ParentKey cmd;

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
				SpriteEditor.execute(new SetKeyframeParent(cmd, comp));

				cmd.parent = (SpriteComponent) componentComboBox.getSelectedItem();
				repaintCommandList();
			});

			add(SwingUtils.getLabel("Set Parent", 14), "gapbottom 4");
			add(componentComboBox, "growx, pushx");
		}

		protected void bind(ParentKey cmd)
		{
			this.cmd = cmd;
			SpriteAnimation anim = cmd.owner.parentAnimation;

			ignoreChanges = true;
			componentComboBox.setModel(new ListAdapterComboboxModel<>(anim.components));
			componentComboBox.setSelectedItem(cmd.parent);
			ignoreChanges = false;
		}

		public class SetKeyframeParent extends AbstractCommand
		{
			private final ParentKey cmd;
			private final SpriteComponent next;
			private final SpriteComponent prev;

			public SetKeyframeParent(ParentKey cmd, SpriteComponent next)
			{
				super("Set Parent");

				this.cmd = cmd;
				this.next = next;
				this.prev = cmd.parent;
			}

			@Override
			public void exec()
			{
				super.exec();

				cmd.parent = next;

				ignoreChanges = true;
				componentComboBox.setSelectedItem(next);
				ignoreChanges = false;

				cmd.incrementModified();
				repaintCommandList();
			}

			@Override
			public void undo()
			{
				super.undo();

				cmd.parent = prev;

				ignoreChanges = true;
				componentComboBox.setSelectedItem(prev);
				ignoreChanges = false;

				cmd.decrementModified();
				repaintCommandList();
			}
		}
	}

	protected static class SetNotifyPanel extends JPanel
	{
		private static SetNotifyPanel instance;
		private NotifyKey cmd;

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

				SpriteEditor.execute(new SetKeyframeNotify(cmd, (int) valueSpinner.getValue()));
			});

			SwingUtils.setFontSize(valueSpinner, 12);
			SwingUtils.centerSpinnerText(valueSpinner);
			SwingUtils.addBorderPadding(valueSpinner);

			add(SwingUtils.getLabel("Set Notify", 14), "gapbottom 4");
			add(valueSpinner, "w 30%!");
		}

		protected void bind(NotifyKey cmd)
		{
			this.cmd = cmd;

			ignoreChanges = true;
			valueSpinner.setValue(cmd.value);
			ignoreChanges = false;
		}

		public class SetKeyframeNotify extends AbstractCommand
		{
			private final NotifyKey cmd;
			private final int next;
			private final int prev;

			public SetKeyframeNotify(NotifyKey cmd, int next)
			{
				super("Set Notify");

				this.cmd = cmd;
				this.next = next;
				this.prev = cmd.value;
			}

			@Override
			public void exec()
			{
				super.exec();

				cmd.value = next;

				ignoreChanges = true;
				valueSpinner.setValue(next);
				ignoreChanges = false;

				cmd.incrementModified();
				repaintCommandList();
			}

			@Override
			public void undo()
			{
				super.undo();

				cmd.value = prev;

				ignoreChanges = true;
				valueSpinner.setValue(prev);
				ignoreChanges = false;

				cmd.decrementModified();
				repaintCommandList();
			}
		}
	}

	protected static class KeyframePanel extends JPanel
	{
		private static KeyframePanel instance;
		private Keyframe cmd;

		private boolean ignoreChanges = false;

		private NameTextField nameField;

		private JSpinner timeSpinner;

		private JCheckBox cbEnableImg, cbEnablePal;
		private JComboBox<SpriteRaster> imageComboBox;
		private JComboBox<SpritePalette> paletteComboBox;

		private JButton btnChoose, btnClear;

		private JSpinner dxSpinner, dySpinner, dzSpinner;
		private JSpinner rxSpinner, rySpinner, rzSpinner;
		private JSpinner sxSpinner, sySpinner, szSpinner;

		protected static KeyframePanel instance()
		{
			if (instance == null)
				instance = new KeyframePanel();
			return instance;
		}

		private void editCallback()
		{
			set(cmd);
			repaint();
		}

		private KeyframePanel()
		{
			nameField = new NameTextField((newValue) -> {
				if (!ignoreChanges)
					SpriteEditor.execute(new SetKeyframeName(cmd, newValue, this::editCallback));
			});
			SwingUtils.addBorderPadding(nameField);

			timeSpinner = new EvenSpinner();
			SwingUtils.setFontSize(timeSpinner, 12);
			timeSpinner.setModel(new SpinnerNumberModel(1, 0, 300, 1));
			timeSpinner.addChangeListener((e) -> {
				if (!ignoreChanges) {
					int newValue = (int) timeSpinner.getValue();
					SpriteEditor.execute(new SetKeyframeDuration(cmd, newValue, this::editCallback));
				}
			});
			SwingUtils.centerSpinnerText(timeSpinner);
			SwingUtils.addBorderPadding(timeSpinner);

			imageComboBox = new JComboBox<>();
			imageComboBox.setUI(new BlankArrowUI());
			SpriteRasterRenderer renderer = new SpriteRasterRenderer();
			renderer.setMinimumSize(new Dimension(80, 80));
			renderer.setPreferredSize(new Dimension(80, 80));
			imageComboBox.setRenderer(renderer);
			imageComboBox.setMaximumRowCount(5);
			imageComboBox.addActionListener((e) -> {
				if (!ignoreChanges) {
					SpriteRaster newValue = (SpriteRaster) imageComboBox.getSelectedItem();
					SpriteEditor.execute(new SetKeyframeRaster(cmd, newValue, this::editCallback));
				}
			});

			btnChoose = new JButton("Select");
			SwingUtils.addBorderPadding(btnChoose);

			btnChoose.addActionListener((e) -> {
				Sprite sprite = cmd.owner.parentAnimation.parentSprite;
				SpriteRaster raster = SpriteEditor.instance().promptForRaster(sprite);
				if (raster != null)
					SpriteEditor.execute(new SetKeyframeRaster(cmd, raster, this::editCallback));
			});

			btnClear = new JButton("Clear");
			SwingUtils.addBorderPadding(btnClear);

			btnClear.addActionListener((e) -> {
				SpriteEditor.execute(new SetKeyframeRaster(cmd, null, this::editCallback));
			});

			cbEnableImg = new JCheckBox(" Raster");
			cbEnableImg.addActionListener((e) -> {
				boolean value = cbEnableImg.isSelected();
				SpriteEditor.execute(new SetKeyframeEnableRaster(cmd, value, this::editCallback));
			});

			paletteComboBox = new JComboBox<>();
			SwingUtils.setFontSize(paletteComboBox, 14);
			paletteComboBox.setMaximumRowCount(24);
			//	paletteComboBox.setRenderer(new PaletteCellRenderer("Use Raster Default"));
			paletteComboBox.addActionListener((e) -> {
				if (!ignoreChanges) {
					SpritePalette newValue = (SpritePalette) paletteComboBox.getSelectedItem();
					SpriteEditor.execute(new SetKeyframePalette(cmd, newValue, this::editCallback));
				}
			});

			cbEnablePal = new JCheckBox(" Palette");
			cbEnablePal.addActionListener((e) -> {
				boolean value = cbEnablePal.isSelected();
				SpriteEditor.execute(new SetKeyframeEnablePalette(cmd, value, this::editCallback));
			});

			dxSpinner = new JSpinner();
			SwingUtils.setFontSize(dxSpinner, 12);
			dxSpinner.setModel(new SpinnerNumberModel(0, -256, 256, 1));
			dxSpinner.addChangeListener((e) -> {
				if (!ignoreChanges) {
					int value = (int) dxSpinner.getValue();
					SpriteEditor.execute(new SetKeyframePosition(cmd, 0, value, this::editCallback));
				}
			});
			SwingUtils.centerSpinnerText(dxSpinner);
			SwingUtils.addBorderPadding(dxSpinner);

			dySpinner = new JSpinner();
			SwingUtils.setFontSize(dySpinner, 12);
			dySpinner.setModel(new SpinnerNumberModel(0, -256, 256, 1));
			dySpinner.addChangeListener((e) -> {
				if (!ignoreChanges) {
					int value = (int) dySpinner.getValue();
					SpriteEditor.execute(new SetKeyframePosition(cmd, 1, value, this::editCallback));
				}
			});
			SwingUtils.centerSpinnerText(dySpinner);
			SwingUtils.addBorderPadding(dySpinner);

			dzSpinner = new JSpinner();
			SwingUtils.setFontSize(dzSpinner, 12);
			dzSpinner.setModel(new SpinnerNumberModel(0, -256, 256, 1));
			dzSpinner.addChangeListener((e) -> {
				if (!ignoreChanges) {
					int value = (int) dzSpinner.getValue();
					SpriteEditor.execute(new SetKeyframePosition(cmd, 2, value, this::editCallback));
				}
			});
			SwingUtils.centerSpinnerText(dzSpinner);
			SwingUtils.addBorderPadding(dzSpinner);

			rxSpinner = new JSpinner();
			SwingUtils.setFontSize(rxSpinner, 12);
			rxSpinner.setModel(new SpinnerNumberModel(0, -180, 180, 1));
			rxSpinner.addChangeListener((e) -> {
				if (!ignoreChanges) {
					int value = (int) rxSpinner.getValue();
					SpriteEditor.execute(new SetKeyframeRotation(cmd, 0, value, this::editCallback));
				}
			});
			SwingUtils.centerSpinnerText(rxSpinner);
			SwingUtils.addBorderPadding(rxSpinner);

			rySpinner = new JSpinner();
			SwingUtils.setFontSize(rySpinner, 12);
			rySpinner.setModel(new SpinnerNumberModel(0, -180, 180, 1));
			rySpinner.addChangeListener((e) -> {
				if (!ignoreChanges) {
					int value = (int) rySpinner.getValue();
					SpriteEditor.execute(new SetKeyframeRotation(cmd, 1, value, this::editCallback));
				}
			});
			SwingUtils.centerSpinnerText(rySpinner);
			SwingUtils.addBorderPadding(rySpinner);

			rzSpinner = new JSpinner();
			SwingUtils.setFontSize(rzSpinner, 12);
			rzSpinner.setModel(new SpinnerNumberModel(0, -180, 180, 1));
			rzSpinner.addChangeListener((e) -> {
				if (!ignoreChanges) {
					int value = (int) rzSpinner.getValue();
					SpriteEditor.execute(new SetKeyframeRotation(cmd, 2, value, this::editCallback));
				}
			});
			SwingUtils.centerSpinnerText(rzSpinner);
			SwingUtils.addBorderPadding(rzSpinner);

			sxSpinner = new JSpinner();
			SwingUtils.setFontSize(sxSpinner, 12);
			sxSpinner.setModel(new SpinnerNumberModel(1, 0, 500, 1));
			sxSpinner.addChangeListener((e) -> {
				if (!ignoreChanges) {
					int value = (int) sxSpinner.getValue();
					SpriteEditor.execute(new SetKeyframeScale(cmd, 0, value, this::editCallback));
				}
			});
			SwingUtils.centerSpinnerText(sxSpinner);
			SwingUtils.addBorderPadding(sxSpinner);

			sySpinner = new JSpinner();
			SwingUtils.setFontSize(sySpinner, 12);
			sySpinner.setModel(new SpinnerNumberModel(1, 0, 500, 1));
			sySpinner.addChangeListener((e) -> {
				if (!ignoreChanges) {
					int value = (int) sySpinner.getValue();
					SpriteEditor.execute(new SetKeyframeScale(cmd, 1, value, this::editCallback));
				}
			});
			SwingUtils.centerSpinnerText(sySpinner);
			SwingUtils.addBorderPadding(sySpinner);

			szSpinner = new JSpinner();
			SwingUtils.setFontSize(szSpinner, 12);
			szSpinner.setModel(new SpinnerNumberModel(1, 0, 500, 1));
			szSpinner.addChangeListener((e) -> {
				if (!ignoreChanges) {
					int value = (int) szSpinner.getValue();
					SpriteEditor.execute(new SetKeyframeScale(cmd, 2, value, this::editCallback));
				}
			});
			SwingUtils.centerSpinnerText(szSpinner);
			SwingUtils.addBorderPadding(szSpinner);

			JPanel spinPanel = new JPanel(new MigLayout("fillx, ins 0, wrap 4", "[]8[sg spin]6[sg spin]6[sg spin]", "[]8[]8[]"));
			String spinnerConstraints = "w 75!";

			spinPanel.add(SwingUtils.getLabel("Pos", 12), "pushx, growx");
			spinPanel.add(dxSpinner, spinnerConstraints);
			spinPanel.add(dySpinner, spinnerConstraints);
			spinPanel.add(dzSpinner, spinnerConstraints);

			spinPanel.add(SwingUtils.getLabel("Rot", 12), "pushx, growx");
			spinPanel.add(rxSpinner, spinnerConstraints);
			spinPanel.add(rySpinner, spinnerConstraints);
			spinPanel.add(rzSpinner, spinnerConstraints);

			spinPanel.add(SwingUtils.getLabel("Scale", 12), "pushx, growx");
			spinPanel.add(sxSpinner, spinnerConstraints);
			spinPanel.add(sySpinner, spinnerConstraints);
			spinPanel.add(szSpinner, spinnerConstraints);

			setLayout(new MigLayout("ins 0, wrap 2", "[grow]8[grow]", "[]8"));

			add(SwingUtils.getLabel("Keyframe Properties", 14), "gapbottom 4, span, wrap");
			add(new JLabel("Name"));
			add(nameField, "growx");
			add(new JLabel("Duration"));
			add(timeSpinner, "w 75!, split 2");
			add(SwingUtils.getLabel("frames", 12), "gapleft 8");

			JPanel rasterPanel = new JPanel(new MigLayout("fillx, ins 0, wrap 2", "[sg but][sg but]"));

			rasterPanel.add(imageComboBox, "growx, h 120!, span");
			rasterPanel.add(btnChoose, "growx, sg imgbut");
			rasterPanel.add(btnClear, "growx, sg imgbut");

			add(cbEnableImg, "top");
			add(rasterPanel, "grow");

			add(cbEnablePal, "gaptop 2, top");
			add(paletteComboBox, "growx");

			add(spinPanel, "growx, span, gaptop 8");
		}

		protected void set(Keyframe cmd)
		{
			this.cmd = cmd;

			ignoreChanges = true;

			nameField.setText(cmd.name);

			timeSpinner.setValue(cmd.duration);

			imageComboBox.setSelectedItem(cmd.img);
			imageComboBox.setEnabled(cmd.setImage);
			btnChoose.setEnabled(cmd.setImage);
			btnClear.setEnabled(cmd.setImage);
			cbEnableImg.setSelected(cmd.setImage);

			paletteComboBox.setSelectedItem(cmd.pal);
			paletteComboBox.setEnabled(cmd.setPalette);
			cbEnablePal.setSelected(cmd.setPalette);

			dxSpinner.setValue(cmd.dx);
			dySpinner.setValue(cmd.dy);
			dzSpinner.setValue(cmd.dz);

			rxSpinner.setValue(cmd.rx);
			rySpinner.setValue(cmd.ry);
			rzSpinner.setValue(cmd.rz);

			sxSpinner.setValue(cmd.sx);
			sySpinner.setValue(cmd.sy);
			szSpinner.setValue(cmd.sz);

			ignoreChanges = false;
		}

		protected void setModel(ComboBoxModel<SpriteRaster> imgModel, ComboBoxModel<SpritePalette> palModel)
		{
			ignoreChanges = true;
			imageComboBox.setModel(imgModel);
			paletteComboBox.setModel(palModel);
			ignoreChanges = false;
		}
	}
}
