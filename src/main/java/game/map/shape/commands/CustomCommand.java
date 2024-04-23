package game.map.shape.commands;

import javax.swing.JPanel;

import game.map.editor.commands.AbstractCommand;
import game.map.mesh.AbstractMesh;
import game.map.shape.Model;
import net.miginfocom.swing.MigLayout;
import util.ui.HexTextField;

public class CustomCommand extends DisplayCommand
{
	private int r, s;

	public CustomCommand(AbstractMesh parentMesh)
	{
		this(parentMesh, 0, 0);
	}

	public CustomCommand(AbstractMesh parentMesh, int r, int s)
	{
		super(parentMesh);
		this.r = r;
		this.s = s;
	}

	@Override
	public String toString()
	{
		return String.format("F3DEX2: %08X %08X", r, s);
	}

	@Override
	public CmdType getType()
	{ return CmdType.Custom; }

	@Override
	public int[] getF3DEX2Command()
	{ return new int[] { r, s }; }

	@Override
	public DisplayCommand deepCopy()
	{
		return new CustomCommand(parentMesh, r, s);
	}

	public final static class CustomCommandPanel extends JPanel
	{
		private int r, s;

		public CustomCommandPanel(CustomCommand cmd)
		{
			HexTextField rText = new HexTextField((value) -> {
				r = value;
			});
			HexTextField sText = new HexTextField((value) -> {
				s = value;
			});
			rText.setValue(r);
			sText.setValue(s);
			setLayout(new MigLayout("fill"));
			add(rText, "growx, sg fields, split 2");
			add(sText, "growx, sg fields");
		}
	}

	public final class SetValues extends AbstractCommand
	{
		private final Model mdl;
		private final int oldr, olds;
		private final int newr, news;

		public SetValues(Model mdl, CustomCommandPanel panel)
		{
			super("Set Custom Command");
			this.mdl = mdl;
			oldr = r;
			olds = s;
			this.newr = panel.r;
			this.news = panel.s;
		}

		@Override
		public boolean shouldExec()
		{
			return (oldr != newr) || (olds != news);
		}

		@Override
		public void exec()
		{
			super.exec();
			r = newr;
			s = news;
			mdl.displayListChanged();
		}

		@Override
		public void undo()
		{
			super.undo();
			r = oldr;
			s = olds;
			mdl.displayListChanged();
		}
	}
}
