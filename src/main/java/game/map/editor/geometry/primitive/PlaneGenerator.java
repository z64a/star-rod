package game.map.editor.geometry.primitive;

import game.map.editor.ui.dialogs.GeneratePrimitiveOptionsDialog;
import game.map.mesh.Triangle;
import game.map.mesh.Vertex;
import game.map.shape.TriangleBatch;
import game.map.shape.UV;
import util.ui.LabeledIntegerSpinner;

public class PlaneGenerator extends ShapeGenerator
{
	private final LabeledIntegerSpinner sizeSpinner;
	private final LabeledIntegerSpinner xSpinner;
	private final LabeledIntegerSpinner zSpinner;

	public PlaneGenerator()
	{
		super(Primitive.PLANAR_GRID);

		sizeSpinner = new LabeledIntegerSpinner("Size", 10, 5000, 200);
		xSpinner = new LabeledIntegerSpinner("X Segments", 1, 64, 4);
		zSpinner = new LabeledIntegerSpinner("Z Segments", 1, 64, 4);
	}

	@Override
	public void addFields(GeneratePrimitiveOptionsDialog panel)
	{
		panel.addSpinner(sizeSpinner);
		panel.addSpinner(xSpinner);
		panel.addSpinner(zSpinner);
	}

	@Override
	public void setVisible(boolean b)
	{
		sizeSpinner.setVisible(b);
		xSpinner.setVisible(b);
		zSpinner.setVisible(b);
	}

	@Override
	public TriangleBatch generate(int centerX, int centerY, int centerZ)
	{
		int numX = xSpinner.getValue();
		int numZ = zSpinner.getValue();
		int size = sizeSpinner.getValue();

		return generate(numX, numZ, size, size, centerX, centerY, centerZ);
	}

	private static TriangleBatch generate(int numX, int numZ, int sizeX, int sizeZ, int centerX, int centerY, int centerZ)
	{
		Vertex[][] grid = new Vertex[numX + 1][numZ + 1];
		for (int i = 0; i <= numX; i++)
			for (int j = 0; j <= numZ; j++) {
				Vertex v = new Vertex(
					centerX + Math.round(sizeX * ((float) i / numX - 0.5)),
					centerY,
					centerZ + Math.round(sizeZ * ((float) j / numZ - 0.5)));

				v.uv = new UV(
					Math.round(UV_SCALE * (i - numX / 2.0)),
					Math.round(UV_SCALE * (j - numZ / 2.0)));
				grid[i][j] = v;
			}

		TriangleBatch batch = new TriangleBatch(null);

		for (int i = 0; i < numX; i++)
			for (int j = 0; j < numZ; j++) {
				Triangle t1 = new Triangle(grid[i][j + 1], grid[i + 1][j], grid[i][j]);
				Triangle t2 = new Triangle(grid[i + 1][j], grid[i][j + 1], grid[i + 1][j + 1]);
				batch.triangles.add(t1);
				batch.triangles.add(t2);
			}

		return batch;
	}
}
