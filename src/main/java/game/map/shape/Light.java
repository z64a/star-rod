package game.map.shape;

import static app.IconResource.ICON_DOWN;
import static app.IconResource.ICON_UP;

import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Toolkit;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;

import game.map.editor.MapEditor;
import game.map.editor.commands.AbstractCommand;
import game.map.editor.ui.SwingGUI;
import game.map.scripts.LightingPanel;
import game.map.shape.LightSet.DeleteLight;
import game.map.shape.LightSet.MoveLightDown;
import game.map.shape.LightSet.MoveLightUp;
import net.miginfocom.swing.MigLayout;
import util.MathUtil;
import util.ui.IntVectorPanel;

public class Light
{
	public transient final LightSet parent;
	public transient LightPanel panel;

	public int[] color = new int[3];
	public int[] dir = new int[3];

	public Light(LightSet parent)
	{
		this.parent = parent;
		panel = new LightPanel(this);
	}

	public Light(LightSet parent, ByteBuffer bb)
	{
		int packed = bb.getInt();
		int duplicate = bb.getInt();
		int direction = bb.getInt();
		int zero = bb.getInt();

		assert (packed == duplicate);
		assert ((packed & 0xFF) == 0);
		assert ((direction & 0xFF) == 0);
		assert (zero == 0);

		color[0] = (packed >> 24) & 0xFF;
		color[1] = (packed >> 16) & 0xFF;
		color[2] = (packed >> 8) & 0xFF;

		dir[0] = (direction >> 24) & 0xFF;
		dir[1] = (direction >> 16) & 0xFF;
		dir[2] = (direction >> 8) & 0xFF;

		this.parent = parent;
		panel = new LightPanel(this);
	}

	public Light(LightSet parent, int[] data)
	{
		color[0] = (data[0] >> 24) & 0xFF;
		color[1] = (data[0] >> 16) & 0xFF;
		color[2] = (data[0] >> 8) & 0xFF;

		// these are SIGNED 8-bit numbers
		dir[0] = (data[2] >> 24);
		dir[1] = (data[2] << 8) >> 24;
		dir[2] = (data[2] << 16) >> 24;

		this.parent = parent;
		panel = new LightPanel(this);
	}

	public void write(RandomAccessFile raf) throws IOException
	{
		int packedColor = ((color[0] & 0xFF) << 24) | ((color[1] & 0xFF) << 16) | ((color[2] & 0xFF) << 8);
		int packedDirection = ((dir[0] & 0xFF) << 24) | ((dir[1] & 0xFF) << 16) | ((dir[2] & 0xFF) << 8);

		raf.writeInt(packedColor);
		raf.writeInt(packedColor);
		raf.writeInt(packedDirection);
		raf.writeInt(0);
	}

	public int[] getPacked()
	{
		int packedColor = ((color[0] & 0xFF) << 24) | ((color[1] & 0xFF) << 16) | ((color[2] & 0xFF) << 8);
		int packedDirection = ((dir[0] & 0xFF) << 24) | ((dir[1] & 0xFF) << 16) | ((dir[2] & 0xFF) << 8);

		return new int[] { packedColor, packedColor, packedDirection };
	}

	public Light deepCopy(LightSet parent)
	{
		return new Light(parent, getPacked());
	}

	public LightPanel getPanel()
	{ return panel; }

	public static class LightPanel extends JPanel
	{
		private final Light light;

		private IntVectorPanel colorPanel;
		private IntVectorPanel dirPanel;

		private final JLabel indexLabel;
		private final JButton moveUpButton;
		private final JButton moveDownButton;
		private final JButton deleteButton;

		private LightPanel(Light light)
		{
			this.light = light;

			colorPanel = new IntVectorPanel(3, (index, newValue) -> {
				MapEditor.execute(new SetLightColorChannel(light, index, newValue));
			});

			dirPanel = new IntVectorPanel(3, (index, newValue) -> {
				MapEditor.execute(new SetLightDirectionComponent(light, index, newValue));
			});

			setLayout(new MigLayout("fill, ins 0, gap 0"));

			colorPanel.setValues(light.color);
			dirPanel.setValues(light.dir);

			moveUpButton = new JButton(ICON_UP);
			moveUpButton.setMargin(new Insets(1, 2, 1, 2));
			moveUpButton.addActionListener((e) -> {
				MapEditor.execute(new MoveLightUp(light));
			});

			moveDownButton = new JButton(ICON_DOWN);
			moveDownButton.setMargin(new Insets(1, 2, 1, 2));
			moveDownButton.addActionListener((e) -> {
				MapEditor.execute(new MoveLightDown(light));
			});

			deleteButton = new JButton("X");
			Font f = deleteButton.getFont();
			deleteButton.setFont(f.deriveFont(f.getStyle() | Font.BOLD));
			deleteButton.setMargin(new Insets(1, 2, 1, 2));
			deleteButton.addActionListener((e) -> {
				MapEditor.execute(new DeleteLight(light));
			});

			JButton chooseColorButton = new JButton("Choose");
			chooseColorButton.addActionListener((e) -> {
				SwingGUI.instance().notify_OpenDialog();
				Color c = new Color(light.color[0], light.color[1], light.color[2]);
				c = JColorChooser.showDialog(null, "Choose Light Color", c);
				SwingGUI.instance().notify_CloseDialog();

				if (c != null)
					MapEditor.execute(new SetLightColor(light, c.getRed(), c.getGreen(), c.getBlue()));
			});

			JButton normalizeButton = new JButton("Normalize");
			normalizeButton.addActionListener((e) -> {
				int x = light.dir[0];
				int y = light.dir[1];
				int z = light.dir[2];
				double norm = Math.sqrt(x * x + y * y + z * z);
				if (norm < MathUtil.SMALL_NUMBER) {
					Toolkit.getDefaultToolkit().beep();
					return; // really cant be less than 1 if its valid
				}

				double d = 127 / norm;
				int nx = (int) (x * d);
				int ny = (int) (y * d);
				int nz = (int) (z * d);
				MapEditor.execute(new SetLightDirection(light, nx, ny, nz));
			});

			indexLabel = new JLabel("???");

			setLayout(new MigLayout("fillx, ins 0", "[grow][push][grow]"));

			add(indexLabel, "growx");
			add(new JPanel(), "pushx");
			add(moveDownButton, "growx, sg but, split 3");
			add(moveUpButton, "growx, sg but");
			add(deleteButton, "growx, sg but, wrap");

			add(new JLabel("Color"), "growx");
			add(colorPanel, "growx");
			add(chooseColorButton, "growx, wrap");

			add(new JLabel("Direction"), "growx");
			add(dirPanel, "growx");
			add(normalizeButton, "growx, wrap");

			add(new JPanel(), "span, h 4!"); // gap
		}

		public void updateFields()
		{
			colorPanel.setValues(light.color);
			dirPanel.setValues(light.dir);
			indexLabel.setText(String.format("Light %X", light.getListIndex()));
		}
	}

	// find list position based on reference equality
	public int getListIndex()
	{
		int i = 0;
		for (Light light : parent) {
			if (light == this)
				return i;
			i++;
		}
		return -1;
	}

	public static final class SetLightColor extends AbstractCommand
	{
		private final Light light;
		private final int[] oldValues;
		private final int[] newValues;

		public SetLightColor(Light light, int R, int G, int B)
		{
			super("Set Light Color");
			this.light = light;
			oldValues = new int[] { light.color[0], light.color[1], light.color[2] };
			newValues = new int[] { R, G, B };
		}

		@Override
		public boolean shouldExec()
		{
			return oldValues[0] != newValues[0] || oldValues[1] != newValues[1] || oldValues[2] != newValues[2];
		}

		@Override
		public void exec()
		{
			super.exec();
			light.color[0] = newValues[0];
			light.color[1] = newValues[1];
			light.color[2] = newValues[2];
			LightingPanel.instance().setLights(light.parent, false);
		}

		@Override
		public void undo()
		{
			super.undo();
			light.color[0] = oldValues[0];
			light.color[1] = oldValues[1];
			light.color[2] = oldValues[2];
			LightingPanel.instance().setLights(light.parent, false);
		}
	}

	public static final class SetLightColorChannel extends AbstractCommand
	{
		private final Light light;
		private final int index;
		private final int oldValue;
		private final int newValue;

		public SetLightColorChannel(Light light, int index, int val)
		{
			super("Set Light Color");
			this.light = light;
			this.index = index;
			oldValue = light.color[index];
			newValue = val;
		}

		@Override
		public boolean shouldExec()
		{
			return oldValue != newValue;
		}

		@Override
		public void exec()
		{
			super.exec();
			light.color[index] = newValue;
			LightingPanel.instance().setLights(light.parent, false);
		}

		@Override
		public void undo()
		{
			super.undo();
			light.color[index] = oldValue;
			LightingPanel.instance().setLights(light.parent, false);
		}
	}

	public static final class SetLightDirection extends AbstractCommand
	{
		private final Light light;
		private final int[] oldValues;
		private final int[] newValues;

		public SetLightDirection(Light light, int x, int y, int z)
		{
			super("Set Light Direction");
			this.light = light;
			oldValues = new int[] { light.dir[0], light.dir[1], light.dir[2] };
			newValues = new int[] { x, y, z };
		}

		@Override
		public boolean shouldExec()
		{
			return oldValues[0] != newValues[0] || oldValues[1] != newValues[1] || oldValues[2] != newValues[2];
		}

		@Override
		public void exec()
		{
			super.exec();
			light.dir[0] = newValues[0];
			light.dir[1] = newValues[1];
			light.dir[2] = newValues[2];
			LightingPanel.instance().setLights(light.parent, false);
		}

		@Override
		public void undo()
		{
			super.undo();
			light.dir[0] = oldValues[0];
			light.dir[1] = oldValues[1];
			light.dir[2] = oldValues[2];
			LightingPanel.instance().setLights(light.parent, false);
		}
	}

	public static final class SetLightDirectionComponent extends AbstractCommand
	{
		private final Light light;
		private final int index;
		private final int oldValue;
		private final int newValue;

		public SetLightDirectionComponent(Light light, int index, int val)
		{
			super("Set Light Direction");
			this.light = light;
			this.index = index;
			oldValue = light.dir[index];
			newValue = val;
		}

		@Override
		public boolean shouldExec()
		{
			return oldValue != newValue;
		}

		@Override
		public void exec()
		{
			super.exec();
			light.dir[index] = newValue;
			LightingPanel.instance().setLights(light.parent, false);
		}

		@Override
		public void undo()
		{
			super.undo();
			light.dir[index] = oldValue;
			LightingPanel.instance().setLights(light.parent, false);
		}
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(Arrays.hashCode(this.color), Arrays.hashCode(this.dir));
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Light other = (Light) obj;
		if (!Arrays.equals(this.color, other.color))
			return false;
		if (!Arrays.equals(this.dir, other.dir))
			return false;
		return true;
	}
}
