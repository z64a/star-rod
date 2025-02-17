package game.map.editor.commands;

import java.util.ArrayList;
import java.util.List;

import common.commands.AbstractCommand;
import game.map.editor.MapEditor;
import game.map.mesh.TexturedMesh;
import game.map.shape.Model;
import game.map.shape.TriangleBatch;
import game.map.shape.commands.DisplayCommand;
import game.map.shape.commands.DisplayCommand.CmdType;

public class JoinModels extends AbstractCommand
{
	private List<Model> models;
	private ArrayList<List<DisplayCommand>> oldDisplayLists;

	public JoinModels(List<Model> models)
	{
		super("Join Models");

		this.models = models;
		oldDisplayLists = new ArrayList<>(models.size());

		for (Model mdl : models) {
			List<DisplayCommand> displayList = new ArrayList<>(mdl.getMesh().displayListModel.size());
			for (DisplayCommand cmd : mdl.getMesh().displayListModel)
				displayList.add(cmd);
			oldDisplayLists.add(displayList);
		}
	}

	@Override
	public void exec()
	{
		super.exec();

		MapEditor editor = MapEditor.instance();
		Model first = models.get(0);

		for (int i = 1; i < models.size(); i++) {
			Model mdl = models.get(i);
			TexturedMesh mesh = mdl.getMesh();

			for (DisplayCommand cmd : mesh.displayListModel) {
				if (cmd.getType() == CmdType.DrawTriangleBatch) {
					TexturedMesh newMesh = first.getMesh();
					newMesh.displayListModel.addElement(cmd);
					((TriangleBatch) cmd).setParent(newMesh);
				}
			}

			editor.map.remove(mdl);
			editor.selectionManager.deleteObject(mdl);
		}

		first.updateMeshHierarchy();
		first.dirtyAABB = true;
	}

	@Override
	public void undo()
	{
		super.undo();

		MapEditor editor = MapEditor.instance();

		for (int i = models.size() - 1; i >= 0; i--) {
			Model mdl = models.get(i);

			if (i != 0) {
				editor.map.add(mdl);
				editor.selectionManager.createObject(mdl);
			}

			TexturedMesh mesh = mdl.getMesh();
			mesh.displayListModel.clear();

			for (DisplayCommand cmd : oldDisplayLists.get(i)) {
				mesh.displayListModel.addElement(cmd);
				if (cmd.getType() == CmdType.DrawTriangleBatch)
					((TriangleBatch) cmd).setParent(mesh);
			}

			mdl.updateMeshHierarchy();
			mdl.dirtyAABB = true;
		}
	}
}
