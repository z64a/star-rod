package game.sprite.editor;

import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import app.SwingUtils;
import game.sprite.ImgAsset;
import game.sprite.Sprite;
import game.sprite.SpritePalette;
import game.sprite.SpriteRaster;
import net.miginfocom.swing.MigLayout;
import util.ui.ListAdapterComboboxModel;

public class RastersTab extends JPanel
{
	// currently featured SpriteRaster
	private SpriteRaster sr = null;

	private final ListPanel<SpriteRaster> rasterList;

	private final JPanel rasterInfoPanel;
	private final JPanel backPalettePanel;
	private final ImageInfoPanel frontImgPanel;
	private final ImageInfoPanel backImgPanel;

	private final JComboBox<SpritePalette> defaultPaletteBox;
	private final JComboBox<SpritePalette> backPaletteBox;

	private boolean ignoreChanges = false;

	public RastersTab(SpriteEditor editor)
	{
		// create raster list

		rasterList = new ListPanel<>() {
			@Override
			public void onSelectEDT(SpriteRaster selected)
			{
				if (ignoreChanges)
					return;

				setRasterEDT(selected);
			}

			@Override
			public void onDelete(SpriteRaster raster)
			{
				raster.deleted = true;
			}

			@Override
			public void rename(int index, String newName)
			{
				DefaultListModel<SpriteRaster> listModel = getListModel();
				SpriteRaster sr = listModel.get(index);
				if (!sr.name.equals(newName)) {
					sr.name = makeUniqueName(sr, newName);
					// beep if the chosen name had to be changed to become unique
					if (!sr.name.equals(newName)) {
						Toolkit.getDefaultToolkit().beep();
					}
				}
			}
		};

		rasterList.list.setCellRenderer(new RasterCellRenderer());

		// create buttons

		JButton addButton = new JButton("Add New Raster");
		addButton.addActionListener((e) -> {
			SpriteRaster newRaster = new SpriteRaster(sr.getSprite());
			newRaster.name = makeUniqueName(newRaster, "NewRaster");
			DefaultListModel<SpriteRaster> listModel = rasterList.getListModel();
			listModel.addElement(newRaster);
			selectRaster(newRaster);
		});
		SwingUtils.addBorderPadding(addButton);

		JButton dupeButton = new JButton("Duplicate Selected");
		dupeButton.addActionListener((e) -> {
			SpriteRaster original = rasterList.list.getSelectedValue();
			if (original == null)
				return;

			SpriteRaster sr = new SpriteRaster(original);
			sr.name = makeUniqueName(sr, original.name);
			DefaultListModel<SpriteRaster> listModel = rasterList.getListModel();
			listModel.addElement(sr);
			selectRaster(sr);
		});
		SwingUtils.addBorderPadding(dupeButton);

		JButton renameButton = new JButton("Rename Selected");
		renameButton.addActionListener((e) -> {
			int index = rasterList.list.getSelectedIndex();
			if (index >= 0) {
				String oldName = rasterList.list.getSelectedValue().name;
				rasterList.promptRenameAt(index, oldName);
				rasterList.list.repaint();
			}
		});
		SwingUtils.addBorderPadding(renameButton);

		// create info panel

		rasterInfoPanel = new JPanel(new MigLayout("fill, ins 0, wrap, hidemode 0"));

		frontImgPanel = new ImageInfoPanel(false);
		backImgPanel = new ImageInfoPanel(true);

		frontImgPanel.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e)) {
					ImgAsset img = editor.getSelectedImage();
					sr.front.assignImg(img);
					sr.loadEditorImages();
					frontImgPanel.setImageEDT(sr.front);
					rasterList.list.repaint();
				}
			}
		});

		backImgPanel.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e)) {
					ImgAsset img = editor.getSelectedImage();
					sr.back.assignImg(img);
					sr.loadEditorImages();
					backImgPanel.setImageEDT(sr.front);
					rasterList.list.repaint();
				}
			}
		});

		defaultPaletteBox = new JComboBox<>();
		SwingUtils.setFontSize(defaultPaletteBox, 14);
		defaultPaletteBox.setMaximumRowCount(24);
		defaultPaletteBox.setRenderer(new PaletteSlicesRenderer());
		defaultPaletteBox.addActionListener((e) -> {
			if (ignoreChanges)
				return;
			SpritePalette selectedPal = (SpritePalette) defaultPaletteBox.getSelectedItem();
			if (selectedPal != null && selectedPal != sr.front.pal) {
				sr.front.assignPal(selectedPal);
				sr.loadEditorImages();
				rasterInfoPanel.repaint();
				rasterList.list.repaint();
			}
		});

		backPaletteBox = new JComboBox<>();
		SwingUtils.setFontSize(backPaletteBox, 14);
		backPaletteBox.setMaximumRowCount(24);
		backPaletteBox.setRenderer(new PaletteSlicesRenderer());
		backPaletteBox.addActionListener((e) -> {
			if (ignoreChanges)
				return;
			SpritePalette selectedPal = (SpritePalette) backPaletteBox.getSelectedItem();
			if (selectedPal != null && selectedPal != sr.back.pal) {
				sr.back.assignPal(selectedPal);
				sr.loadEditorImages();
				rasterInfoPanel.repaint();
				rasterList.list.repaint();
			}
		});

		backPalettePanel = new JPanel(new MigLayout("fill, ins 0, wrap"));
		backPalettePanel.add(SwingUtils.getLabel("Back Palette:", 12));
		backPalettePanel.add(backPaletteBox, "w 60%::");

		// assemble info panel

		rasterInfoPanel.add(frontImgPanel, "sg img, split 2");
		rasterInfoPanel.add(backImgPanel, "sg img");
		rasterInfoPanel.add(SwingUtils.getLabel("Palette:", 12));
		rasterInfoPanel.add(defaultPaletteBox, "w 60%::");
		rasterInfoPanel.add(backPalettePanel, "grow");

		// assemble tab

		setLayout(new MigLayout("fill, ins 16, wrap, hidemode 0"));
		add(rasterList, "grow, pushy");
		add(addButton, "sg but, growx, split 3");
		add(dupeButton, "sg but, growx");
		add(renameButton, "sg but, growx");
		add(rasterInfoPanel, "pushx, grow, top");
	}

	private static boolean isNameUsed(SpriteRaster sr, String name)
	{
		for (SpriteRaster other : sr.getSprite().rasters) {
			if (sr != other && other.name.equals(name))
				return true;
		}
		return false;
	}

	private static String makeUniqueName(SpriteRaster sr, String name)
	{
		if (!isNameUsed(sr, name))
			return name;

		int i = 1;
		Matcher m = Pattern.compile("(\\D+)(\\d+)").matcher(name);

		if (m.matches() && m.group(2) != null) {
			name = m.group(1);
			i = Integer.parseInt(m.group(2)) + 1;
		}

		while (true) {
			String newName = String.format("%s%d", name, i);
			if (!isNameUsed(sr, newName))
				return newName;
			// try the next name
			i++;
		}
	}

	public void setSpriteEDT(Sprite sprite)
	{
		assert (SwingUtilities.isEventDispatchThread());

		defaultPaletteBox.setModel(new ListAdapterComboboxModel<>(sprite.palettes));
		backPaletteBox.setModel(new ListAdapterComboboxModel<>(sprite.palettes));

		rasterList.list.setModel(sprite.rasters);

		SpriteRaster selected = null;

		if (sprite.rasters.size() > 0) {
			selected = sprite.rasters.get(0);
		}

		backImgPanel.setVisible(sprite.hasBack);
		backPalettePanel.setVisible(sprite.hasBack);

		selectRaster(selected);
	}

	public void selectRaster(SpriteRaster sr)
	{
		rasterList.list.setSelectedValue(sr, true);
	}

	public void setRasterEDT(SpriteRaster sr)
	{
		assert (SwingUtilities.isEventDispatchThread());

		this.sr = sr;

		ignoreChanges = true;

		if (sr != null) {
			boolean hasBack = sr.getSprite().hasBack;

			frontImgPanel.setImageEDT(sr.front);

			defaultPaletteBox.setSelectedItem(sr.front.pal);
			defaultPaletteBox.setEnabled(true);

			if (hasBack) {
				backImgPanel.setImageEDT(sr.back);
				backPaletteBox.setEnabled(true);
			}
			else {
				backImgPanel.setImageEDT(null);
				backPaletteBox.setEnabled(false);
			}

			backPaletteBox.setSelectedItem(sr.back.pal);

			rasterInfoPanel.setVisible(true);
		}
		else {
			frontImgPanel.setImageEDT(null);
			backImgPanel.setImageEDT(null);

			defaultPaletteBox.setSelectedItem(null);
			defaultPaletteBox.setEnabled(false);

			backPaletteBox.setSelectedItem(null);
			backPaletteBox.setEnabled(false);

			rasterInfoPanel.setVisible(false);
		}

		ignoreChanges = false;
	}
}
