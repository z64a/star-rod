package game.map.editor.ui;

import java.awt.Font;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;

import org.apache.commons.text.StringEscapeUtils;

import game.map.editor.EditorShortcut;
import game.map.editor.MapKeyConfig;
import net.miginfocom.swing.MigLayout;

public class MapShortcutsPanel extends JPanel
{
	private final MapKeyConfig keyConfig;

	public MapShortcutsPanel(MapKeyConfig keyConfig)
	{
		this.keyConfig = keyConfig;
		setLayout(new MigLayout("fill"));

		JTabbedPane tabs = new JTabbedPane();

		tabs.addTab("Editor", new JScrollPane(getEditorTab()));
		tabs.addTab("Play in Editor", new JScrollPane(getPlayInEditorTab()));
		tabs.addTab("Selection", new JScrollPane(getSelectionTab()));
		tabs.addTab("Transform", new JScrollPane(getTransformTab()));
		tabs.addTab("Drawing", new JScrollPane(getDrawTab()));
		tabs.addTab("Viewports", new JScrollPane(getViewportsTab()));
		tabs.addTab("Grid / Snap", new JScrollPane(getGridTab()));

		add(tabs, "grow, w 540!, h 500!");
	}

	private String keys(EditorShortcut ... inputs)
	{
		return java.util.Arrays.stream(inputs).map(keyConfig::getBindingText).collect(java.util.stream.Collectors.joining(" / "));
	}

	private void addHeader(JPanel panel, String text)
	{
		addHeader(panel, text, null);
	}

	private void addHeader(JPanel panel, String text, String tooltip)
	{
		boolean first = panel.getComponentCount() == 0;
		String fmt = first ? "span, wrap, growx, gaptop 8" : "span, wrap, growx, gaptop 8";

		JLabel lbl = new JLabel((tooltip == null) ? text : text + "*");
		lbl.setFont(new Font(lbl.getFont().getFontName(), Font.BOLD, 12));

		if (tooltip != null)
			lbl.setToolTipText(tooltip);

		panel.add(lbl, fmt);
	}

	private void addShortcut(JPanel panel, String name, String keys)
	{
		addShortcut(panel, name, keys, "");
	}

	private void addShortcut(JPanel panel, String desc, String keys, String tip)
	{
		String lblText = tip.isEmpty() ? desc : desc + "*";
		JLabel lbl = new JLabel(lblText);
		if (!tip.isEmpty())
			lbl.setToolTipText(tip);
		panel.add(lbl, "growx");
		panel.add(new JLabel("<html><div style='width: 170px'>" + StringEscapeUtils.escapeHtml4(keys) + "</div></html>"), "growx, wrap");
	}

	private JPanel getEditorTab()
	{
		JPanel tab = new JPanel(new MigLayout("fillx", "[grow,fill][240!,fill]"));

		addHeader(tab, "General");
		addShortcut(tab, "Save", keys(EditorShortcut.SAVE));
		addShortcut(tab, "Quit", keys(EditorShortcut.QUIT));
		addShortcut(tab, "Quit to Menu", keys(EditorShortcut.SWITCH));
		addShortcut(tab, "Undo", keys(EditorShortcut.UNDO));
		addShortcut(tab, "Redo", keys(EditorShortcut.REDO));
		addShortcut(tab, "Toggle Info Panel", keys(EditorShortcut.TOGGLE_INFO_PANEL));

		addHeader(tab, "Switch Transform Mode");
		addShortcut(tab, "Objects", keys(EditorShortcut.SELECT_OBJECTS));
		addShortcut(tab, "Triangles", keys(EditorShortcut.SELECT_TRIANGLES));
		addShortcut(tab, "Vertices", keys(EditorShortcut.SELECT_VERTICIES));
		addShortcut(tab, "Points", keys(EditorShortcut.SELECT_POINTS));

		addHeader(tab, "Switch Object Tab");
		addShortcut(tab, "Models", keys(EditorShortcut.OPEN_MODEL_TAB));
		addShortcut(tab, "Colliders", keys(EditorShortcut.OPEN_COLLIDER_TAB));
		addShortcut(tab, "Zones", keys(EditorShortcut.OPEN_ZONE_TAB));
		addShortcut(tab, "Markers", keys(EditorShortcut.OPEN_MARKER_TAB));

		return tab;
	}

	private JPanel getPlayInEditorTab()
	{
		JPanel shortcuts = new JPanel(new MigLayout("fillx, ins 0", "[grow,fill][240!,fill]"));

		addHeader(shortcuts, "Controls");
		addShortcut(shortcuts, "Begin/End", keys(EditorShortcut.PLAY_IN_EDITOR_TOGGLE));

		addShortcut(shortcuts, "Walk", keys(EditorShortcut.MOVE_FORWARD, EditorShortcut.MOVE_LEFT, EditorShortcut.MOVE_BACKWARD, EditorShortcut.MOVE_RIGHT));
		addShortcut(shortcuts, "Jump", keys(EditorShortcut.PLAY_IN_EDITOR_JUMP), "Hold for a higher jump; release for a short hop.");
		addShortcut(shortcuts, "Spin", keys(EditorShortcut.PLAY_IN_EDITOR_SPIN));
		addShortcut(shortcuts, "Run", "Hold " + keys(EditorShortcut.PLAY_IN_EDITOR_RUN));
		addShortcut(shortcuts, "Hover", "Hold " + keys(EditorShortcut.PLAY_IN_EDITOR_HOVER));

		JPanel info = new JPanel(new MigLayout("fillx, wrap, ins 0"));
		addHeader(info, "How to Use");

		info.add(new JLabel("<html><div style='width: 360px'><p>Use the Begin/End control in the 3D viewport to spawn "
			+ "at the location of the 3D cursor. Gravity, jump, and collision "
			+ "physics approximate in-game movement.</p><br><p>Current camera zones will switch "
			+ "automatically as you move about. You can set hidden colliders and "
			+ "zones to be ignored (they will still exist in-game).</p><br>"
			+ "<p>All normal editor actions are available in this mode.</p></div></html>"),
			"growx, span, wrap");

		JPanel tab = new JPanel(new MigLayout("fillx, wrap"));
		tab.add(shortcuts, "growx");
		tab.add(info, "growx");
		return tab;
	}

	private JPanel getSelectionTab()
	{
		JPanel tab = new JPanel(new MigLayout("fillx", "[grow,fill][240!,fill]"));

		addHeader(tab, "Change Selection");
		addShortcut(tab, "Select", "Left Click");
		addShortcut(tab, "Select Add/Remove", "Ctrl + Left Click");
		addShortcut(tab, "Select All", keys(EditorShortcut.SELECT_ALL));
		addShortcut(tab, "Find Object", keys(EditorShortcut.FIND_OBJECT));
		addShortcut(tab, "Copy Objects", keys(EditorShortcut.COPY_OBJECTS), "You can even copy/paste objects between maps.");
		addShortcut(tab, "Paste Objects", keys(EditorShortcut.PASTE_OBJECTS));
		addShortcut(tab, "Delete Selected", keys(EditorShortcut.DELETE_SELECTED));

		addHeader(tab, "Modify Properties of Selection");
		addShortcut(tab, "Hide Selected", keys(EditorShortcut.HIDE_SELECTED));
		addShortcut(tab, "Open UV Editor", keys(EditorShortcut.TOGGLE_UV_EDIT), "Must have models or model triangles selected.");
		addShortcut(tab, "Toggle Double Sided", keys(EditorShortcut.TOGGLE_TWO_SIDED),
			"<html>Toggles triangle type for selected zones and colliders.<br>"
				+ "Useful for making one-way walls.</html>");

		return tab;
	}

	private JPanel getTransformTab()
	{
		JPanel tab = new JPanel(new MigLayout("fillx", "[grow,fill][240!,fill]"));

		addHeader(tab, "Transforming Selected Objects");
		addShortcut(tab, "Translate", "Left Mouse Drag", "Hold shift to translate a selecton without having to click on it.");
		addShortcut(tab, "Rotate", "Right Mouse Drag");
		addShortcut(tab, "Scale", "Space + Left Mouse Drag");
		addShortcut(tab, "Uniform Scale", "Space + Right Mouse Drag");
		addShortcut(tab, "Clone", "Any above + Alt");
		addShortcut(tab, "Nudge", keys(EditorShortcut.NUDGE_UP, EditorShortcut.NUDGE_DOWN, EditorShortcut.NUDGE_LEFT, EditorShortcut.NUDGE_RIGHT,
			EditorShortcut.NUDGE_OUT, EditorShortcut.NUDGE_IN));
		addShortcut(tab, "Translate", "Hold Ctrl + Any Nudge Key");

		addHeader(tab, "Special Transformations");
		addShortcut(tab, "Flip Along X", keys(EditorShortcut.FLIP_SELECTED_X));
		addShortcut(tab, "Flip Along Y", keys(EditorShortcut.FLIP_SELECTED_Y));
		addShortcut(tab, "Flip Along Z", keys(EditorShortcut.FLIP_SELECTED_Z));
		addShortcut(tab, "Flip Normals", keys(EditorShortcut.FLIP_NORMALS));
		addShortcut(tab, "Open Transform Menu", keys(EditorShortcut.OPEN_TRANSFORM_DIALOG));

		addShortcut(tab, "Nudge to Grid", keys(EditorShortcut.ROUND_VERTICIES), "Moves any selected vertices to the nearest grid point.");

		return tab;
	}

	private JPanel getDrawTab()
	{
		JPanel shortcuts = new JPanel(new MigLayout("fillx, ins 0", "[grow,fill][240!,fill]"));
		addHeader(shortcuts, "Polygon Drawing and Cut");
		addShortcut(shortcuts, "Draw Convex", "Hold " + keys(EditorShortcut.DRAW_CONVEX));
		addShortcut(shortcuts, "Draw Concave", "Hold " + keys(EditorShortcut.DRAW_CONCAVE));
		addShortcut(shortcuts, "Draw Walls", "Hold " + keys(EditorShortcut.DRAW_WALLS));
		addShortcut(shortcuts, "Cut Meshes", "Hold " + keys(EditorShortcut.CUT_GEOMETRY),
			"Two points to define cutting plane and a third to choose the 'positive' side.");

		JPanel info = new JPanel(new MigLayout("fillx, wrap, ins 0"));
		addHeader(info, "How to Use");

		info.add(new JLabel("<html><div style='width: 360px'>Hold down one of these keys and click in an ortho view to define vertices. "
			+ "The vertices will automatically connect and UVs will be created.</div></html>"), "growx, span, wrap");

		JPanel tab = new JPanel(new MigLayout("fillx, wrap"));
		tab.add(shortcuts, "growx");
		tab.add(info, "growx");
		return tab;
	}

	private JPanel getViewportsTab()
	{
		JPanel tab = new JPanel(new MigLayout("fillx", "[grow,fill][240!,fill]"));

		addHeader(tab, "Moving the Camera");
		addShortcut(tab, "Pan (2D View)", keys(EditorShortcut.MOVE_FORWARD, EditorShortcut.MOVE_LEFT, EditorShortcut.MOVE_BACKWARD, EditorShortcut.MOVE_RIGHT));
		addShortcut(tab, "Zoom (2D View)", "Mousewheel");
		addShortcut(tab, "Move (3D View)",
			"Hold Shift + " + keys(EditorShortcut.MOVE_FORWARD, EditorShortcut.MOVE_LEFT, EditorShortcut.MOVE_BACKWARD, EditorShortcut.MOVE_RIGHT));
		addShortcut(tab, "Center on Selection", keys(EditorShortcut.CENTER_VIEW));

		addHeader(tab, "Rendering Options");
		addShortcut(tab, "Toggle 4-View", keys(EditorShortcut.TOGGLE_QUADVIEW));
		addShortcut(tab, "Toggle Wireframe", keys(EditorShortcut.TOGGLE_WIREFRAME));
		addShortcut(tab, "Toggle Edge Highlights", keys(EditorShortcut.TOGGLE_EDGES));
		addShortcut(tab, "Toggle Normals", keys(EditorShortcut.SHOW_NORMALS));
		addShortcut(tab, "Toggle Geometry Flags", keys(EditorShortcut.USE_GEOMETRY_FLAGS));
		addShortcut(tab, "Toggle Transform Gizmo", keys(EditorShortcut.SHOW_GIZMO));

		addHeader(tab, "Object Visibility");
		addShortcut(tab, "Toggle Models", keys(EditorShortcut.SHOW_MODELS),
			"Show/hide other object types: " + keys(EditorShortcut.SHOW_ONLY_MODELS));
		addShortcut(tab, "Toggle Colliders", keys(EditorShortcut.SHOW_COLLIDERS),
			"Show/hide other object types: " + keys(EditorShortcut.SHOW_ONLY_COLLIDERS));
		addShortcut(tab, "Toggle Zones", keys(EditorShortcut.SHOW_ZONES),
			"Show/hide other object types: " + keys(EditorShortcut.SHOW_ONLY_ZONES));
		addShortcut(tab, "Toggle Markers", keys(EditorShortcut.SHOW_MARKERS),
			"Show/hide other object types: " + keys(EditorShortcut.SHOW_ONLY_MARKERS));

		return tab;
	}

	private JPanel getGridTab()
	{
		JPanel tab = new JPanel(new MigLayout("fillx", "[grow,fill][240!,fill]"));

		addHeader(tab, "Grid Options");
		addShortcut(tab, "Toggle Grid", keys(EditorShortcut.TOGGLE_GRID));
		addShortcut(tab, "Switch Grid Type", keys(EditorShortcut.TOGGLE_GRID_TYPE),
			"<html>Switches between \"powers of 2\" (binary) and \"powers of 10\" (decimal) grids.<br>"
				+ "Most in-game geometry follows a decimal grid.</html>");
		addShortcut(tab, "Increase Grid Spacing", keys(EditorShortcut.INCREASE_GRID_POWER));
		addShortcut(tab, "Decrease Grid Spacing", keys(EditorShortcut.DECREASE_GRID_POWER));

		addHeader(tab, "Snap Options");
		addShortcut(tab, "Snap Translation", keys(EditorShortcut.SNAP_TRANSLATION));
		addShortcut(tab, "Snap Rotation", keys(EditorShortcut.SNAP_ROTATION));
		addShortcut(tab, "Snap Scale", keys(EditorShortcut.SNAP_SCALE));
		addShortcut(tab, "Toggle Scale Snap Mode", keys(EditorShortcut.SNAP_SCALE_GRID),
			"<html>Switches the rescale snap method between:<br>"
				+ "a. increments of 10%<br>"
				+ "b. nearest multiple of grid spacing</html>");

		addShortcut(tab, "Toggle Vertex Snap", keys(EditorShortcut.VERTEX_SNAP), "Vertices will snap to others during ortho view translations.");
		addShortcut(tab, "Toggle Vertex Snap to All", keys(EditorShortcut.VERTEX_SNAP_LIMIT),
			"Snap between only like objects (models, colliders, zones) or any object.");

		return tab;
	}
}
