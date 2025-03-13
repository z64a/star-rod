package common.commands;

import java.util.ArrayList;

public class CommandBatch extends AbstractCommand
{
	private ArrayList<AbstractCommand> commands;
	private final boolean hasName;
	private boolean modifiesData = true;

	public CommandBatch()
	{
		this("Command Batch", false);
	}

	public CommandBatch(String name)
	{
		this(name, true);
	}

	private CommandBatch(String name, boolean hasName)
	{
		super(name, !hasName);
		commands = new ArrayList<>();
		this.hasName = hasName;
	}

	public void addCommand(AbstractCommand c)
	{
		commands.add(c);

		if (hasName)
			c.silence();
	}

	public void setModifiesData(boolean value)
	{
		modifiesData = value;
	}

	@Override
	public boolean modifiesData()
	{
		return modifiesData;
	}

	@Override
	public boolean shouldExec()
	{
		return commands.size() > 0;
	}

	@Override
	public void exec()
	{
		super.exec();
		for (AbstractCommand cmd : commands) {
			if (!cmd.shouldExec())
				continue;
			if (cmd.getState() != AbstractCommand.ExecState.EXECUTED)
				cmd.exec();
		}
	}

	@Override
	public void undo()
	{
		super.undo();
		for (int i = commands.size() - 1; i >= 0; i--) {
			AbstractCommand cmd = commands.get(i);
			if (!cmd.shouldExec())
				continue;
			cmd.undo();
		}
	}
}
