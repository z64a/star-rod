package game.sprite.editor.animators.keyframe;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import javax.swing.ComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import app.SwingUtils;
import game.sprite.Sprite;
import game.sprite.SpritePalette;
import game.sprite.SpriteRaster;
import game.sprite.editor.Editable;
import game.sprite.editor.SpriteEditor;
import game.sprite.editor.animators.BlankArrowUI;
import game.sprite.editor.animators.SpriteRasterRenderer;
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
import util.ui.NameTextField;

public class Keyframe extends AnimKeyframe
{
	public Keyframe(KeyframeAnimator animator)
	{
		super(animator);

		name = "New Keyframe";
	}

	@Override
	public Keyframe copy()
	{
		Keyframe clone = new Keyframe(animator);
		clone.name = name;
		clone.duration = duration;
		clone.setImage = setImage;
		clone.img = img;
		clone.setPalette = setPalette;
		clone.pal = pal;
		clone.unknown = unknown;
		clone.dx = dx;
		clone.dy = dy;
		clone.dz = dz;
		clone.rx = rx;
		clone.ry = ry;
		clone.rz = rz;
		clone.sx = sx;
		clone.sy = sy;
		clone.sz = sz;
		return clone;
	}

	int listPos = -1;

	public String name = "";

	public int duration;

	public boolean setImage = false;
	public SpriteRaster img = null;

	public boolean setPalette = false;
	public SpritePalette pal = null;

	public boolean unknown;
	public int dx = 0, dy = 0, dz = 0;
	public int rx = 0, ry = 0, rz = 0;
	public int sx = 100, sy = 100, sz = 100;

	@Override
	public AdvanceResult apply()
	{
		if (setImage) {
			owner.sr = img;
			owner.sp = null;
		}

		if (setPalette) {
			owner.sp = pal;
		}

		// these all use default values if not set by the keyframe
		if (duration > 0) {
			owner.dx = dx;
			owner.dy = dy;
			owner.dz = dz;

			owner.rx = rx;
			owner.ry = ry;
			owner.rz = rz;

			owner.scaleX = sx;
			owner.scaleY = sy;
			owner.scaleZ = sz;
		}

		owner.delayCount = duration;
		if (duration > 0) {
			owner.gotoTime += duration;
		}

		return (duration > 0) ? AdvanceResult.BLOCK : AdvanceResult.NEXT;
	}

	@Override
	public int length()
	{
		// zero IQ method is fine and less prone to bugs
		List<Short> dummySeq = new LinkedList<>();
		addTo(dummySeq);
		return dummySeq.size();
	}

	@Override
	protected void addTo(List<Short> seq)
	{
		if (duration > 0 && dx != 0 || dy != 0 || dz != 0) {
			seq.add(unknown ? (short) 0x3001 : (short) 0x3000);
			seq.add((short) dx);
			seq.add((short) dy);
			seq.add((short) dz);
		}

		if (duration > 0 && rx != 0 || ry != 0 || rz != 0) {
			seq.add((short) (0x4000 | (rx & 0xFFF)));
			seq.add((short) ry);
			seq.add((short) rz);
		}

		if (duration > 0 && sx != 100 || sy != 100 || sz != 100) {
			if (sx == sy && sy == sz) {
				seq.add((short) 0x5000);
				seq.add((short) sx);
			}
			else {
				if (sx != 100) {
					seq.add((short) 0x5001);
					seq.add((short) sx);
				}
				if (sy != 100) {
					seq.add((short) 0x5002);
					seq.add((short) sy);
				}
				if (sz != 100) {
					seq.add((short) 0x5003);
					seq.add((short) sz);
				}
			}
		}

		if (setImage) {
			int id = (img == null) ? -1 : img.getIndex();
			seq.add((short) (0x1000 | (id & 0xFFF)));
		}

		if (setPalette) {
			int id = (pal == null) ? -1 : pal.getIndex();
			seq.add((short) (0x6000 | (id & 0xFFF)));
		}

		if (duration > 0)
			seq.add((short) (0x0000 | (duration & 0xFFF)));
	}

	// 0VVV : 0 is valid, leads to a duration of 4095
	public int setDuration(Queue<Short> cmdQueue)
	{
		duration = (cmdQueue.poll() & 0xFFF);

		if (duration == 0)
			duration = 4095;

		return 1;
	}

	// 1VVV : FFF is valid value for "no image"
	public int setImage(Queue<Short> cmdQueue)
	{
		// ensure FFF -> FFFF with sign extension
		int id = (cmdQueue.poll() << 20) >> 20;
		img = (id < 0) ? null : owner.parentAnimation.parentSprite.rasters.get(id);
		setImage = true;
		return 1;
	}

	// 3VVV XXXX YYYY ZZZZ
	public int setPosition(Queue<Short> cmdQueue)
	{
		//NOTE: this flag does nothing
		unknown = (cmdQueue.poll() & 0xFFF) == 1;
		dx = cmdQueue.poll();
		dy = cmdQueue.poll();
		dz = cmdQueue.poll();
		return 4;
	}

	// 4XXX YYYY ZZZZ
	// set rotation (euler angles)
	public int setRotation(Queue<Short> cmdQueue)
	{
		// ensure FFF -> FFFF with sign extension
		rx = ((cmdQueue.poll() << 20) >> 20);
		ry = cmdQueue.poll();
		rz = cmdQueue.poll();
		return 3;
	}

	// 5VVV UUUU : scale (%)
	public int setScale(Queue<Short> cmdQueue)
	{
		int type = (cmdQueue.poll() & 0xFFF);
		int scale = cmdQueue.poll();
		switch (type) {
			case 0:
				sx = scale;
				sy = scale;
				sz = scale;
				break;
			case 1:
				sx = scale;
				break;
			case 2:
				sy = scale;
				break;
			case 3:
				sz = scale;
				break;
		}
		return 2;
	}

	// 6VVV
	public int setPalette(Queue<Short> cmdQueue)
	{
		// ensure FFF -> FFFF with sign extension
		int id = (cmdQueue.poll() << 20) >> 20;
		pal = (id < 0) ? null : owner.parentAnimation.parentSprite.palettes.get(id);
		setPalette = true;
		return 1;
	}

	@Override
	public String getName()
	{
		return "Keyframe";
	}

	@Override
	public Color getTextColor()
	{
		if (hasError())
			return SwingUtils.getRedTextColor();
		else if (duration % 2 == 1)
			return SwingUtils.getYellowTextColor();
		else if (isTarget)
			return SwingUtils.getGreenTextColor();
		else
			return null;
	}

	@Override
	public String toString()
	{
		SpriteEditor editor = SpriteEditor.instance();
		boolean highlight = (highlighted && editor != null && editor.highlightCommand);

		StringBuilder sb = new StringBuilder("<html>");
		if (highlight)
			sb.append("<b>");
		sb.append(name);
		if (highlight)
			sb.append("</b>");
		sb.append("</html>");
		return sb.toString();
	}

	@Override
	public Component getPanel()
	{
		KeyframePanel.instance().set(this);
		return KeyframePanel.instance();
	}

	@Override
	public String checkErrorMsg()
	{
		if (duration == 0)
			return "Keyframe: invalid duration";

		if (setPalette && (pal == null))
			return "Keyframe: undefined palette";

		return null;
	}

	@Override
	public void addEditableDownstream(List<Editable> downstream)
	{
		if (setImage && img != null)
			downstream.add(img);

		if (setPalette && pal != null)
			downstream.add(pal);
	}

	protected static class KeyframePanel extends JPanel
	{
		private static KeyframePanel instance;
		private boolean ignoreChanges = false;

		private Keyframe cmd;

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
			KeyframeAnimatorEditor.repaintCommandList();
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

		private void set(Keyframe cmd)
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
