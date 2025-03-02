package game.sprite.editor;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.SwingUtilities;

import app.SwingUtils;
import common.commands.AbstractCommand;
import game.map.editor.render.TextureManager;
import game.sprite.ImgAsset;
import game.sprite.ImgRef;
import game.sprite.Sprite;
import game.sprite.SpritePalette;
import game.sprite.SpriteRaster;
import net.miginfocom.swing.MigLayout;
import util.ui.ListAdapterComboboxModel;

public class ImageRefPanel extends JPanel
{
	private static final int IMG_SIZE = 160;

	private ImgRef ref = null;

	private final RastersTab tab;
	private final boolean isBack;

	private final JPanel image;
	private final JLabel lblTitle;
	private final JRadioButton btnUseFront;
	private final JRadioButton btnSeparate;
	private final JButton btnBind;
	private final JButton btnSelect;

	private final JComboBox<SpritePalette> paletteBox;

	private boolean ignoreChanges = false;

	public ImageRefPanel(RastersTab tab, boolean isBack)
	{
		this.tab = tab;
		this.isBack = isBack;

		lblTitle = SwingUtils.getLabel("", 14);

		image = new JPanel() {
			@Override
			public void paintComponent(Graphics g)
			{
				super.paintComponent(g);

				Graphics2D g2 = (Graphics2D) g;
				g.drawImage(TextureManager.background, 0, 0, IMG_SIZE, IMG_SIZE, null);

				if (ref == null)
					return;

				boolean hasBack = ref.parentRaster.parentSprite.hasBack;

				ImgRef drawRef = ref;
				if (isBack && hasBack && !ref.parentRaster.independentBack)
					drawRef = ref.parentRaster.front;

				if (drawRef != null && drawRef.asset != null) {
					ImgAsset drawAsset = drawRef.asset;
					drawAsset.previewImg = new BufferedImage(
						drawAsset.getPalette().getIndexColorModel(),
						drawAsset.previewImg.getRaster(), false, null);

					SwingUtils.centerAndFitImage(drawAsset.previewImg, this, g2);
				}
			}
		};

		btnBind = new JButton("Bind Asset");
		btnBind.addActionListener((e) -> {
			if (ref != null)
				tab.tryBindAsset(ref, this::setImage);
		});
		SwingUtils.addBorderPadding(btnBind);

		btnSelect = new JButton("Select Asset");
		btnSelect.addActionListener((e) -> {
			if (ref != null)
				tab.trySelectAsset(ref);
		});
		SwingUtils.addBorderPadding(btnSelect);

		JPanel buttonsPanel = new JPanel(new MigLayout("fill, ins 0, wrap"));
		buttonsPanel.add(btnBind, "growx");
		buttonsPanel.add(btnSelect, "growx");

		buttonsPanel.add(new JPanel(), "growy, pushy");

		btnUseFront = new JRadioButton("Use front");
		btnSeparate = new JRadioButton("Independent");

		if (isBack) {
			btnUseFront.setSelected(true);
			btnUseFront.addActionListener((e) -> {
				if (!ignoreChanges && btnUseFront.isSelected() && ref != null)
					SpriteEditor.execute(new SetRasterIndependent(ref.parentRaster, false));
			});

			btnSeparate.setSelected(false);
			btnSeparate.addActionListener((e) -> {
				if (!ignoreChanges && btnSeparate.isSelected() && ref != null)
					SpriteEditor.execute(new SetRasterIndependent(ref.parentRaster, true));
			});

			ButtonGroup group = new ButtonGroup();
			group.add(btnUseFront);
			group.add(btnSeparate);

			buttonsPanel.add(new JLabel("Asset Mode:"), "gaptop push, gapleft 4");
			buttonsPanel.add(btnUseFront, "gapleft 4");
			buttonsPanel.add(btnSeparate, "gapleft 4, gapbottom 4");
		}

		paletteBox = new JComboBox<>();
		SwingUtils.setFontSize(paletteBox, 14);
		paletteBox.setMaximumRowCount(24);
		paletteBox.setRenderer(new PaletteCellRenderer());
		paletteBox.addActionListener((e) -> {
			if (!ignoreChanges && ref != null) {
				SpritePalette pal = (SpritePalette) paletteBox.getSelectedItem();
				SpriteEditor.execute(new SetImgRefPalette(ref, pal));
			}
		});

		setLayout(new MigLayout("fill, wrap"));
		add(lblTitle, "growx");
		add(image, String.format("w %d!, h %d!", IMG_SIZE, IMG_SIZE) + ", split 2");
		add(buttonsPanel, "grow");
		add(paletteBox, "growx, gaptop 8");
	}

	public void repaintImage()
	{
		image.repaint();
	}

	public void refresh()
	{
		setImage(ref);
		tab.repaint();
	}

	public void setSprite(Sprite sprite)
	{
		if (sprite == null) {
			ignoreChanges = true;
			paletteBox.setModel(new DefaultComboBoxModel<SpritePalette>());
			ignoreChanges = false;
		}
		else {
			ignoreChanges = true;
			paletteBox.setModel(new ListAdapterComboboxModel<>(sprite.palettes));
			ignoreChanges = false;
		}
	}

	public void setRaster(SpriteRaster img)
	{
		if (img == null) {
			setImage(null);
			return;
		}

		if (isBack)
			setImage(img.back);
		else
			setImage(img.front);
	}

	private void setImage(ImgRef newRef)
	{
		assert (SwingUtilities.isEventDispatchThread());

		// easier to just separate these functions than dealing with all four
		// combinations of isBack/hasBack at the same time
		if (isBack)
			setImageAsBack(newRef);
		else
			setImageAsFront(newRef);
	}

	private void setImageAsFront(ImgRef newRef)
	{
		assert (SwingUtilities.isEventDispatchThread());

		ref = newRef;
		image.repaint();

		if (ref == null) {
			lblTitle.setForeground(SwingUtils.getRedTextColor());
			lblTitle.setText("ERROR");
			return;
		}

		boolean hasBack = ref.parentRaster.parentSprite.hasBack;

		String text = hasBack ? "Front: " : "Asset: ";
		Color color = null;

		if (!ref.resolved) {
			color = SwingUtils.getRedTextColor();
			text += ref.getName() + " (missing)";
		}
		else if (ref.asset == null) {
			color = SwingUtils.getRedTextColor();
			text += "(not bound)";
		}
		else {
			text += ref.getName();
		}

		lblTitle.setForeground(color);
		lblTitle.setText(text);

		ignoreChanges = true;
		paletteBox.setSelectedItem(ref.pal);
		ignoreChanges = false;
	}

	private void setImageAsBack(ImgRef newRef)
	{
		assert (SwingUtilities.isEventDispatchThread());

		ref = newRef;
		image.repaint();

		if (ref == null) {
			lblTitle.setForeground(SwingUtils.getRedTextColor());
			lblTitle.setText("ERROR");
			return;
		}

		boolean hasBack = ref.parentRaster.parentSprite.hasBack;
		boolean useFront = !ref.parentRaster.independentBack;

		if (!hasBack) {
			// should never appear, as this panel should not be visible if the sprite is not two-sided
			image.repaint();
			lblTitle.setForeground(SwingUtils.getRedTextColor());
			lblTitle.setText("UNUSED");
			return;
		}

		// set radio button selection for mode
		if (isBack) {
			if (useFront)
				btnUseFront.setSelected(true);
			else
				btnSeparate.setSelected(true);
		}
		btnBind.setEnabled(!useFront);
		btnSelect.setEnabled(!useFront);
		paletteBox.setVisible(!useFront);

		String text = "Back: ";
		Color color = null;

		if (useFront) {
			color = SwingUtils.getBlueTextColor();
			text += "(using front)";
		}
		else if (!ref.resolved) {
			color = SwingUtils.getRedTextColor();
			text += ref.getName() + " (missing)";
		}
		else if (ref.asset == null) {
			color = SwingUtils.getRedTextColor();
			text += "(not bound)";
		}
		else {
			text += ref.getName();
		}

		lblTitle.setForeground(color);
		lblTitle.setText(text);

		ignoreChanges = true;
		paletteBox.setSelectedItem(ref.pal);
		ignoreChanges = false;
	}

	private class SetRasterIndependent extends AbstractCommand
	{
		private final SpriteRaster img;
		private final boolean next;
		private final boolean prev;

		public SetRasterIndependent(SpriteRaster img, boolean value)
		{
			super(value ? "Use Separate Assets" : "Use Shared Asset");

			this.img = img;
			this.prev = img.independentBack;
			this.next = value;
		}

		@Override
		public void exec()
		{
			super.exec();

			img.independentBack = next;
			refresh();
		}

		@Override
		public void undo()
		{
			super.undo();

			img.independentBack = prev;
			refresh();
		}
	}

	private class SetImgRefPalette extends AbstractCommand
	{
		private final ImgRef ref;
		private final SpritePalette next;
		private final SpritePalette prev;

		public SetImgRefPalette(ImgRef ref, SpritePalette pal)
		{
			super("Assign Palette");

			this.ref = ref;
			this.prev = ref.pal;
			this.next = pal;
		}

		@Override
		public void exec()
		{
			super.exec();

			ref.assignPal(next); //FIXME
			//	ref.pal = next;
			ref.parentRaster.reloadEditorImages();
			repaint();
		}

		@Override
		public void undo()
		{
			super.undo();

			ref.assignPal(prev); //FIXME
			//ref.pal = prev;
			ref.parentRaster.reloadEditorImages();
			repaint();
		}
	}
}
