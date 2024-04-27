package game.map.editor.ui;

import java.awt.Container;
import java.util.ArrayList;

import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import com.alexandriasoftware.swing.JSplitButton;

import app.SwingUtils;
import game.map.Map;
import game.map.MapObject;
import game.map.MapObject.MapObjectType;
import game.map.editor.EditorShortcut;
import game.map.editor.MapEditor;
import game.map.editor.MapInfoPanel;
import game.map.editor.selection.SelectionManager.GUISelectionInterface;
import game.map.editor.ui.info.BlankInfoPanel;
import game.map.editor.ui.info.ColliderInfoPanel;
import game.map.editor.ui.info.MarkerInfoPanel;
import game.map.editor.ui.info.ModelInfoPanel;
import game.map.editor.ui.info.ZoneInfoPanel;
import game.map.hit.Collider;
import game.map.hit.Zone;
import game.map.marker.Marker;
import game.map.shape.Model;
import game.map.tree.ColliderJTree;
import game.map.tree.MapObjectJTree;
import game.map.tree.MapObjectNode;
import game.map.tree.MarkerJTree;
import game.map.tree.ModelJTree;
import game.map.tree.ZoneJTree;
import net.miginfocom.swing.MigLayout;
import util.Logger;

public class MapObjectPanel extends JTabbedPane implements TreeSelectionListener, GUISelectionInterface
{
	private static final int TAB_MODEL = 0;
	private static final int TAB_COLLIDER = 1;
	private static final int TAB_ZONE = 2;
	private static final int TAB_MARKER = 3;

	private final MapEditor editor;

	private MapObjectJTree<Model> modelJTree;
	private MapObjectJTree<Collider> colliderJTree;
	private MapObjectJTree<Zone> zoneJTree;
	private MapObjectJTree<Marker> markerJTree;

	private final Container infoPanelContainer;
	private MapInfoPanel<?> currentInfoPanel;

	private BlankInfoPanel blankInfoPanel;
	private ModelInfoPanel modelInfoPanel;
	private ColliderInfoPanel colliderInfoPanel;
	private ZoneInfoPanel zoneInfoPanel;
	private MarkerInfoPanel markerInfoPanel;

	public MapObjectPanel(SwingGUI gui, MapEditor editor, Container infoPanelContainer)
	{
		this.editor = editor;
		this.infoPanelContainer = infoPanelContainer;

		addTab("Models   ", getModelSubpanel(gui));
		addTab("Colliders  ", getColliderSubpanel(gui));
		addTab("Zones    ", getZoneSubpanel(gui));
		addTab("Markers   ", getMarkerSubpanel(gui));

		SwingUtils.setFontSize(this, 12f);

		addChangeListener(e -> {
			switch (getSelectedIndex()) {
				default:
					Logger.logWarning("Selected nonexistent tab!");
				case TAB_MODEL:
					editor.selectionManager.setObjectType(MapObjectType.MODEL);
					break;
				case TAB_COLLIDER:
					editor.selectionManager.setObjectType(MapObjectType.COLLIDER);
					break;
				case TAB_ZONE:
					editor.selectionManager.setObjectType(MapObjectType.ZONE);
					break;
				case TAB_MARKER:
					editor.selectionManager.setObjectType(MapObjectType.MARKER);
					break;
			}
		});

		blankInfoPanel = new BlankInfoPanel();
		setInfoPanel(blankInfoPanel);
		//		infoPanelContainer.setVisible(false);
	}

	public MapObjectNode<Model> getPopupModel()
	{
		return modelJTree.popupSource;
	}

	public MapObjectNode<Collider> getPopupCollider()
	{
		return colliderJTree.popupSource;
	}

	public MapObjectNode<Zone> getPopupZone()
	{
		return zoneJTree.popupSource;
	}

	public MapObjectNode<Marker> getPopupMarker()
	{
		return markerJTree.popupSource;
	}

	private JPanel getModelSubpanel(SwingGUI gui)
	{
		modelInfoPanel = new ModelInfoPanel();

		JPanel treeViewPanel = new JPanel();

		modelJTree = new ModelJTree(editor, gui, this);
		modelJTree.addTreeSelectionListener(this);

		treeViewPanel.setLayout(new MigLayout("fill, ins 0"));
		treeViewPanel.add(new JScrollPane(modelJTree), "grow");

		JPopupMenu createMenu = new JPopupMenu();
		gui.registerPopupMenu(createMenu);
		JSplitButton createButton = new JSplitButton("Create");
		createButton.setPopupMenu(createMenu);
		createButton.setAlwaysPopup(true);

		JMenuItem createGroupButton = new JMenuItem("New Group");
		createGroupButton.setToolTipText("Create a new model group below the root.");
		gui.addButtonCommand(createGroupButton, GuiCommand.CREATE_MODEL_GROUP);
		createMenu.add(createGroupButton);

		JMenuItem createPrimitiveItem = new JMenuItem("New Primitive");
		createPrimitiveItem.setToolTipText("Create a new model from primitive shapes.");
		gui.addButtonCommand(createPrimitiveItem, GuiCommand.SHOW_CREATE_PRIMITIVE_MODEL_DIALOG);
		createMenu.add(createPrimitiveItem);

		JMenuItem generateFromItem = new JMenuItem("From Triangles");
		generateFromItem.setToolTipText("Generate a model from selected triangles.");
		gui.addButtonCommand(generateFromItem, GuiCommand.SHOW_CREATE_MODEL_FROM_DIALOG);
		createMenu.add(generateFromItem);

		JMenuItem generateFromPaths = new JMenuItem("From Paths");
		generateFromPaths.setToolTipText("Generate a ribbon with center and edge paths.");
		gui.addButtonCommand(generateFromPaths, GuiCommand.SHOW_EXTRUDE_RIBBON_MODEL_DIALOG);
		createMenu.add(generateFromPaths);

		JPopupMenu modifyMenu = new JPopupMenu();
		gui.registerPopupMenu(modifyMenu);
		JSplitButton modifyButton = new JSplitButton("Modify Selected");
		modifyButton.setPopupMenu(modifyMenu);
		modifyButton.setAlwaysPopup(true);

		JMenuItem joinModelsButton = new JMenuItem("Join Models");
		joinModelsButton.setToolTipText("Merge two or more models into one.");
		gui.addButtonCommand(joinModelsButton, GuiCommand.JOIN_MODELS);
		modifyMenu.add(joinModelsButton);

		JMenuItem splitModelsButton = new JMenuItem("Split Model");
		splitModelsButton.setToolTipText("Create a new model from selected triangles.");
		gui.addButtonCommand(splitModelsButton, GuiCommand.SPLIT_MODEL);
		modifyMenu.add(splitModelsButton);

		modifyMenu.addSeparator();

		JMenuItem separateVerticesButton = new JMenuItem("Separate Vertices");
		separateVerticesButton.setToolTipText("Separate shared vertices, creating new copies for each triangle.");
		gui.addButtonCommand(separateVerticesButton, GuiCommand.SEPARATE_VERTS);
		modifyMenu.add(separateVerticesButton);

		JMenuItem fuseVerticesButton = new JMenuItem("Fuse Vertices");
		fuseVerticesButton.setToolTipText("Fuse shared vertices that have identical UVs.");
		gui.addButtonCommand(fuseVerticesButton, GuiCommand.FUSE_VERTS);
		modifyMenu.add(fuseVerticesButton);

		JMenuItem cleanDegeneratesButton = new JMenuItem("Cleanup Degenerates");
		cleanDegeneratesButton.setToolTipText("Remove triangles of zero area.");
		gui.addButtonCommand(cleanDegeneratesButton, GuiCommand.CLEANUP_TRIS);
		modifyMenu.add(cleanDegeneratesButton);

		modifyMenu.addSeparator();

		JMenuItem invertNormalsButton = new JMenuItem("Invert Normals");
		invertNormalsButton.setToolTipText("Invert normals for selected triangles.");
		invertNormalsButton.addActionListener((e) -> editor.enqueueKeyEvent(EditorShortcut.FLIP_NORMALS));
		modifyMenu.add(invertNormalsButton);

		JMenuItem cameraNormalsButton = new JMenuItem("Normals to Camera");
		cameraNormalsButton.setToolTipText("Orients normals of selected triangles toward the camera position of the 3D viewport.");
		cameraNormalsButton.addActionListener((e) -> editor.enqueueKeyEvent(EditorShortcut.NORMALS_TO_CAMERA));
		modifyMenu.add(cameraNormalsButton);

		modifyMenu.addSeparator();

		JMenuItem generateUVButton = new JMenuItem("Generate UVs");
		generateUVButton.setToolTipText("Generate UVs for selected triangles.");
		gui.addButtonCommand(generateUVButton, GuiCommand.SHOW_GENERATE_UV_DIALOG);
		modifyMenu.add(generateUVButton);

		JMenuItem setRenderModeButton = new JMenuItem("Set Render Mode");
		setRenderModeButton.setToolTipText("Set the render mode for all selected models.");
		gui.addButtonCommand(setRenderModeButton, GuiCommand.SET_ALL_RENDER_MODE);
		modifyMenu.add(setRenderModeButton);

		JPanel modelSubpanel = new JPanel();
		modelSubpanel.setLayout(new MigLayout("fill, ins 4, wrap"));

		modelSubpanel.add(modifyButton, "gaptop 4, growx, pushx, sg buttons, split 2");
		modelSubpanel.add(createButton, "gaptop 4, growx, pushx, sg buttons");

		modelSubpanel.add(treeViewPanel, "gaptop 4, grow, pushy");

		return modelSubpanel;
	}

	private JPanel getColliderSubpanel(SwingGUI gui)
	{
		colliderInfoPanel = new ColliderInfoPanel();

		JPanel treeViewPanel = new JPanel();

		colliderJTree = new ColliderJTree(editor, gui, this);
		colliderJTree.addTreeSelectionListener(this);

		treeViewPanel.setLayout(new MigLayout("fill, ins 0"));
		treeViewPanel.add(new JScrollPane(colliderJTree), "grow");

		JPopupMenu createMenu = new JPopupMenu();
		gui.registerPopupMenu(createMenu);
		JSplitButton createButton = new JSplitButton("Create");
		createButton.setPopupMenu(createMenu);
		createButton.setAlwaysPopup(true);

		JMenuItem createGroupButton = new JMenuItem("New Group");
		createGroupButton.setToolTipText("Create a new top-level collision group.");
		gui.addButtonCommand(createGroupButton, GuiCommand.CREATE_COLLIDER_GROUP);
		createMenu.add(createGroupButton);

		JMenuItem createPrimitiveItem = new JMenuItem("New Primitive");
		createPrimitiveItem.setToolTipText("Create a new collider from primitive shapes.");
		gui.addButtonCommand(createPrimitiveItem, GuiCommand.SHOW_CREATE_PRIMITIVE_COLLIDER_DIALOG);
		createMenu.add(createPrimitiveItem);

		JMenuItem generateFromItem = new JMenuItem("From Triangles");
		generateFromItem.setToolTipText("Generate a collider from selected triangles.");
		gui.addButtonCommand(generateFromItem, GuiCommand.SHOW_CREATE_COLLIDER_FROM_DIALOG);
		createMenu.add(generateFromItem);

		JPopupMenu modifyMenu = new JPopupMenu();
		gui.registerPopupMenu(modifyMenu);
		JSplitButton modifyButton = new JSplitButton("Modify Selected");
		modifyButton.setPopupMenu(modifyMenu);
		modifyButton.setAlwaysPopup(true);

		JMenuItem joinButton = new JMenuItem("Join Colliders");
		joinButton.setToolTipText("Merge two or more colliders into one.");
		gui.addButtonCommand(joinButton, GuiCommand.JOIN_COLLIDERS);
		modifyMenu.add(joinButton);

		JMenuItem splitButton = new JMenuItem("Split Collider");
		splitButton.setToolTipText("Create a new collider from selected triangles.");
		gui.addButtonCommand(splitButton, GuiCommand.SPLIT_COLLIDER);
		modifyMenu.add(splitButton);

		modifyMenu.addSeparator();

		JMenuItem separateVerticesButton = new JMenuItem("Separate Vertices");
		separateVerticesButton.setToolTipText("Separate shared vertices, creating new copies for each triangle.");
		gui.addButtonCommand(separateVerticesButton, GuiCommand.SEPARATE_VERTS);
		modifyMenu.add(separateVerticesButton);

		JMenuItem fuseVerticesButton = new JMenuItem("Fuse Vertices");
		fuseVerticesButton.setToolTipText("Fuse shared vertices that have identical UVs.");
		gui.addButtonCommand(fuseVerticesButton, GuiCommand.FUSE_VERTS);
		modifyMenu.add(fuseVerticesButton);

		JMenuItem cleanDegeneratesButton = new JMenuItem("Cleanup Degenerates");
		cleanDegeneratesButton.setToolTipText("Remove triangles of zero area.");
		gui.addButtonCommand(cleanDegeneratesButton, GuiCommand.CLEANUP_TRIS);
		modifyMenu.add(cleanDegeneratesButton);

		modifyMenu.addSeparator();

		JMenuItem invertNormalsButton = new JMenuItem("Invert Normals");
		invertNormalsButton.setToolTipText("Invert normals for selected triangles.");
		invertNormalsButton.addActionListener((e) -> editor.enqueueKeyEvent(EditorShortcut.FLIP_NORMALS));
		modifyMenu.add(invertNormalsButton);

		JMenuItem cameraNormalsButton = new JMenuItem("Normals to Camera");
		cameraNormalsButton.setToolTipText("Orients normals of selected triangles toward the camera position of the 3D viewport.");
		cameraNormalsButton.addActionListener((e) -> editor.enqueueKeyEvent(EditorShortcut.NORMALS_TO_CAMERA));
		modifyMenu.add(cameraNormalsButton);

		modifyMenu.addSeparator();

		JMenuItem makeZoneButton = new JMenuItem("Convert to Zone");
		makeZoneButton.setToolTipText("Change collider into a zone.");
		gui.addButtonCommand(makeZoneButton, GuiCommand.CONVERT_COLLIDER_TO_ZONE);
		modifyMenu.add(makeZoneButton);

		/*
		JMenuItem bvhButton = new JMenuItem("Create BVH");
		bvhButton.setToolTipText("Create a bounding volume hierarchy for selected colliders.");
		gui.addButtonCommand(bvhButton, GuiCommand.CREATE_BVH);
		modifyMenu.add(bvhButton);
		*/

		JPanel colliderSubpanel = new JPanel();
		colliderSubpanel.setLayout(new MigLayout("fill, ins 4, wrap"));

		colliderSubpanel.add(modifyButton, "gaptop 4, growx, pushx, sg buttons, split 2");
		colliderSubpanel.add(createButton, "gaptop 4, growx, pushx, sg buttons");

		colliderSubpanel.add(treeViewPanel, "gaptop 4, grow, pushy");

		return colliderSubpanel;
	}

	private JPanel getZoneSubpanel(SwingGUI gui)
	{
		zoneInfoPanel = new ZoneInfoPanel();

		JPanel treeViewPanel = new JPanel();

		zoneJTree = new ZoneJTree(editor, gui, this);
		zoneJTree.addTreeSelectionListener(this);

		treeViewPanel.setLayout(new MigLayout("fill, ins 0"));
		treeViewPanel.add(new JScrollPane(zoneJTree), "grow");

		JPopupMenu createMenu = new JPopupMenu();
		gui.registerPopupMenu(createMenu);
		JSplitButton createButton = new JSplitButton("Create");
		createButton.setPopupMenu(createMenu);
		createButton.setAlwaysPopup(true);

		JMenuItem createGroupItem = new JMenuItem("New Group");
		createGroupItem.setToolTipText("Create a new top-level zone group.");
		gui.addButtonCommand(createGroupItem, GuiCommand.CREATE_ZONE_GROUP);
		createMenu.add(createGroupItem);

		JMenuItem createPrimitiveItem = new JMenuItem("New Primitive");
		createPrimitiveItem.setToolTipText("Create a new zone from primitive shapes.");
		gui.addButtonCommand(createPrimitiveItem, GuiCommand.SHOW_CREATE_PRIMITIVE_ZONE_DIALOG);
		createMenu.add(createPrimitiveItem);

		JMenuItem generateFromItem = new JMenuItem("From Triangles");
		generateFromItem.setToolTipText("Generate a zone from selected triangles.");
		gui.addButtonCommand(generateFromItem, GuiCommand.SHOW_CREATE_ZONE_FROM_DIALOG);
		createMenu.add(generateFromItem);

		JPopupMenu modifyMenu = new JPopupMenu();
		gui.registerPopupMenu(modifyMenu);
		JSplitButton modifyButton = new JSplitButton("Modify Selected");
		modifyButton.setPopupMenu(modifyMenu);
		modifyButton.setAlwaysPopup(true);

		JMenuItem joinButton = new JMenuItem("Join Zones");
		joinButton.setToolTipText("Merge two or more zones into one.");
		gui.addButtonCommand(joinButton, GuiCommand.JOIN_ZONES);
		modifyMenu.add(joinButton);

		JMenuItem splitButton = new JMenuItem("Split Zones");
		splitButton.setToolTipText("Create a new zone from selected triangles.");
		gui.addButtonCommand(splitButton, GuiCommand.SPLIT_ZONE);
		modifyMenu.add(splitButton);

		modifyMenu.addSeparator();

		JMenuItem separateVerticesButton = new JMenuItem("Separate Vertices");
		separateVerticesButton.setToolTipText("Separate shared vertices, creating new copies for each triangle.");
		gui.addButtonCommand(separateVerticesButton, GuiCommand.SEPARATE_VERTS);
		modifyMenu.add(separateVerticesButton);

		JMenuItem fuseVerticesButton = new JMenuItem("Fuse Vertices");
		fuseVerticesButton.setToolTipText("Fuse shared vertices that have identical UVs.");
		gui.addButtonCommand(fuseVerticesButton, GuiCommand.FUSE_VERTS);
		modifyMenu.add(fuseVerticesButton);

		JMenuItem cleanDegeneratesButton = new JMenuItem("Cleanup Degenerates");
		cleanDegeneratesButton.setToolTipText("Remove triangles of zero area.");
		gui.addButtonCommand(cleanDegeneratesButton, GuiCommand.CLEANUP_TRIS);
		modifyMenu.add(cleanDegeneratesButton);

		modifyMenu.addSeparator();

		JMenuItem invertNormalsButton = new JMenuItem("Invert Normals");
		invertNormalsButton.setToolTipText("Invert normals for selected triangles.");
		invertNormalsButton.addActionListener((e) -> editor.enqueueKeyEvent(EditorShortcut.FLIP_NORMALS));
		modifyMenu.add(invertNormalsButton);

		JMenuItem cameraNormalsButton = new JMenuItem("Normals to Camera");
		cameraNormalsButton.setToolTipText("Orient normals of selected triangles toward the 3D camera position.");
		cameraNormalsButton.addActionListener((e) -> editor.enqueueKeyEvent(EditorShortcut.NORMALS_TO_CAMERA));
		modifyMenu.add(cameraNormalsButton);

		modifyMenu.addSeparator();

		JMenuItem makeColliderButton = new JMenuItem("Convert to Collider");
		makeColliderButton.setToolTipText("Change zone into a collider.");
		gui.addButtonCommand(makeColliderButton, GuiCommand.CONVERT_ZONE_TO_COLLIDER);
		modifyMenu.add(makeColliderButton);

		JPanel zoneSubpanel = new JPanel();
		zoneSubpanel.setLayout(new MigLayout("fill, ins 4, wrap"));

		zoneSubpanel.add(modifyButton, "gaptop 4, growx, pushx, sg buttons, split 2");
		zoneSubpanel.add(createButton, "gaptop 4, growx, pushx, sg buttons");

		zoneSubpanel.add(treeViewPanel, "gaptop 4, grow, pushy");

		return zoneSubpanel;
	}

	private JPanel getMarkerSubpanel(SwingGUI gui)
	{
		markerInfoPanel = new MarkerInfoPanel();

		JPanel treeViewPanel = new JPanel();

		markerJTree = new MarkerJTree(editor, gui, this);
		markerJTree.addTreeSelectionListener(this);

		treeViewPanel.setLayout(new MigLayout("fill, ins 0"));
		treeViewPanel.add(new JScrollPane(markerJTree), "grow");

		// dummy button to occupy unused space
		JSplitButton modifyButton = new JSplitButton("Modify Selected");
		modifyButton.setEnabled(false);

		JPopupMenu createMenu = new JPopupMenu();
		gui.registerPopupMenu(createMenu);
		JSplitButton createButton = new JSplitButton("Create");
		createButton.setPopupMenu(createMenu);
		createButton.setAlwaysPopup(true);

		JMenuItem createGroupButton = new JMenuItem("New Group");
		createGroupButton.setToolTipText("Create a new top-level marker group.");
		gui.addButtonCommand(createGroupButton, GuiCommand.CREATE_MARKER_GROUP);
		createMenu.add(createGroupButton);

		JMenuItem createMarkerButton = new JMenuItem("New Marker");
		createMarkerButton.setToolTipText("Create a new generic marker that can be referenced from map scripts.");
		gui.addButtonCommand(createMarkerButton, GuiCommand.SHOW_CREATE_MARKER_DIALOG);
		createMenu.add(createMarkerButton);

		JPanel markerSubpanel = new JPanel();
		markerSubpanel.setLayout(new MigLayout("fill, ins 4, wrap"));

		markerSubpanel.add(modifyButton, "gaptop 4, growx, pushx, sg buttons, split 2");
		markerSubpanel.add(createButton, "gaptop 4, growx, pushx, sg buttons");

		markerSubpanel.add(treeViewPanel, "gaptop 4, grow, pushy");

		return markerSubpanel;
	}

	public void setMap(Map m)
	{
		modelInfoPanel.setData(null);
		colliderInfoPanel.setData(null);
		zoneInfoPanel.setData(null);
		markerInfoPanel.setData(null);
	}

	@Override
	public void finishUpdate()
	{
		MapObject obj = editor.selectionManager.getMostRecentObject();

		if (obj == null || obj.editorOnly()) {
			setInfoPanel(blankInfoPanel);
			infoPanelContainer.repaint();
			//		infoPanelContainer.setVisible(false);
			return;
		}

		switch (obj.getObjectType()) {
			case MODEL:
				Model mdl = (Model) obj;
				modelInfoPanel.setData(mdl);
				setInfoPanel(modelInfoPanel);
				break;

			case COLLIDER:
				Collider c = (Collider) obj;
				colliderInfoPanel.setData(c);
				setInfoPanel(colliderInfoPanel);
				break;

			case ZONE:
				Zone z = (Zone) obj;
				zoneInfoPanel.setData(z);
				setInfoPanel(zoneInfoPanel);
				break;

			case MARKER:
				Marker m = (Marker) obj;
				markerInfoPanel.setData(m);
				setInfoPanel(markerInfoPanel);
				break;

			case EDITOR:
				throw new IllegalStateException("Cannot sync EDITOR objects to GUI!");
		}

		//	infoPanelContainer.setVisible(true);
		infoPanelContainer.repaint();
	}

	private void setInfoPanel(MapInfoPanel<?> c)
	{
		infoPanelContainer.removeAll();
		infoPanelContainer.add(c, "grow");
		currentInfoPanel = c;
	}

	public void repaintCurrentTree()
	{
		switch (getSelectedIndex()) {
			default:
				Logger.logWarning("Tried repainting invalid tab!");
				break;
			case TAB_MODEL:
				modelJTree.repaint();
				break;
			case TAB_COLLIDER:
				colliderJTree.repaint();
				break;
			case TAB_ZONE:
				zoneJTree.repaint();
				break;
			case TAB_MARKER:
				markerJTree.repaint();
				break;
		}
	}

	public void reload(MapObject obj)
	{
		switch (obj.getObjectType()) {
			default:
				Logger.logWarning("Tried reloading invalid tree!");
				break;
			case MODEL:
				((DefaultTreeModel) modelJTree.getModel()).reload(obj.getNode());
				break;
			case COLLIDER:
				((DefaultTreeModel) colliderJTree.getModel()).reload(obj.getNode());
				break;
			case ZONE:
				((DefaultTreeModel) zoneJTree.getModel()).reload(obj.getNode());
				break;
			case MARKER:
				((DefaultTreeModel) markerJTree.getModel()).reload(obj.getNode());
				break;
		}
	}

	public void loadMapObjects()
	{
		modelJTree.setModel(editor.map.modelTree);
		for (int i = 0; i < modelJTree.getRowCount(); i++)
			modelJTree.expandRow(i);

		colliderJTree.setModel(editor.map.colliderTree);
		for (int i = 0; i < colliderJTree.getRowCount(); i++)
			colliderJTree.expandRow(i);

		zoneJTree.setModel(editor.map.zoneTree);
		for (int i = 0; i < zoneJTree.getRowCount(); i++)
			zoneJTree.expandRow(i);

		markerJTree.setModel(editor.map.markerTree);
		for (int i = 0; i < markerJTree.getRowCount(); i++)
			markerJTree.expandRow(i);

		repaint();
	}

	public void setInfoPanel(MapObject obj)
	{
		switch (obj.getObjectType()) {
			case MODEL:
				this.setSelectedIndex(TAB_MODEL);

				Model mdl = (Model) obj;
				modelInfoPanel.setData(mdl);
				setInfoPanel(modelInfoPanel);

				modelJTree.scrollPathToVisible(new TreePath(mdl.getNode().getPath()));
				modelJTree.repaint();
				break;

			case COLLIDER:
				this.setSelectedIndex(TAB_COLLIDER);

				Collider c = (Collider) obj;
				colliderInfoPanel.setData(c);
				setInfoPanel(colliderInfoPanel);

				colliderJTree.scrollPathToVisible(new TreePath(c.getNode().getPath()));
				colliderJTree.repaint();
				break;

			case ZONE:
				this.setSelectedIndex(TAB_ZONE);

				Zone z = (Zone) obj;
				zoneInfoPanel.setData(z);
				setInfoPanel(zoneInfoPanel);

				zoneJTree.scrollPathToVisible(new TreePath(z.getNode().getPath()));
				zoneJTree.repaint();
				break;

			case MARKER:
				this.setSelectedIndex(TAB_MARKER);

				Marker m = (Marker) obj;
				markerInfoPanel.setData(m);
				setInfoPanel(markerInfoPanel);

				markerJTree.scrollPathToVisible(new TreePath(m.getNode().getPath()));
				markerJTree.repaint();
				break;
			case EDITOR:
				// these have no info panel
		}
	}

	// please don't access this from outside this class and MapObjectJTree!
	public boolean disableListener = false; // ignore GUI updates from selection changes we've made manually

	@Override
	public void valueChanged(TreeSelectionEvent e)
	{
		if (disableListener)
			return;

		/*
		MapObjectJTree<?> sourceTree = (MapObjectJTree<?>)e.getSource();
		MapObjectTreeModel<?> sourceModel = ((MapObjectTreeModel<?>)sourceTree.getModel());
		if(sourceModel.ignoreMe)
		{
			sourceModel.ignoreMe = false;
			return;
		}
		*/

		ArrayList<MapObject> added = new ArrayList<>();
		ArrayList<MapObject> removed = new ArrayList<>();

		TreePath[] paths = e.getPaths();
		for (int i = 0; i < paths.length; i++) {
			DefaultMutableTreeNode node = (DefaultMutableTreeNode) paths[i].getLastPathComponent();
			MapObject obj = (MapObject) node.getUserObject();

			if (e.isAddedPath(i))
				added.add(obj);
			else
				removed.add(obj);
		}

		// ugly workaround to handle additive vs replacement selection events
		MapObjectJTree<?> sourceTree = (MapObjectJTree<?>) e.getSource();
		if (!sourceTree.additiveSelection) {
			disableListener = true;
			if (sourceTree != modelJTree)
				modelJTree.clearSelection();
			if (sourceTree != colliderJTree)
				colliderJTree.clearSelection();
			if (sourceTree != zoneJTree)
				zoneJTree.clearSelection();
			if (sourceTree != markerJTree)
				markerJTree.clearSelection();
			disableListener = false;

			// replacement selection events clear the previous selection
			editor.selectionManager.clearObjectsFromGUI();
		}
		else {
			// additive selection events
			editor.selectionManager.deselectObjectsFromGUI(removed);
		}

		editor.selectionManager.selectObjectsFromGUI(added);
	}

	@Override
	public void setObjectsSelected(Iterable<MapObject> objs)
	{
		disableListener = true;
		MapObject lastObjectAdded = null;

		for (MapObject obj : objs) {
			if (obj.editorOnly())
				continue;

			TreePath path = new TreePath(obj.getNode().getPath());

			switch (obj.getObjectType()) {
				case MODEL:
					modelJTree.addSelectionPath(path);
					break;
				case COLLIDER:
					colliderJTree.addSelectionPath(path);
					break;
				case ZONE:
					zoneJTree.addSelectionPath(path);
					break;
				case MARKER:
					markerJTree.addSelectionPath(path);
					break;
				case EDITOR:
					throw new IllegalStateException("Cannot sync EDITOR objects to GUI!");
			}
			lastObjectAdded = obj;
		}

		if (lastObjectAdded != null)
			setInfoPanel(lastObjectAdded);

		disableListener = false;
	}

	@Override
	public void setObjectsDeselected(Iterable<MapObject> objs)
	{
		disableListener = true;

		for (MapObject obj : objs) {
			if (obj.editorOnly())
				continue;

			TreePath path = new TreePath(obj.getNode().getPath());

			switch (obj.getObjectType()) {
				case MODEL:
					modelJTree.removeSelectionPath(path);
					break;
				case COLLIDER:
					colliderJTree.removeSelectionPath(path);
					break;
				case ZONE:
					zoneJTree.removeSelectionPath(path);
					break;
				case MARKER:
					markerJTree.removeSelectionPath(path);
					break;
				case EDITOR:
					throw new IllegalStateException("Cannot sync EDITOR objects to GUI!");
			}
		}

		disableListener = false;
	}

	@Override
	public void setObjectsDeleted(Iterable<MapObject> objs)
	{
		disableListener = true;

		for (MapObject obj : objs) {
			if (obj.editorOnly())
				continue;

			TreePath path = new TreePath(obj.getNode().getPath());

			switch (obj.getObjectType()) {
				case MODEL:
					modelJTree.removeSelectionPath(path);
					break;
				case COLLIDER:
					colliderJTree.removeSelectionPath(path);
					break;
				case ZONE:
					zoneJTree.removeSelectionPath(path);
					break;
				case MARKER:
					markerJTree.removeSelectionPath(path);
					break;
				case EDITOR:
					throw new IllegalStateException("Cannot sync EDITOR objects to GUI!");
			}
		}

		disableListener = false;
	}

	@Override
	public void setObjectsCreated(Iterable<MapObject> objs)
	{
		disableListener = true;
		MapObject lastObjectAdded = null;

		for (MapObject obj : objs) {
			if (obj.editorOnly())
				continue;

			TreePath path = new TreePath(obj.getNode().getPath());

			switch (obj.getObjectType()) {
				case MODEL:
					modelJTree.addSelectionPath(path);
					break;
				case COLLIDER:
					colliderJTree.addSelectionPath(path);
					break;
				case ZONE:
					zoneJTree.addSelectionPath(path);
					break;
				case MARKER:
					markerJTree.addSelectionPath(path);
					break;
				case EDITOR:
					throw new IllegalStateException("Cannot sync EDITOR objects to GUI!");
			}
			lastObjectAdded = obj;
		}

		if (lastObjectAdded != null)
			setInfoPanel(lastObjectAdded);

		disableListener = false;
	}

	public void setLightSetsVisible(boolean debugShowLightSets)
	{
		modelInfoPanel.setLightSetsVisible(debugShowLightSets);
	}
}
