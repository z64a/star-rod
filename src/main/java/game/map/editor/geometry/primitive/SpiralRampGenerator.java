package game.map.editor.geometry.primitive;

import javax.swing.JCheckBox;

import game.map.editor.ui.dialogs.GeneratePrimitiveOptionsDialog;
import game.map.mesh.Triangle;
import game.map.mesh.Vertex;
import game.map.shape.TriangleBatch;
import game.map.shape.UV;
import util.MathUtil;
import util.ui.LabeledIntegerSpinner;

public class SpiralRampGenerator extends ShapeGenerator
{
	private final LabeledIntegerSpinner innerRadiusSpinner;
	private final LabeledIntegerSpinner outerRadiusSpinner;
	private final LabeledIntegerSpinner divisionsSpinner;
	private final LabeledIntegerSpinner angleSpinner;
	private final LabeledIntegerSpinner riseSpinner;
	private final LabeledIntegerSpinner taperSpinner;

	private final LabeledIntegerSpinner sideHeightSpinner;
	private final JCheckBox makeBottomCheckbox;

	public SpiralRampGenerator()
	{
		super(Primitive.SPIRAL_RAMP);

		riseSpinner = new LabeledIntegerSpinner("Step Rise", 1, 500, 10);
		innerRadiusSpinner = new LabeledIntegerSpinner("Inner Radius", 0, 5000, 100);
		outerRadiusSpinner = new LabeledIntegerSpinner("Outer Radius", 10, 5000, 200);
		divisionsSpinner = new LabeledIntegerSpinner("Segments", 2, 256, 24);
		angleSpinner = new LabeledIntegerSpinner("Arc Angle", 15, 1440, 360);

		taperSpinner = new LabeledIntegerSpinner("Taper Percent", 0, 100, 0);

		sideHeightSpinner = new LabeledIntegerSpinner("Side Height", 0, 500, 0);

		makeBottomCheckbox = new JCheckBox(" Create Bottom Faces");
		makeBottomCheckbox.setSelected(false);
		makeBottomCheckbox.setFont(makeBottomCheckbox.getFont().deriveFont(12f));
	}

	@Override
	public void addFields(GeneratePrimitiveOptionsDialog panel)
	{
		panel.addSpinner(riseSpinner);
		panel.addSpinner(innerRadiusSpinner);
		panel.addSpinner(outerRadiusSpinner);
		panel.addSpinner(divisionsSpinner);
		panel.addSpinner(angleSpinner);
		panel.addSpinner(taperSpinner);
		panel.addSpinner(sideHeightSpinner);
		panel.addCheckBox(makeBottomCheckbox);
	}

	@Override
	public void setVisible(boolean b)
	{
		riseSpinner.setVisible(b);
		innerRadiusSpinner.setVisible(b);
		outerRadiusSpinner.setVisible(b);
		divisionsSpinner.setVisible(b);
		angleSpinner.setVisible(b);
		taperSpinner.setVisible(b);
		sideHeightSpinner.setVisible(b);
		makeBottomCheckbox.setVisible(b);
	}

	@Override
	public TriangleBatch generate(int centerX, int centerY, int centerZ)
	{
		int steps = divisionsSpinner.getValue();
		int stepRise = riseSpinner.getValue();
		int innerRadius = innerRadiusSpinner.getValue();
		int outerRadius = outerRadiusSpinner.getValue();
		int angle = angleSpinner.getValue();
		int taperPercent = taperSpinner.getValue();

		int sideHeight = sideHeightSpinner.getValue();
		boolean makeBottom = makeBottomCheckbox.isSelected();

		if (innerRadius == outerRadius)
			outerRadius = innerRadius + 50;

		return generate(steps, stepRise, innerRadius, outerRadius, angle,
			taperPercent, sideHeight, makeBottom,
			centerX, centerY, centerZ);
	}

	private static TriangleBatch generate(
		int steps, int stepRise, int innerR, int outerR, int angle,
		int taperPercent, int sideHeight, boolean makeBottom,
		int centerX, int centerY, int centerZ)
	{
		int N = steps + 1;
		double angleRad = Math.toRadians(angle);

		int uOffset = 0;
		int vOffset = (UV_SCALE * 3) / 2;

		int overflow = UV_SCALE * steps - Short.MAX_VALUE;
		if (overflow > 0) {
			uOffset = -UV_SCALE * (1 + overflow / UV_SCALE);
			uOffset = uOffset < Short.MIN_VALUE ? -UV_SCALE * (Short.MIN_VALUE / UV_SCALE) : uOffset;
		}

		float finalRadius = MathUtil.lerp(taperPercent / 100.0f, outerR, innerR);

		Vertex[] ringI = new Vertex[N];
		Vertex[] ringO = new Vertex[N];

		for (int i = 0; i < N; i++) {
			int H = i * stepRise;
			double theta = angleRad * i / steps;
			double X = Math.sin(theta);
			double Z = Math.cos(theta);

			float radius = MathUtil.lerp(i, 0.0f, steps, outerR, finalRadius);

			ringI[i] = new Vertex(
				centerX + (int) Math.round(innerR * X),
				centerY + H,
				centerZ - (int) Math.round(innerR * Z));

			ringO[i] = new Vertex(
				centerX + (int) Math.round(radius * X),
				centerY + H,
				centerZ - (int) Math.round(radius * Z));

			ringI[i].uv = new UV(i * UV_SCALE + uOffset, 0);
			ringO[i].uv = new UV(i * UV_SCALE + uOffset, UV_SCALE);
		}

		TriangleBatch batch = new TriangleBatch(null);

		for (int i = 0; i < (N - 1); i++) {
			batch.triangles.add(new Triangle(ringO[i], ringI[i], ringI[i + 1]));
			batch.triangles.add(new Triangle(ringO[i], ringI[i + 1], ringO[i + 1]));
		}

		if (sideHeight > 0) {
			Vertex[] upperI = new Vertex[N];
			Vertex[] upperO = new Vertex[N];

			for (int i = 0; i < N; i++) {
				upperI[i] = ringI[i].deepCopy();
				upperO[i] = ringO[i].deepCopy();

				upperI[i].uv = new UV(i * UV_SCALE + uOffset, vOffset);
				upperO[i].uv = new UV(i * UV_SCALE + uOffset, vOffset);
			}

			float uvScale = UV_SCALE / (outerR - innerR);

			Vertex[] lowerI = new Vertex[N];
			Vertex[] lowerO = new Vertex[N];
			for (int i = 0; i < steps + 1; i++) {
				lowerI[i] = new Vertex(
					upperI[i].getCurrentX(),
					upperI[i].getCurrentY() - sideHeight,
					upperI[i].getCurrentZ());
				lowerO[i] = new Vertex(
					upperO[i].getCurrentX(),
					upperO[i].getCurrentY() - sideHeight,
					upperO[i].getCurrentZ());

				lowerI[i].uv = new UV(upperI[i].uv.getU() + uOffset, vOffset + uvScale * sideHeight);
				lowerO[i].uv = new UV(upperO[i].uv.getU() + uOffset, vOffset + uvScale * sideHeight);
			}

			for (int i = 0; i < steps; i++) {
				batch.triangles.add(new Triangle(lowerI[i + 1], upperI[i], lowerI[i]));
				batch.triangles.add(new Triangle(upperI[i + 1], upperI[i], lowerI[i + 1]));

				batch.triangles.add(new Triangle(upperO[i], lowerO[i + 1], lowerO[i]));
				batch.triangles.add(new Triangle(upperO[i], upperO[i + 1], lowerO[i + 1]));
			}

			if (makeBottom) {
				Vertex[] bottomI = new Vertex[steps + 1];
				Vertex[] bottomO = new Vertex[steps + 1];

				for (int i = 0; i < steps + 1; i++) {
					bottomI[i] = lowerI[i].deepCopy();
					bottomO[i] = lowerO[i].deepCopy();

					bottomI[i].uv = new UV(i * UV_SCALE + uOffset, -vOffset);
					bottomO[i].uv = new UV(i * UV_SCALE + uOffset, UV_SCALE - vOffset);
				}

				for (int i = 0; i < steps; i++) {
					batch.triangles.add(new Triangle(bottomI[i], bottomO[i], bottomI[i + 1]));
					batch.triangles.add(new Triangle(bottomI[i + 1], bottomO[i], bottomO[i + 1]));
				}
			}
		}

		return batch;
	}
}
