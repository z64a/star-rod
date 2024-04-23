package game.map.editor;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.HashMap;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;

import game.sprite.editor.ShortcutListPanel;

/**
 * Key combinations that execute some action when pressed in the editor window
 * (specifically, when the GL canvas has focus). Each can be linked to a checkbox
 * JMenuItem, which the Editor can use to sync boolean fields with the GUI. This
 * also causes the keyboard shortcut to show up in the JMenu.<br>
 * <br>
 * When adding a new shortcut, be sure to add a corresponding entry to {@link ShortcutListPanel},
 * add a handler in the Editor class, and (optionally) bind a JMenuItem.
 */
// NOTE: when adding a new shortcut, be sure to add an entry for it in
public enum EditorShortcut
{
	// @formatter:off
	// UNDO/REDO key listening are handled by a KeyEventDispatcher in SwingGUI
	UNDO				(),
	REDO				(),

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
	PLAY_IN_EDITOR_HOVER	(KeyEvent.VK_K),
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

	DEBUG_TOGGLE_LIGHT_SETS		();
	// @formatter:on

	private enum Modifier
	{
		NONE, CTRL, SHFT
	}

	private final Modifier keyMod;
	public final int key;

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

	private KeyStroke getKeyStoke()
	{
		// get the swing key stroke for this shortcut
		int awtMask = 0;
		switch (keyMod) {
			case NONE:
				awtMask = 0;
				break;
			case CTRL:
				awtMask = InputEvent.CTRL_DOWN_MASK;
				break;
			case SHFT:
				awtMask = InputEvent.SHIFT_DOWN_MASK;
				break;
		}

		return KeyStroke.getKeyStroke(key, awtMask);
	}

	public void bindMenuItem(MapEditor editor, JMenuItem item)
	{
		if (key != NO_KEY) {
			// set the accelerator, but unbind the input. we only want the shortcut to appear.
			KeyStroke awtKeyStroke = getKeyStoke();
			item.setAccelerator(awtKeyStroke);
			item.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(awtKeyStroke, "none");
		}

		item.addActionListener((e) -> {
			editor.enqueueKeyEvent(this);
		});
	}

	public void bindMenuCheckbox(MapEditor editor, JCheckBoxMenuItem checkbox)
	{
		this.checkbox = checkbox;
		bindMenuItem(editor, checkbox);
	}

	public void setCheckBoxText(String text)
	{
		assert (checkbox != null);
		checkbox.setText(text);
	}

	public void setCheckbox(boolean newValue)
	{
		assert (checkbox != null);
		checkbox.setSelected(newValue);
	}

	public JCheckBoxMenuItem getCheckBox()
	{ return checkbox; }

	private static final HashMap<Integer, EditorShortcut> inputKeyMap;
	private static final HashMap<Integer, EditorShortcut> inputCtrlKeyMap;
	private static final HashMap<Integer, EditorShortcut> inputShiftKeyMap;

	static {
		inputKeyMap = new HashMap<>();
		inputCtrlKeyMap = new HashMap<>();
		inputShiftKeyMap = new HashMap<>();

		for (EditorShortcut shortcut : EditorShortcut.values()) {
			if (shortcut.key == NO_KEY)
				continue;

			switch (shortcut.keyMod) {
				case NONE:
					inputKeyMap.put(shortcut.key, shortcut);
					break;
				case CTRL:
					inputCtrlKeyMap.put(shortcut.key, shortcut);
					break;
				case SHFT:
					inputShiftKeyMap.put(shortcut.key, shortcut);
					break;
			}
		}
	}

	public static EditorShortcut get(int keycode)
	{
		return inputKeyMap.get(keycode);
	}

	public static EditorShortcut getCtrl(int keycode)
	{
		return inputCtrlKeyMap.get(keycode);
	}

	public static EditorShortcut getShift(int keycode)
	{
		return inputShiftKeyMap.get(keycode);
	}
}
