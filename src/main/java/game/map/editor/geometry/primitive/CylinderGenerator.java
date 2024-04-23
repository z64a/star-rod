package game.map.editor.geometry.primitive;

import javax.swing.JCheckBox;

import app.SwingUtils;
import game.map.editor.ui.dialogs.GeneratePrimitiveOptionsDialog;
import game.map.mesh.Triangle;
import game.map.mesh.Vertex;
import game.map.shape.TriangleBatch;
import game.map.shape.UV;
import util.ui.LabeledIntegerSpinner;

public class CylinderGenerator extends ShapeGenerator
{
	private final LabeledIntegerSpinner radiusSpinner;
	private final LabeledIntegerSpinner heightSpinner;
	private final LabeledIntegerSpinner facesSpinner;
	private final LabeledIntegerSpinner divisionsSpinner;
	private final LabeledIntegerSpinner angleSpinner;
	private final LabeledIntegerSpinner taperSpinner;
	private final JCheckBox topCheckbox;
	private final JCheckBox bottomCheckbox;

	public CylinderGenerator()
	{
		super(Primitive.CYLINDER);

		radiusSpinner = new LabeledIntegerSpinner("Radius", 10, 5000, 50);
		heightSpinner = new LabeledIntegerSpinner("Height", 10, 5000, 200);
		facesSpinner = new LabeledIntegerSpinner("Radial Faces", 3, 64, 12);
		divisionsSpinner = new LabeledIntegerSpinner("Vertical Segments", 1, 64, 1);
		angleSpinner = new LabeledIntegerSpinner("Start Angle", 0, 360, 360);
		taperSpinner = new LabeledIntegerSpinner("Taper Percent", 0, 100, 0);
		topCheckbox = new JCheckBox(" Create Top Cap");
		topCheckbox.setSelected(false);
		SwingUtils.setFontSize(topCheckbox, 12);
		bottomCheckbox = new JCheckBox(" Create Bottom Cap");
		bottomCheckbox.setSelected(false);
		SwingUtils.setFontSize(bottomCheckbox, 12);
	}

	@Override
	public void addFields(GeneratePrimitiveOptionsDialog panel)
	{
		panel.addSpinner(radiusSpinner);
		panel.addSpinner(heightSpinner);
		panel.addSpinner(facesSpinner);
		panel.addSpinner(divisionsSpinner);
		panel.addSpinner(angleSpinner);
		panel.addSpinner(taperSpinner);
		panel.addCheckBox(topCheckbox);
		panel.addCheckBox(bottomCheckbox);
	}

	@Override
	public void setVisible(boolean b)
	{
		radiusSpinner.setVisible(b);
		heightSpinner.setVisible(b);
		facesSpinner.setVisible(b);
		divisionsSpinner.setVisible(b);
		angleSpinner.setVisible(b);
		taperSpinner.setVisible(b);
		topCheckbox.setVisible(b);
		bottomCheckbox.setVisible(b);
	}

	@Override
	public TriangleBatch generate(int centerX, int centerY, int centerZ)
	{
		int radius = radiusSpinner.getValue();
		int height = heightSpinner.getValue();
		int vdivs = divisionsSpinner.getValue();
		int faces = facesSpinner.getValue();
		int angle = angleSpinner.getValue();
		int taper = taperSpinner.getValue();
		boolean includeTop = topCheckbox.isSelected();
		boolean includeBottom = bottomCheckbox.isSelected();
		centerY += heightSpinner.getValue() / 2;

		return generate(radius, height, vdivs, faces, angle, taper, includeTop, includeBottom, centerX, centerY, centerZ);
	}

	private static TriangleBatch generate(
		int radius, int height, int vdivs, int faces, int angle, int taper,
		boolean includeTop, boolean includeBottom,
		int centerX, int centerY, int centerZ)
	{
		Vertex[][] side = new Vertex[faces + 1][vdivs + 1];
		Vertex[][] caps = new Vertex[faces + 1][2];
		Vertex[] capCenter = new Vertex[2];

		double radAngle = Math.toRadians(angle);

		for (int i = 0; i <= faces; i++)
			for (int k = 0; k <= vdivs; k++) {
				float scale = (float) k / vdivs;
				float taperScale = Math.min(Math.max((1.0f - scale * taper / 100.0f), 0.0f), 1.0f);

				side[i][k] = new Vertex(
					centerX + Math.round(radius * taperScale * Math.sin(radAngle + 2 * Math.PI * i / faces)),
					centerY + Math.round(height * (scale - 0.5)),
					centerZ - Math.round(radius * taperScale * Math.cos(radAngle + 2 * Math.PI * i / faces)));
				side[i][k].uv = new UV(4 * (i * UV_SCALE / faces), 2 * scale * UV_SCALE);
			}

		for (int i = 0; i <= faces; i++)
			for (int k = 0; k < 2; k++) {
				float taperScale = Math.min(Math.max((1 - k * taper / 100.0f), 0.0f), 1.0f);

				caps[i][k] = new Vertex(
					centerX + Math.round(radius * taperScale * Math.sin(radAngle + 2 * Math.PI * i / faces)),
					centerY + Math.round(height * (k - 0.5)),
					centerZ - Math.round(radius * taperScale * Math.cos(radAngle + 2 * Math.PI * i / faces)));
				caps[i][k].uv = new UV(
					Math.round(UV_SCALE * 0.5 * (Math.sin(radAngle + 2 * Math.PI * i / faces) + 1) + 3 * k * UV_SCALE / 2),
					Math.round(UV_SCALE * 0.5 * (Math.cos(radAngle + 2 * Math.PI * i / faces) + 1)) - 3 * UV_SCALE / 2);
			}

		capCenter[0] = new Vertex(centerX, centerY - Math.round(0.5 * height), centerZ);
		capCenter[0].uv = new UV(UV_SCALE / 2, -UV_SCALE);
		capCenter[1] = new Vertex(centerX, centerY + Math.round(0.5 * height), centerZ);
		capCenter[1].uv = new UV(4 * UV_SCALE / 2, -UV_SCALE);

		TriangleBatch batch = new TriangleBatch(null);

		// sides
		for (int i = 0; i < faces; i++) {
			for (int k = 0; k < vdivs; k++) {
				batch.triangles.add(new Triangle(side[i][k], side[i][k + 1], side[i + 1][k]));
				batch.triangles.add(new Triangle(side[i + 1][k], side[i][k + 1], side[i + 1][k + 1]));
			}
		}

		if (includeBottom) {
			for (int i = 0; i < faces; i++)
				batch.triangles.add(new Triangle(capCenter[0], caps[i][0], caps[i + 1][0]));
		}

		if (includeTop) {
			for (int i = 0; i < faces; i++)
				batch.triangles.add(new Triangle(capCenter[1], caps[i + 1][1], caps[i][1]));
		}

		return batch;
	}
}
