package game.sprite.editor.animators.command;

import java.awt.Container;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

import app.SwingUtils;
import game.sprite.Sprite;
import game.sprite.SpritePalette;
import game.sprite.SpriteRaster;
import game.sprite.editor.SpriteEditor;
import game.sprite.editor.animators.AnimElement;
import game.sprite.editor.animators.AnimElementsList;
import game.sprite.editor.animators.ComponentAnimationEditor;
import game.sprite.editor.animators.command.SetImage.SetImagePanel;
import game.sprite.editor.animators.command.SetPalette.SetPalettePanel;
import game.sprite.editor.commands.CreateCommand;
import net.miginfocom.swing.MigLayout;
import util.Logger;
import util.ui.ListAdapterComboboxModel;

public class CommandAnimatorEditor extends ComponentAnimationEditor
{
	public static final String PANEL_LAYOUT_PROPERTIES = "ins 0 10 0 10, wrap";

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

		instance().commandList.setModel(animator.commands);

		animator.commands.removeListDataListener(instance().commandListListener);
		animator.commands.addListDataListener(instance().commandListListener);
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

	public static CommandAnimatorEditor instance()
	{
		if (instance == null)
			instance = new CommandAnimatorEditor();
		return instance;
	}

	protected static void repaintCommandList()
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
		waitBtn.addActionListener((e) -> create(new Wait(animator)));

		JButton imgBtn = new JButton("Raster");
		imgBtn.addActionListener((e) -> create(new SetImage(animator)));

		JButton palBtn = new JButton("Palette");
		palBtn.addActionListener((e) -> create(new SetPalette(animator)));

		JButton labelBtn = new JButton("Label");
		labelBtn.addActionListener((e) -> create(new Label(animator)));

		JButton gotoBtn = new JButton("Goto");
		gotoBtn.addActionListener((e) -> create(new Goto(animator)));

		JButton loopBtn = new JButton("Repeat");
		loopBtn.addActionListener((e) -> create(new Loop(animator)));

		JButton posBtn = new JButton("Position");
		posBtn.addActionListener((e) -> create(new SetPosition(animator)));

		JButton rotBtn = new JButton("Rotation");
		rotBtn.addActionListener((e) -> create(new SetRotation(animator)));

		JButton scaleBtn = new JButton("Scale");
		scaleBtn.addActionListener((e) -> create(new SetScale(animator)));

		JButton parentBtn = new JButton("Parent");
		parentBtn.addActionListener((e) -> create(new SetParent(animator)));

		JButton notifyBtn = new JButton("Notify");
		notifyBtn.addActionListener((e) -> create(new SetNotify(animator)));

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

	@Override
	public void restoreSelection(int lastSelectedIndex)
	{
		commandList.ignoreSelectionChange = true;

		// restore component selection (if valid) for new animation
		int old = lastSelectedIndex;
		if (old >= -1 && old < commandList.getModel().getSize()) {
			// note: -1 is a valid index corresponding to no selection
			commandList.setSelectedIndex(old);
			setSelected(commandList.getSelectedValue());
		}
		else if (commandList.getModel().getSize() > 0) {
			// safety condition -- remove? should not be needed if undo/redo state intact
			Logger.logfWarning("Reference to out of range component ID: %d", old);
			commandList.setSelectedIndex(0);
			setSelected(commandList.getSelectedValue());
		}
		else {
			// extra safety condition -- also remove?
			Logger.logfWarning("Reference to invalid component ID: %d", old);
			commandList.setSelectedIndex(-1);
			setSelected(null);
		}

		commandList.ignoreSelectionChange = false;
	}

	@Override
	public int getSelection()
	{
		return commandList.getSelectedIndex();
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
}
