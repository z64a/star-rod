package game.map.editor;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;

import common.KeyBinding;
import common.KeyInput;

/**
 * Key combinations that execute some action when pressed in the editor window
 * (specifically, when the GL canvas has focus). Each can be linked to a checkbox
 * JMenuItem, which the Editor can use to sync boolean fields with the GUI. This
 * also causes the keyboard shortcut to show up in the JMenu.<br>
 * <br>
 * Bindings are configured per editor; these enum values retain the default keys.
 */
public enum EditorShortcut implements KeyInput
{
	// @formatter:off
	// Undo/redo also work in editor panels through the editor key dispatcher.
	UNDO				(Modifier.CTRL, KeyEvent.VK_Z),
	REDO				(Modifier.CTRL, KeyEvent.VK_Y),

	SELECT_ALL			(Modifier.CTRL, KeyEvent.VK_A),
	FIND_OBJECT			(Modifier.CTRL, KeyEvent.VK_F),

	COPY_OBJECTS		(Modifier.CTRL, KeyEvent.VK_C),
	PASTE_OBJECTS		(Modifier.CTRL, KeyEvent.VK_V),

	OPEN_TRANSFORM_DIALOG	(Modifier.CTRL, KeyEvent.VK_T),

	// for colliders and zones
	TOGGLE_TWO_SIDED		(KeyEvent.VK_L),

	TOGGLE_UV_EDIT			(KeyEvent.VK_U),

	PLAY_IN_EDITOR_TOGGLE	(KeyEvent.VK_P),
	PLAY_IN_EDITOR_JUMP		(KeyEvent.VK_J),
	PLAY_IN_EDITOR_SPIN		(KeyEvent.VK_R),
	PLAY_IN_EDITOR_HOVER	(KeyEvent.VK_K),
	PLAY_IN_EDITOR_RUN		(KeyEvent.VK_SHIFT),

	MOVE_FORWARD		(KeyEvent.VK_W),
	MOVE_BACKWARD		(KeyEvent.VK_S),
	MOVE_LEFT			(KeyEvent.VK_A),
	MOVE_RIGHT			(KeyEvent.VK_D),

	DRAW_CONVEX			(KeyEvent.VK_COMMA),
	DRAW_CONCAVE		(KeyEvent.VK_PERIOD),
	DRAW_WALLS			(KeyEvent.VK_SLASH),
	CUT_GEOMETRY		(KeyEvent.VK_BACK_QUOTE),
	PIE_IGNORE_HIDDEN_COL	(),
	PIE_IGNORE_HIDDEN_ZONE	(),
	PIE_SHOW_ACTIVE_CAMERA	(),
	PIE_ENABLE_MAP_EXITS	(),

	TOGGLE_INFO_PANEL	(KeyEvent.VK_I),

	SELECTION_PAINTING	(KeyEvent.VK_Q),

	SELECT_OBJECTS		(KeyEvent.VK_1),
	SELECT_TRIANGLES	(KeyEvent.VK_2),
	SELECT_VERTICIES	(KeyEvent.VK_3),
	SELECT_POINTS		(KeyEvent.VK_4),

	OPEN_MODEL_TAB		(Modifier.SHFT, KeyEvent.VK_1),
	OPEN_COLLIDER_TAB	(Modifier.SHFT, KeyEvent.VK_2),
	OPEN_ZONE_TAB		(Modifier.SHFT, KeyEvent.VK_3),
	OPEN_MARKER_TAB		(Modifier.SHFT, KeyEvent.VK_4),

	TOGGLE_GRID			(KeyEvent.VK_G),
	TOGGLE_GRID_TYPE	(Modifier.SHFT, KeyEvent.VK_G),
	INCREASE_GRID_POWER (KeyEvent.VK_EQUALS),
	DECREASE_GRID_POWER (KeyEvent.VK_MINUS),

	VERTEX_SNAP			(KeyEvent.VK_6),
	VERTEX_SNAP_LIMIT	(Modifier.SHFT, KeyEvent.VK_6),

	SNAP_TRANSLATION	(KeyEvent.VK_7),
	SNAP_ROTATION		(KeyEvent.VK_8),
	SNAP_SCALE			(KeyEvent.VK_9),
	SNAP_SCALE_GRID		(KeyEvent.VK_0),

	ROUND_VERTICIES		(Modifier.CTRL, KeyEvent.VK_N),

	MOVE_MARKER_POINTS	(),

	DUPLICATE_SELECTED	(),
	DELETE_SELECTED		(KeyEvent.VK_DELETE),
	HIDE_SELECTED		(KeyEvent.VK_H),
	FLIP_SELECTED_X		(Modifier.SHFT, KeyEvent.VK_X),
	FLIP_SELECTED_Y		(Modifier.SHFT, KeyEvent.VK_Y),
	FLIP_SELECTED_Z		(Modifier.SHFT, KeyEvent.VK_Z),
	FLIP_NORMALS		(Modifier.SHFT, KeyEvent.VK_N),
	NORMALS_TO_CAMERA	(),

	SHOW_MODELS			(KeyEvent.VK_F1),
	SHOW_COLLIDERS		(KeyEvent.VK_F2),
	SHOW_ZONES			(KeyEvent.VK_F3),
	SHOW_MARKERS		(KeyEvent.VK_F4),
	SHOW_ONLY_MODELS	(Modifier.SHFT, KeyEvent.VK_F1),
	SHOW_ONLY_COLLIDERS	(Modifier.SHFT, KeyEvent.VK_F2),
	SHOW_ONLY_ZONES		(Modifier.SHFT, KeyEvent.VK_F3),
	SHOW_ONLY_MARKERS	(Modifier.SHFT, KeyEvent.VK_F4),
	SHOW_NORMALS		(KeyEvent.VK_N),
	SHOW_GIZMO			(KeyEvent.VK_Y),
	SHOW_ENTITY_COLLISION (),
	USE_COLLIDER_COLORS (),

	SHOW_AABB			(KeyEvent.VK_B),
	SHOW_AXES			(),
	USE_GAME_ASPECT_RATIO		(),
	USE_MAP_CAM_PROPERTIES		(),
	USE_MAP_BG_COLOR	(),
	USE_GEOMETRY_FLAGS	(KeyEvent.VK_M),
	USE_FILTERING		(),
	USE_TEXTURE_LOD		(),
	RESET_LAYOUT		(),
	RESET_OPTIONS		(),

	NUDGE_UP			(KeyEvent.VK_UP),
	NUDGE_DOWN			(KeyEvent.VK_DOWN),
	NUDGE_LEFT			(KeyEvent.VK_LEFT),
	NUDGE_RIGHT			(KeyEvent.VK_RIGHT),
	NUDGE_OUT			(KeyEvent.VK_PAGE_UP),
	NUDGE_IN			(KeyEvent.VK_PAGE_DOWN),

	TOGGLE_WIREFRAME	(KeyEvent.VK_T),
	TOGGLE_EDGES		(KeyEvent.VK_E),
	TOGGLE_QUADVIEW		(KeyEvent.VK_F),
	CENTER_VIEW			(KeyEvent.VK_C),

	SAVE				(Modifier.CTRL, KeyEvent.VK_S),

	SWITCH				(Modifier.SHFT, KeyEvent.VK_ESCAPE),
	QUIT				(KeyEvent.VK_ESCAPE),

	SHOW_ADVANCED_OPTIONS		();
	// @formatter:on

	private enum Modifier
	{
		NONE, CTRL, SHFT
	}

	private final Modifier keyMod;
	private final int key;

	// toggle shortcuts can be bound to checkboxes
	private JCheckBoxMenuItem checkbox = null;

	private static final int NO_KEY = 0;

	// some 'shortcuts' have no key binding, they are just used to
	// synchonize an Editor boolean with the state of a checkbox
	// or forward a command to the editor
	private EditorShortcut()
	{
		this(Modifier.NONE, NO_KEY);
	}

	private EditorShortcut(int keyCode)
	{
		this(Modifier.NONE, keyCode);
	}

	private EditorShortcut(Modifier keyMod, int keyCode)
	{
		this.keyMod = keyMod;
		this.key = keyCode;
	}

	@Override
	public KeyBinding getDefaultBinding()
	{
		int modifiers = switch (keyMod) {
			case CTRL -> InputEvent.CTRL_DOWN_MASK;
			case SHFT -> InputEvent.SHIFT_DOWN_MASK;
			default -> 0;
		};
		return new KeyBinding(key, modifiers);
	}

	@Override
	public boolean allowsModifierKey()
	{
		return this == PLAY_IN_EDITOR_RUN;
	}

	public void bindMenuItem(MapEditor editor, JMenuItem item)
	{
		Runnable updateAccelerator = () -> {
			KeyStroke oldStroke = item.getAccelerator();
			if (oldStroke != null)
				item.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).remove(oldStroke);
			KeyStroke stroke = editor.keyConfig.getBinding(this).toKeyStroke();
			item.setAccelerator(stroke);
			// Display the shortcut; the canvas and global dispatcher handle key events.
			if (stroke != null)
				item.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(stroke, "none");
		};
		updateAccelerator.run();
		editor.keyConfig.addListener(updateAccelerator);
		item.addActionListener(e -> editor.executeShortcut(this));
	}

	public void bindMenuCheckbox(MapEditor editor, JCheckBoxMenuItem checkbox)
	{
		this.checkbox = checkbox;
		bindMenuItem(editor, checkbox);
	}

	public void setCheckBoxText(String text)
	{
		assert (checkbox != null);
		MapEditor.instance().runInEDT(() -> checkbox.setText(text));
	}

	public void setCheckbox(boolean newValue)
	{
		assert (checkbox != null);
		MapEditor.instance().runInEDT(() -> checkbox.setSelected(newValue));
	}

	public JCheckBoxMenuItem getCheckBox()
	{
		return checkbox;
	}

	@Override
	public String getDisplayName()
	{
		switch (this) {
			case OPEN_TRANSFORM_DIALOG:
				return "Open Transform Menu";
			case TOGGLE_TWO_SIDED:
				return "Toggle Double Sided";
			case PLAY_IN_EDITOR_TOGGLE:
				return "Start / Stop Play in Editor";
			case PLAY_IN_EDITOR_JUMP:
				return "Play in Editor Jump";
			case PLAY_IN_EDITOR_SPIN:
				return "Play in Editor Spin";
			case PLAY_IN_EDITOR_HOVER:
				return "Play in Editor Hover";
			case SELECT_VERTICIES:
				return "Select Vertices";
			case OPEN_MODEL_TAB:
				return "Open Models Tab";
			case OPEN_COLLIDER_TAB:
				return "Open Colliders Tab";
			case OPEN_ZONE_TAB:
				return "Open Zones Tab";
			case OPEN_MARKER_TAB:
				return "Open Markers Tab";
			case INCREASE_GRID_POWER:
				return "Increase Grid Spacing";
			case DECREASE_GRID_POWER:
				return "Decrease Grid Spacing";
			case VERTEX_SNAP_LIMIT:
				return "Vertex Snap to All";
			case SNAP_SCALE_GRID:
				return "Toggle Scale Snap Mode";
			case ROUND_VERTICIES:
				return "Nudge to Grid";
			case SHOW_ONLY_MODELS:
				return "Show Only Models";
			case SHOW_ONLY_COLLIDERS:
				return "Show Only Colliders";
			case SHOW_ONLY_ZONES:
				return "Show Only Zones";
			case SHOW_ONLY_MARKERS:
				return "Show Only Markers";
			case USE_GEOMETRY_FLAGS:
				return "Toggle Geometry Flags";
			case NUDGE_OUT:
				return "Nudge Out";
			case NUDGE_IN:
				return "Nudge In";
			case TOGGLE_QUADVIEW:
				return "Toggle Four Views";
			case SWITCH:
				return "Quit to Menu";
			case DRAW_CONVEX:
				return "Draw Convex Polygon";
			case DRAW_CONCAVE:
				return "Draw Concave Polygon";
			case CUT_GEOMETRY:
				return "Cut Geometry";
			default:
				return KeyInput.super.getDisplayName();
		}
	}

	@Override
	public String getCategory()
	{
		switch (this) {
			case UNDO:
			case REDO:
			case SAVE:
			case SWITCH:
			case QUIT:
				return "General";

			case SELECT_OBJECTS:
			case SELECT_TRIANGLES:
			case SELECT_VERTICIES:
			case SELECT_POINTS:
			case OPEN_MODEL_TAB:
			case OPEN_COLLIDER_TAB:
			case OPEN_ZONE_TAB:
			case OPEN_MARKER_TAB:
			case TOGGLE_UV_EDIT:
			case TOGGLE_INFO_PANEL:
				return "Editing Modes";

			case MOVE_FORWARD:
			case MOVE_BACKWARD:
			case MOVE_LEFT:
			case MOVE_RIGHT:
			case CENTER_VIEW:
				return "Navigation";

			case SELECT_ALL:
			case FIND_OBJECT:
			case COPY_OBJECTS:
			case PASTE_OBJECTS:
			case DELETE_SELECTED:
			case HIDE_SELECTED:
				return "Selection";

			case OPEN_TRANSFORM_DIALOG:
			case ROUND_VERTICIES:
			case FLIP_SELECTED_X:
			case FLIP_SELECTED_Y:
			case FLIP_SELECTED_Z:
			case FLIP_NORMALS:
			case TOGGLE_TWO_SIDED:
			case NUDGE_UP:
			case NUDGE_DOWN:
			case NUDGE_LEFT:
			case NUDGE_RIGHT:
			case NUDGE_OUT:
			case NUDGE_IN:
				return "Transform";

			case DRAW_CONVEX:
			case DRAW_CONCAVE:
			case DRAW_WALLS:
			case CUT_GEOMETRY:
				return "Drawing";

			case PLAY_IN_EDITOR_TOGGLE:
			case PLAY_IN_EDITOR_JUMP:
			case PLAY_IN_EDITOR_SPIN:
			case PLAY_IN_EDITOR_HOVER:
			case PLAY_IN_EDITOR_RUN:
				return "Play in Editor";

			case TOGGLE_GRID:
			case TOGGLE_GRID_TYPE:
			case INCREASE_GRID_POWER:
			case DECREASE_GRID_POWER:
			case VERTEX_SNAP:
			case VERTEX_SNAP_LIMIT:
			case SNAP_TRANSLATION:
			case SNAP_ROTATION:
			case SNAP_SCALE:
			case SNAP_SCALE_GRID:
				return "Grid and Snap";

			case SHOW_MODELS:
			case SHOW_COLLIDERS:
			case SHOW_ZONES:
			case SHOW_MARKERS:
			case SHOW_ONLY_MODELS:
			case SHOW_ONLY_COLLIDERS:
			case SHOW_ONLY_ZONES:
			case SHOW_ONLY_MARKERS:
			case SHOW_NORMALS:
			case SHOW_GIZMO:
			case SHOW_AABB:
			case TOGGLE_QUADVIEW:
			case TOGGLE_WIREFRAME:
			case TOGGLE_EDGES:
			case USE_GEOMETRY_FLAGS:
				return "Visibility";

			default:
				return "Other";
		}
	}
}
