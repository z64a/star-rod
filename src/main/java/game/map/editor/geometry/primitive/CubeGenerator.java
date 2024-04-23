package game.map.editor.geometry.primitive;

import game.map.editor.ui.dialogs.GeneratePrimitiveOptionsDialog;
import game.map.mesh.Triangle;
import game.map.mesh.Vertex;
import game.map.shape.TriangleBatch;
import game.map.shape.UV;
import util.ui.LabeledIntegerSpinner;

public class CubeGenerator extends ShapeGenerator
{
	private final LabeledIntegerSpinner sizeSpinner;
	private final LabeledIntegerSpinner subdivSpinner;

	public CubeGenerator()
	{
		super(Primitive.CUBE);
		sizeSpinner = new LabeledIntegerSpinner("Size", 10, 5000, 200);
		subdivSpinner = new LabeledIntegerSpinner("Face Subdivisions", 0, 16, 0);
	}

	@Override
	public void addFields(GeneratePrimitiveOptionsDialog panel)
	{
		panel.addSpinner(sizeSpinner);
		panel.addSpinner(subdivSpinner);
	}

	@Override
	public void setVisible(boolean b)
	{
		sizeSpinner.setVisible(b);
		subdivSpinner.setVisible(b);
	}

	@Override
	public TriangleBatch generate(int centerX, int centerY, int centerZ)
	{
		int size = sizeSpinner.getValue();
		int subdivs = subdivSpinner.getValue();
		centerY += sizeSpinner.getValue() / 2;

		return generate(size, subdivs, centerX, centerY, centerZ);
	}

	private static TriangleBatch generate(int size, int subdivs, int centerX, int centerY, int centerZ)
	{
		int numH = 5 + 4 * subdivs;
		int numV = 2 + subdivs;
		Vertex[][] rings = new Vertex[numH][numV];

		// 2d template, will be copied vertically to form rings
		int[][] template = new int[5 + 4 * subdivs][2];

		// start with the upper left corner
		int k = 0;
		template[k][0] = -size / 2;
		template[k][1] = -size / 2;
		k++;

		// iterate down...
		for (int i = 1; i <= (subdivs + 1); i++) {
			template[k][0] = template[k - i][0];
			template[k][1] = template[k - i][1] + Math.round(size * (float) i / (subdivs + 1));
			k++;
		}

		// ...right...
		for (int i = 1; i <= (subdivs + 1); i++) {
			template[k][0] = template[k - i][0] + Math.round(size * (float) i / (subdivs + 1));
			template[k][1] = template[k - i][1];
			k++;
		}

		// ...up...
		for (int i = 1; i <= (subdivs + 1); i++) {
			template[k][0] = template[k - i][0];
			template[k][1] = template[k - i][1] - Math.round(size * (float) i / (subdivs + 1));
			k++;
		}

		// ...left
		for (int i = 1; i <= (subdivs + 1); i++) {
			template[k][0] = template[k - i][0] - Math.round(size * (float) i / (subdivs + 1));
			template[k][1] = template[k - i][1];
			k++;
		}

		// create the rings
		for (int j = 0; j < numV; j++) {
			float vscale = (float) j / (numV - 1);
			for (int i = 0; i < numH; i++) {
				rings[i][j] = new Vertex(
					centerX + template[i][0],
					centerY + Math.round(size * (-0.5 + vscale)),
					centerZ + template[i][1]);

				rings[i][j].uv = new UV(
					Math.round((float) i / (subdivs + 1) * UV_SCALE),
					Math.round((float) j / (subdivs + 1) * UV_SCALE));
			}
		}

		TriangleBatch batch = new TriangleBatch(null);

		// add rings to batch
		for (int j = 0; j < (numV - 1); j++) {
			for (int i = 0; i < (numH - 1); i++) {
				batch.triangles.add(new Triangle(rings[i][j], rings[i + 1][j], rings[i][j + 1]));
				batch.triangles.add(new Triangle(rings[i][j + 1], rings[i + 1][j], rings[i + 1][j + 1]));
			}
		}

		int numCap = 2 + subdivs;

		Vertex[][] top = new Vertex[numCap][numCap];
		Vertex[][] bottom = new Vertex[numCap][numCap];

		for (int i = 0; i < numCap; i++) {
			for (int j = 0; j < numCap; j++) {
				int posX = Math.round(((float) i / (numCap - 1) - 0.5f) * size);
				int posZ = Math.round(((float) j / (numCap - 1) - 0.5f) * size);

				top[i][j] = new Vertex(centerX + posX, centerY + Math.round(0.5 * size), centerZ + posZ);
				bottom[i][j] = new Vertex(centerX + posX, centerY - Math.round(0.5 * size), centerZ + posZ);

				top[i][j].uv = new UV(
					Math.round(((float) i / (subdivs + 1)) * UV_SCALE),
					Math.round(((float) j / (subdivs + 1) - 2.0f) * UV_SCALE));

				bottom[i][j].uv = new UV(
					Math.round(((float) i / (subdivs + 1) + 2.0f) * UV_SCALE),
					Math.round(((float) j / (subdivs + 1) - 2.0f) * UV_SCALE));
			}
		}

		for (int i = 0; i < (numCap - 1); i++) {
			for (int j = 0; j < (numCap - 1); j++) {
				batch.triangles.add(new Triangle(top[i][j], top[i][j + 1], top[i + 1][j]));
				batch.triangles.add(new Triangle(top[i + 1][j], top[i][j + 1], top[i + 1][j + 1]));

				batch.triangles.add(new Triangle(bottom[i][j], bottom[i + 1][j], bottom[i][j + 1]));
				batch.triangles.add(new Triangle(bottom[i][j + 1], bottom[i + 1][j], bottom[i + 1][j + 1]));
			}
		}

		return batch;
	}
}
