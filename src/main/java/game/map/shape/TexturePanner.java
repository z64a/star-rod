package game.map.shape;

import static game.map.MapKey.*;

import javax.swing.DefaultListModel;
import javax.swing.JLabel;

import org.w3c.dom.Element;

import common.commands.AbstractCommand;
import game.map.editor.ui.ScriptManager;
import renderer.shaders.ShaderManager;
import renderer.shaders.scene.ModelShader;
import util.xml.XmlKey;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class TexturePanner
{
	public static final int MAIN_U = 0;
	public static final int MAIN_V = 1;
	public static final int AUX_U = 2;
	public static final int AUX_V = 3;

	public static final int MAIN_S = 4;
	public static final int MAIN_T = 5;
	public static final int AUX_S = 6;
	public static final int AUX_T = 7;

	public static final int NUM_TRACKS = 4;
	public static final int NUM_COORDS = 8;

	public static final int TEXEL_RATIO = 1024;
	public static final int DEFAULT_MAXIMUM = 0x20000;

	public final int panID;
	public final PannerParams params;

	private static final double FRAME_TIME = 1.0 / 30.0;
	private double frameTime = 0;

	// used for animation
	private final int[] accum = new int[NUM_COORDS];
	private final int[] delay = new int[NUM_COORDS];
	private final int[] value = new int[NUM_COORDS];

	public static class PannerParams
	{
		public final int[] freq = new int[NUM_TRACKS];
		public final int[] init = new int[NUM_COORDS];
		public final int[] rate = new int[NUM_COORDS];
		public int maxUV = DEFAULT_MAXIMUM;
		public int maxST = 128;

		public boolean generate;
		public boolean useTexels;

		public void set(PannerParams other)
		{
			for (int i = 0; i < NUM_COORDS; i++) {
				this.init[i] = other.init[i];
				this.rate[i] = other.rate[i];
			}

			for (int i = 0; i < NUM_TRACKS; i++) {
				this.freq[i] = other.freq[i];
			}

			this.maxUV = other.maxUV;
			this.maxST = other.maxST;
			this.generate = other.generate;
			this.useTexels = other.useTexels;
		}

		public PannerParams get()
		{
			PannerParams copy = new PannerParams();
			copy.set(this);
			return copy;
		}

		public void setMax(int value)
		{
			if (useTexels)
				maxST = value;
			else
				maxUV = value;
		}

		public int getMax()
		{
			if (useTexels)
				return maxST;
			else
				return maxUV;
		}

		public PannerParams getOutput()
		{
			PannerParams out = get();

			if (out.useTexels) {
				out.maxUV = maxST * TEXEL_RATIO;

				for (int i = 0; i < 4; i++) {
					out.init[i + MAIN_U] = init[i + MAIN_S] * TEXEL_RATIO;
					out.rate[i + MAIN_U] = rate[i + MAIN_S] * TEXEL_RATIO;
				}
			}

			return out;
		}
	}

	public boolean isNonzero()
	{
		int base = params.useTexels ? MAIN_S : MAIN_U;
		for (int i = 0; i < 4; i++) {
			if (params.init[base + i] != 0)
				return true;
			if (params.rate[base + i] != 0)
				return true;
		}
		return false;
	}

	public TexturePanner(int id)
	{
		this.panID = id;
		this.params = new PannerParams();
	}

	public void reset()
	{
		for (int i = 0; i < NUM_COORDS; i++) {
			accum[i] = 0;
			delay[i] = 0;
		}
		frameTime = 0;
	}

	public void tick(double deltaTime)
	{
		frameTime += deltaTime;
		if (frameTime < FRAME_TIME)
			return;

		frameTime -= FRAME_TIME;

		for (int i = 0; i < NUM_COORDS; i++) {
			if (params.rate[i] == 0)
				accum[i] = 0;

			if (params.freq[i % NUM_TRACKS] > 0) {
				delay[i]++;
				if (delay[i] >= params.freq[i % NUM_TRACKS]) {
					accum[i] += params.rate[i];
					delay[i] = 0;
				}
			}

			int max = (i >= MAIN_S) ? params.maxST : params.maxUV;

			// compute value and clamp
			value[i] = params.init[i] + accum[i];
			if (max < value[i])
				value[i] -= max;
			else if (value[i] < 0)
				value[i] += max;

			// clamp accumulator value
			if (max < accum[i])
				accum[i] -= max;
			else if (accum[i] < 0)
				accum[i] += max;
		}
	}

	public void setShaderParams()
	{
		ModelShader modelShader = ShaderManager.get(ModelShader.class);
		if (params.useTexels) {
			modelShader.mainScroll.set(value[MAIN_S] * TEXEL_RATIO, value[MAIN_T] * TEXEL_RATIO);
			modelShader.auxScroll.set(value[AUX_S] * TEXEL_RATIO, value[AUX_T] * TEXEL_RATIO);
		}
		else {
			modelShader.mainScroll.set(value[MAIN_U], value[MAIN_V]);
			modelShader.auxScroll.set(value[AUX_U], value[AUX_V]);
		}
	}

	// load from XML tag
	public static void load(DefaultListModel<TexturePanner> texPanUnits, XmlReader xmr, Element pannerElem)
	{
		xmr.requiresAttribute(pannerElem, ATTR_PAN_ID);
		int pannerID = xmr.readHex(pannerElem, ATTR_PAN_ID);
		if (pannerID > 15 || pannerID < 0)
			xmr.complain("Invalid panner ID: " + pannerID);

		TexturePanner panner = texPanUnits.get(pannerID);
		if (xmr.hasAttribute(pannerElem, ATTR_GENERATE))
			panner.params.generate = xmr.readBoolean(pannerElem, ATTR_GENERATE);

		if (xmr.hasAttribute(pannerElem, ATTR_USE_TEXELS))
			panner.params.useTexels = xmr.readBoolean(pannerElem, ATTR_USE_TEXELS);

		if (xmr.hasAttribute(pannerElem, ATTR_PAN_MAX)) {
			int max = xmr.readInt(pannerElem, ATTR_PAN_MAX);
			if (panner.params.useTexels) {
				panner.params.maxUV = max * TEXEL_RATIO;
				panner.params.maxST = max;
			}
			else {
				panner.params.maxUV = max;
				panner.params.maxST = max / TEXEL_RATIO;
			}
		}

		loadCoords(xmr, pannerElem, ATTR_PAN_INIT, panner.params.init, panner.params.useTexels);
		loadCoords(xmr, pannerElem, ATTR_PAN_STEP, panner.params.rate, panner.params.useTexels);

		if (xmr.hasAttribute(pannerElem, ATTR_PAN_FREQ)) {
			int[] vals = xmr.readIntArray(pannerElem, ATTR_PAN_FREQ, 4);
			for (int i = 0; i < vals.length; i++) {
				panner.params.freq[i] = vals[i];
			}
		}
	}

	// load from script params
	private static void loadCoords(XmlReader xmr, Element pannerElem, XmlKey key, int[] array, boolean useTexels)
	{
		if (xmr.hasAttribute(pannerElem, key)) {
			int[] vals = xmr.readIntArray(pannerElem, key, 4);
			for (int i = 0; i < vals.length; i++) {
				if (useTexels) {
					array[MAIN_U + i] = vals[i] * TEXEL_RATIO;
					array[MAIN_S + i] = vals[i];
				}
				else {
					array[MAIN_U + i] = vals[i];
					array[MAIN_S + i] = vals[i] / TEXEL_RATIO;
				}
			}
		}
	}

	public void toXML(XmlWriter xmw)
	{
		XmlTag pannerTag = xmw.createTag(TAG_PANNER, true);
		xmw.addHex(pannerTag, ATTR_PAN_ID, panID);
		xmw.addBoolean(pannerTag, ATTR_GENERATE, params.generate);
		xmw.addBoolean(pannerTag, ATTR_USE_TEXELS, params.useTexels);
		xmw.addInt(pannerTag, ATTR_PAN_MAX, params.useTexels ? params.maxST : params.maxUV);

		saveCoords(xmw, pannerTag, ATTR_PAN_INIT, params.init, params.useTexels);
		saveCoords(xmw, pannerTag, ATTR_PAN_STEP, params.rate, params.useTexels);

		xmw.addIntArray(pannerTag, ATTR_PAN_FREQ, params.freq[0], params.freq[1], params.freq[2], params.freq[3]);

		xmw.printTag(pannerTag);
	}

	private static void saveCoords(XmlWriter xmw, XmlTag tag, XmlKey key, int[] array, boolean useTexels)
	{
		if (useTexels)
			xmw.addIntArray(tag, key, array[MAIN_S], array[MAIN_T], array[AUX_S], array[AUX_T]);
		else
			xmw.addIntArray(tag, key, array[MAIN_U], array[MAIN_V], array[AUX_U], array[AUX_V]);
	}

	public static class SetTexPannerParams extends AbstractCommand
	{
		private final TexturePanner panner;

		private final PannerParams oldParams;
		private final PannerParams newParams;

		public SetTexPannerParams(TexturePanner panner, PannerParams originalParams)
		{
			super("Set Tex Panner Params");
			this.panner = panner;

			oldParams = originalParams.get();
			newParams = panner.params.get();
		}

		@Override
		public boolean shouldExec()
		{
			return !oldParams.equals(newParams);
		}

		@Override
		public void exec()
		{
			super.exec();
			panner.params.set(newParams);
			panner.reset();
			ScriptManager.instance().updatePannersTab();
		}

		@Override
		public void undo()
		{
			super.undo();
			panner.params.set(oldParams);
			panner.reset();
			ScriptManager.instance().updatePannersTab();
		}
	}

	public void setLabels(JLabel main, JLabel aux)
	{
		if (params.useTexels) {
			setLabel(main, MAIN_S);
			setLabel(aux, AUX_S);
		}
		else {
			setLabel(main, MAIN_U);
			setLabel(aux, AUX_U);
		}
	}

	private void setLabel(JLabel lbl, int channel)
	{
		String u = getText(channel);
		String v = getText(channel + 1);

		lbl.setText("<html><div style='text-align: center;'>" + u + "<br>" + v + "</html>");
	}

	private String getText(int channel)
	{
		StringBuilder sb = new StringBuilder();

		int init = params.init[channel];
		int rate = params.rate[channel];

		if (init != 0) {
			sb.append(init);

			if (rate != 0) {
				sb.append((rate < 0) ? " - " : " + ");
				sb.append(Math.abs(rate));
				sb.append("t");
			}
		}
		else {
			if (rate == 0) {
				sb.append("0");
			}
			else {
				sb.append((rate < 0) ? "-" : "+");
				sb.append(Math.abs(rate));
				sb.append("t");
			}
		}

		return sb.toString();
	}
}
