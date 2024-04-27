package game.globals.editor;

public abstract class GlobalsRecord implements GlobalsListable
{
	public static final int DELETED = -100;

	public int listIndex;
	public boolean modified;

	@Override
	public final int getIndex()
	{
		return listIndex;
	}

	public final void setIndex(int index)
	{
		listIndex = index;
	}

	public final boolean getModified()
	{
		return modified;
	}

	public final void setModified(boolean value)
	{
		modified = value;
	}

	public abstract String getIdentifier(); // assumed to be unqiue

	@Override
	public abstract String getFilterableString();

	@Override
	public abstract boolean canDeleteFromList();
}
