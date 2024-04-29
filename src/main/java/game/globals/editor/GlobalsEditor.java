package game.globals.editor;

import java.awt.Dimension;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;

import javax.imageio.ImageIO;
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;

import app.Environment;
import app.LoadingBar;
import app.StarRodFrame;
import app.SwingUtils;
import app.config.Options;
import assets.AssetHandle;
import assets.AssetManager;
import assets.ExpectedAsset;
import game.ProjectDatabase;
import game.globals.MoveRecord;
import game.globals.editor.GlobalsData.GlobalsCategory;
import game.globals.editor.tabs.ItemTab;
import game.globals.editor.tabs.MoveTab;
import game.message.Message;
import game.message.editor.MessageAsset;
import net.miginfocom.swing.MigLayout;
import util.DualHashMap;
import util.IterableListModel;
import util.Logger;

public class GlobalsEditor
{
	private static final String MENU_BAR_SPACING = "    ";
	public static final int WINDOW_SIZE_X = 800;
	public static final int WINDOW_SIZE_Y = 800;

	private StarRodFrame frame;
	public boolean exitToMainMenu;

	private JTabbedPane tabbedPane;
	private ArrayList<GlobalEditorTab> tabList;
	private int selectedTabIndex;

	public final GlobalsData data;
	public final IterableListModel<Message> messageListModel;
	public final HashMap<String, Message> messageNameMap;

	public final DefaultComboBoxModel<String> moveTypes = new DefaultComboBoxModel<>();
	public final DefaultComboBoxModel<String> actionTips = new DefaultComboBoxModel<>();
	private final DualHashMap<String, String> actionTipNameMapping = new DualHashMap<>();

	public static abstract class GlobalEditorTab extends JPanel
	{
		public final GlobalsEditor editor;
		public final int tabIndex;
		private JLabel tabLabel;
		private boolean modified;

		protected GlobalEditorTab(GlobalsEditor editor, int tabIndex)
		{
			this.editor = editor;
			this.tabIndex = tabIndex;
		}

		protected void setModified()
		{
			modified = true;
			tabLabel.setText(getTabName() + " *");
		}

		protected void clearModified()
		{
			modified = false;
			tabLabel.setText(getTabName());
		}

		protected abstract String getTabName();

		protected abstract ExpectedAsset getIcon();

		protected abstract GlobalsCategory getDataType();

		protected abstract void notifyDataChange(GlobalsCategory type);

		protected void onDeselectTab()
		{}

		protected void onSelectTab()
		{}
	}

	public static void main(String[] args) throws InterruptedException
	{
		Environment.initialize();

		CountDownLatch guiClosedSignal = new CountDownLatch(1);
		new GlobalsEditor(guiClosedSignal);
		guiClosedSignal.await();

		Environment.exit();
	}

	public GlobalsEditor(CountDownLatch guiClosedSignal)
	{
		LoadingBar.show("Launching Globals Editor");

		tabList = new ArrayList<>();

		messageListModel = new IterableListModel<>();
		messageNameMap = new HashMap<>();
		loadMessages();

		data = new GlobalsData();
		data.loadAllData();

		// load move types
		moveTypes.removeAllElements();
		moveTypes.addAll(ProjectDatabase.EMoveType.getValueList());

		// load action tips
		actionTipNameMapping.clear();
		actionTips.removeAllElements();
		actionTips.addElement("NONE");
		actionTipNameMapping.add(MoveRecord.NO_ACTION, "NONE");
		for (String s : ProjectDatabase.EBattleMessages.getValueList()) {
			if (s.startsWith("BTL_MSG_ACTION_TIP_")) {
				String displayed = s.substring("BTL_MSG_ACTION_TIP_".length());
				actionTipNameMapping.add(s, displayed);
				actionTips.addElement(displayed);
			}
		}

		for (GlobalsCategory type : GlobalsCategory.values()) {
			for (GlobalEditorTab tab : tabList)
				tab.notifyDataChange(type);
		}

		createGUI(guiClosedSignal);

		LoadingBar.dismiss();
		frame.setVisible(true);
	}

	private boolean currentTabHasChanges()
	{
		GlobalEditorTab tab = (GlobalEditorTab) tabbedPane.getSelectedComponent();
		return tab.modified;
	}

	private void reloadCurrentTab()
	{
		GlobalEditorTab current = (GlobalEditorTab) tabbedPane.getSelectedComponent();
		GlobalsCategory category = current.getDataType();
		data.loadAllData();

		for (GlobalEditorTab tab : tabList)
			tab.notifyDataChange(category);

		current.clearModified();
	}

	private boolean tabsHaveChanges()
	{
		boolean modified = false;
		for (GlobalEditorTab tab : tabList)
			modified = modified || tab.modified;
		return modified;
	}

	private void saveAllChanges()
	{
		for (GlobalEditorTab tab : tabList) {
			if (tab.modified)
				tab.clearModified();
		}
		data.saveAllData();
	}

	private void createGUI(CountDownLatch guiClosedSignal)
	{
		frame = new StarRodFrame();

		frame.setTitle(Environment.decorateTitle("Globals Editor"));
		frame.setBounds(0, 0, WINDOW_SIZE_X, WINDOW_SIZE_Y);
		frame.setLocationRelativeTo(null);

		frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e)
			{
				int choice = JOptionPane.NO_OPTION;

				if (tabsHaveChanges()) {
					choice = SwingUtils.getConfirmDialog()
						.setTitle("Warning")
						.setMessage("Unsaved changes will be lost!", "Would you like to save now?")
						.choose();
				}

				switch (choice) {
					case JOptionPane.YES_OPTION:
						saveAllChanges();
						break;
					case JOptionPane.NO_OPTION:
						break;
					case JOptionPane.CANCEL_OPTION:
						return;
				}

				guiClosedSignal.countDown();
				frame.dispose();
			}
		});

		tabbedPane = new JTabbedPane();

		createTab(new ItemTab(this, tabbedPane.getTabCount()));
		createTab(new MoveTab(this, tabbedPane.getTabCount()));

		tabbedPane.addChangeListener((e) -> {
			if (selectedTabIndex >= 0)
				tabList.get(selectedTabIndex).onDeselectTab();

			selectedTabIndex = tabbedPane.getSelectedIndex();
			tabList.get(selectedTabIndex).onSelectTab();
		});

		// initial selection
		tabList.get(0).onSelectTab();

		SwingUtils.setFontSize(tabbedPane, 14);

		frame.setLayout(new MigLayout("fill"));
		frame.add(tabbedPane, "grow");

		JMenuBar menuBar = new JMenuBar();
		frame.setJMenuBar(menuBar);

		addActionsMenu(menuBar);
	}

	private void addActionsMenu(JMenuBar menuBar)
	{
		JMenuItem item;

		JMenu menu = new JMenu(MENU_BAR_SPACING + "Actions" + MENU_BAR_SPACING);
		menu.getPopupMenu().setLightWeightPopupEnabled(false);
		menuBar.add(menu);

		item = new JMenuItem("Reload Data");
		item.addActionListener((e) -> {
			int choice = JOptionPane.OK_OPTION;
			if (currentTabHasChanges()) {
				choice = SwingUtils.getConfirmDialog()
					.setTitle("Warning")
					.setMessage("Any changes will be lost.", "Are you sure you want to reload?")
					.setMessageType(JOptionPane.WARNING_MESSAGE)
					.setOptionsType(JOptionPane.YES_NO_OPTION)
					.choose();
			}

			if (choice == JOptionPane.OK_OPTION)
				reloadCurrentTab();
		});
		menu.add(item);

		menu.addSeparator();

		item = new JMenuItem("Save Changes");
		item.addActionListener((e) -> {
			int choice = JOptionPane.OK_OPTION;
			if (tabsHaveChanges()) {
				choice = SwingUtils.getConfirmDialog()
					.setTitle("Warning")
					.setMessage("Are you sure you want to overwrite existing data?")
					.setMessageType(JOptionPane.WARNING_MESSAGE)
					.setOptionsType(JOptionPane.YES_NO_OPTION)
					.choose();
			}
			if (choice == JOptionPane.OK_OPTION)
				saveAllChanges();
		});
		menu.add(item);

		menu.addSeparator();

		item = new JMenuItem("Switch Tools");
		item.addActionListener((e) -> {
			exitToMainMenu = true;
			WindowEvent closingEvent = new WindowEvent(frame, WindowEvent.WINDOW_CLOSING);
			Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(closingEvent);
		});
		menu.add(item);

		item = new JMenuItem("Exit");
		item.addActionListener((e) -> {
			exitToMainMenu = Environment.mainConfig.getBoolean(Options.ExitToMenu);
			WindowEvent closingEvent = new WindowEvent(frame, WindowEvent.WINDOW_CLOSING);
			Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(closingEvent);
		});
		menu.add(item);
	}

	private void createTab(GlobalEditorTab tab)
	{
		tabList.add(tab);

		tab.tabLabel = new JLabel(tab.getTabName());
		tab.tabLabel.setHorizontalTextPosition(SwingConstants.TRAILING);
		tab.tabLabel.setIcon(getTabIcon(tab.getIcon()));
		tab.tabLabel.setIconTextGap(8);

		if (tab.getTabName().length() > 10)
			tab.tabLabel.setPreferredSize(new Dimension(110, 24));
		else
			tab.tabLabel.setPreferredSize(new Dimension(80, 24));

		tabbedPane.addTab(null, tab);
		tabbedPane.setTabComponentAt(tab.tabIndex, tab.tabLabel);
	}

	private static final ImageIcon getTabIcon(ExpectedAsset asset)
	{
		File source = asset.getFile();

		if (!source.exists()) {
			Logger.log("Could not find " + asset.getPath());
			return null;
		}

		ImageIcon imageIcon;

		try {
			imageIcon = new ImageIcon(ImageIO.read(source));
		}
		catch (IOException e) {
			System.err.println("Exception while reading icon: " + asset.getPath());
			return null;
		}

		int height = 24;
		float aspectRatio = imageIcon.getIconWidth() / imageIcon.getIconHeight();

		if (imageIcon.getIconHeight() <= height)
			return imageIcon;

		Image image = imageIcon.getImage().getScaledInstance(Math.round(aspectRatio * height), height, java.awt.Image.SCALE_SMOOTH);
		return new ImageIcon(image);
	}

	private void loadMessages()
	{
		messageNameMap.clear();

		try {
			for (AssetHandle ah : AssetManager.getMessages()) {
				Logger.log("Reading messages from: " + ah.getName());
				MessageAsset group = new MessageAsset(ah);
				for (Message msg : group.messages) {
					messageNameMap.put("MSG_" + msg.name, msg);
					messageListModel.addElement(msg);
				}
			}
		}
		catch (IOException e) {
			Logger.logError(e.getMessage());
		}

		Logger.logf("Loaded %d messages", messageListModel.getSize());
	}

	public Message getMessage(String msgName)
	{
		return messageNameMap.get(msgName);
	}

	public String getDispActionTip(String real)
	{
		if (actionTipNameMapping.contains(real))
			return actionTipNameMapping.get(real);
		else
			return real;
	}

	public String getRealActionTip(String displayed)
	{
		if (actionTipNameMapping.containsInverse(displayed))
			return actionTipNameMapping.getInverse(displayed);
		else
			return displayed;
	}
}
