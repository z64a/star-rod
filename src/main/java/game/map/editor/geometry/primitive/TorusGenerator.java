package game.map.editor.geometry.primitive;

import game.map.editor.ui.dialogs.GeneratePrimitiveOptionsDialog;
import game.map.mesh.Triangle;
import game.map.mesh.Vertex;
import game.map.shape.TriangleBatch;
import game.map.shape.UV;
import util.ui.LabeledIntegerSpinner;

public class TorusGenerator extends ShapeGenerator
{
	private final LabeledIntegerSpinner majorRSpinner;
	private final LabeledIntegerSpinner majorDivsSpinner;
	private final LabeledIntegerSpinner minorRSpinner;
	private final LabeledIntegerSpinner minorDivsSpinner;

	private final LabeledIntegerSpinner majorAngleStartSpinner;
	private final LabeledIntegerSpinner majorAngleEndSpinner;
	private final LabeledIntegerSpinner minorAngleStartSpinner;
	private final LabeledIntegerSpinner minorAngleEndSpinner;

	public TorusGenerator()
	{
		super(Primitive.TORUS);

		majorRSpinner = new LabeledIntegerSpinner("Major Radius", 10, 5000, 200);
		majorDivsSpinner = new LabeledIntegerSpinner("Major Segments", 3, 32, 12);

		minorRSpinner = new LabeledIntegerSpinner("Minor Radius", 10, 5000, 50);
		minorDivsSpinner = new LabeledIntegerSpinner("Minor Segments", 3, 32, 12);

		majorAngleStartSpinner = new LabeledIntegerSpinner("Major Start Angle", 0, 360, 0);
		majorAngleEndSpinner = new LabeledIntegerSpinner("Major End Angle", 0, 360, 360);

		minorAngleStartSpinner = new LabeledIntegerSpinner("Minor Start Angle", 0, 360, 0);
		minorAngleEndSpinner = new LabeledIntegerSpinner("Minor End Angle", 0, 360, 360);
	}

	@Override
	public void addFields(GeneratePrimitiveOptionsDialog panel)
	{
		panel.addSpinner(majorRSpinner);
		panel.addSpinner(majorDivsSpinner);
		panel.addSpinner(majorAngleStartSpinner);
		panel.addSpinner(majorAngleEndSpinner);

		panel.addSpinner(minorRSpinner);
		panel.addSpinner(minorDivsSpinner);
		panel.addSpinner(minorAngleStartSpinner);
		panel.addSpinner(minorAngleEndSpinner);
	}

	@Override
	public void setVisible(boolean b)
	{
		majorRSpinner.setVisible(b);
		majorDivsSpinner.setVisible(b);
		minorRSpinner.setVisible(b);
		minorDivsSpinner.setVisible(b);

		majorAngleStartSpinner.setVisible(b);
		majorAngleEndSpinner.setVisible(b);
		minorAngleStartSpinner.setVisible(b);
		minorAngleEndSpinner.setVisible(b);
	}

	@Override
	public TriangleBatch generate(int centerX, int centerY, int centerZ)
	{
		int r1 = majorRSpinner.getValue();
		int divsT = majorDivsSpinner.getValue();
		int startT = majorAngleStartSpinner.getValue();
		int endT = majorAngleEndSpinner.getValue();

		int r2 = minorRSpinner.getValue();
		int divsP = minorDivsSpinner.getValue();
		int startP = minorAngleStartSpinner.getValue();
		int endP = minorAngleEndSpinner.getValue();

		return generate(r1, divsT, startT, endT, r2, divsP, startP, endP, centerX, centerY, centerZ);
	}

	private static TriangleBatch generate(
		int r1, int divsT, int startT, int endT,
		int r2, int divsP, int startP, int endP,
		int centerX, int centerY, int centerZ)
	{
		double radStartP = Math.toRadians(startP);
		double radEndP = Math.toRadians(endP);
		double dP = (radEndP - radStartP) / divsP;

		double[][] template = new double[divsP + 1][2];
		for (int j = 0; j <= divsP; j++) {
			template[j][0] = r2 * Math.sin(radStartP + j * dP);
			template[j][1] = r2 * Math.cos(radStartP + j * dP) + r1;
		}

		double radStartT = Math.toRadians(startT);
		double radEndT = Math.toRadians(endT);
		double dT = (radEndT - radStartT) / divsT;

		Vertex[][] grid = new Vertex[divsT + 1][divsP + 1];
		for (int i = 0; i <= divsT; i++)
			for (int j = 0; j <= divsP; j++) {
				Vertex v = new Vertex(
					Math.round(centerX + Math.sin(radStartT + i * dT) * template[j][1]),
					Math.round(centerY + template[j][0]),
					Math.round(centerZ + Math.cos(radStartT + i * dT) * template[j][1]));

				v.uv = new UV(UV_SCALE * (i - divsT / 2), UV_SCALE * (j - divsP / 2));
				grid[i][j] = v;
			}

		TriangleBatch batch = new TriangleBatch(null);

		for (int i = 0; i < divsT; i++)
			for (int j = 0; j < divsP; j++) {
				Triangle t1 = new Triangle(grid[i + 1][j], grid[i][j + 1], grid[i][j]);
				Triangle t2 = new Triangle(grid[i][j + 1], grid[i + 1][j], grid[i + 1][j + 1]);
				batch.triangles.add(t1);
				batch.triangles.add(t2);
			}

		return batch;
	}
}
