package game.sprite.editor.animators;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

import app.SwingUtils;
import game.sprite.Sprite;
import game.sprite.SpriteAnimation;
import game.sprite.SpriteComponent;
import game.sprite.SpritePalette;
import game.sprite.SpriteRaster;
import game.sprite.editor.IndexableComboBoxRenderer;
import game.sprite.editor.SpriteEditor;
import game.sprite.editor.animators.KeyframeAnimator.AnimKeyframe;
import game.sprite.editor.animators.KeyframeAnimator.Goto;
import game.sprite.editor.animators.KeyframeAnimator.Keyframe;
import game.sprite.editor.animators.KeyframeAnimator.Loop;
import net.miginfocom.swing.MigLayout;
import util.ui.DragReorderList;
import util.ui.ListAdapterComboboxModel;

public class KeyframeAnimatorEditor
{
	private static final String PANEL_LAYOUT_PROPERTIES = "ins 0 10 0 10, wrap";

	private static KeyframeAnimatorEditor instance;

	private DragReorderList<AnimKeyframe> commandList;
	private ListDataListener commandListListener;

	private JPanel commandListPanel;
	private JPanel commandEditPanel;

	private SpriteEditor editor;
	private KeyframeAnimator animator;

	private AnimKeyframe clipboard;

	public static void bind(SpriteEditor editor, KeyframeAnimator animator, Container commandListContainer, Container commandEditContainer)
	{
		instance().editor = editor;
		instance().animator = animator;

		commandListContainer.removeAll();
		commandListContainer.add(instance().commandListPanel, "grow");

		commandEditContainer.removeAll();
		commandEditContainer.add(instance().commandEditPanel, "grow");

		instance().commandList.setModel(animator.keyframeListModel);

		animator.keyframeListModel.removeListDataListener(instance().commandListListener);
		animator.keyframeListModel.addListDataListener(instance().commandListListener);
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
		commandList = new DragReorderList<>();
		commandList.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		commandList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		commandListPanel = new JPanel(new MigLayout("fill, ins 0, wrap 3",
			"[grow, sg col][grow, sg col][grow, sg col]"));
		commandEditPanel = new JPanel(new MigLayout("fill, ins 0, wrap"));

		commandList.addListSelectionListener((e) -> {
			if (e.getValueIsAdjusting())
				return;

			AnimKeyframe cmd = commandList.getSelectedValue();
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
				AnimKeyframe cmd = commandList.getSelectedValue();
				if (cmd == null) {
					Toolkit.getDefaultToolkit().beep();
					return;
				}
				clipboard = (AnimKeyframe) cmd.copy();
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
				AnimKeyframe copy = (AnimKeyframe) clipboard.copy();
				commandList.getDefaultModel().add(i + 1, copy);
			}
		});

		am.put("duplicate", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				AnimKeyframe cmd = commandList.getSelectedValue();
				if (cmd == null) {
					Toolkit.getDefaultToolkit().beep();
					return;
				}
				int i = commandList.getSelectedIndex();
				AnimKeyframe copy = (AnimKeyframe) cmd.copy();
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

		JButton newBtn = new JButton("Keyframe");
		newBtn.addActionListener((e) -> create(animator.new Keyframe()));

		JButton repeatBtn = new JButton("Repeat");
		repeatBtn.addActionListener((e) -> create(animator.new Loop(null, 3)));

		JButton gotoBtn = new JButton("Goto");
		gotoBtn.addActionListener((e) -> create(animator.new Goto(null)));

		JScrollPane listScrollPane = new JScrollPane(commandList);
		listScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

		commandListPanel.add(SwingUtils.getLabel("Commands", 14), "growx, span");
		commandListPanel.add(listScrollPane, "grow, span, push");

		commandListPanel.add(newBtn, "growx");
		commandListPanel.add(repeatBtn, "growx");
		commandListPanel.add(gotoBtn, "growx");

		commandListListener = new ListDataListener() {
			@Override
			public void contentsChanged(ListDataEvent e)
			{
				animator.recalculateLinks();
				animator.resetAnimation();
			}

			@Override
			public void intervalAdded(ListDataEvent e)
			{
				animator.recalculateLinks();
				animator.resetAnimation();
			}

			@Override
			public void intervalRemoved(ListDataEvent e)
			{
				animator.recalculateLinks();
				animator.resetAnimation();
			}
		};
	}

	private static void create(AnimKeyframe cmd)
	{
		DefaultListModel<AnimKeyframe> model = instance().commandList.getDefaultModel();

		if (instance().commandList.isSelectionEmpty())
			model.addElement(cmd);
		else
			model.add(instance().commandList.getSelectedIndex() + 1, cmd);
	}

	protected static class GotoPanel extends JPanel
	{
		private static GotoPanel instance;
		private Goto cmd;

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

		protected void set(Goto cmd, DefaultListModel<AnimKeyframe> animKeyframes)
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
		private Loop cmd;

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

		protected void set(Loop cmd, DefaultListModel<AnimKeyframe> animKeyframes)
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

	protected static class KeyframePanel extends JPanel
	{
		private static KeyframePanel instance;
		private Keyframe cmd;

		private boolean ignoreChanges = false;

		private JSpinner timeSpinner;

		private JCheckBox cbEnableImg, cbEnablePal, cbEnableParent;

		private JComboBox<SpriteRaster> imageComboBox;
		private JComboBox<SpritePalette> paletteComboBox;
		private JComboBox<SpriteComponent> componentComboBox;

		private JSpinner dxSpinner, dySpinner, dzSpinner;
		private JSpinner rxSpinner, rySpinner, rzSpinner;
		private JSpinner sxSpinner, sySpinner, szSpinner;
		private JSpinner notifySpinner;

		protected static KeyframePanel instance()
		{
			if (instance == null)
				instance = new KeyframePanel();
			return instance;
		}

		private KeyframePanel()
		{
			timeSpinner = new JSpinner();
			SwingUtils.setFontSize(timeSpinner, 12);
			timeSpinner.setModel(new SpinnerNumberModel(1, 0, 300, 1));
			SwingUtils.centerSpinnerText(timeSpinner);
			timeSpinner.addChangeListener((e) -> {
				if (ignoreChanges)
					return;
				cmd.duration = (int) timeSpinner.getValue();
			});

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
			});

			cbEnableImg = new JCheckBox(" Raster");
			cbEnableImg.addActionListener((e) -> {
				if (ignoreChanges)
					return;
				boolean value = cbEnableImg.isSelected();
				cmd.setImage = value;
				imageComboBox.setEnabled(value);
			});

			paletteComboBox = new JComboBox<>();
			SwingUtils.setFontSize(paletteComboBox, 14);
			paletteComboBox.setMaximumRowCount(24);
			paletteComboBox.setRenderer(new IndexableComboBoxRenderer("Default Palette"));
			paletteComboBox.addActionListener((e) -> {
				if (ignoreChanges || cmd == null)
					return;
				cmd.pal = (SpritePalette) paletteComboBox.getSelectedItem();
			});

			cbEnablePal = new JCheckBox(" Palette");
			cbEnablePal.addActionListener((e) -> {
				if (ignoreChanges)
					return;
				boolean value = cbEnablePal.isSelected();
				cmd.setPalette = value;
				paletteComboBox.setEnabled(value);
			});

			componentComboBox = new JComboBox<>();
			SwingUtils.setFontSize(componentComboBox, 14);
			componentComboBox.setMaximumRowCount(24);
			componentComboBox.setRenderer(new IndexableComboBoxRenderer());
			componentComboBox.addActionListener((e) -> {
				if (ignoreChanges)
					return;
				cmd.parentComp = (SpriteComponent) componentComboBox.getSelectedItem();
			});

			cbEnableParent = new JCheckBox(" Parent");
			cbEnableParent.addActionListener((e) -> {
				if (ignoreChanges)
					return;
				boolean value = cbEnableParent.isSelected();
				cmd.setParent = value;

				componentComboBox.setEnabled(cmd.setParent);
			});

			notifySpinner = new JSpinner();
			SwingUtils.setFontSize(notifySpinner, 12);
			notifySpinner.setModel(new SpinnerNumberModel(0, 0, 255, 1));
			SwingUtils.centerSpinnerText(notifySpinner);
			notifySpinner.addChangeListener((e) -> {
				if (ignoreChanges)
					return;
				cmd.notifyValue = (int) notifySpinner.getValue();
			});

			dxSpinner = new JSpinner();
			SwingUtils.setFontSize(dxSpinner, 12);
			dxSpinner.setModel(new SpinnerNumberModel(0, -256, 256, 1));
			SwingUtils.centerSpinnerText(dxSpinner);
			dxSpinner.addChangeListener((e) -> {
				if (ignoreChanges)
					return;
				cmd.dx = (int) dxSpinner.getValue();
			});

			dySpinner = new JSpinner();
			SwingUtils.setFontSize(dySpinner, 12);
			dySpinner.setModel(new SpinnerNumberModel(0, -256, 256, 1));
			SwingUtils.centerSpinnerText(dySpinner);
			dySpinner.addChangeListener((e) -> {
				if (ignoreChanges)
					return;
				cmd.dy = (int) dySpinner.getValue();
			});

			dzSpinner = new JSpinner();
			SwingUtils.setFontSize(dzSpinner, 12);
			dzSpinner.setModel(new SpinnerNumberModel(0, -256, 256, 1));
			SwingUtils.centerSpinnerText(dzSpinner);
			dzSpinner.addChangeListener((e) -> {
				if (ignoreChanges)
					return;
				cmd.dz = (int) dzSpinner.getValue();
			});

			rxSpinner = new JSpinner();
			SwingUtils.setFontSize(rxSpinner, 12);
			rxSpinner.setModel(new SpinnerNumberModel(0, -180, 180, 1));
			SwingUtils.centerSpinnerText(rxSpinner);
			rxSpinner.addChangeListener((e) -> {
				if (ignoreChanges)
					return;
				cmd.rx = (int) rxSpinner.getValue();
			});

			rySpinner = new JSpinner();
			SwingUtils.setFontSize(rySpinner, 12);
			rySpinner.setModel(new SpinnerNumberModel(0, -180, 180, 1));
			SwingUtils.centerSpinnerText(rySpinner);
			rySpinner.addChangeListener((e) -> {
				if (ignoreChanges)
					return;
				cmd.ry = (int) rySpinner.getValue();
			});

			rzSpinner = new JSpinner();
			SwingUtils.setFontSize(rzSpinner, 12);
			rzSpinner.setModel(new SpinnerNumberModel(0, -180, 180, 1));
			SwingUtils.centerSpinnerText(rzSpinner);
			rzSpinner.addChangeListener((e) -> {
				if (ignoreChanges)
					return;
				cmd.rz = (int) rzSpinner.getValue();
			});

			sxSpinner = new JSpinner();
			SwingUtils.setFontSize(sxSpinner, 12);
			sxSpinner.setModel(new SpinnerNumberModel(1, 0, 500, 1));
			SwingUtils.centerSpinnerText(sxSpinner);
			sxSpinner.addChangeListener((e) -> {
				if (ignoreChanges)
					return;
				cmd.sx = (int) sxSpinner.getValue();
			});

			sySpinner = new JSpinner();
			SwingUtils.setFontSize(sySpinner, 12);
			sySpinner.setModel(new SpinnerNumberModel(1, 0, 500, 1));
			SwingUtils.centerSpinnerText(sySpinner);
			sySpinner.addChangeListener((e) -> {
				if (ignoreChanges)
					return;
				cmd.sy = (int) sySpinner.getValue();
			});

			szSpinner = new JSpinner();
			SwingUtils.setFontSize(szSpinner, 12);
			szSpinner.setModel(new SpinnerNumberModel(1, 0, 500, 1));
			SwingUtils.centerSpinnerText(szSpinner);
			szSpinner.addChangeListener((e) -> {
				if (ignoreChanges)
					return;
				cmd.sz = (int) szSpinner.getValue();
			});

			setLayout(new MigLayout("ins 0 10 0 10", "[grow]4[grow]"));

			add(SwingUtils.getLabel("Keyframe Properties", 14), "gapbottom 4, span, wrap");

			add(SwingUtils.getLabel("Duration ", SwingConstants.RIGHT, 12), "sg lbl, gaptop 2, top");
			add(timeSpinner, "w 25%, split 2");
			add(SwingUtils.getLabel("frames", 12), "gapleft 6, wrap");

			add(cbEnableImg, "top");
			add(imageComboBox, "growx, h 120!, wrap");

			add(cbEnablePal, "gaptop 2, top");
			add(paletteComboBox, "growx, wrap, gapbottom 8");

			add(cbEnableParent, "gaptop 2, top");
			add(componentComboBox, "growx, wrap, gapbottom 8");

			add(SwingUtils.getLabel("Position", 12), "sg lbl, top, wrap");
			add(new JLabel(), "w 8!, span, split 4");
			add(dxSpinner, "sg xyz");
			add(dySpinner, "sg xyz");
			add(dzSpinner, "sg xyz, wrap");

			add(SwingUtils.getLabel("Rotation", 12), "sg lbl, top, wrap");
			add(new JLabel(), "w 8!, span, split 4");
			add(rxSpinner, "sg xyz");
			add(rySpinner, "sg xyz");
			add(rzSpinner, "sg xyz, wrap");

			add(SwingUtils.getLabel("Scale", 12), "sg lbl, top, wrap");
			add(new JLabel(), "w 8!, span, split 4");
			add(sxSpinner, "sg xyz");
			add(sySpinner, "sg xyz");
			add(szSpinner, "sg xyz, wrap");

			add(SwingUtils.getLabel("Set Notify", 12), "sg lbl, top, wrap");
			add(new JLabel(), "w 8!, span, split 2");
			add(notifySpinner, "sg xyz, wrap");
		}

		protected void set(Keyframe cmd)
		{
			this.cmd = cmd;

			ignoreChanges = true;

			timeSpinner.setValue(cmd.duration);

			imageComboBox.setSelectedItem(cmd.img);
			imageComboBox.setEnabled(cmd.setImage);
			cbEnableImg.setSelected(cmd.setImage);

			paletteComboBox.setSelectedItem(cmd.pal);
			paletteComboBox.setEnabled(cmd.setPalette);
			cbEnablePal.setSelected(cmd.setPalette);

			SpriteAnimation anim = cmd.ownerComp.parentAnimation;

			DefaultComboBoxModel<SpriteComponent> componentModel = new DefaultComboBoxModel<>();
			componentModel.addElement(null);
			for (int i = 0; i < anim.components.size(); i++) {
				SpriteComponent comp = anim.components.get(i);
				componentModel.addElement(comp);
			}
			componentComboBox.setModel(componentModel);
			componentComboBox.setSelectedItem(cmd.parentComp);

			componentComboBox.setEnabled(cmd.setParent);
			cbEnableParent.setSelected(cmd.setParent);

			notifySpinner.setValue(cmd.notifyValue);

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
