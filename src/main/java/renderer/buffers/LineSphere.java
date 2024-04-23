package renderer.buffers;

import game.map.editor.render.PresetColor;
import renderer.shaders.RenderState;

public class LineSphere extends BufferedLines
{
	public LineSphere(int divR, int divH, int stride)
	{
		RenderState.setColor(PresetColor.WHITE);
		RenderState.setLineWidth(1.0f);

		int[][] rings = new int[divH + 1][divR + 1];

		// caps
		int vtop = addVertex().setPosition(0, 1.0f, 0.0f).getIndex();
		int vbot = addVertex().setPosition(0, -1.0f, 0.0f).getIndex();
		for (int j = 0; j <= divR; j++) {
			rings[0][j] = vtop;
			rings[divH][j] = vbot;
		}

		// points on sphere
		for (int i = 1; i < divH; i++) {
			double pitch = Math.PI * i / divH;
			float y = (float) Math.cos(pitch);
			float rho = (float) Math.sin(pitch);

			for (int j = 0; j < divR; j++) {
				double yaw = 2.0 * Math.PI * j / divR;
				float x = rho * (float) Math.cos(yaw);
				float z = rho * (float) Math.sin(yaw);
				rings[i][j] = addVertex().setPosition(x, y, z).getIndex();
			}
			rings[i][divR] = rings[i][0];
		}

		// lattitude lines
		for (int i = 0; i < divH; i++) {
			for (int j = 0; j < divR; j += stride)
				add(rings[i][j], rings[i + 1][j]);
		}

		// longitude lines
		for (int i = 0; i < divH; i += stride) {
			for (int j = 0; j < divR; j++)
				add(rings[i][j], rings[i][j + 1]);
		}

		loadBuffers();
	}
}
