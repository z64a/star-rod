package game.map.editor.ui;

import java.awt.Dimension;
import java.util.function.Consumer;

import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;

import com.alexandriasoftware.swing.JSplitButton;

import game.map.MapObject;
import game.map.MapObject.MapObjectType;
import game.map.marker.Marker.MarkerType;
import game.map.scripts.UISelectionHelper;
import net.miginfocom.swing.MigLayout;
import util.ui.StringField;

public class BoundObjectPanel extends JPanel
{
	private static final Dimension POPUP_OPTION_SIZE = new Dimension(150, 24);

	private final Consumer<String> editCallback;
	private final MapObjectType objectType;
	private final MarkerType markerType;
	private final StringField field;
	private final LabelWithTip lbl;

	public BoundObjectPanel(MarkerType type,
		String labelText,
		Consumer<String> editCallback)
	{
		this(MapObjectType.MARKER, type, labelText, null, editCallback);
	}

	public BoundObjectPanel(MarkerType type,
		String labelText, String tooltip,
		Consumer<String> editCallback)
	{
		this(MapObjectType.MARKER, type, labelText, tooltip, editCallback);
	}

	public BoundObjectPanel(MapObjectType type,
		String labelText,
		Consumer<String> editCallback)
	{
		this(type, null, labelText, null, editCallback);
	}

	public BoundObjectPanel(MapObjectType type,
		String labelText, String tooltip,
		Consumer<String> editCallback)
	{
		this(type, null, labelText, tooltip, editCallback);
	}

	private BoundObjectPanel(MapObjectType objectType, MarkerType markerType,
		String labelText, String tooltip,
		Consumer<String> editCallback)
	{
		this.objectType = objectType;
		this.markerType = markerType;
		this.editCallback = editCallback;

		field = new StringField((s) -> {
			editCallback.accept(s);
		});
		field.setHorizontalAlignment(SwingConstants.LEFT);

		JPopupMenu commandMenu = new JPopupMenu();
		buildPopupMenu(commandMenu);
		SwingGUI.instance().registerPopupMenu(commandMenu);

		JSplitButton button = new JSplitButton("Action");
		button.setPopupMenu(commandMenu);
		button.setAlwaysPopup(true);

		setLayout(new MigLayout("fill, ins 0"));
		lbl = new LabelWithTip(labelText, 12, tooltip);
		add(lbl, "w 80!, gapleft 8, gapright 8, split 3");
		add(field, "growx");
		add(button, "w 120!");
	}

	public void setLabelText(String labelText)
	{
		lbl.setText(labelText);
	}

	public void setText(String text)
	{
		field.setText(text);
	}

	public String getText()
	{
		return field.getText();
	}

	private void buildPopupMenu(JPopupMenu menu)
	{
		JMenuItem item;

		item = new JMenuItem("Use Selected");
		item.setPreferredSize(POPUP_OPTION_SIZE);
		item.addActionListener(e -> {
			useSelected();
		});
		menu.add(item);

		item = new JMenuItem("Select Object");
		item.setPreferredSize(POPUP_OPTION_SIZE);
		item.addActionListener(e -> {
			UISelectionHelper.selectObject(objectType, field.getText());
		});
		menu.add(item);
	}

	private void useSelected()
	{
		MapObject obj = null;

		// get last selected object, check type
		if (objectType == MapObjectType.MARKER)
			obj = UISelectionHelper.getLastMarker(markerType);
		else
			obj = UISelectionHelper.getLastObject(objectType);

		if (obj != null)
			editCallback.accept(obj.getName());
	}
}
