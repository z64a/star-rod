package game.map.editor.geometry.primitive;

import javax.swing.JCheckBox;

import game.map.editor.ui.dialogs.GeneratePrimitiveOptionsDialog;
import game.map.mesh.Triangle;
import game.map.mesh.Vertex;
import game.map.shape.TriangleBatch;
import game.map.shape.UV;
import util.ui.LabeledIntegerSpinner;

public class ConeGenerator extends ShapeGenerator
{
	private final LabeledIntegerSpinner radiusSpinner;
	private final LabeledIntegerSpinner heightSpinner;
	private final LabeledIntegerSpinner facesSpinner;
	private final LabeledIntegerSpinner divisionsSpinner;
	private final JCheckBox bottomCheckbox;
	private final JCheckBox fuseTopCheckbox;

	public ConeGenerator()
	{
		super(Primitive.CONE);

		radiusSpinner = new LabeledIntegerSpinner("Radius", 10, 5000, 50);
		heightSpinner = new LabeledIntegerSpinner("Height", 10, 5000, 200);
		facesSpinner = new LabeledIntegerSpinner("Radial Faces", 3, 64, 12);
		divisionsSpinner = new LabeledIntegerSpinner("Vertical Segments", 1, 64, 1);
		bottomCheckbox = new JCheckBox(" Create Bottom Cap");
		fuseTopCheckbox = new JCheckBox(" Fuse Top Vertex");
		bottomCheckbox.setSelected(false);
		fuseTopCheckbox.setSelected(false);
		bottomCheckbox.setFont(bottomCheckbox.getFont().deriveFont(12f));
		fuseTopCheckbox.setFont(fuseTopCheckbox.getFont().deriveFont(12f));
	}

	@Override
	public void addFields(GeneratePrimitiveOptionsDialog panel)
	{
		panel.addSpinner(radiusSpinner);
		panel.addSpinner(heightSpinner);
		panel.addSpinner(facesSpinner);
		panel.addSpinner(divisionsSpinner);
		panel.addCheckBox(bottomCheckbox);
		panel.addCheckBox(fuseTopCheckbox);
	}

	@Override
	public void setVisible(boolean b)
	{
		radiusSpinner.setVisible(b);
		heightSpinner.setVisible(b);
		facesSpinner.setVisible(b);
		divisionsSpinner.setVisible(b);
		bottomCheckbox.setVisible(b);
		fuseTopCheckbox.setVisible(b);
	}

	@Override
	public TriangleBatch generate(int centerX, int centerY, int centerZ)
	{
		int radius = radiusSpinner.getValue();
		int height = heightSpinner.getValue();
		int faces = facesSpinner.getValue();
		int vdivs = divisionsSpinner.getValue();
		boolean includeCap = bottomCheckbox.isSelected();
		boolean fuseTop = fuseTopCheckbox.isSelected();
		centerY += heightSpinner.getValue() / 2;

		return generate(radius, height, vdivs, faces, includeCap, fuseTop, centerX, centerY, centerZ);
	}

	private static TriangleBatch generate(int radius, int height, int vdivs, int faces,
		boolean includeCap, boolean fuseTop, int centerX, int centerY, int centerZ)
	{
		Vertex[][] rings = new Vertex[faces + 1][vdivs + 1];
		Vertex[] cap = new Vertex[faces + 1];
		Vertex[] capCenter = new Vertex[2];

		for (int i = 0; i <= faces; i++)
			for (int k = 0; k <= vdivs; k++) {
				float scale = (float) k / vdivs;

				rings[i][k] = new Vertex(
					centerX + Math.round(radius * (1.0f - scale) * Math.sin(2 * Math.PI * i / faces)),
					centerY + Math.round(height * (scale - 0.5)),
					centerZ - Math.round(radius * (1.0f - scale) * Math.cos(2 * Math.PI * i / faces)));
				rings[i][k].uv = new UV(4 * (i * UV_SCALE / faces), 2 * scale * UV_SCALE);
			}

		// last ring
		for (int i = 0; i <= faces; i++) {
			cap[i] = new Vertex(
				centerX + Math.round(radius * Math.sin(2 * Math.PI * i / faces)),
				centerY - Math.round(0.5 * height),
				centerZ - Math.round(radius * Math.cos(2 * Math.PI * i / faces)));
			cap[i].uv = new UV(
				Math.round(UV_SCALE * 0.5 * (Math.sin(2 * Math.PI * i / faces) + 1)),
				Math.round(UV_SCALE * 0.5 * (Math.cos(2 * Math.PI * i / faces) + 1) - 3 * UV_SCALE / 2));
		}

		capCenter[0] = new Vertex(centerX, centerY - Math.round(0.5 * height), centerZ);
		capCenter[0].uv = new UV(UV_SCALE / 2, -UV_SCALE);
		capCenter[1] = new Vertex(centerX, centerY + Math.round(0.5 * height), centerZ);
		capCenter[1].uv = new UV(2 * UV_SCALE, vdivs * UV_SCALE);

		TriangleBatch batch = new TriangleBatch(null);

		// rings
		for (int i = 0; i < faces; i++) {
			for (int k = 0; k < (vdivs - 1); k++) {
				batch.triangles.add(new Triangle(rings[i][k], rings[i][k + 1], rings[i + 1][k]));
				batch.triangles.add(new Triangle(rings[i + 1][k], rings[i][k + 1], rings[i + 1][k + 1]));
			}
		}

		for (int i = 0; i < faces; i++) {
			if (fuseTop)
				batch.triangles.add(new Triangle(rings[i][vdivs - 1], capCenter[1], rings[i + 1][vdivs - 1]));
			else
				batch.triangles.add(new Triangle(rings[i][vdivs - 1], rings[i][vdivs], rings[i + 1][vdivs - 1]));
		}

		//bottom
		if (includeCap) {
			for (int i = 0; i < faces; i++)
				batch.triangles.add(new Triangle(capCenter[0], cap[i], cap[i + 1]));
		}

		return batch;
	}
}
