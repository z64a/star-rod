package game.map.editor.ui.info.marker;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;

import app.SwingUtils;
import game.map.editor.MapEditor;
import game.map.editor.selection.SelectionManager.SelectionMode;
import game.map.editor.ui.PathList;
import game.map.editor.ui.info.MarkerInfoPanel;
import game.map.marker.PathComponent;
import game.map.marker.PathData.AddPathPoint;
import net.miginfocom.swing.MigLayout;

public class PathSubpanel extends JPanel
{
	private final MarkerInfoPanel parent;

	private PathList pathList;
	private JCheckBox cbShowInterp;

	public PathSubpanel(MarkerInfoPanel parent)
	{
		this.parent = parent;

		JButton addPointButton = new JButton("Add Point");
		addPointButton.addActionListener((e) -> {
			MapEditor.execute(new AddPathPoint(parent.getData().pathComponent.path));
		});
		SwingUtils.addBorderPadding(addPointButton);

		cbShowInterp = new JCheckBox(" Show spline interp");
		cbShowInterp.addActionListener((e) -> {
			PathComponent comp = parent.getData().pathComponent;
			boolean newValue = cbShowInterp.isSelected();

			MapEditor.execute(comp.showInterp.mutator(newValue));
		});

		pathList = new PathList();

		JScrollPane pathScrollPane = new JScrollPane(pathList);
		pathScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		pathScrollPane.setBorder(null);

		setLayout(new MigLayout("fill, ins 0"));
		add(pathScrollPane, "growy, pushy, growx, span, wrap");
		add(cbShowInterp, "growx, pushx");
		add(addPointButton, "growx, pushx");
	}

	public void updateFields()
	{
		PathComponent comp = parent.getData().pathComponent;

		pathList.setModel(comp.path.points);
		comp.path.markDegenerates();

		cbShowInterp.setSelected(comp.showInterp.get());
	}

	public void updateDynamicFields(boolean force)
	{
		boolean pointSelectionMode = (MapEditor.instance().selectionManager.getSelectionMode() == SelectionMode.POINT);
		if (!pointSelectionMode)
			return;

		PathComponent comp = parent.getData().pathComponent;
		comp.path.markDegenerates();

		pathList.repaint();
	}
}
