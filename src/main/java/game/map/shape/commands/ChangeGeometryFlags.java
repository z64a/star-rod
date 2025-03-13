package game.map.shape.commands;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import common.commands.AbstractCommand;
import game.map.mesh.AbstractMesh;
import game.map.shape.Model;
import net.miginfocom.swing.MigLayout;

public abstract class ChangeGeometryFlags extends DisplayCommand
{
	protected transient boolean cullBack; // 0x00000400 = G_CULL_BACK
	protected transient boolean useLighting; // 0x00020000 = G_LIGHTING
	protected transient boolean smoothShading; // 0x00200000 = G_SHADING_SMOOTH

	// Unused:
	// 0x00000200 = G_CULL_FRONT
	// 0x00000600 = G_CULL_BOTH
	// 0x00010000 = G_FOG
	// 0x00040000 = G_TEXTURE_GEN
	// 0x00080000 = G_TEXTURE_GEN_LINEAR

	public ChangeGeometryFlags(AbstractMesh parentMesh)
	{
		super(parentMesh);
	}

	public static ChangeGeometryFlags getCommand(AbstractMesh parentMesh, int r, int s)
	{
		if (r == 0xD9FFFFFF)
			return new SetGeometryFlags(parentMesh, r, s);
		else
			return new ClearGeometryFlags(parentMesh, r, s);
	}

	protected void setFlags(int flags)
	{
		cullBack = (flags & 0x00000400) != 0;
		useLighting = (flags & 0x00020000) != 0;
		smoothShading = (flags & 0x00200000) != 0;
	}

	protected int getFlags()
	{
		int s = 0;
		if (cullBack)
			s |= 0x00000400;
		if (useLighting)
			s |= 0x00020000;
		if (smoothShading)
			s |= 0x00200000;
		return s;
	}

	public final static class GeometryFlagsPanel extends JPanel
	{
		private final JCheckBox cullBackCheckBox;
		private final JCheckBox useLightingCheckBox;
		private final JCheckBox smoothShadingCheckBox;

		public GeometryFlagsPanel(ChangeGeometryFlags cmd)
		{
			cullBackCheckBox = new JCheckBox();
			cullBackCheckBox.setSelected(cmd.cullBack);

			useLightingCheckBox = new JCheckBox();
			useLightingCheckBox.setSelected(cmd.useLighting);

			smoothShadingCheckBox = new JCheckBox();
			smoothShadingCheckBox.setSelected(cmd.smoothShading);

			setLayout(new MigLayout("fill, wrap 2"));

			add(cullBackCheckBox);
			add(new JLabel("Cull Back Faces"), "push");

			add(useLightingCheckBox);
			add(new JLabel("Enable Lighting"), "push");

			add(smoothShadingCheckBox);
			add(new JLabel("Use Smooth Shading"), "push");
		}
	}

	public final class SetFlags extends AbstractCommand
	{
		private final Model mdl;
		private final boolean oldCullBack, oldUseLight, oldSmoothShade;
		private final boolean newCullBack, newUseLight, newSmoothShade;

		public SetFlags(Model mdl, GeometryFlagsPanel panel)
		{
			super("Change Geometry Flags");
			this.mdl = mdl;

			oldCullBack = cullBack;
			oldUseLight = useLighting;
			oldSmoothShade = smoothShading;

			newCullBack = panel.cullBackCheckBox.isSelected();
			newUseLight = panel.useLightingCheckBox.isSelected();
			newSmoothShade = panel.smoothShadingCheckBox.isSelected();
		}

		@Override
		public boolean shouldExec()
		{
			return (oldCullBack != newCullBack) ||
				(oldUseLight != newUseLight) ||
				(oldSmoothShade != newSmoothShade);
		}

		@Override
		public void exec()
		{
			super.exec();
			cullBack = newCullBack;
			useLighting = newUseLight;
			smoothShading = newSmoothShade;
			mdl.displayListChanged();
		}

		@Override
		public void undo()
		{
			super.undo();
			cullBack = oldCullBack;
			useLighting = oldUseLight;
			smoothShading = oldSmoothShade;
			mdl.displayListChanged();
		}
	}
}
