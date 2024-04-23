package game.map.editor.geometry.primitive;

import javax.swing.JCheckBox;

import app.SwingUtils;
import game.map.editor.ui.dialogs.GeneratePrimitiveOptionsDialog;
import game.map.mesh.Triangle;
import game.map.mesh.Vertex;
import game.map.shape.TriangleBatch;
import game.map.shape.UV;
import util.ui.LabeledIntegerSpinner;

public class RadialGridGenerator extends ShapeGenerator
{
	private final LabeledIntegerSpinner innerRadiusSpinner;
	private final LabeledIntegerSpinner outerRadiusSpinner;
	private final LabeledIntegerSpinner rSpinner;
	private final LabeledIntegerSpinner aSpinner;
	private final LabeledIntegerSpinner angleSpinner;
	private final LabeledIntegerSpinner riseSpinner;
	private final JCheckBox fuseCheckbox;
	private final JCheckBox planarUVCheckbox;

	public RadialGridGenerator()
	{
		super(Primitive.RADIAL_GRID);

		innerRadiusSpinner = new LabeledIntegerSpinner("Inner Radius", 0, 5000, 100);
		outerRadiusSpinner = new LabeledIntegerSpinner("Outer Radius", 10, 5000, 200);
		rSpinner = new LabeledIntegerSpinner("Radial Segments", 1, 64, 4);
		aSpinner = new LabeledIntegerSpinner("Angular Segments", 3, 64, 12);
		angleSpinner = new LabeledIntegerSpinner("Arc Angle", 1, 360, 360);
		riseSpinner = new LabeledIntegerSpinner("Radial Rise", -100, 100, 0);

		fuseCheckbox = new JCheckBox(" Fuse Center Vertices");
		fuseCheckbox.setSelected(false);
		SwingUtils.setFontSize(fuseCheckbox, 12);

		planarUVCheckbox = new JCheckBox(" Use Planar UVs");
		planarUVCheckbox.setSelected(false);
		SwingUtils.setFontSize(planarUVCheckbox, 12);
	}

	@Override
	public void addFields(GeneratePrimitiveOptionsDialog panel)
	{
		panel.addSpinner(innerRadiusSpinner);
		panel.addSpinner(outerRadiusSpinner);
		panel.addSpinner(rSpinner);
		panel.addSpinner(aSpinner);
		panel.addSpinner(angleSpinner);
		panel.addSpinner(riseSpinner);
		panel.addCheckBox(fuseCheckbox);
		panel.addCheckBox(planarUVCheckbox);
	}

	@Override
	public void setVisible(boolean b)
	{
		innerRadiusSpinner.setVisible(b);
		outerRadiusSpinner.setVisible(b);
		rSpinner.setVisible(b);
		aSpinner.setVisible(b);
		angleSpinner.setVisible(b);
		riseSpinner.setVisible(b);
		fuseCheckbox.setVisible(b);
		planarUVCheckbox.setVisible(b);
	}

	@Override
	public TriangleBatch generate(int centerX, int centerY, int centerZ)
	{
		int numR = rSpinner.getValue();
		int numA = aSpinner.getValue();
		int radius1 = innerRadiusSpinner.getValue();
		int radius2 = outerRadiusSpinner.getValue();
		int angle = angleSpinner.getValue();
		int rise = riseSpinner.getValue();
		boolean fuse = fuseCheckbox.isSelected();
		boolean planarUVs = planarUVCheckbox.isSelected();

		return generate(numR, numA, radius1, radius2, angle, rise, fuse, planarUVs, centerX, centerY, centerZ);
	}

	private static TriangleBatch generate(
		int numR, int numA, int radiusInner, int radiusOuter,
		int angle, int rise, boolean fuseCenter, boolean planarUVs,
		int centerX, int centerY, int centerZ)
	{
		Vertex[][] rings = new Vertex[numR + 1][numA + 1];

		double angleInterval = Math.toRadians(angle) / numA;
		double radInterval = (float) (radiusOuter - radiusInner) / numR;

		for (int i = 0; i <= numR; i++) {
			if (i == 0 && radiusInner == 0 && fuseCenter) {
				Vertex center = new Vertex(centerX, centerY, centerZ);
				center.uv = new UV(
					(int) (UV_SCALE * (i - numR / 2.0)), 0);

				for (int j = 0; j <= numA; j++)
					rings[0][j] = center;

				continue;
			}

			double radius = radiusInner + i * radInterval;

			for (int j = 0; j <= numA; j++) {
				double X = Math.sin(angleInterval * j);
				double Z = Math.cos(angleInterval * j);

				rings[i][j] = new Vertex(
					centerX + Math.round(radius * X),
					centerY + i * rise,
					centerZ - Math.round(radius * Z));
				rings[i][j].uv = new UV(
					Math.round(UV_SCALE * (i - numR / 2.0)),
					Math.round(UV_SCALE * (j - numA / 2.0)));
			}
		}

		if (planarUVs) {
			for (int i = 0; i <= numR; i++)
				for (int j = 0; j <= numA; j++) {
					rings[i][j].uv = new UV(
						rings[i][j].getCurrentX() * 16,
						rings[i][j].getCurrentZ() * 16);
				}
		}

		TriangleBatch batch = new TriangleBatch(null);

		for (int i = 0; i < numR; i++)
			for (int j = 0; j < numA; j++) {
				Triangle t1 = new Triangle(rings[i + 1][j + 1], rings[i + 1][j], rings[i][j]);
				Triangle t2 = new Triangle(rings[i + 1][j + 1], rings[i][j], rings[i][j + 1]);
				batch.triangles.add(t1);
				batch.triangles.add(t2);
			}

		return batch;
	}
}
