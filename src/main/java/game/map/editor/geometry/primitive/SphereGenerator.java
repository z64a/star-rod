package game.map.editor.geometry.primitive;

import game.map.editor.ui.dialogs.GeneratePrimitiveOptionsDialog;
import game.map.mesh.Triangle;
import game.map.mesh.Vertex;
import game.map.shape.TriangleBatch;
import game.map.shape.UV;
import util.ui.LabeledIntegerSpinner;

public class SphereGenerator extends ShapeGenerator
{
	private final LabeledIntegerSpinner radiusSpinner;
	private final LabeledIntegerSpinner latFacesSpinner;
	private final LabeledIntegerSpinner lonFacesSpinner;

	public SphereGenerator()
	{
		super(Primitive.SPHERE);

		radiusSpinner = new LabeledIntegerSpinner("Radius", 10, 5000, 200);
		latFacesSpinner = new LabeledIntegerSpinner("Vertical Segments", 3, 32, 12);
		lonFacesSpinner = new LabeledIntegerSpinner("Radial Segments", 3, 32, 12);
	}

	@Override
	public void addFields(GeneratePrimitiveOptionsDialog panel)
	{
		panel.addSpinner(radiusSpinner);
		panel.addSpinner(latFacesSpinner);
		panel.addSpinner(lonFacesSpinner);
	}

	@Override
	public void setVisible(boolean b)
	{
		radiusSpinner.setVisible(b);
		latFacesSpinner.setVisible(b);
		lonFacesSpinner.setVisible(b);
	}

	@Override
	public TriangleBatch generate(int centerX, int centerY, int centerZ)
	{
		int radius = radiusSpinner.getValue();
		int latFaces = latFacesSpinner.getValue();
		int lonFaces = lonFacesSpinner.getValue();

		return generate(radius, latFaces, lonFaces, centerX, centerY, centerZ);
	}

	private static TriangleBatch generate(int radius, int latfaces, int longfaces, int centerX, int centerY, int centerZ)
	{
		Vertex[][] rings = new Vertex[latfaces + 1][longfaces + 1];

		for (int j = 0; j <= longfaces; j++) {
			rings[0][j] = new Vertex(centerX, centerY + radius, centerZ);
			rings[0][j].uv = new UV(Math.round(UV_SCALE * j / longfaces), 0);
			rings[latfaces][j] = new Vertex(centerX, centerY - radius, centerZ);
			rings[latfaces][j].uv = new UV(Math.round(UV_SCALE * j / longfaces), UV_SCALE);
		}

		for (int i = 1; i < latfaces; i++)
			for (int j = 0; j <= longfaces; j++) {
				double phi = (2 * Math.PI * j / longfaces);
				double theta = (Math.PI * i / latfaces);

				rings[i][j] = new Vertex(
					centerX + Math.round(radius * Math.cos(phi) * Math.sin(theta)),
					centerY + Math.round(radius * Math.cos(theta)),
					centerZ + Math.round(radius * Math.sin(phi) * Math.sin(theta)));

				rings[i][j].uv = new UV(
					Math.round(UV_SCALE * j / longfaces),
					Math.round(UV_SCALE * i / latfaces));
			}

		TriangleBatch batch = new TriangleBatch(null);

		for (int i = 0; i < latfaces; i++)
			for (int j = 0; j < longfaces; j++) {
				batch.triangles.add(new Triangle(rings[i][j], rings[i][j + 1], rings[i + 1][j]));
				batch.triangles.add(new Triangle(rings[i][j + 1], rings[i + 1][j + 1], rings[i + 1][j]));
			}

		return batch;
	}
}
