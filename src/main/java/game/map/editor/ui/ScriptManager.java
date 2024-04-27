package game.map.editor.ui;

import java.awt.Container;
import java.awt.Dimension;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;

import app.SwingUtils;
import game.map.Map;
import game.map.editor.MapEditor;
import game.map.editor.MapEditor.IShutdownListener;
import game.map.editor.UpdateListener;
import game.map.scripts.CameraOptionsPanel;
import game.map.scripts.GeneratorsPanel;
import game.map.scripts.LightingPanel;
import game.map.scripts.MainOptionsPanel;
import game.map.scripts.PannerListPanel;
import game.map.scripts.ShadingPanel;
import net.miginfocom.swing.MigLayout;

public class ScriptManager implements IShutdownListener, UpdateListener
{
	// singleton
	private static ScriptManager instance = null;

	private ScriptManager()
	{}

	public static ScriptManager instance()
	{
		if (instance == null) {
			instance = new ScriptManager();
			MapEditor.instance().registerOnShutdown(instance);
		}
		return instance;
	}

	@Override
	public void shutdown()
	{
		instance = null;
	}

	private MainOptionsPanel generalTab;
	private CameraOptionsPanel cameraTab;
	private PannerListPanel pannersTab;
	private GeneratorsPanel generatorsTab;
	private ShadingPanel shadingTab;
	private LightingPanel lightingTab;

	private JTabbedPane tabs;
	private JComponent lightingScrollPane;

	private Map currentMap = null;

	public JPanel createScriptsTab()
	{
		tabs = new JTabbedPane();

		generalTab = new MainOptionsPanel();
		//	cameraTab = new CameraOptionsPanel();
		generatorsTab = GeneratorsPanel.instance();
		pannersTab = new PannerListPanel();
		shadingTab = new ShadingPanel();
		lightingTab = new LightingPanel();

		createTab(tabs, "General", generalTab);
		//	createTab(tabs, "Camera", cameraTab);
		createTab(tabs, "Generators", generatorsTab);
		createTab(tabs, "Panners", pannersTab);
		createTab(tabs, "Shading", shadingTab);

		lightingScrollPane = createTab(tabs, "Lighting", lightingTab);

		tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);

		JPanel panel = new JPanel(new MigLayout("fill, ins 4"));
		panel.add(tabs, "grow");
		return panel;
	}

	public void setLightSetsVisible(boolean showLightSets)
	{
		if (showLightSets)
			lightingScrollPane = createTab(tabs, "Lighting", lightingTab);
		else
			tabs.remove(lightingScrollPane);
	}

	private static JComponent createTab(JTabbedPane tabs, String name, Container contents)
	{
		JLabel lbl = SwingUtils.getLabel(name, 12);
		lbl.setPreferredSize(new Dimension(60, 20));
		lbl.setHorizontalAlignment(SwingConstants.CENTER);

		JScrollPane scrollPane = new JScrollPane(contents);
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.setBorder(null);
		tabs.addTab(null, scrollPane);
		tabs.setTabComponentAt(tabs.getTabCount() - 1, lbl);

		return scrollPane;
	}

	public void setMap(Map m)
	{
		if (currentMap != null)
			currentMap.scripts.deregisterListener(this);

		currentMap = m;

		if (currentMap != null)
			currentMap.scripts.registerListener(this);

		generalTab.setMap(m);
		//	cameraTab.setMap(m);
		generatorsTab.setMap(m);
		pannersTab.setMap(m);
		shadingTab.setMap(m);
		lightingTab.setMap(m);
	}

	/*
	public void updateGeneralFields(ScriptData data)
	{
		generalTab.updateFields(data);
	}
	
	public void updateCameraFields(ScriptData data)
	{
		cameraTab.updateFields(data);
	}
	 */

	public void updatePannersTab()
	{
		pannersTab.updateFields();
	}

	public void updateGeneratorTree()
	{
		generatorsTab.repaintTree();
	}

	public static final String tag_General = "GeneralTab";
	public static final String tag_Camera = "CameraTab";
	public static final String tag_Shading = "ShadingTab";

	@Override
	public void update(String tag)
	{
		if (tag.equals(tag_General))
			generalTab.updateFields(currentMap.scripts);
		else if (tag.equals(tag_Camera))
			generalTab.updateFields(currentMap.scripts);
		else if (tag.endsWith(tag_Shading))
			shadingTab.updateFields(currentMap.scripts);
		else {
			generalTab.updateFields(currentMap.scripts);
			cameraTab.updateFields(currentMap.scripts);
		}
	}
}
