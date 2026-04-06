package game.map.marker;

import static game.map.shading.ShadingKey.*;

import java.util.Collection;
import java.util.function.Consumer;

import org.w3c.dom.Element;

import common.Vector3f;
import common.commands.AbstractCommand;
import common.commands.EditableField;
import common.commands.EditableField.EditableFieldFactory;
import common.commands.EditableField.StandardBoolName;
import game.map.Axis;
import game.map.JsonFeatures.JsonLightComp;
import game.map.JsonFeatures.JsonMarker;
import game.map.MutablePoint;
import game.map.editor.camera.MapEditViewport;
import game.map.editor.render.Renderer;
import game.map.editor.render.RenderingOptions;
import game.map.editor.render.SortedRenderable;
import game.map.editor.render.TextureManager;
import game.map.editor.selection.PickRay.PickHit;
import game.map.editor.ui.info.MarkerInfoPanel;
import game.map.shading.FalloffType;
import game.map.shape.TransformMatrix;
import renderer.shaders.RenderState;
import renderer.shaders.RenderState.PolygonMode;
import renderer.shaders.ShaderManager;
import renderer.shaders.scene.BasicTexturedShader;
import renderer.shaders.scene.LineShader;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class LightComponent extends BaseMarkerComponent
{
	private final Consumer<Object> notifyCallback = (o) -> {
		parentMarker.updateListeners(MarkerInfoPanel.TAG_SHADING);
	};

	public EditableField<Boolean> enabled = EditableFieldFactory.create(true)
		.setCallback(notifyCallback).setName(new StandardBoolName("Source")).build();

	public EditableField<Integer> color = EditableFieldFactory.create(0xB4B4B4)
		.setCallback(notifyCallback).setName("Set Color").build();

	public FalloffType falloffType = FalloffType.Uniform;

	// actual 'true' value, only changed by the editor indirectly
	public double falloffCoeff;

	// derived value from falloffCoeff & falloffType on load -- this is what is actually edited
	public double falloffDist;

	public LightComponent(Marker parent)
	{
		super(parent);
	}

	private void setFromFlags(int flags)
	{
		enabled.set((flags & 1) != 0);

		if ((flags & 0x8) != 0)
			falloffType = FalloffType.Quadratic;
		else if ((flags & 0x4) != 0)
			falloffType = FalloffType.Linear;
		else
			falloffType = FalloffType.Uniform;
	}

	private int getFlags()
	{
		int flags = enabled.get() ? 1 : 0;
		switch (falloffType) {
			case Uniform:
				break;
			case Linear:
				flags |= 4;
				break;
			case Quadratic:
				flags |= 8;
				break;
		}
		return flags;
	}

	private void setColor(int[] rgb)
	{
		setColor(rgb[0], rgb[1], rgb[2]);
	}

	private void setColor(int R, int G, int B)
	{
		color.set((R & 0xFF) << 16 | (G & 0xFF) << 8 | (B & 0xFF));
	}

	public int[] getColor()
	{
		int rgb = color.get();
		return new int[] { (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF };
	}

	@Override
	public void fromXML(XmlReader xmr, Element elem)
	{
		if (xmr.hasAttribute(elem, ATTR_LIGHT_FLAGS))
			setFromFlags((byte) xmr.readHex(elem, ATTR_LIGHT_FLAGS));

		if (xmr.hasAttribute(elem, ATTR_LIGHT_POS)) {
			int[] xyz = xmr.readIntArray(elem, ATTR_LIGHT_POS, 3);
			parentMarker.position.setPosition(xyz);
		}

		if (xmr.hasAttribute(elem, ATTR_LIGHT_COLOR))
			setColor(xmr.readIntArray(elem, ATTR_LIGHT_COLOR, 3));

		if (xmr.hasAttribute(elem, ATTR_LIGHT_COEFFICIENT)) {
			falloffCoeff = xmr.readFloat(elem, ATTR_LIGHT_COEFFICIENT);
			falloffDist = coeff2dist(falloffType, falloffCoeff);
		}
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag lightTag = xmw.createTag(TAG_LIGHT, true);

		xmw.addHex(lightTag, ATTR_LIGHT_FLAGS, getFlags());
		xmw.addIntArray(lightTag, ATTR_LIGHT_COLOR, getColor());
		xmw.addDouble(lightTag, ATTR_LIGHT_COEFFICIENT, falloffDist);

		xmw.printTag(lightTag);
	}

	@Override
	public LightComponent deepCopy(Marker copyParent)
	{
		LightComponent copy = new LightComponent(copyParent);
		copy.color.copy(color);
		copy.falloffCoeff = falloffCoeff;
		copy.falloffDist = falloffDist;
		copy.falloffType = falloffType;
		copy.enabled.copy(enabled);
		return copy;
	}

	@Override
	protected void fromJson(JsonMarker in)
	{
		if (in.lightComp == null)
			return;

		setColor(in.lightComp.rgb);
		falloffType = in.lightComp.mode;

		setByCoeff(in.lightComp.falloff);
	}

	@Override
	protected void toJson(JsonMarker out)
	{
		out.lightComp = new JsonLightComp();

		int x = parentMarker.position.getX();
		int y = parentMarker.position.getY();
		int z = parentMarker.position.getZ();

		out.lightComp.rgb = getColor();
		out.lightComp.pos = new int[] { x, y, z };
		out.lightComp.mode = falloffType;

		out.lightComp.falloff = (float) falloffCoeff;
	}

	public void setByCoeff(float coeff)
	{
		falloffCoeff = coeff;
		falloffDist = coeff2dist(falloffType, falloffCoeff);
	}

	public void setByDist(double dist)
	{
		falloffDist = dist;
		falloffCoeff = dist2coeff(falloffType, dist);
	}

	public static double coeff2dist(FalloffType type, double coeff)
	{
		if (coeff == 0.0)
			return 0.0;

		switch (type) {
			case Linear:
				return 1.0 / coeff;
			case Quadratic:
				return 1.0 / Math.sqrt(coeff);
			default:
			case Uniform:
				return coeff;
		}
	}

	public static double dist2coeff(FalloffType type, double dist)
	{
		if (dist == 0.0)
			return 0.0;

		switch (type) {
			case Linear:
				return 1.0 / dist;
			case Quadratic:
				return 1.0 / (dist * dist);
			default:
			case Uniform:
				return dist;
		}
	}

	@Override
	public void addRenderables(RenderingOptions opts, Collection<SortedRenderable> renderables, PickHit shadowHit)
	{
		// no shadow for light markers
	}

	@Override
	public void render(RenderingOptions opts, MapEditViewport view, Renderer renderer)
	{
		if (opts.thumbnailMode)
			return;

		MutablePoint pos = parentMarker.position;
		int[] rgb = getColor();

		if (parentMarker.selected) {
			TransformMatrix mtx = TransformMatrix.identity();
			mtx.scale(falloffDist);
			mtx.translate(pos.getX(), pos.getY(), pos.getZ());

			LineShader shader = ShaderManager.use(LineShader.class);
			shader.color.set(rgb[0] / 255.0f, rgb[1] / 255.0f, rgb[2] / 255.0f, 1.0f);
			shader.useVertexColor.set(false);

			RenderState.setLineWidth(1.0f);
			RenderState.setDepthWrite(false);
			Renderer.instance().renderLineSphere48(mtx);
			RenderState.setDepthWrite(true);
		}

		RenderState.setPolygonMode(PolygonMode.FILL);
		BasicTexturedShader texShader = ShaderManager.use(BasicTexturedShader.class);
		texShader.texture.bind(TextureManager.glLightTexID);
		texShader.baseColor.set(rgb[0] / 255.0f, rgb[1] / 255.0f, rgb[2] / 255.0f, 1.0f);
		texShader.multiplyBaseColor.set(true);
		texShader.selected.set(parentMarker.selected);

		float renderYaw = 0;
		float renderPitch = 0;

		switch (view.type) {
			case PERSPECTIVE:
				Vector3f deltaPos = Vector3f.sub(view.camera.pos, pos.getVector());
				double R = Math.sqrt(deltaPos.x * deltaPos.x + deltaPos.z * deltaPos.z);
				renderYaw = -(float) Math.toDegrees(Math.atan2(deltaPos.x, deltaPos.z));
				renderPitch = (float) Math.toDegrees(Math.atan2(deltaPos.y, R));
				break;
			case FRONT:
				break;
			case SIDE:
				renderYaw = 90.0f;
				break;
			case TOP:
				renderPitch = 90.0f;
				break;
		}

		TransformMatrix mtx = TransformMatrix.identity();
		mtx.rotate(Axis.X, -renderPitch);
		mtx.rotate(Axis.Y, -renderYaw);
		mtx.translate(pos.getX(), pos.getY(), pos.getZ());

		texShader.setXYQuadCoords(-25, -25, 25, 25, 0);
		texShader.renderQuad(mtx);

		RenderState.setModelMatrix(null);
	}

	public static final class SetLightFalloff extends AbstractCommand
	{
		private final LightComponent comp;
		private final double oldValue;
		private final double newValue;

		public SetLightFalloff(LightComponent comp, double newFalloff)
		{
			super("Set Source Radius");
			this.comp = comp;
			oldValue = comp.falloffDist;
			newValue = newFalloff;
		}

		@Override
		public boolean modifiesData()
		{
			return false;
		}

		@Override
		public boolean shouldExec()
		{
			return oldValue != newValue;
		}

		@Override
		public void exec()
		{
			super.exec();
			comp.setByDist(newValue);
			comp.parentMarker.updateListeners(MarkerInfoPanel.TAG_SHADING);
		}

		@Override
		public void undo()
		{
			super.undo();
			comp.setByDist(oldValue);
			comp.parentMarker.updateListeners(MarkerInfoPanel.TAG_SHADING);
		}
	}

	public static final class SetFalloffType extends AbstractCommand
	{
		private final LightComponent comp;
		private final FalloffType oldValue;
		private final FalloffType newValue;
		private final double dist;

		public SetFalloffType(LightComponent comp, FalloffType newType)
		{
			super("Set Falloff Type");
			this.comp = comp;
			oldValue = comp.falloffType;
			newValue = newType;
			dist = comp.falloffDist;
		}

		@Override
		public boolean modifiesData()
		{
			return false;
		}

		@Override
		public boolean shouldExec()
		{
			return oldValue != newValue;
		}

		@Override
		public void exec()
		{
			super.exec();
			comp.falloffType = newValue;
			comp.setByDist(dist);
			comp.parentMarker.updateListeners(MarkerInfoPanel.TAG_SHADING);
		}

		@Override
		public void undo()
		{
			super.undo();
			comp.falloffType = oldValue;
			comp.setByDist(dist);
			comp.parentMarker.updateListeners(MarkerInfoPanel.TAG_SHADING);
		}
	}
}
