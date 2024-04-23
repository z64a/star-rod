package game.map.editor.geometry.primitive;

import game.map.editor.ui.dialogs.GeneratePrimitiveOptionsDialog;
import game.map.mesh.Triangle;
import game.map.mesh.Vertex;
import game.map.shape.TriangleBatch;
import game.map.shape.UV;
import util.ui.LabeledIntegerSpinner;

public class RingGenerator extends ShapeGenerator
{
	private final LabeledIntegerSpinner innerRadiusSpinner;
	private final LabeledIntegerSpinner outerRadiusSpinner;
	private final LabeledIntegerSpinner divisionsSpinner;
	private final LabeledIntegerSpinner angleSpinner;
	private final LabeledIntegerSpinner thicknessSpinner;

	public RingGenerator()
	{
		super(Primitive.RING);

		innerRadiusSpinner = new LabeledIntegerSpinner("Inner Radius", 0, 5000, 100);
		outerRadiusSpinner = new LabeledIntegerSpinner("Outer Radius", 10, 5000, 200);
		divisionsSpinner = new LabeledIntegerSpinner("Segments", 3, 64, 12);
		angleSpinner = new LabeledIntegerSpinner("Arc Angle", 1, 360, 360);
		thicknessSpinner = new LabeledIntegerSpinner("Thickness", 0, 1000, 20);
	}

	@Override
	public void addFields(GeneratePrimitiveOptionsDialog panel)
	{
		panel.addSpinner(innerRadiusSpinner);
		panel.addSpinner(outerRadiusSpinner);
		panel.addSpinner(divisionsSpinner);
		panel.addSpinner(angleSpinner);
		panel.addSpinner(thicknessSpinner);
	}

	@Override
	public void setVisible(boolean b)
	{
		innerRadiusSpinner.setVisible(b);
		outerRadiusSpinner.setVisible(b);
		divisionsSpinner.setVisible(b);
		angleSpinner.setVisible(b);
		thicknessSpinner.setVisible(b);
	}

	@Override
	public TriangleBatch generate(int centerX, int centerY, int centerZ)
	{
		int innerRadius = innerRadiusSpinner.getValue();
		int outerRadius = outerRadiusSpinner.getValue();
		int divisions = divisionsSpinner.getValue();
		int angle = angleSpinner.getValue();
		int thickness = thicknessSpinner.getValue();

		return generate(divisions, innerRadius, outerRadius, angle, thickness, centerX, centerY, centerZ);
	}

	private static TriangleBatch generate(
		int divs, int innerR, int outerR, int angle, int thickness,
		int centerX, int centerY, int centerZ)
	{
		Vertex[][] rings = new Vertex[divs + 1][5];

		double angleRad = Math.toRadians(angle);

		for (int i = 0; i <= divs; i++) {
			double X = Math.sin(angleRad * i / divs);
			double Z = Math.cos(angleRad * i / divs);

			int inX = (int) Math.round(innerR * X);
			int inZ = (int) Math.round(innerR * Z);

			int outX = (int) Math.round(outerR * X);
			int outZ = (int) Math.round(outerR * Z);

			rings[i][0] = new Vertex(centerX + inX, centerY + thickness, centerZ + inZ);
			rings[i][1] = new Vertex(centerX + outX, centerY + thickness, centerZ + outZ);
			rings[i][2] = new Vertex(centerX + outX, centerY, centerZ + outZ);
			rings[i][3] = new Vertex(centerX + inX, centerY, centerZ + inZ);
			rings[i][4] = new Vertex(centerX + inX, centerY + thickness, centerZ + inZ);

			int u = Math.round(UV_SCALE * (i - divs / 2.0f));
			for (int j = 0; j < 5; j++)
				rings[i][j].uv = new UV(u, UV_SCALE * (j - 2));
		}

		TriangleBatch batch = new TriangleBatch(null);

		for (int i = 0; i < divs; i++) {
			batch.triangles.add(new Triangle(rings[i][0], rings[i][1], rings[i + 1][0]));
			batch.triangles.add(new Triangle(rings[i + 1][0], rings[i][1], rings[i + 1][1]));

			if (thickness != 0) {
				for (int j = 1; j < 4; j++) {
					batch.triangles.add(new Triangle(rings[i][j], rings[i][j + 1], rings[i + 1][j]));
					batch.triangles.add(new Triangle(rings[i + 1][j + 1], rings[i + 1][j], rings[i][j + 1]));
				}
			}
		}

		if (thickness != 0 && angle != 360) {
			Vertex[][] caps = new Vertex[4][2];

			for (int k = 0; k < 4; k++) {
				caps[k][0] = rings[0][k].deepCopy();
				caps[k][0].uv = new UV(UV_SCALE * (k % 2), 0);
			}

			int u1 = Math.round(UV_SCALE * (-1 - divs / 2.0f));
			int u2 = Math.round(UV_SCALE * (-2 - divs / 2.0f));
			caps[0][0].uv = new UV(u1, 0);
			caps[1][0].uv = new UV(u1, UV_SCALE);
			caps[2][0].uv = new UV(u2, UV_SCALE);
			caps[3][0].uv = new UV(u2, 0);

			for (int k = 0; k < 4; k++) {
				caps[k][1] = rings[divs][k].deepCopy();
				caps[k][1].uv = new UV(UV_SCALE * (k % 2), 0);
			}

			u1 = Math.round(UV_SCALE * (1 + divs / 2.0f));
			u2 = Math.round(UV_SCALE * (2 + divs / 2.0f));
			caps[0][1].uv = new UV(u1, 0);
			caps[1][1].uv = new UV(u1, UV_SCALE);
			caps[2][1].uv = new UV(u2, UV_SCALE);
			caps[3][1].uv = new UV(u2, 0);

			batch.triangles.add(new Triangle(caps[1][0], caps[0][0], caps[2][0]));
			batch.triangles.add(new Triangle(caps[3][0], caps[2][0], caps[0][0]));

			batch.triangles.add(new Triangle(caps[0][1], caps[1][1], caps[2][1]));
			batch.triangles.add(new Triangle(caps[2][1], caps[3][1], caps[0][1]));
		}

		return batch;
	}
}
