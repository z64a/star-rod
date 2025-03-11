package game.sprite.editor;

import javax.swing.JComboBox;

public class CommandComboBox<T> extends JComboBox<T>
{
	private int ignoreChanges;

	public void incrementIgnoreChanges()
	{
		ignoreChanges++;
		assert (ignoreChanges > 0);
	}

	public void decrementIgnoreChanges()
	{
		assert (ignoreChanges > 0);
		ignoreChanges--;
	}

	public boolean ignoreChanges()
	{
		return ignoreChanges > 0;
	}

	public boolean allowChanges()
	{
		return ignoreChanges == 0;
	}
}
