package util.ui;

import java.util.List;

import javax.swing.JCheckBox;
import javax.swing.JPanel;

import game.globals.editor.FlagSet;
import net.miginfocom.swing.MigLayout;

public class FlagEditorPanel extends JPanel
{
	private final JCheckBox[] checkBoxes;
	private final int[] flagBits;

	public static class Flag
	{
		public final int bits;
		public final String name;
		public final String desc;

		public Flag(int bits, String name)
		{
			this(bits, name, "");
		}

		public Flag(int bits, String name, String desc)
		{
			this.bits = bits;
			this.name = name;
			this.desc = desc;
		}
	}

	public FlagEditorPanel(FlagSet flags)
	{
		List<String> flagBitNames = flags.getAllDisp();
		List<Integer> flagBitValues = flags.getAllBits();
		int count = flagBitNames.size();

		checkBoxes = new JCheckBox[count];
		flagBits = new int[count];

		if (count < 10) {
			setLayout(new MigLayout("fillx, ins 8 0 8 8, wrap"));
		}
		else {
			setLayout(new MigLayout("fillx, ins 8 0 8 8, wrap 2"));
		}

		for (int i = 0; i < count; i++) {
			flagBits[i] = flagBitValues.get(i);
			checkBoxes[i] = new JCheckBox(" " + flagBitNames.get(i));

			add(checkBoxes[i], "growx, sg checkbox");
		}

		setValue(flags.getBits());
	}

	private void setValue(int value)
	{
		for (int i = 0; i < checkBoxes.length; i++) {
			if (checkBoxes[i].isSelected() && (value & flagBits[i]) == 0)
				checkBoxes[i].setSelected(false);

			if (!checkBoxes[i].isSelected() && (value & flagBits[i]) != 0)
				checkBoxes[i].setSelected(true);
		}
	}

	public int getValue()
	{
		int value = 0;

		for (int i = 0; i < checkBoxes.length; i++) {
			if (checkBoxes[i].isSelected()) {
				value |= flagBits[i];
			}
		}

		return value;
	}
}
