package game.globals.editor;

import util.ui.FilterListable;

public interface GlobalsListable extends FilterListable
{
	public int getIndex();

	@Override
	public String getFilterableString();

	public boolean canDeleteFromList();
}
