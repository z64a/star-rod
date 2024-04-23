package game.map.scripts;

import java.awt.Color;
import java.awt.Component;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;

import app.SwingUtils;
import game.map.editor.MapEditor;
import game.map.editor.MapInfoPanel;
import game.map.editor.commands.AbstractCommand;
import game.map.editor.commands.CommandBatch;
import game.map.editor.ui.LabelWithTip;
import game.map.editor.ui.SwatchPanel;
import game.map.editor.ui.SwingGUI;
import game.map.shading.ShadingLightSource;
import game.map.shading.ShadingProfile;
import game.map.shading.ShadingProfile.AddLightSource;
import game.map.shading.ShadingProfile.RemoveLightSource;
import game.map.shading.ShadingProfile.SetAmbientColor;
import game.map.shading.ShadingProfile.SetAmbientIntensity;
import game.map.shading.ShadingProfile.SetProfileName;
import net.miginfocom.swing.MigLayout;
import util.ui.HexTextField;
import util.ui.IntTextField;
import util.ui.NameTextField;

public class ShadingProfileInfoPanel extends MapInfoPanel<ShadingProfile>
{
	private ShadingPanel parent;
	private NameTextField nameField;

	private SwatchPanel ambientPreview;
	private HexTextField colorField;
	private IntTextField intensityField;

	private JPanel lightListPanel;
	private JList<ShadingLightSource> sourceList;
	private ShadingSourceInfoPanel sourcePanel;

	public ShadingProfileInfoPanel(ShadingPanel parent)
	{
		super(false);

		this.parent = parent;
		sourcePanel = new ShadingSourceInfoPanel(this);

		nameField = new NameTextField((newValue) -> {
			if (ignoreEvents() || getData() == null)
				return;
			MapEditor.execute(new SetProfileName(getData(), newValue));
		});
		SwingUtils.addBorderPadding(nameField);

		colorField = new HexTextField(6, (newValue) -> {
			if (ignoreEvents() || getData() == null)
				return;
			MapEditor.execute(new SetAmbientColor(getData(), newValue));
		});
		colorField.setHorizontalAlignment(SwingConstants.CENTER);
		SwingUtils.addBorderPadding(colorField);

		intensityField = new IntTextField((newValue) -> {
			if (ignoreEvents() || getData() == null)
				return;
			MapEditor.execute(new SetAmbientIntensity(getData(), newValue & 0xFF));
		});
		intensityField.setHorizontalAlignment(SwingConstants.CENTER);
		SwingUtils.addBorderPadding(intensityField);

		JButton chooseColorButton = new JButton("Choose");
		chooseColorButton.addActionListener((e) -> {
			if (getData() == null)
				return;

			SwingGUI.instance().notify_OpenDialog();
			Color c = new Color(getData().color.get());
			c = JColorChooser.showDialog(null, "Choose Ambient Color", c);
			SwingGUI.instance().notify_CloseDialog();

			if (c != null)
				MapEditor.execute(new SetAmbientColor(getData(), c.getRGB()));
		});

		sourceList = new JList<>() {
			// clicking blank space returns -1 instead of the last list item
			@Override
			public int locationToIndex(Point location)
			{
				int index = super.locationToIndex(location);
				if (index != -1 && !getCellBounds(index, index).contains(location)) {
					return -1;
				}
				else {
					return index;
				}
			}
		};
		sourceList.setCellRenderer(new SourceCellRenderer());

		/*
		sourceList.addListSelectionListener((e) -> {
			if(getData() == null)
				return;
		
			MapEditor.instance().selectionManager.deselectLightsFromGUI(getData().sources);
			if(sourceList.getSelectedValue() != null)
				MapEditor.instance().selectionManager.selectLightsFromGUI(sourceList.getSelectedValue());
		
		});
		*/

		// clear the selection when clicking blank space
		sourceList.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e)
			{
				int index = sourceList.locationToIndex(e.getPoint());
				if (index == -1)
					sourceList.clearSelection();
			}
		});

		sourceList.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		JScrollPane listScrollPane = new JScrollPane(sourceList);
		listScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

		sourceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		sourceList.addListSelectionListener((e) -> {
			if (e.getValueIsAdjusting() || ignoreEvents() || getData() == null)
				return;
			MapEditor.execute(new SetLightSource(getData(), sourceList.getSelectedValue()));
		});

		sourceList.getInputMap().put(KeyStroke.getKeyStroke("control D"), "DuplicateSelected");
		sourceList.getActionMap().put("DuplicateSelected", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				ShadingLightSource selected = sourceList.getSelectedValue();
				createSource(selected.copy(), sourceList.getSelectedIndex());
			}
		});

		sourceList.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "DeleteCommands");
		sourceList.getActionMap().put("DeleteCommands", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				deleteSelectedSource();
			}
		});

		JButton createLightButton = new JButton("Create");
		createLightButton.addActionListener((e) -> {
			createSource(new ShadingLightSource(getData()), -1);
		});

		JButton duplicateLightButton = new JButton("Duplicate");
		duplicateLightButton.addActionListener((e) -> {
			ShadingLightSource selected = sourceList.getSelectedValue();
			if (selected == null)
				return;
			createSource(selected.copy(), sourceList.getSelectedIndex());
		});

		JButton deleteLightButton = new JButton("Delete");
		deleteLightButton.addActionListener((e) -> {
			deleteSelectedSource();
		});

		ambientPreview = new SwatchPanel(1.32f, 1.33f);

		setLayout(new MigLayout("ins 0, fill", "[grow][22%][22%][22%]"));

		add(new JLabel("Profile Name"));
		add(nameField, "growx, span 3, wrap");

		add(new JLabel("Ambient Color"));
		add(ambientPreview, "growx, growy");
		add(colorField, "growx");
		add(chooseColorButton, "growx, growy, wrap");

		add(new LabelWithTip("Highlight Scale", "Controls how large the sprite edge highlights can be."));
		add(intensityField, "growx, wrap");

		lightListPanel = new JPanel(new MigLayout("fill, ins 0"));
		lightListPanel.add(listScrollPane, "grow");

		add(SwingUtils.getLabel("Light Sources", 14), "gaptop 16, gapbottom 8, wrap");
		add(lightListPanel, "h 30%, growx, gapbottom 4, span, wrap");

		add(duplicateLightButton, "growx, sg but, span, split 3");
		add(createLightButton, "growx, sg but");
		add(deleteLightButton, "growx, sg but, wrap");

		add(sourcePanel, "grow, span, wrap, gaptop 16");
		add(new JPanel(), "pushy, span, wrap");
	}

	private void createSource(ShadingLightSource source, int index)
	{
		if (getData() == null)
			return;
		if (getData().sources.size() >= ShadingProfile.MAX_LIGHTS)
			return;
		AddLightSource createCmd = new AddLightSource(getData(), source, index);
		CommandBatch createBatch = new CommandBatch("Create Light Source");
		createBatch.addCommand(createCmd);
		createBatch.addCommand(new SetLightSource(getData(), source));
		MapEditor.execute(createBatch);
	}

	private void deleteSelectedSource()
	{
		if (getData() == null)
			return;
		ShadingLightSource selected = sourceList.getSelectedValue();
		if (selected == null)
			return;
		CommandBatch deleteBatch = new CommandBatch("Delete Light Source");
		deleteBatch.addCommand(new SetLightSource(getData(), null));
		deleteBatch.addCommand(new RemoveLightSource(getData(), selected));
		MapEditor.execute(deleteBatch);
	}

	public void repaintSourceList()
	{
		sourceList.repaint();
	}

	@Override
	public void updateFields(ShadingProfile newData, String tag)
	{
		if (newData == null)
			return;

		nameField.setText(newData.name.get());
		intensityField.setValue(newData.intensity.get());
		colorField.setValue(newData.color.get());
		ambientPreview.setForeground(new Color(newData.color.get()));
		parent.repaintComboBox();

		if (newData.sources != sourceList.getModel())
			sourceList.setModel(newData.sources);
		sourceList.setSelectedValue(newData.selectedSource, true);

		sourcePanel.setData(newData.selectedSource);
		sourcePanel.setVisible(newData.selectedSource != null);

		Color textColor = newData.invalidName ? SwingUtils.getRedTextColor() : null;
		nameField.setForeground(textColor);
	}

	private class SetLightSource extends AbstractCommand
	{
		private final ShadingProfile profile;
		private final ShadingLightSource oldShading;
		private final ShadingLightSource newShading;

		public SetLightSource(ShadingProfile profile, ShadingLightSource source)
		{
			super("Change Light Source");
			this.profile = profile;
			this.oldShading = profile.selectedSource;
			this.newShading = source;
		}

		@Override
		public boolean shouldExec()
		{
			return newShading != oldShading;
		}

		@Override
		public void exec()
		{
			super.exec();
			profile.selectedSource = newShading;
			updateFields(profile, null);
		}

		@Override
		public void undo()
		{
			super.undo();
			profile.selectedSource = oldShading;
			updateFields(profile, null);
		}
	}

	private static class SourceCellRenderer extends JPanel implements ListCellRenderer<ShadingLightSource>
	{
		private JLabel colorLabel;
		private JLabel posLabel;
		private SwatchPanel colorSwatch;

		public SourceCellRenderer()
		{
			colorLabel = new JLabel();
			posLabel = new JLabel();
			colorSwatch = new SwatchPanel();

			setLayout(new MigLayout("ins 0, fillx"));
			add(colorSwatch, "w 10%!, h 16!, al center");
			add(colorLabel, "growx, w 30%, gapleft 2%");
			add(posLabel, "growx, pushx, gapright push");

			colorSwatch.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY, 1));

			setOpaque(true);
		}

		@Override
		public Component getListCellRendererComponent(
			JList<? extends ShadingLightSource> list,
			ShadingLightSource source,
			int index,
			boolean isSelected,
			boolean cellHasFocus)
		{
			if (isSelected) {
				setBackground(list.getSelectionBackground());
				setForeground(list.getSelectionForeground());
			}
			else {
				setBackground(list.getBackground());
				setForeground(list.getForeground());
			}

			setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
			if (source != null) {
				colorSwatch.setForeground(new Color(source.color.get()));
				colorLabel.setText(String.format("Color = (%06X)", source.color.get()));
				posLabel.setText(String.format("Position = (%d, %d, %d)",
					source.position.getX(), source.position.getY(), source.position.getZ()));
			}

			return this;
		}
	}
}
