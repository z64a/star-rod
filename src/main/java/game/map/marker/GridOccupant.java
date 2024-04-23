package game.map.marker;

import game.map.editor.commands.fields.EditableField;
import game.map.editor.commands.fields.EditableField.EditableFieldFactory;
import util.identity.IdentityArrayList;

public class GridOccupant
{
	public static enum OccupantType
	{
		Block(1),
		Obstruction(2);

		public final int id;

		private OccupantType(int id)
		{
			this.id = id;
		}

		public static OccupantType getFromID(int id)
		{
			if (id == Block.id)
				return Block;
			else
				return Obstruction;
		}
	}

	public final IdentityArrayList<GridOccupant> parentList;

	public final EditableField<OccupantType> type;
	public final int posX;
	public final int posZ;

	public GridOccupant(IdentityArrayList<GridOccupant> parentList, int x, int z, int typeID)
	{
		this(parentList, x, z, OccupantType.getFromID(typeID));
	}

	public GridOccupant(IdentityArrayList<GridOccupant> parentList, int x, int z, OccupantType otype)
	{
		this.parentList = parentList;

		type = EditableFieldFactory.create(OccupantType.Block).setName("Set Grid Occupant").build();

		posX = x;
		posZ = z;

		this.type.set(otype);
	}

	public GridOccupant deepCopy(IdentityArrayList<GridOccupant> copyParentList)
	{
		return new GridOccupant(copyParentList, posX, posZ, type.get());
	}
}
