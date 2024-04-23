package game.map.editor.ui;

import java.util.function.Consumer;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;

import app.SwingUtils;
import game.map.Map;
import game.map.MapObject.MapObjectType;
import game.map.editor.MapEditor;
import game.map.hit.Collider;
import game.map.hit.Zone;
import game.map.marker.Marker;
import game.map.marker.Marker.MarkerType;
import game.map.shape.Model;

public class MapObjectComboBox extends JComboBox<String>
{
	private final MapObjectType objectType;
	private final MarkerType markerType;

	public MapObjectComboBox(MarkerType type, Consumer<String> editCallback)
	{
		this(MapObjectType.MARKER, type, editCallback);
	}

	public MapObjectComboBox(MapObjectType type, Consumer<String> editCallback)
	{
		this(type, null, editCallback);
	}

	private MapObjectComboBox(MapObjectType objectType, MarkerType markerType, Consumer<String> editCallback)
	{
		super(new DefaultComboBoxModel<String>());

		this.objectType = objectType;
		this.markerType = markerType;

		addActionListener((e) -> {
			editCallback.accept((String) getSelectedItem());
		});
		SwingUtils.addBorderPadding(this);

		setMaximumRowCount(16);
		setEditable(true);
	}

	public void updateNames(String currentName)
	{
		Map markerMap = MapEditor.instance().map;
		Map shapeMap = MapEditor.instance().getGeometryMap();
		Map hitMap = MapEditor.instance().getCollisionMap();

		DefaultComboBoxModel<String> nameBoxModel = (DefaultComboBoxModel<String>) getModel();

		nameBoxModel.removeAllElements();

		if (objectType == MapObjectType.MARKER) {
			for (Marker m : markerMap.markerTree) {
				if (m.getType() == markerType)
					nameBoxModel.addElement(m.getName());
			}
		}
		else {
			switch (objectType) {
				case MODEL:
					for (Model mdl : shapeMap.modelTree)
						nameBoxModel.addElement("MODEL_" + mdl.getName());
					break;
				case COLLIDER:
					for (Collider c : hitMap.colliderTree)
						nameBoxModel.addElement("COLLIDER_" + c.getName());
					break;
				case ZONE:
					for (Zone z : hitMap.zoneTree)
						nameBoxModel.addElement("ZONE_" + z.getName());
					break;
				default:
					break;
			}
		}

		setSelectedItem(currentName);
	}

	public void setText(String name)
	{
		setSelectedItem(name);
	}

	public void setSelectedItem(String s)
	{
		super.setSelectedItem(s);

		boolean found = false;
		if (s != null) {
			for (int i = 0; i < getItemCount(); i++) {
				if (getItemAt(i).equals(s)) {
					found = true;
					break;
				}
			}
		}
		setForeground(found ? null : SwingUtils.getRedTextColor());
	}
}
