package game.map.editor.ui.dialogs;

import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;
import util.ui.IntTextField;

public class SetPositionPanel extends JPanel
{
	private final IntTextField fieldX;
	private final IntTextField fieldY;
	private final IntTextField fieldZ;

	public SetPositionPanel(int x, int y, int z)
	{
		fieldX = new IntTextField((v) -> {});
		fieldY = new IntTextField((v) -> {});
		fieldZ = new IntTextField((v) -> {});

		fieldX.setValue(x);
		fieldY.setValue(y);
		fieldZ.setValue(z);

		setLayout(new MigLayout("fill"));

		add(fieldX, "growx, sg coord,");
		add(fieldY, "growx, sg coord");
		add(fieldZ, "growx, sg coord");
	}

	public int[] getVector()
	{ return new int[] {
			Integer.decode(fieldX.getText()),
			Integer.decode(fieldY.getText()),
			Integer.decode(fieldZ.getText())
	}; }
}
