package game.map.editor.geometry.primitive;

import java.awt.Window;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;

import app.SwingUtils;
import game.map.editor.ui.dialogs.GeneratePrimitiveOptionsDialog;
import game.map.mesh.Triangle;
import game.map.mesh.Vertex;
import game.map.shape.TriangleBatch;
import game.map.shape.UV;
import util.ui.LabeledIntegerSpinner;

public class SpiralStairGenerator extends ShapeGenerator
{
	private final Window parent;

	private final LabeledIntegerSpinner innerRadiusSpinner;
	private final LabeledIntegerSpinner outerRadiusSpinner;
	private final LabeledIntegerSpinner divisionsSpinner;
	private final LabeledIntegerSpinner angleSpinner;
	private final LabeledIntegerSpinner riseSpinner;

	private final JCheckBox proportionalUVsCheckbox;

	private final JLabel sideLabel;
	private final JComboBox<SideStyle> sideStyleComboBox;
	private final LabeledIntegerSpinner sideHeightSpinner;
	private final JCheckBox makeBottomCheckbox;

	private enum SideStyle
	{
		// @formatter:off
		NONE	("None"),
		DROP	("Drop to Floor"),
		//		RECT	("Rectangular"),
		ANGLED	("Angled");
		// @formatter:on

		private final String name;

		private SideStyle(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	public SpiralStairGenerator(Window parent)
	{
		super(Primitive.SPIRAL_STAIR);
		this.parent = parent;

		riseSpinner = new LabeledIntegerSpinner("Step Rise", 1, 500, 10);
		innerRadiusSpinner = new LabeledIntegerSpinner("Inner Radius", 0, 5000, 100);
		outerRadiusSpinner = new LabeledIntegerSpinner("Outer Radius", 10, 5000, 200);
		divisionsSpinner = new LabeledIntegerSpinner("Steps", 2, 256, 24);
		angleSpinner = new LabeledIntegerSpinner("Arc Angle", 15, 1440, 360);

		proportionalUVsCheckbox = new JCheckBox(" Proportional UVs");
		proportionalUVsCheckbox.setSelected(true);
		SwingUtils.setFontSize(proportionalUVsCheckbox, 12);

		sideLabel = new JLabel("Side Style");
		sideLabel.setFont(sideLabel.getFont().deriveFont(12f));

		sideStyleComboBox = new JComboBox<>(SideStyle.values());
		sideStyleComboBox.setSelectedItem(SideStyle.NONE);
		sideStyleComboBox.addActionListener((e) -> showOptions());

		sideHeightSpinner = new LabeledIntegerSpinner("Side Height", 0, 500, 20);

		makeBottomCheckbox = new JCheckBox(" Create Bottom Faces");
		makeBottomCheckbox.setSelected(false);
		SwingUtils.setFontSize(makeBottomCheckbox, 12);
	}

	@Override
	public void addFields(GeneratePrimitiveOptionsDialog panel)
	{
		panel.addSpinner(riseSpinner);
		panel.addSpinner(innerRadiusSpinner);
		panel.addSpinner(outerRadiusSpinner);
		panel.addSpinner(divisionsSpinner);
		panel.addSpinner(angleSpinner);
		panel.addCheckBox(proportionalUVsCheckbox);

		panel.add(sideLabel, "w 40%, split 2");
		panel.addComboBox(sideStyleComboBox);
		panel.addSpinner(sideHeightSpinner);

		panel.addCheckBox(makeBottomCheckbox);
	}

	@Override
	public void setVisible(boolean b)
	{
		riseSpinner.setVisible(b);
		innerRadiusSpinner.setVisible(b);
		outerRadiusSpinner.setVisible(b);
		divisionsSpinner.setVisible(b);
		angleSpinner.setVisible(b);
		proportionalUVsCheckbox.setVisible(b);

		sideLabel.setVisible(b);
		sideStyleComboBox.setVisible(b);

		if (b)
			showOptions();
		else {
			sideHeightSpinner.setVisible(false);
			makeBottomCheckbox.setVisible(false);
		}
	}

	private void showOptions()
	{
		sideHeightSpinner.setVisible(false);
		makeBottomCheckbox.setVisible(false);

		SideStyle style = (SideStyle) sideStyleComboBox.getSelectedItem();
		switch (style) {
			case NONE:
				break;

			case DROP:
				break;

			case ANGLED:
				sideHeightSpinner.setVisible(true);
				makeBottomCheckbox.setVisible(true);
				break;
		}

		parent.pack();
	}

	@Override
	public TriangleBatch generate(int centerX, int centerY, int centerZ)
	{
		int steps = divisionsSpinner.getValue();
		int innerRadius = innerRadiusSpinner.getValue();
		int outerRadius = outerRadiusSpinner.getValue();
		int angle = angleSpinner.getValue();
		int stepRise = riseSpinner.getValue();
		boolean proportionalUVs = proportionalUVsCheckbox.isSelected();

		SideStyle sideStyle = (SideStyle) sideStyleComboBox.getSelectedItem();
		int sideHeight = sideHeightSpinner.getValue();
		boolean makeBottom = makeBottomCheckbox.isSelected();

		if (innerRadius == outerRadius)
			outerRadius = innerRadius + 50;

		return generate(steps, stepRise, innerRadius, outerRadius, angle, proportionalUVs,
			sideStyle, sideHeight, makeBottom,
			centerX, centerY, centerZ);
	}

	private static TriangleBatch generate(
		int steps, int stepRise, int innerR, int outerR, int angle, boolean proportionalUVs,
		SideStyle sideStyle, int sideHeight, boolean makeBottom,
		int centerX, int centerY, int centerZ)
	{
		int N = (2 * steps) + 1;
		double angleRad = Math.toRadians(angle);

		float uvScale = UV_SCALE / (outerR - innerR);

		int uOffset = 0;
		int vOffset = (UV_SCALE * 3) / 2;

		int overflow;
		if (proportionalUVs) {
			double arc = (outerR - innerR) * angleRad;
			int U = (int) Math.round(uvScale * (steps * stepRise + arc));
			overflow = U - Short.MAX_VALUE;
		}
		else
			overflow = (2 * UV_SCALE * steps) - Short.MAX_VALUE;

		if (overflow > 0) {
			uOffset = -UV_SCALE * (1 + overflow / UV_SCALE);
			uOffset = uOffset < Short.MIN_VALUE ? -UV_SCALE * (Short.MIN_VALUE / UV_SCALE) : uOffset;
		}

		Vertex[] ringI = new Vertex[N];
		Vertex[] ringO = new Vertex[N];

		for (int i = 0; i < N; i++) {
			int H = ((i + 1) / 2) * stepRise;
			int j = (i / 2);

			double theta = angleRad * j / steps;
			double X = Math.sin(theta);
			double Z = Math.cos(theta);

			ringI[i] = new Vertex(
				centerX + Math.round(innerR * X),
				centerY + H,
				centerZ - Math.round(innerR * Z));

			ringO[i] = new Vertex(
				centerX + Math.round(outerR * X),
				centerY + H,
				centerZ - Math.round(outerR * Z));

			if (proportionalUVs) {
				// rescale texture coordinates to fit staircase width
				double arc = (outerR - innerR) * theta;
				int U = (int) Math.round(uvScale * (H + arc));
				ringI[i].uv = new UV(U + uOffset, 0);
				ringO[i].uv = new UV(U + uOffset, UV_SCALE);
			}
			else {
				int U = i * UV_SCALE;
				ringI[i].uv = new UV(U + uOffset, 0);
				ringO[i].uv = new UV(U + uOffset, UV_SCALE);
			}
		}

		TriangleBatch batch = new TriangleBatch(null);

		for (int i = 0; i < (N - 1); i++) {
			batch.triangles.add(new Triangle(ringO[i], ringI[i], ringI[i + 1]));
			batch.triangles.add(new Triangle(ringO[i], ringI[i + 1], ringO[i + 1]));
		}

		switch (sideStyle) {
			case NONE:
				break;

			case DROP: {
				Vertex[] upperI = new Vertex[N];
				Vertex[] upperO = new Vertex[N];

				for (int i = 0; i < N; i++) {
					upperI[i] = ringI[i].deepCopy();
					upperO[i] = ringO[i].deepCopy();

					int H = ((i + 1) / 2) * stepRise;
					double theta = angleRad * (i / 2) / steps;
					double arc = (outerR - innerR) * theta;
					int U = (int) Math.round(uvScale * arc);

					upperI[i].uv = new UV(U + uOffset, uvScale * H + vOffset);
					upperO[i].uv = new UV(U + uOffset, uvScale * H + vOffset);
				}

				Vertex[] lowerI = new Vertex[N];
				Vertex[] lowerO = new Vertex[N];

				for (int i = 0; i < N; i++) {
					lowerI[i] = new Vertex(upperI[i].getCurrentX(), centerY, upperI[i].getCurrentZ());
					lowerO[i] = new Vertex(upperO[i].getCurrentX(), centerY, upperO[i].getCurrentZ());

					lowerI[i].uv = new UV(upperI[i].uv.getU(), vOffset);
					lowerO[i].uv = new UV(upperO[i].uv.getU(), vOffset);
				}

				for (int i = 0; i < (N - 1); i++) {
					batch.triangles.add(new Triangle(lowerI[i], upperI[i + 1], upperI[i]));
					batch.triangles.add(new Triangle(lowerI[i], lowerI[i + 1], upperI[i + 1]));

					batch.triangles.add(new Triangle(lowerO[i], upperO[i], upperO[i + 1]));
					batch.triangles.add(new Triangle(lowerO[i], upperO[i + 1], lowerO[i + 1]));
				}
			}
				break;

			case ANGLED: {
				Vertex[] upperI = new Vertex[N];
				Vertex[] upperO = new Vertex[N];

				for (int i = 0; i < N; i++) {
					upperI[i] = ringI[i].deepCopy();
					upperO[i] = ringO[i].deepCopy();

					int H = ((i + 1) / 2) * stepRise;
					double theta = angleRad * (i / 2) / steps;
					double arc = (outerR - innerR) * theta;
					int U = (int) Math.round(uvScale * arc);

					upperI[i].uv = new UV(U + uOffset, uvScale * H + vOffset);
					upperO[i].uv = new UV(U + uOffset, uvScale * H + vOffset);
				}

				for (int i = 0; i < (N - 2); i += 2) {
					batch.triangles.add(new Triangle(upperI[i + 1], upperI[i], upperI[i + 2]));
					batch.triangles.add(new Triangle(upperO[i + 2], upperO[i], upperO[i + 1]));
				}

				Vertex[] lowerI = new Vertex[steps + 1];
				Vertex[] lowerO = new Vertex[steps + 1];
				for (int i = 0; i < steps + 1; i++) {
					lowerI[i] = new Vertex(
						upperI[2 * i].getCurrentX(),
						upperI[2 * i].getCurrentY() - sideHeight,
						upperI[2 * i].getCurrentZ());
					lowerO[i] = new Vertex(
						upperO[2 * i].getCurrentX(),
						upperO[2 * i].getCurrentY() - sideHeight,
						upperO[2 * i].getCurrentZ());

					lowerI[i].uv = new UV(upperI[2 * i].uv.getU(), upperI[2 * i].uv.getV() - uvScale * sideHeight);
					lowerO[i].uv = new UV(upperO[2 * i].uv.getU(), upperO[2 * i].uv.getV() - uvScale * sideHeight);
				}

				if (sideHeight > 0) {
					for (int i = 0; i < steps; i++) {
						batch.triangles.add(new Triangle(lowerI[i + 1], upperI[2 * i], lowerI[i]));
						batch.triangles.add(new Triangle(upperI[2 * (i + 1)], upperI[2 * i], lowerI[i + 1]));

						batch.triangles.add(new Triangle(upperO[2 * i], lowerO[i + 1], lowerO[i]));
						batch.triangles.add(new Triangle(upperO[2 * i], upperO[2 * (i + 1)], lowerO[i + 1]));
					}
				}

				if (makeBottom) {
					Vertex[] bottomI = new Vertex[steps + 1];
					Vertex[] bottomO = new Vertex[steps + 1];

					for (int i = 0; i < steps + 1; i++) {
						bottomI[i] = lowerI[i].deepCopy();
						bottomO[i] = lowerO[i].deepCopy();

						if (proportionalUVs) {
							// rescale texture coordinates to fit staircase width
							double arc = (outerR - innerR) * (angleRad / steps);
							int U = (int) Math.round(uvScale * i * (arc + stepRise));

							bottomI[i].uv = new UV(U + uOffset, uvScale * (outerR - innerR) - vOffset);
							bottomO[i].uv = new UV(U + uOffset, -vOffset);
						}
						else {
							int U = i * UV_SCALE;
							bottomI[i].uv = new UV(U + uOffset, -vOffset);
							bottomO[i].uv = new UV(U + uOffset, UV_SCALE - vOffset);
						}
					}

					for (int i = 0; i < steps; i++) {
						batch.triangles.add(new Triangle(bottomI[i], bottomO[i], bottomI[i + 1]));
						batch.triangles.add(new Triangle(bottomI[i + 1], bottomO[i], bottomO[i + 1]));
					}
				}
			}
				break;
		}

		return batch;
	}
}
