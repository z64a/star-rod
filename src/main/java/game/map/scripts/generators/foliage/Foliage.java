package game.map.scripts.generators.foliage;

import static game.map.MapKey.*;

import org.w3c.dom.Element;

import game.map.editor.commands.fields.EditableField;
import game.map.editor.commands.fields.EditableField.EditableFieldFactory;
import game.map.editor.commands.fields.EditableField.StandardBoolName;
import game.map.scripts.GeneratorsPanel;
import game.map.scripts.generators.Generator;
import game.map.tree.CategoryTreeModel;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class Foliage extends Generator
{
	public static enum FoliageType
	{
		Bush,
		Tree;
	}

	public static enum FoliageDataCategory
	{
		// @formatter:off
		TrunkModels	("Trunk Models"),
		LeafModels	("Leaf Models"),
		BushModels	("Bush Models"),
		FXPositions	("FX Positions"),
		Drops		("Drops");
		// @formatter:on

		private final String name;

		private FoliageDataCategory(String name)
		{
			this.name = name;
		}

		public String getName()
		{
			return name;
		}
	}

	public final EditableField<String> overrideName;
	public final EditableField<String> colliderName;

	public final EditableField<String> bombPosName; // trees only
	public final EditableField<Boolean> isStarTree; // trees only

	public final EditableField<Boolean> hasCallback;

	public CategoryTreeModel<FoliageDataCategory, FoliageData> dataTreeModel;

	public Foliage(FoliageType type)
	{
		this(type == FoliageType.Bush ? GeneratorType.Bush : GeneratorType.Tree);
	}

	private Foliage(GeneratorType type)
	{
		super(type);

		overrideName = EditableFieldFactory.create("")
			.setCallback((o) -> {
				FoliageInfoPanel.instance().updateFields(this);
				GeneratorsPanel.instance().repaintTree();
			}).setName("Set " + type.getName() + " Name").build();

		colliderName = EditableFieldFactory.create("")
			.setCallback((o) -> FoliageInfoPanel.instance().updateFields(this))
			.setName("Set Trigger Collider").build();

		bombPosName = EditableFieldFactory.create("")
			.setCallback((o) -> FoliageInfoPanel.instance().updateFields(this))
			.setName("Set Bomb Trigger Pos").build();

		isStarTree = EditableFieldFactory.create(false)
			.setCallback((o) -> FoliageInfoPanel.instance().updateFields(this))
			.setName((newValue) -> {
				return newValue ? "Generate Star Tree" : "Generate Normal Tree";
			})
			.build();

		hasCallback = EditableFieldFactory.create(false)
			.setCallback((o) -> FoliageInfoPanel.instance().updateFields(this))
			.setName(new StandardBoolName("Callback")).build();

		dataTreeModel = new CategoryTreeModel<>("Foliage Data");

		switch (type) {
			case Bush:
				dataTreeModel.addCategory(FoliageDataCategory.BushModels);
				break;
			case Tree:
				dataTreeModel.addCategory(FoliageDataCategory.TrunkModels);
				dataTreeModel.addCategory(FoliageDataCategory.LeafModels);
				break;
			default:
				throw new IllegalStateException("Invalid type for foliage: " + type);
		}

		dataTreeModel.addCategory(FoliageDataCategory.FXPositions);
		dataTreeModel.addCategory(FoliageDataCategory.Drops);
	}

	@Override
	public Generator deepCopy()
	{
		Foliage copy = new Foliage(type);
		copy.overrideName.copy(overrideName);
		copy.colliderName.copy(colliderName);

		copy.bombPosName.copy(bombPosName);
		copy.isStarTree.copy(isStarTree);

		copy.hasCallback.copy(hasCallback);

		switch (type) {
			case Bush:
				for (FoliageData obj : dataTreeModel.getObjectsInCategory(FoliageDataCategory.BushModels))
					copy.dataTreeModel.addToCategory(FoliageDataCategory.BushModels, obj);
				break;

			case Tree:
				for (FoliageData obj : dataTreeModel.getObjectsInCategory(FoliageDataCategory.TrunkModels))
					copy.dataTreeModel.addToCategory(FoliageDataCategory.TrunkModels, obj);

				for (FoliageData obj : dataTreeModel.getObjectsInCategory(FoliageDataCategory.LeafModels))
					copy.dataTreeModel.addToCategory(FoliageDataCategory.LeafModels, obj);
				break;

			default:
				throw new UnsupportedOperationException("Can't serialize foliage of type: " + type);
		}

		for (FoliageData obj : dataTreeModel.getObjectsInCategory(FoliageDataCategory.FXPositions))
			copy.dataTreeModel.addToCategory(FoliageDataCategory.FXPositions, obj);

		for (FoliageData obj : dataTreeModel.getObjectsInCategory(FoliageDataCategory.Drops))
			copy.dataTreeModel.addToCategory(FoliageDataCategory.Drops, obj);

		return copy;
	}

	public static Foliage read(FoliageType type, XmlReader xmr, Element elem)
	{
		Foliage foliage = new Foliage(type);
		foliage.fromXML(xmr, elem);
		return foliage;
	}

	@Override
	public void fromXML(XmlReader xmr, Element elem)
	{
		if (xmr.hasAttribute(elem, ATTR_NAME))
			overrideName.set(xmr.getAttribute(elem, ATTR_NAME));

		if (xmr.hasAttribute(elem, ATTR_FOLIAGE_COL))
			colliderName.set(xmr.getAttribute(elem, ATTR_FOLIAGE_COL));

		if (xmr.hasAttribute(elem, ATTR_TREE_BOMB))
			bombPosName.set(xmr.getAttribute(elem, ATTR_TREE_BOMB));

		if (xmr.hasAttribute(elem, ATTR_TREE_STAR))
			isStarTree.set(xmr.readBoolean(elem, ATTR_TREE_STAR));

		if (xmr.hasAttribute(elem, ATTR_HAS_CALLBACK))
			hasCallback.set(xmr.readBoolean(elem, ATTR_HAS_CALLBACK));

		switch (type) {
			case Bush:
				for (Element mdlElem : xmr.getTags(elem, TAG_MDL_BUSH)) {
					xmr.requiresAttribute(mdlElem, ATTR_MDL_NAME);
					String name = xmr.getAttribute(mdlElem, ATTR_MDL_NAME);
					dataTreeModel.addToCategory(FoliageDataCategory.BushModels, new FoliageModel(this, name));
				}
				break;

			case Tree:
				for (Element mdlElem : xmr.getTags(elem, TAG_MDL_TRUNK)) {
					xmr.requiresAttribute(mdlElem, ATTR_MDL_NAME);
					String name = xmr.getAttribute(mdlElem, ATTR_MDL_NAME);
					dataTreeModel.addToCategory(FoliageDataCategory.TrunkModels, new FoliageModel(this, name));
				}

				for (Element mdlElem : xmr.getTags(elem, TAG_MDL_LEAF)) {
					xmr.requiresAttribute(mdlElem, ATTR_MDL_NAME);
					String name = xmr.getAttribute(mdlElem, ATTR_MDL_NAME);
					dataTreeModel.addToCategory(FoliageDataCategory.LeafModels, new FoliageModel(this, name));
				}
				break;

			default:
				throw new UnsupportedOperationException("Can't serialize foliage of type: " + type);
		}

		for (Element vecElem : xmr.getTags(elem, TAG_FOLIAGE_FX)) {
			xmr.requiresAttribute(vecElem, ATTR_FOLIAGE_FX_POS);
			String name = xmr.getAttribute(vecElem, ATTR_FOLIAGE_FX_POS);
			dataTreeModel.addToCategory(FoliageDataCategory.FXPositions, new FoliageVector(this, name));
		}

		for (Element dropElem : xmr.getTags(elem, TAG_FOLIAGE_DROP)) {
			FoliageDrop drop = FoliageDrop.read(this, xmr, dropElem);
			dataTreeModel.addToCategory(FoliageDataCategory.Drops, drop);
		}
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag tag;

		switch (type) {
			case Bush:
				tag = xmw.createTag(TAG_BUSH, false);
				break;
			case Tree:
				tag = xmw.createTag(TAG_TREE, false);
				break;
			default:
				throw new UnsupportedOperationException("Can't serialize foliage of type: " + type);
		}

		if (overrideName.get() != null && !overrideName.get().isEmpty())
			xmw.addAttribute(tag, ATTR_NAME, overrideName.get());

		xmw.addBoolean(tag, ATTR_HAS_CALLBACK, hasCallback.get());

		if (isStarTree.get())
			xmw.addBoolean(tag, ATTR_TREE_STAR, isStarTree.get());

		if (colliderName.get() != null && !colliderName.get().isEmpty())
			xmw.addAttribute(tag, ATTR_FOLIAGE_COL, colliderName.get());

		if (bombPosName.get() != null && !bombPosName.get().isEmpty())
			xmw.addAttribute(tag, ATTR_TREE_BOMB, bombPosName.get());

		xmw.openTag(tag);

		switch (type) {
			case Bush:
				for (Object obj : dataTreeModel.getObjectsInCategory(FoliageDataCategory.BushModels)) {
					if (obj == null)
						continue;
					String name = ((FoliageModel) obj).modelName.get();
					if (name == null || name.isEmpty())
						continue;

					XmlTag mdlTag = xmw.createTag(TAG_MDL_BUSH, true);
					xmw.addAttribute(mdlTag, ATTR_MDL_NAME, name);
					xmw.printTag(mdlTag);
				}
				break;

			case Tree:
				for (Object obj : dataTreeModel.getObjectsInCategory(FoliageDataCategory.TrunkModels)) {
					if (obj == null)
						continue;
					String name = ((FoliageModel) obj).modelName.get();
					if (name == null || name.isEmpty())
						continue;

					XmlTag mdlTag = xmw.createTag(TAG_MDL_TRUNK, true);
					xmw.addAttribute(mdlTag, ATTR_MDL_NAME, name);
					xmw.printTag(mdlTag);
				}

				for (Object obj : dataTreeModel.getObjectsInCategory(FoliageDataCategory.LeafModels)) {
					if (obj == null)
						continue;
					String name = ((FoliageModel) obj).modelName.get();
					if (name == null || name.isEmpty())
						continue;

					XmlTag mdlTag = xmw.createTag(TAG_MDL_LEAF, true);
					xmw.addAttribute(mdlTag, ATTR_MDL_NAME, name);
					xmw.printTag(mdlTag);
				}
				break;

			default:
				throw new UnsupportedOperationException("Can't serialize foliage of type: " + type);
		}

		for (Object obj : dataTreeModel.getObjectsInCategory(FoliageDataCategory.FXPositions)) {
			if (obj == null)
				continue;
			String name = ((FoliageVector) obj).modelName.get();
			if (name == null || name.isEmpty())
				continue;

			XmlTag vecTag = xmw.createTag(TAG_FOLIAGE_FX, true);
			xmw.addAttribute(vecTag, ATTR_FOLIAGE_FX_POS, name);
			xmw.printTag(vecTag);
		}

		for (Object obj : dataTreeModel.getObjectsInCategory(FoliageDataCategory.Drops)) {
			if (obj == null)
				continue;
			((FoliageDrop) obj).toXML(xmw);
		}

		xmw.closeTag(tag);
	}

	@Override
	public String toString()
	{
		String name = getName();
		return (name != null) ? name : "Invalid " + type;
	}

	public String getName()
	{
		if (overrideName.get() == null || overrideName.get().isEmpty()) {
			if (colliderName.get() == null || colliderName.get().isEmpty())
				return null;

			return type + "_" + colliderName.get().replaceAll("\\s+", "_");
		}
		return overrideName.get().replaceAll("\\s+", "_");
	}
}
