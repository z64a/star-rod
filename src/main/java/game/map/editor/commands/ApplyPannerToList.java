package game.map.editor.commands;

import java.util.HashMap;

import common.commands.AbstractCommand;
import game.map.shape.Model;

public class ApplyPannerToList extends AbstractCommand
{
	private Iterable<Model> modelList;
	private HashMap<Model, Integer> oldPannerMap;
	private int newPannerID;

	public ApplyPannerToList(Iterable<Model> modelList, int pannerID)
	{
		super("Apply Panner to Models");
		this.modelList = modelList;
		this.newPannerID = pannerID;
		oldPannerMap = new HashMap<>();

		for (Model mdl : this.modelList) {
			oldPannerMap.put(mdl, mdl.pannerID.get());
		}
	}

	@Override
	public void exec()
	{
		super.exec();

		for (Model mdl : this.modelList)
			mdl.pannerID.set(newPannerID);
	}

	@Override
	public void undo()
	{
		super.undo();

		for (Model mdl : this.modelList)
			mdl.pannerID.set(oldPannerMap.get(mdl));
	}
}
