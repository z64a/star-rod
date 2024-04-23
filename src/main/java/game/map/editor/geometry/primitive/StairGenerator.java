package game.map.editor.geometry.primitive;

import java.awt.Window;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;

import game.map.editor.ui.dialogs.GeneratePrimitiveOptionsDialog;
import game.map.mesh.Triangle;
import game.map.mesh.Vertex;
import game.map.shape.TriangleBatch;
import game.map.shape.UV;
import util.ui.LabeledIntegerSpinner;

public class StairGenerator extends ShapeGenerator
{
	private final Window parent;

	private final LabeledIntegerSpinner widthSpinner;
	private final LabeledIntegerSpinner stepsSpinner;
	private final LabeledIntegerSpinner riseSpinner;
	private final LabeledIntegerSpinner depthSpinner;

	private final JCheckBox proportionalUVsCheckbox;

	private final JLabel sideLabel;
	private final JComboBox<SideStyle> sideStyleComboBox;
	private final LabeledIntegerSpinner sideHeightSpinner;
	private final JCheckBox makeBottomCheckbox;
	private final JCheckBox lessTrianglesCheckbox;

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

	public StairGenerator(Window parent)
	{
		super(Primitive.STAIR);
		this.parent = parent;

		widthSpinner = new LabeledIntegerSpinner("Width", 10, 5000, 100);
		stepsSpinner = new LabeledIntegerSpinner("Steps", 1, 500, 10);
		riseSpinner = new LabeledIntegerSpinner("Step Rise", 1, 500, 10);
		depthSpinner = new LabeledIntegerSpinner("Step Length", 1, 500, 20);

		proportionalUVsCheckbox = new JCheckBox(" Proportional UVs");
		proportionalUVsCheckbox.setSelected(true);
		proportionalUVsCheckbox.setFont(proportionalUVsCheckbox.getFont().deriveFont(12f));

		sideLabel = new JLabel("Side Style");
		sideLabel.setFont(sideLabel.getFont().deriveFont(12f));

		sideStyleComboBox = new JComboBox<>(SideStyle.values());
		sideStyleComboBox.setSelectedItem(SideStyle.NONE);
		sideStyleComboBox.addActionListener((e) -> showOptions());

		sideHeightSpinner = new LabeledIntegerSpinner("Side Height", 0, 500, 20);

		makeBottomCheckbox = new JCheckBox(" Create Bottom Faces");
		makeBottomCheckbox.setSelected(false);
		makeBottomCheckbox.setFont(makeBottomCheckbox.getFont().deriveFont(12f));

		lessTrianglesCheckbox = new JCheckBox(" Reduce Triangle Count");
		lessTrianglesCheckbox.setSelected(false);
		lessTrianglesCheckbox.setFont(makeBottomCheckbox.getFont().deriveFont(12f));
	}

	@Override
	public void addFields(GeneratePrimitiveOptionsDialog panel)
	{
		panel.addSpinner(widthSpinner);
		panel.addSpinner(stepsSpinner);
		panel.addSpinner(riseSpinner);
		panel.addSpinner(depthSpinner);
		panel.addCheckBox(proportionalUVsCheckbox);

		panel.add(sideLabel, "w 40%, split 2");
		panel.addComboBox(sideStyleComboBox);
		panel.addSpinner(sideHeightSpinner);

		panel.addCheckBox(makeBottomCheckbox);
		panel.addCheckBox(lessTrianglesCheckbox);
	}

	@Override
	public void setVisible(boolean b)
	{
		widthSpinner.setVisible(b);
		stepsSpinner.setVisible(b);
		riseSpinner.setVisible(b);
		depthSpinner.setVisible(b);
		proportionalUVsCheckbox.setVisible(b);

		sideLabel.setVisible(b);
		sideStyleComboBox.setVisible(b);

		if (b)
			showOptions();
		else {
			sideHeightSpinner.setVisible(false);
			makeBottomCheckbox.setVisible(false);
			lessTrianglesCheckbox.setVisible(false);
		}
	}

	private void showOptions()
	{
		sideHeightSpinner.setVisible(false);
		makeBottomCheckbox.setVisible(false);
		lessTrianglesCheckbox.setVisible(false);

		SideStyle style = (SideStyle) sideStyleComboBox.getSelectedItem();
		switch (style) {
			case NONE:
				break;

			case DROP:
				lessTrianglesCheckbox.setVisible(true);
				break;

			case ANGLED:
				sideHeightSpinner.setVisible(true);
				makeBottomCheckbox.setVisible(true);
				lessTrianglesCheckbox.setVisible(true);
				break;
		}

		parent.pack();
	}

	@Override
	public TriangleBatch generate(int centerX, int centerY, int centerZ)
	{
		int width = widthSpinner.getValue();
		int steps = stepsSpinner.getValue();
		int stepRise = riseSpinner.getValue();
		int stepDepth = depthSpinner.getValue();
		boolean proportionalUVs = proportionalUVsCheckbox.isSelected();
		boolean makeBottom = makeBottomCheckbox.isSelected();
		boolean lessTriangles = lessTrianglesCheckbox.isSelected();

		SideStyle sideStyle = (SideStyle) sideStyleComboBox.getSelectedItem();
		int sideHeight = sideHeightSpinner.getValue();

		return generate(width, steps, stepRise, stepDepth, proportionalUVs,
			sideStyle, sideHeight, makeBottom, lessTriangles,
			centerX, centerY, centerZ);
	}

	private static TriangleBatch generate(
		int width, int steps, int stepRise, int stepDepth, boolean proportionalUVs,
		SideStyle sideStyle, int sideHeight, boolean makeBottom, boolean lessTriangles,
		int centerX, int centerY, int centerZ)
	{
		int N = (2 * steps) + 1;
		Vertex[] topR = new Vertex[N];
		Vertex[] topL = new Vertex[N];

		int R = Math.round(width / 2.0f);
		float uvScale = UV_SCALE / width;
		int vOffset = (UV_SCALE * 3) / 2;

		for (int i = 0; i < N; i++) {
			int H = ((i + 1) / 2) * stepRise;
			int L = (i / 2) * stepDepth;

			topR[i] = new Vertex(centerX + R, centerY + H, centerZ + L);
			topL[i] = new Vertex(centerX - R, centerY + H, centerZ + L);

			if (proportionalUVs) {
				// rescale texture coordinates to fit staircase width
				int U = (int) (uvScale * (H + L));
				topR[i].uv = new UV(U, 0);
				topL[i].uv = new UV(U, UV_SCALE);
			}
			else {
				int U = i * UV_SCALE;
				topR[i].uv = new UV(U, 0);
				topL[i].uv = new UV(U, UV_SCALE);
			}
		}

		TriangleBatch batch = new TriangleBatch(null);

		for (int i = 0; i < (N - 1); i++) {
			batch.triangles.add(new Triangle(topR[i], topL[i], topR[i + 1]));
			batch.triangles.add(new Triangle(topL[i + 1], topR[i + 1], topL[i]));
		}

		switch (sideStyle) {
			case NONE:
				break;

			case DROP: {
				Vertex[] upperR = new Vertex[N];
				Vertex[] upperL = new Vertex[N];

				for (int i = 0; i < N; i++) {
					upperR[i] = topR[i].deepCopy();
					upperL[i] = topL[i].deepCopy();

					int H = ((i + 1) / 2) * stepRise;
					int L = (i / 2) * stepDepth;

					upperR[i].uv = new UV(uvScale * L, uvScale * H + vOffset);
					upperL[i].uv = new UV(uvScale * L, uvScale * H + vOffset);
				}

				if (lessTriangles) {
					for (int i = 0; i < (N - 2); i += 2) {
						batch.triangles.add(new Triangle(upperR[i], upperR[i + 1], upperR[i + 2]));
						batch.triangles.add(new Triangle(upperL[i], upperL[i + 2], upperL[i + 1]));
					}

					Vertex cR = new Vertex(centerX + R, centerY, centerZ + steps * stepDepth);
					Vertex cL = new Vertex(centerX - R, centerY, centerZ + steps * stepDepth);

					cR.uv = new UV(uvScale * steps * stepDepth, vOffset);
					cL.uv = new UV(uvScale * steps * stepDepth, vOffset);

					batch.triangles.add(new Triangle(upperR[0], upperR[N - 1], cR));
					batch.triangles.add(new Triangle(upperL[0], cL, upperL[N - 1]));
				}
				else {
					Vertex[] lowerR = new Vertex[N];
					Vertex[] lowerL = new Vertex[N];

					for (int i = 0; i < N; i++) {
						lowerR[i] = new Vertex(upperR[i].getCurrentX(), centerY, upperR[i].getCurrentZ());
						lowerL[i] = new Vertex(upperL[i].getCurrentX(), centerY, upperL[i].getCurrentZ());

						lowerR[i].uv = new UV(upperR[i].uv.getU(), vOffset);
						lowerL[i].uv = new UV(upperL[i].uv.getU(), vOffset);
					}

					for (int i = 0; i < (N - 1); i++) {
						batch.triangles.add(new Triangle(lowerR[i], upperR[i], upperR[i + 1]));
						batch.triangles.add(new Triangle(lowerR[i], upperR[i + 1], lowerR[i + 1]));

						batch.triangles.add(new Triangle(lowerL[i], upperL[i + 1], upperL[i]));
						batch.triangles.add(new Triangle(lowerL[i], lowerL[i + 1], upperL[i + 1]));
					}
				}
			}
				break;

			case ANGLED: {
				Vertex[] upperR = new Vertex[N];
				Vertex[] upperL = new Vertex[N];

				for (int i = 0; i < N; i++) {
					upperR[i] = topR[i].deepCopy();
					upperL[i] = topL[i].deepCopy();

					int H = ((i + 1) / 2) * stepRise;
					int L = (i / 2) * stepDepth;

					upperR[i].uv = new UV(uvScale * L, uvScale * H + vOffset);
					upperL[i].uv = new UV(uvScale * L, uvScale * H + vOffset);
				}

				for (int i = 0; i < (N - 2); i += 2) {
					batch.triangles.add(new Triangle(upperR[i], upperR[i + 1], upperR[i + 2]));
					batch.triangles.add(new Triangle(upperL[i], upperL[i + 2], upperL[i + 1]));
				}

				Vertex[] lowerR = new Vertex[steps + 1];
				Vertex[] lowerL = new Vertex[steps + 1];
				for (int i = 0; i < steps + 1; i++) {
					lowerR[i] = new Vertex(
						upperR[2 * i].getCurrentX(),
						upperR[2 * i].getCurrentY() - sideHeight,
						upperR[2 * i].getCurrentZ());
					lowerL[i] = new Vertex(
						upperL[2 * i].getCurrentX(),
						upperL[2 * i].getCurrentY() - sideHeight,
						upperL[2 * i].getCurrentZ());

					// triangles on either end of each step
					lowerR[i].uv = new UV(upperR[2 * i].uv.getU(), upperR[2 * i].uv.getV() - uvScale * sideHeight);
					lowerL[i].uv = new UV(upperL[2 * i].uv.getU(), upperL[2 * i].uv.getV() - uvScale * sideHeight);
				}

				if (sideHeight > 0) {
					if (lessTriangles) {
						batch.triangles.add(new Triangle(lowerL[0], lowerL[steps], upperL[0]));
						batch.triangles.add(new Triangle(lowerL[steps], upperL[N - 1], upperL[0]));

						batch.triangles.add(new Triangle(lowerR[0], upperR[0], lowerR[steps]));
						batch.triangles.add(new Triangle(lowerR[steps], upperR[0], upperR[N - 1]));
					}
					else {
						for (int i = 0; i < steps; i++) {
							batch.triangles.add(new Triangle(upperR[2 * i], lowerR[i + 1], lowerR[i]));
							batch.triangles.add(new Triangle(upperR[2 * i], upperR[2 * (i + 1)], lowerR[i + 1]));

							batch.triangles.add(new Triangle(lowerL[i + 1], upperL[2 * i], lowerL[i]));
							batch.triangles.add(new Triangle(upperL[2 * (i + 1)], upperL[2 * i], lowerL[i + 1]));
						}
					}
				}

				if (makeBottom) {
					Vertex[] bottomR = new Vertex[steps + 1];
					Vertex[] bottomL = new Vertex[steps + 1];

					for (int i = 0; i < steps + 1; i++) {
						bottomR[i] = lowerR[i].deepCopy();
						bottomL[i] = lowerL[i].deepCopy();

						int L = i * (stepDepth + stepRise);

						bottomR[i].uv = new UV(uvScale * L, uvScale * R - vOffset);
						bottomL[i].uv = new UV(uvScale * L, -vOffset);
					}

					if (lessTriangles) {
						batch.triangles.add(new Triangle(bottomL[0], bottomR[0], bottomR[steps]));
						batch.triangles.add(new Triangle(bottomL[0], bottomR[steps], bottomL[steps]));
					}
					else {
						for (int i = 0; i < steps; i++) {
							batch.triangles.add(new Triangle(bottomL[i], bottomR[i], bottomR[i + 1]));
							batch.triangles.add(new Triangle(bottomL[i], bottomR[i + 1], bottomL[i + 1]));
						}
					}
				}
			}
				break;
		}

		return batch;
	}
}
