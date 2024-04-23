package game.map.editor.geometry.primitive;

import game.map.editor.ui.dialogs.GeneratePrimitiveOptionsDialog;
import game.map.mesh.Triangle;
import game.map.mesh.Vertex;
import game.map.shape.TriangleBatch;
import game.map.shape.UV;
import util.ui.LabeledIntegerSpinner;

public class BeveledCubeGenerator extends ShapeGenerator
{
	private final LabeledIntegerSpinner sizeSpinner;
	private final LabeledIntegerSpinner bevelSpinner;
	//	private final JCheckBox cbSharpMiter;

	public BeveledCubeGenerator()
	{
		super(Primitive.CUBE_BEVEL);
		sizeSpinner = new LabeledIntegerSpinner("Size", 10, 5000, 200);
		bevelSpinner = new LabeledIntegerSpinner("Bevel Length", 0, 1000, 25);
		//		cbSharpMiter = new JCheckBox(" Sharp Miter");
		//		cbSharpMiter.setSelected(false);
		//		SwingUtils.setFontSize(cbSharpMiter, 12);
	}

	@Override
	public void addFields(GeneratePrimitiveOptionsDialog panel)
	{
		panel.addSpinner(sizeSpinner);
		panel.addSpinner(bevelSpinner);
		//		panel.addCheckBox(cbSharpMiter);
	}

	@Override
	public void setVisible(boolean b)
	{
		sizeSpinner.setVisible(b);
		bevelSpinner.setVisible(b);
		//		cbSharpMiter.setVisible(b);
	}

	@Override
	public TriangleBatch generate(int centerX, int centerY, int centerZ)
	{
		int size = sizeSpinner.getValue();
		int bevels = bevelSpinner.getValue();
		//		boolean sharpMiter = cbSharpMiter.isSelected();
		centerY += sizeSpinner.getValue() / 2;

		return generate(size, bevels, false, centerX, centerY, centerZ);
	}

	// ring indices
	private static final int LOWER = 0;
	private static final int UPPER = 1;
	private static final int BOT = 2;
	private static final int TOP = 3;

	private static TriangleBatch generate(int size, int bevelLength, boolean sharpMiter, int centerX, int centerY, int centerZ)
	{
		bevelLength = Math.min(bevelLength, (size / 2) - 1);

		int halfLength = Math.round((float) size / 2);
		float Lb = (float) (bevelLength * Math.sqrt(2));
		float Ls = (float) (size - 2.0 * bevelLength);
		float effectiveSize = (float) (size - bevelLength * (2.0 - Math.sqrt(2)));

		// uv grid layout
		int[] uForCol = new int[9];
		float x = (Lb / 2.0f);
		uForCol[0] = Math.round(x / effectiveSize * UV_SCALE);
		x += Ls;
		uForCol[1] = Math.round(x / effectiveSize * UV_SCALE);
		x += Lb;
		uForCol[2] = Math.round(x / effectiveSize * UV_SCALE);
		x += Ls;
		uForCol[3] = Math.round(x / effectiveSize * UV_SCALE);
		x += Lb;
		uForCol[4] = Math.round(x / effectiveSize * UV_SCALE);
		x += Ls;
		uForCol[5] = Math.round(x / effectiveSize * UV_SCALE);
		x += Lb;
		uForCol[6] = Math.round(x / effectiveSize * UV_SCALE);
		x += Ls;
		uForCol[7] = Math.round(x / effectiveSize * UV_SCALE);
		x += Lb;
		uForCol[8] = Math.round(x / effectiveSize * UV_SCALE);

		int[] vForRow = new int[6];
		x = (Lb / 2.0f);
		vForRow[0] = Math.round(UV_SCALE * (Lb + Ls + x) / effectiveSize);
		vForRow[1] = Math.round(UV_SCALE * (Ls + x) / effectiveSize);
		vForRow[2] = Math.round(UV_SCALE * x / effectiveSize);
		vForRow[3] = Math.round(UV_SCALE * -x / effectiveSize);
		vForRow[4] = Math.round(UV_SCALE * (-1 - x / effectiveSize));
		vForRow[5] = Math.round(UV_SCALE * (-1 - (Ls + x) / effectiveSize));

		// create vertices for top and bottom faces
		Vertex[][] cap = new Vertex[4][2];
		cap[0][0] = new Vertex(centerX - halfLength + bevelLength, centerY - halfLength, centerZ - halfLength + bevelLength);
		cap[1][0] = new Vertex(centerX - halfLength + bevelLength, centerY - halfLength, centerZ + halfLength - bevelLength);
		cap[2][0] = new Vertex(centerX + halfLength - bevelLength, centerY - halfLength, centerZ + halfLength - bevelLength);
		cap[3][0] = new Vertex(centerX + halfLength - bevelLength, centerY - halfLength, centerZ - halfLength + bevelLength);
		for (int i = 0; i < 4; i++)
			cap[i][1] = new Vertex(cap[i][0].getCurrentX(), centerY + halfLength, cap[i][0].getCurrentZ());

		cap[0][0].uv = new UV(uForCol[0], vForRow[5]);
		cap[1][0].uv = new UV(uForCol[0], vForRow[4]);
		cap[2][0].uv = new UV(uForCol[1], vForRow[4]);
		cap[3][0].uv = new UV(uForCol[1], vForRow[5]);

		cap[0][1].uv = new UV(uForCol[4], vForRow[5]);
		cap[1][1].uv = new UV(uForCol[4], vForRow[4]);
		cap[2][1].uv = new UV(uForCol[5], vForRow[4]);
		cap[3][1].uv = new UV(uForCol[5], vForRow[5]);

		// create vertices for first ring
		Vertex[][] rings = new Vertex[9][4];
		int ringBottomY = centerY - halfLength + bevelLength;
		int ringTopY = centerY + halfLength - bevelLength;
		rings[0][LOWER] = new Vertex(centerX - halfLength, ringBottomY, centerZ - halfLength + bevelLength);
		rings[1][LOWER] = new Vertex(centerX - halfLength, ringBottomY, centerZ + halfLength - bevelLength);
		rings[2][LOWER] = new Vertex(centerX - halfLength + bevelLength, ringBottomY, centerZ + halfLength);
		rings[3][LOWER] = new Vertex(centerX + halfLength - bevelLength, ringBottomY, centerZ + halfLength);
		rings[4][LOWER] = new Vertex(centerX + halfLength, ringBottomY, centerZ + halfLength - bevelLength);
		rings[5][LOWER] = new Vertex(centerX + halfLength, ringBottomY, centerZ - halfLength + bevelLength);
		rings[6][LOWER] = new Vertex(centerX + halfLength - bevelLength, ringBottomY, centerZ - halfLength);
		rings[7][LOWER] = new Vertex(centerX - halfLength + bevelLength, ringBottomY, centerZ - halfLength);
		rings[8][LOWER] = new Vertex(centerX - halfLength, ringBottomY, centerZ - halfLength + bevelLength);

		// create other rings
		for (int i = 0; i < 9; i++) {
			int j = ((i + 1) / 2) % 4;
			rings[i][UPPER] = new Vertex(rings[i][LOWER].getCurrentX(), ringTopY, rings[i][LOWER].getCurrentZ());
			rings[i][TOP] = new Vertex(cap[j][1].getCurrentX(), centerY + halfLength, cap[j][1].getCurrentZ());
			rings[i][BOT] = new Vertex(cap[j][0].getCurrentX(), centerY - halfLength, cap[j][0].getCurrentZ());
		}

		for (int i = 0; i < 9; i++) {
			rings[i][TOP].uv = new UV(uForCol[i], vForRow[0]);
			rings[i][UPPER].uv = new UV(uForCol[i], vForRow[1]);
			rings[i][LOWER].uv = new UV(uForCol[i], vForRow[2]);
			rings[i][BOT].uv = new UV(uForCol[i], vForRow[3]);
		}

		// create vertices for miter
		Vertex[][] miter = new Vertex[4][2];

		if (sharpMiter) {
			int halfBevelLength = Math.round(bevelLength / 2.0f);
			int miterTopY = centerY + halfLength - halfBevelLength;
			int miterBottomY = centerY - halfLength + halfBevelLength;

			miter[0][0] = new Vertex(centerX - halfLength + halfBevelLength, miterBottomY, centerZ + halfLength - halfBevelLength);
			miter[1][0] = new Vertex(centerX + halfLength - halfBevelLength, miterBottomY, centerZ + halfLength - halfBevelLength);
			miter[2][0] = new Vertex(centerX + halfLength - halfBevelLength, miterBottomY, centerZ - halfLength + halfBevelLength);
			miter[3][0] = new Vertex(centerX - halfLength + halfBevelLength, miterBottomY, centerZ - halfLength + halfBevelLength);

			for (int i = 0; i < 4; i++)
				miter[i][1] = new Vertex(miter[i][0].getCurrentX(), miterTopY, miter[i][0].getCurrentZ());
		}
		else {
			miter[0][0] = new Vertex(centerX - halfLength + bevelLength, centerY - halfLength, centerZ + halfLength - bevelLength);
			miter[1][0] = new Vertex(centerX + halfLength - bevelLength, centerY - halfLength, centerZ + halfLength - bevelLength);
			miter[2][0] = new Vertex(centerX + halfLength - bevelLength, centerY - halfLength, centerZ - halfLength + bevelLength);
			miter[3][0] = new Vertex(centerX - halfLength + bevelLength, centerY - halfLength, centerZ - halfLength + bevelLength);

			for (int i = 0; i < 4; i++)
				miter[i][1] = new Vertex(miter[i][0].getCurrentX(), centerY + halfLength, miter[i][0].getCurrentZ());
		}

		for (int i = 0; i < 4; i++) {
			if (sharpMiter) {
				miter[i][1].uv = new UV((i + 1) * UV_SCALE, (vForRow[0] + vForRow[1]) / 2.0f);
				miter[i][0].uv = new UV((i + 1) * UV_SCALE, (vForRow[3] + vForRow[4]) / 2.0f);
			}
			else {
				miter[i][0].uv = new UV((i + 1) * UV_SCALE, vForRow[3]);
				miter[i][1].uv = new UV((i + 1) * UV_SCALE, vForRow[0]);
			}
		}

		TriangleBatch batch = new TriangleBatch(null);

		// add rings
		for (int i = 0; i < 8; i++) {
			batch.triangles.add(new Triangle(rings[i][LOWER], rings[i + 1][LOWER], rings[i][UPPER]));
			batch.triangles.add(new Triangle(rings[i][UPPER], rings[i + 1][LOWER], rings[i + 1][UPPER]));
		}

		// add top
		batch.triangles.add(new Triangle(cap[0][0], cap[2][0], cap[1][0]));
		batch.triangles.add(new Triangle(cap[2][0], cap[0][0], cap[3][0]));

		// add bottom
		batch.triangles.add(new Triangle(cap[0][1], cap[1][1], cap[2][1]));
		batch.triangles.add(new Triangle(cap[2][1], cap[3][1], cap[0][1]));

		// add bevels
		int k = 0;
		for (int i = 0; i < 4; i++) {
			batch.triangles.add(new Triangle(rings[k + 1][UPPER], rings[k + 1][TOP], rings[k][TOP]));
			batch.triangles.add(new Triangle(rings[k][UPPER], rings[k + 1][UPPER], rings[k][TOP]));

			batch.triangles.add(new Triangle(rings[k + 1][LOWER], rings[k][BOT], rings[k + 1][BOT]));
			batch.triangles.add(new Triangle(rings[k][LOWER], rings[k][BOT], rings[k + 1][LOWER]));

			k += 2;
		}

		// miters
		if (sharpMiter) {
			batch.triangles.add(new Triangle(rings[1][UPPER], rings[2][UPPER], miter[0][1]));
			batch.triangles.add(new Triangle(rings[1][UPPER], rings[2][TOP], miter[0][1]));
			batch.triangles.add(new Triangle(rings[1][TOP], rings[2][UPPER], miter[0][1]));

			//TODO
		}
		else {
			batch.triangles.add(new Triangle(rings[1][UPPER], rings[2][UPPER], miter[0][1]));
			batch.triangles.add(new Triangle(rings[3][UPPER], rings[4][UPPER], miter[1][1]));
			batch.triangles.add(new Triangle(rings[5][UPPER], rings[6][UPPER], miter[2][1]));
			batch.triangles.add(new Triangle(rings[7][UPPER], rings[8][UPPER], miter[3][1]));

			batch.triangles.add(new Triangle(rings[2][LOWER], rings[1][LOWER], miter[0][0]));
			batch.triangles.add(new Triangle(rings[4][LOWER], rings[3][LOWER], miter[1][0]));
			batch.triangles.add(new Triangle(rings[6][LOWER], rings[5][LOWER], miter[2][0]));
			batch.triangles.add(new Triangle(rings[8][LOWER], rings[7][LOWER], miter[3][0]));
		}

		return batch;
	}
}
