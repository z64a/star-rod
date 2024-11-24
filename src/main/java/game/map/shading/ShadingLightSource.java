package game.map.shading;

import static game.map.shading.ShadingKey.*;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

import org.w3c.dom.Element;

import common.Vector3f;
import game.ProjectDatabase;
import game.map.Axis;
import game.map.MutablePoint;
import game.map.editor.MapEditor;
import game.map.editor.PointObject;
import game.map.editor.camera.MapEditViewport;
import game.map.editor.commands.AbstractCommand;
import game.map.editor.commands.fields.EditableField;
import game.map.editor.commands.fields.EditableField.EditableFieldFactory;
import game.map.editor.commands.fields.EditableField.StandardBoolName;
import game.map.editor.render.Renderer;
import game.map.editor.render.RenderingOptions;
import game.map.editor.render.TextureManager;
import game.map.shading.SpriteShadingEditor.JsonShadingLight;
import game.map.shape.TransformMatrix;
import renderer.shaders.RenderState;
import renderer.shaders.RenderState.PolygonMode;
import renderer.shaders.ShaderManager;
import renderer.shaders.scene.BasicTexturedShader;
import renderer.shaders.scene.LineShader;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlSerializable;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class ShadingLightSource extends PointObject implements XmlSerializable
{
	public transient final ShadingProfile profile;

	private final Consumer<Object> notifyCallback = (o) -> {
		notifyListeners();
	};

	public static enum FalloffType
	{
		Uniform,
		Linear,
		Quadratic
	}

	public int unk_0E;

	public EditableField<Integer> color = EditableFieldFactory.create(0xB4B4B4)
		.setCallback(notifyCallback).setName("Set Color").build();

	public EditableField<Float> falloff = EditableFieldFactory.create(1000.0f)
		.setCallback(notifyCallback).setName("Set Falloff").build();

	public EditableField<FalloffType> falloffType = EditableFieldFactory.create(FalloffType.Linear)
		.setCallback(notifyCallback).setName("Set Falloff Type").build();

	public EditableField<Boolean> enabled = EditableFieldFactory.create(true)
		.setCallback(notifyCallback).setName(new StandardBoolName("Source")).build();

	public ShadingLightSource(ShadingProfile parent)
	{
		super(new Vector3f(0, 0, 0), 16.0f);
		this.profile = parent;
	}

	public ShadingLightSource(ShadingProfile parent, ByteBuffer bb)
	{
		super(new Vector3f(0, 0, 0), 16.0f);
		this.profile = parent;

		int flags = bb.get();
		setFromFlags(flags);
		setColor(bb.get(), bb.get(), bb.get());
		position.setX(bb.getShort());
		position.setY(bb.getShort());
		position.setZ(bb.getShort());
		falloff.set(bb.getFloat());
		unk_0E = bb.get() & 0xFF;
		int padding = bb.get(); // always zero
		assert (padding == 0);
		assert (unk_0E == 0);
		assert ((flags & 0xF2) == 0); // only 1, 4, 8 are valid
		assert ((flags & 1) == 1); // lights are always enabled
	}

	public ShadingLightSource copy()
	{
		ShadingLightSource copy = new ShadingLightSource(profile);
		copy.position = new MutablePoint(position.getX(), position.getY(), position.getZ());
		copy.color.copy(color);
		copy.falloff.copy(falloff);
		copy.falloffType.copy(falloffType);
		copy.enabled.copy(enabled);
		copy.unk_0E = unk_0E;
		return copy;
	}

	@Override
	public void initialize()
	{}

	private void setFromFlags(int flags)
	{
		enabled.set((flags & 1) != 0);

		if ((flags & 0x8) != 0)
			falloffType.set(FalloffType.Quadratic);
		else if ((flags & 0x4) != 0)
			falloffType.set(FalloffType.Linear);
		else
			falloffType.set(FalloffType.Uniform);
	}

	private int getFlags()
	{
		int flags = enabled.get() ? 1 : 0;
		switch (falloffType.get()) {
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

	public void put(ByteBuffer bb)
	{
		bb.put((byte) getFlags());
		int[] rgb = getColor();
		bb.put((byte) rgb[0]);
		bb.put((byte) rgb[1]);
		bb.put((byte) rgb[2]);
		bb.putShort((short) position.getX());
		bb.putShort((short) position.getY());
		bb.putShort((short) position.getZ());
		bb.putFloat(falloff.get());
		bb.put((byte) unk_0E);
		bb.put((byte) 0);
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
	public String toString()
	{
		int[] rgb = getColor();
		return String.format("Color = (%d %d %d), Position = (%d %d %d)",
			rgb[0], rgb[1], rgb[2], position.getX(), position.getY(), position.getZ());
	}

	public static ShadingLightSource read(XmlReader xmr, Element elem, ShadingProfile parent)
	{
		ShadingLightSource source = new ShadingLightSource(parent);
		source.fromXML(xmr, elem);
		return source;
	}

	@Override
	public void fromXML(XmlReader xmr, Element elem)
	{
		if (xmr.hasAttribute(elem, ATTR_LIGHT_FLAGS))
			setFromFlags((byte) xmr.readHex(elem, ATTR_LIGHT_FLAGS));

		if (xmr.hasAttribute(elem, ATTR_LIGHT_POS)) {
			int[] xyz = xmr.readIntArray(elem, ATTR_LIGHT_POS, 3);
			position.setX(xyz[0]);
			position.setY(xyz[1]);
			position.setZ(xyz[2]);
			recalculateAABB();
		}

		if (xmr.hasAttribute(elem, ATTR_LIGHT_COLOR))
			setColor(xmr.readIntArray(elem, ATTR_LIGHT_COLOR, 3));

		if (xmr.hasAttribute(elem, ATTR_LIGHT_COEFFICIENT))
			falloff.set(xmr.readFloat(elem, ATTR_LIGHT_COEFFICIENT));

		if (xmr.hasAttribute(elem, ATTR_LIGHT_UNK))
			unk_0E = xmr.readHex(elem, ATTR_LIGHT_UNK);
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag lightTag = xmw.createTag(TAG_LIGHT, true);

		xmw.addHex(lightTag, ATTR_LIGHT_FLAGS, getFlags());
		xmw.addIntArray(lightTag, ATTR_LIGHT_POS, position.getX(), position.getY(), position.getZ());
		xmw.addIntArray(lightTag, ATTR_LIGHT_COLOR, getColor());
		xmw.addFloat(lightTag, ATTR_LIGHT_COEFFICIENT, falloff.get());
		xmw.addHex(lightTag, ATTR_LIGHT_UNK, unk_0E);

		xmw.printTag(lightTag);
	}

	public static ShadingLightSource read(JsonShadingLight jsonLight, ShadingProfile parent)
	{
		ShadingLightSource source = new ShadingLightSource(parent);

		if (jsonLight.rgb != null) {
			source.setColor(jsonLight.rgb);
		}

		if (jsonLight.pos != null) {
			source.position.setX(jsonLight.pos[0]);
			source.position.setY(jsonLight.pos[1]);
			source.position.setZ(jsonLight.pos[2]);
		}
		source.recalculateAABB();

		source.falloff.set(jsonLight.falloff);

		switch (jsonLight.mode.toLowerCase()) {
			case "linear":
				source.falloffType.set(FalloffType.Linear);
				break;
			case "quadratic":
				source.falloffType.set(FalloffType.Quadratic);
				break;
			case "uniform":
			default:
				source.falloffType.set(FalloffType.Uniform);
				break;
		}

		return source;
	}

	public void render(MapEditViewport viewport, RenderingOptions opts, Vector3f cameraPos, boolean uiselected)
	{
		int[] rgb = getColor();

		if (uiselected) {
			TransformMatrix mtx = TransformMatrix.identity();
			mtx.scale(100);
			mtx.translate(position.getX(), position.getY(), position.getZ());

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
		texShader.selected.set(selected);

		float renderYaw = 0;
		float renderPitch = 0;

		switch (viewport.type) {
			case PERSPECTIVE:
				Vector3f deltaPos = Vector3f.sub(cameraPos, position.getVector());
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
		mtx.translate(position.getX(), position.getY(), position.getZ());

		texShader.setXYQuadCoords(-25, -25, 25, 25, 0);
		texShader.renderQuad(mtx);

		RenderState.setModelMatrix(null);
	}

	public static final class SetLightColor extends AbstractCommand
	{
		private final ShadingLightSource source;
		private final int oldValue;
		private final int newValue;

		public SetLightColor(ShadingLightSource source, int newColor)
		{
			super("Set Source Color");
			this.source = source;
			oldValue = source.color.get();
			newValue = newColor;
		}

		@Override
		public boolean modifiesMap()
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
			source.color.set(newValue);
			ProjectDatabase.SpriteShading.modified = true;
		}

		@Override
		public void undo()
		{
			super.undo();
			source.color.set(oldValue);
			ProjectDatabase.SpriteShading.modified = true;
		}
	}

	public static final class SetLightCoord extends AbstractCommand
	{
		private final ShadingLightSource light;
		private final int index;
		private final int oldValue;
		private final int newValue;

		public SetLightCoord(ShadingLightSource light, int index, int val)
		{
			super("Set Source Position");
			this.light = light;
			this.index = index;
			switch (index) {
				case 0:
					oldValue = light.position.getX();
					break;
				case 2:
					oldValue = light.position.getZ();
					break;
				default:
					oldValue = light.position.getY();
					break;
			}
			newValue = val;
		}

		@Override
		public boolean modifiesMap()
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
			switch (index) {
				case 0:
					light.position.setX(newValue);
					break;
				case 1:
					light.position.setY(newValue);
					break;
				case 2:
					light.position.setZ(newValue);
					break;
			}
			//	light.notifyListeners();
			light.recalculateAABB();
			ProjectDatabase.SpriteShading.modified = true;
		}

		@Override
		public void undo()
		{
			super.undo();
			switch (index) {
				case 0:
					light.position.setX(oldValue);
					break;
				case 1:
					light.position.setY(oldValue);
					break;
				case 2:
					light.position.setZ(oldValue);
					break;
			}
			//	light.notifyListeners();
			light.recalculateAABB();
			ProjectDatabase.SpriteShading.modified = true;
		}
	}

	public static final class SetLightFalloff extends AbstractCommand
	{
		private final ShadingLightSource source;
		private final float oldValue;
		private final float newValue;

		public SetLightFalloff(ShadingLightSource source, float newFalloff)
		{
			super("Set Source Falloff");
			this.source = source;
			oldValue = source.falloff.get();
			newValue = newFalloff;
		}

		@Override
		public boolean modifiesMap()
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
			source.falloff.set(newValue);
			ProjectDatabase.SpriteShading.modified = true;
		}

		@Override
		public void undo()
		{
			super.undo();
			source.falloff.set(oldValue);
			ProjectDatabase.SpriteShading.modified = true;
		}
	}

	public static final class SetLightFalloffType extends AbstractCommand
	{
		private final ShadingLightSource source;
		private final FalloffType oldValue;
		private final FalloffType newValue;

		public SetLightFalloffType(ShadingLightSource source, FalloffType newFalloff)
		{
			super("Set Falloff Type");
			this.source = source;
			oldValue = source.falloffType.get();
			newValue = newFalloff;
		}

		@Override
		public boolean modifiesMap()
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
			source.falloffType.set(newValue);
			ProjectDatabase.SpriteShading.modified = true;
		}

		@Override
		public void undo()
		{
			super.undo();
			source.falloffType.set(oldValue);
			ProjectDatabase.SpriteShading.modified = true;
		}
	}

	public static final class SetLightEnabled extends AbstractCommand
	{
		private final ShadingLightSource source;
		private final boolean oldValue;
		private final boolean newValue;

		public SetLightEnabled(ShadingLightSource source, boolean enabled)
		{
			super(enabled ? "Enable Light" : "Disable Light");
			this.source = source;
			oldValue = source.enabled.get();
			newValue = enabled;
		}

		@Override
		public boolean modifiesMap()
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
			source.enabled.set(newValue);
			ProjectDatabase.SpriteShading.modified = true;
		}

		@Override
		public void undo()
		{
			super.undo();
			source.enabled.set(oldValue);
			ProjectDatabase.SpriteShading.modified = true;
		}
	}

	@Override
	public float getRadius()
	{
		if (MapEditor.instance() == null)
			return 15.0f;
		else
			return MapEditor.instance().grid.binary ? 16.0f : 15.0f;
	}
}
