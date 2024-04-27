package game.map.editor.ui;

import java.awt.Font;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import net.miginfocom.swing.MigLayout;

public class MapShortcutsPanel extends JPanel
{
	public MapShortcutsPanel()
	{
		setLayout(new MigLayout("fill"));

		JTabbedPane tabs = new JTabbedPane();

		tabs.addTab("Editor", getEditorTab());
		tabs.addTab("Play in Editor", getPlayInEditorTab());
		tabs.addTab("Selection", getSelectionTab());
		tabs.addTab("Transform", getTransformTab());
		tabs.addTab("Drawing", getDrawTab());
		tabs.addTab("Viewports", getViewportsTab());
		tabs.addTab("Grid / Snap", getGridTab());

		add(tabs, "grow, w 400!, h 500!");
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
		panel.add(new JLabel(keys), "growx, wrap");
	}

	private JPanel getEditorTab()
	{
		JPanel tab = new JPanel(new MigLayout("fillx", "[50%][50%]"));

		addHeader(tab, "General");
		addShortcut(tab, "Save", "Ctrl + S");
		addShortcut(tab, "Quit", "Esc");
		addShortcut(tab, "Quit to Menu", "Shift + Esc");
		addShortcut(tab, "Undo", "Ctrl + Z");
		addShortcut(tab, "Redo", "Ctrl + Y");
		addShortcut(tab, "Toggle Info Panel", "I");

		addHeader(tab, "Switch Transform Mode");
		addShortcut(tab, "Objects", "1");
		addShortcut(tab, "Triangles", "2");
		addShortcut(tab, "Vertices", "3");
		addShortcut(tab, "Points", "4");

		addHeader(tab, "Switch Object Tab");
		addShortcut(tab, "Models", "Shift + 1");
		addShortcut(tab, "Colliders", "Shift + 2");
		addShortcut(tab, "Zones", "Shift + 3");
		addShortcut(tab, "Markers", "Shift + 4");

		return tab;
	}

	private JPanel getPlayInEditorTab()
	{
		JPanel shortcuts = new JPanel(new MigLayout("fillx, ins 0", "[50%][50%]"));

		addHeader(shortcuts, "Controls");
		addShortcut(shortcuts, "Begin/End", "P");

		addShortcut(shortcuts, "Move", "WASD");
		addShortcut(shortcuts, "Jump", "J");
		addShortcut(shortcuts, "Run", "Hold Shift");
		addShortcut(shortcuts, "Hover", "Hold K");

		JPanel info = new JPanel(new MigLayout("fillx, wrap, ins 0"));
		addHeader(info, "How to Use");

		// screws up the layout for reasons completely unknown!
		info.add(new JLabel("<html><p>Press P when using the 3D viewport to spawn "
			+ "at the location of the 3D cursor. Gravity, jump, and collision "
			+ "physics match in-game.</p><br><p>Current camera zones will switch "
			+ "automatically as you move about. You can set hidden colliders and "
			+ "zones to be ignored (they will still exist in-game).</p><br>"
			+ "<p>All normal editor actions are available in this mode.</p></html>"),
			"growx, span, wrap");

		JPanel tab = new JPanel(new MigLayout("fillx, wrap"));
		tab.add(shortcuts, "growx");
		tab.add(info, "growx");
		return tab;
	}

	private JPanel getSelectionTab()
	{
		JPanel tab = new JPanel(new MigLayout("fillx", "[50%][50%]"));

		addHeader(tab, "Change Selection");
		addShortcut(tab, "Select", "Left Click");
		addShortcut(tab, "Select Add/Remove", "Ctrl + Left Click");
		addShortcut(tab, "Select All", "Ctrl + A");
		addShortcut(tab, "Find Object", "Ctrl + F");
		addShortcut(tab, "Copy Objects", "Ctrl + C", "You can even copy/paste objects between maps.");
		addShortcut(tab, "Paste Objects", "Ctrl + V");
		addShortcut(tab, "Delete Selected", "Delete");

		addHeader(tab, "Modify Properties of Selection");
		addShortcut(tab, "Hide Selected", "H");
		addShortcut(tab, "Open UV Editor", "U", "Must have models or model triangles selected.");
		addShortcut(tab, "Toggle Double Sided", "L",
			"<html>Toggles triangle type for selected zones and colliders.<br>"
				+ "Useful for making one-way walls.</html>");

		return tab;
	}

	private JPanel getTransformTab()
	{
		JPanel tab = new JPanel(new MigLayout("fillx", "[50%][50%]"));

		addHeader(tab, "Transforming Selected Objects");
		addShortcut(tab, "Translate", "Left Mouse Drag", "Hold shift to translate a selecton without having to click on it.");
		addShortcut(tab, "Rotate", "Right Mouse Drag");
		addShortcut(tab, "Scale", "Space + Left Mouse Drag");
		addShortcut(tab, "Uniform Scale", "Space + Right Mouse Drag");
		addShortcut(tab, "Clone", "Any above + Alt");
		addShortcut(tab, "Nudge", "Arrow Keys + Page Up/Down");
		addShortcut(tab, "Translate", "Hold Ctrl + Any Nudge Key");

		addHeader(tab, "Special Transformations");
		addShortcut(tab, "Flip Along X", "Shift + X");
		addShortcut(tab, "Flip Along Y", "Shift + Y");
		addShortcut(tab, "Filp Along Z", "Shift + Z");
		addShortcut(tab, "Flip Normals", "Shift + N");
		addShortcut(tab, "Open Transform Menu", "Ctrl + T");

		addShortcut(tab, "Nudge to Grid", "Ctrl + N", "Moves any selected vertices to the nearest grid point.");

		return tab;
	}

	private JPanel getDrawTab()
	{
		JPanel shortcuts = new JPanel(new MigLayout("fillx, ins 0", "[50%][50%]"));
		addHeader(shortcuts, "Polygon Drawing and Cut");
		addShortcut(shortcuts, "Draw Convex", "Hold <");
		addShortcut(shortcuts, "Draw Concave", "Hold >");
		addShortcut(shortcuts, "Draw Walls", "Hold /");
		addShortcut(shortcuts, "Cut Meshes", "Hold ~", "Two points to define cutting plane and a third to choose the 'positive' side.");

		JPanel info = new JPanel(new MigLayout("fillx, wrap, ins 0"));
		addHeader(info, "How to Use");

		// screws up the layout for reasons completely unknown!
		info.add(new JLabel("<html>Hold down one of these keys and click in an ortho view to define vertices. "
			+ "The vertices will automatically connect and UVs will be created.</html>"), "growx, span, wrap");

		JPanel tab = new JPanel(new MigLayout("fillx, wrap"));
		tab.add(shortcuts, "growx");
		tab.add(info, "growx");
		return tab;
	}

	private JPanel getViewportsTab()
	{
		JPanel tab = new JPanel(new MigLayout("fillx", "[50%][50%]"));

		addHeader(tab, "Moving the Camera");
		addShortcut(tab, "Pan (2D View)", "W/A/S/D");
		addShortcut(tab, "Zoom (2D View)", "Mousewheel");
		addShortcut(tab, "Move (3D View)", "Hold Shift + W/A/S/D");
		addShortcut(tab, "Center on Selection", "C");

		addHeader(tab, "Rendering Options");
		addShortcut(tab, "Toggle 4-View", "F");
		addShortcut(tab, "Toggle Wireframe", "T");
		addShortcut(tab, "Toggle Edge Highlights", "E");
		addShortcut(tab, "Toggle Normals", "N");
		addShortcut(tab, "Toggle Geometry Flags", "M");
		addShortcut(tab, "Toggle Transform Gizmo", "Y");

		addHeader(tab, "Object Visibility", "Use shift with any of these options to show/hide all OTHER objects.");
		addShortcut(tab, "Toggle Models", "F1");
		addShortcut(tab, "Toggle Colliders", "F2");
		addShortcut(tab, "Toggle Zones", "F3");
		addShortcut(tab, "Toggle Markers", "F4");

		return tab;
	}

	private JPanel getGridTab()
	{
		JPanel tab = new JPanel(new MigLayout("fillx", "[50%][50%]"));

		addHeader(tab, "Grid Options");
		addShortcut(tab, "Toggle Grid", "G");
		addShortcut(tab, "Switch Grid Type", "Shift + G",
			"<html>Switches between \"powers of 2\" (binary) and \"powers of 10\" (decimal) grids.<br>"
				+ "Most in-game geometry follows a decimal grid.</html>");
		addShortcut(tab, "Increase Grid Spacing", "+");
		addShortcut(tab, "Decrease Grid Spacing", "-");

		addHeader(tab, "Snap Options");
		addShortcut(tab, "Snap Translation", "7");
		addShortcut(tab, "Snap Rotation", "8");
		addShortcut(tab, "Snap Scale", "9");
		addShortcut(tab, "Toggle Scale Snap Mode", "0",
			"<html>Switches the rescale snap method between:<br>"
				+ "a. increments of 10%<br>"
				+ "b. nearest multiple of grid spacing</html>");

		addShortcut(tab, "Toggle Vertex Snap", "6", "Vertices will snap to others during ortho view translations.");
		addShortcut(tab, "Toggle Vertex Snap to All", "Shift + 6", "Snap between only like objects (models, colliders, zones) or any object.");

		return tab;
	}
}
