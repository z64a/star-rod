package app;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.CountDownLatch;

import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

import app.config.Options;
import net.miginfocom.swing.MigLayout;

public class ThemesEditor
{
	public static final int WINDOW_SIZE_X = 640;
	public static final int WINDOW_SIZE_Y = 600;

	private StarRodFrame frame;
	public boolean exitToMainMenu;

	public String initialThemeName;

	private JLabel lblR;
	private JLabel lblG;
	private JLabel lblB;

	public static void main(String[] args) throws InterruptedException
	{
		Environment.initialize();

		CountDownLatch guiClosedSignal = new CountDownLatch(1);
		new ThemesEditor(guiClosedSignal);
		guiClosedSignal.await();

		Environment.exit();
	}

	public ThemesEditor(CountDownLatch guiClosedSignal)
	{
		initialThemeName = Themes.getCurrentThemeName();

		frame = new StarRodFrame();

		frame.setTitle(Environment.decorateTitle("Choose Theme"));

		frame.setBounds(0, 0, WINDOW_SIZE_X, WINDOW_SIZE_Y);
		frame.setLocationRelativeTo(null);

		frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e)
			{
				exitToMainMenu = true;
				String currentThemeName = Themes.getCurrentThemeName();
				if (!initialThemeName.equals(currentThemeName)) {
					int choice = SwingUtils.getConfirmDialog()
						.setTitle("Save Changes")
						.setMessage("Theme has been changed.", "Do you want to save changes?")
						.choose();

					switch (choice) {
						case JOptionPane.YES_OPTION:
							Environment.mainConfig.setString(Options.Theme, Themes.getCurrentThemeKey());
							Environment.mainConfig.saveConfigFile();

							SwingUtils.getWarningDialog()
								.setTitle("Theme Changed")
								.setMessage("Theme has been changed.", "Star Rod must restart.")
								.show();

							Environment.exit();
							break;
						case JOptionPane.NO_OPTION:
							Themes.setThemeByName(initialThemeName);
							SwingUtilities.updateComponentTreeUI(frame);
							break;
						case JOptionPane.CANCEL_OPTION:
							return;
					}
				}

				guiClosedSignal.countDown();
				frame.dispose();
			}
		});

		frame.setLayout(new MigLayout("fill"));
		frame.add(getThemesPanel(), "w 240!, growy");
		frame.add(getPreviewPanel(), "span, grow, push");

		frame.setVisible(true);
	}

	private JPanel getThemesPanel()
	{
		DefaultListModel<String> themes = new DefaultListModel<>();

		for (String name : Themes.getThemeNames())
			themes.addElement(name);

		JList<String> themeList = new JList<>(themes);
		themeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		themeList.setSelectedValue(Themes.getCurrentThemeName(), true);

		themeList.addListSelectionListener(e -> SwingUtilities.invokeLater(() -> {
			Themes.setThemeByName(themeList.getSelectedValue());
			SwingUtilities.updateComponentTreeUI(frame);
			lblR.setForeground(SwingUtils.getRedTextColor());
			lblG.setForeground(SwingUtils.getGreenTextColor());
			lblB.setForeground(SwingUtils.getBlueTextColor());
		}));

		JPanel panel = new JPanel(new MigLayout("fill, wrap, ins 16"));
		panel.add(SwingUtils.getLabel("Choose a Theme:", 14));
		panel.add(new JScrollPane(themeList), "grow, pushy");
		return panel;
	}

	private JPanel getPreviewPanel()
	{
		JPanel panel = new JPanel(new MigLayout("fill, wrap, ins 16"));

		JTextField exampleField = new JTextField("input");
		SwingUtils.addBorderPadding(exampleField);

		panel.add(new JLabel("Input Field:"), "sg lbl, w 80!, split 3");
		panel.add(exampleField, "w 160!");
		panel.add(new JLabel(), "growx, pushx");

		JComboBox<String> comboBox = new JComboBox<>();
		comboBox.addItem("First");
		comboBox.addItem("Second");
		comboBox.addItem("Third");

		panel.add(new JLabel("Combo Box:"), "sg lbl, split 2");
		panel.add(comboBox, "w 160!");

		panel.add(new JLabel(""), "sg lbl, split 2");
		panel.add(new JCheckBox("Check Box"), "gaptop 8");

		JCheckBox checkedBox = new JCheckBox("Another Check Box");
		checkedBox.setSelected(true);

		panel.add(new JLabel(""), "sg lbl, split 2");
		panel.add(checkedBox);

		JPanel tabPanel = new JPanel(new MigLayout("fill, wrap"));
		JTabbedPane tabs = new JTabbedPane();
		tabs.addTab("Tab  ", tabPanel);
		panel.add(tabs, "span, grow, push");

		JRadioButton but1 = new JRadioButton("Option A");
		JRadioButton but2 = new JRadioButton("Option B");
		JRadioButton but3 = new JRadioButton("Option C");
		ButtonGroup grp = new ButtonGroup();
		grp.add(but1);
		grp.add(but2);
		grp.add(but3);
		but3.setSelected(true);

		tabPanel.add(but1, "growx, sg radio, split 3");
		tabPanel.add(but2, "growx, sg radio");
		tabPanel.add(but3, "growx, sg radio");

		JSlider slider = new JSlider(0, 100, 25);
		slider.setMajorTickSpacing(0);
		slider.setMinorTickSpacing(0);
		slider.setPaintTicks(true);

		tabPanel.add(slider, "grow");

		lblR = SwingUtils.getLabel("<html><b>Red Text</b></html>", SwingConstants.CENTER, 12);
		lblG = SwingUtils.getLabel("<html><b>Green Text</b></html>", SwingConstants.CENTER, 12);
		lblB = SwingUtils.getLabel("<html><b>Blue Text</b></html>", SwingConstants.CENTER, 12);
		lblR.setForeground(SwingUtils.getRedTextColor());
		lblG.setForeground(SwingUtils.getGreenTextColor());
		lblB.setForeground(SwingUtils.getBlueTextColor());
		tabPanel.add(lblR, "growx, sg font, split 3");
		tabPanel.add(lblG, "growx, sg font");
		tabPanel.add(lblB, "growx, sg font");

		JButton disabledButton = new JButton("Button 3");
		disabledButton.setEnabled(false);
		tabPanel.add(new JButton("Button 1"), "growx, split 3, gaptop 8");
		tabPanel.add(new JButton("Button 2"), "growx");
		tabPanel.add(disabledButton, "growx");

		DefaultListModel<String> listModel = new DefaultListModel<>();
		String[] planets = {
				"Mercury", "Venus", "Mars", "Jupiter", "Saturn", "Uranus", "Neptune"
		};
		for (String planet : planets)
			listModel.addElement(planet);
		JList<String> list = new JList<>(listModel);
		SwingUtils.addBorderPadding(list);

		DefaultMutableTreeNode root = new DefaultMutableTreeNode("Root");
		DefaultTreeModel treeModel = new DefaultTreeModel(root);

		DefaultMutableTreeNode animalsNode = new DefaultMutableTreeNode("Animals");
		String[] animals = {
				"Elephant", "Lion", "Tiger", "Bear", "Horse", "Rabbit", "Deer", "Dolphin"
		};
		root.add(animalsNode);
		for (String animal : animals)
			animalsNode.add(new DefaultMutableTreeNode(animal));

		DefaultMutableTreeNode fruitsNode = new DefaultMutableTreeNode("Fruits");
		root.add(fruitsNode);
		String[] fruits = {
				"Apple", "Orange", "Pineapple", "Strawberry", "Watermelon", "Mango", "Peach"
		};
		for (String fruit : fruits)
			fruitsNode.add(new DefaultMutableTreeNode(fruit));

		DefaultMutableTreeNode vegetablesNode = new DefaultMutableTreeNode("Vegetables");
		String[] vegetables = {
				"Carrot", "Tomato", "Broccoli", "Potato", "Spinach", "Onion", "Cucumber", "Lettuce", "Cauliflower"
		};
		root.add(vegetablesNode);
		for (String vegetable : vegetables)
			vegetablesNode.add(new DefaultMutableTreeNode(vegetable));

		JTree tree = new JTree(treeModel);
		tree.setRootVisible(false);
		tree.expandRow(1);
		SwingUtils.addBorderPadding(tree);

		tabPanel.add(SwingUtils.getLabel("Sample List", 14), "gaptop 8, split 2, w 50%");
		tabPanel.add(SwingUtils.getLabel("Sample Tree", 14), "w 50%");
		tabPanel.add(new JScrollPane(list), "split 2, w 50%, growy");
		tabPanel.add(new JScrollPane(tree), "w 50%, growy");

		tabPanel.add(new JLabel(), "span, growy, pushy");
		return panel;
	}
}
