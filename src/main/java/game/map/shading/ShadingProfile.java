package game.map.shading;

import static game.map.shading.ShadingKey.*;

import java.awt.Color;
import java.awt.Component;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JList;

import org.w3c.dom.Element;

import app.SwingUtils;
import common.BaseCamera;
import common.Vector3f;
import game.ProjectDatabase;
import game.map.Axis;
import game.map.editor.MapEditor;
import game.map.editor.UpdateProvider;
import game.map.editor.camera.MapEditViewport;
import game.map.editor.commands.AbstractCommand;
import game.map.editor.commands.fields.EditableField;
import game.map.editor.commands.fields.EditableField.EditableFieldFactory;
import game.map.editor.render.RenderingOptions;
import game.map.shading.ShadingLightSource.FalloffType;
import game.map.shading.SpriteShadingEditor.JsonShadingLight;
import game.map.shading.SpriteShadingEditor.JsonShadingProfile;
import game.map.shape.TransformMatrix;
import game.sprite.Sprite;
import renderer.shaders.scene.SpriteShader;
import util.IterableListModel;
import util.MathUtil;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlSerializable;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class ShadingProfile extends UpdateProvider implements XmlSerializable
{
	private final Consumer<Object> notifyCallback = (o) -> {
		notifyListeners();
	};

	public EditableField<String> name = EditableFieldFactory.create("")
		.setCallback(notifyCallback).setName("Set Name").build();

	public EditableField<Integer> color = EditableFieldFactory.create(0xB4B4B4)
		.setCallback(notifyCallback).setName("Set Color").build();

	public EditableField<Integer> intensity = EditableFieldFactory.create(30)
		.setCallback(notifyCallback).setName("Set Intensity").build();

	public static final int MAX_LIGHTS = 7;
	public final IterableListModel<ShadingLightSource> sources;

	public boolean vanilla;

	public final int group;
	public int key;

	public transient ShadingLightSource selectedSource = null;
	public transient boolean invalidName = false;

	public ShadingProfile(int group, int index)
	{
		this.group = group;
		this.key = (group << 16) | index;

		sources = new IterableListModel<>();
	}

	private void setColor(int[] rgb)
	{
		setColor(rgb[0], rgb[1], rgb[2]);
	}

	private void setColor(int R, int G, int B)
	{
		color.set((R & 0xFF) << 16 | (G & 0xFF) << 8 | (B & 0xFF));

	}

	private int[] getColor()
	{
		int rgb = color.get();
		return new int[] { (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF };
	}

	public ShadingProfile(ByteBuffer bb, int group, int index)
	{
		this.group = group;
		this.key = (group << 16) | index;

		int num = bb.get();
		int zero = bb.get();
		assert (zero == 0);

		setColor(bb.get(), bb.get(), bb.get());
		intensity.set(bb.get() & 0xFF);

		sources = new IterableListModel<>();
		for (int i = 0; i < num; i++)
			sources.addElement(new ShadingLightSource(this, bb));
	}

	public ByteBuffer getData()
	{
		ByteBuffer buf = ByteBuffer.allocateDirect(6 + 16 * sources.size());

		buf.put((byte) sources.size());
		buf.put((byte) 0);

		int[] rgb = getColor();
		buf.put((byte) rgb[0]);
		buf.put((byte) rgb[1]);
		buf.put((byte) rgb[2]);
		buf.put((byte) (int) intensity.get());

		for (ShadingLightSource source : sources)
			source.put(buf);

		return buf;
	}

	@Override
	public String toString()
	{
		return name.get();
	}

	public static ShadingProfile read(JsonShadingProfile jsonProfile, int groupIdx, int profileIdx)
	{
		ShadingProfile profile = new ShadingProfile(groupIdx, profileIdx);

		profile.name.set(jsonProfile.name);
		profile.intensity.set(jsonProfile.power);

		if (jsonProfile.ambient != null) {
			profile.setColor(jsonProfile.ambient);
		}

		if (jsonProfile.lights != null) {
			for (JsonShadingLight light : jsonProfile.lights) {
				profile.sources.addElement(ShadingLightSource.read(light, profile));
			}
		}

		if (profile.name.get().isEmpty())
			profile.name.set(String.format("%08X", profile.key));

		return profile;
	}

	public static ShadingProfile read(XmlReader xmr, Element elem, int groupIdx, int profileIdx)
	{
		ShadingProfile profile = new ShadingProfile(groupIdx, profileIdx);
		profile.fromXML(xmr, elem);
		if (profile.name.get().isEmpty())
			profile.name.set(String.format("%08X", profile.key));
		return profile;
	}

	@Override
	public void fromXML(XmlReader xmr, Element elem)
	{
		if (xmr.hasAttribute(elem, ATTR_PROFILE_NAME))
			name.set(xmr.getAttribute(elem, ATTR_PROFILE_NAME));

		if (xmr.hasAttribute(elem, ATTR_PROFILE_VANILLA))
			vanilla = xmr.readBoolean(elem, ATTR_PROFILE_VANILLA);

		if (xmr.hasAttribute(elem, ATTR_PROFILE_POWER))
			intensity.set(xmr.readHex(elem, ATTR_PROFILE_POWER));

		if (xmr.hasAttribute(elem, ATTR_PROFILE_AMBIENT))
			setColor(xmr.readIntArray(elem, ATTR_PROFILE_AMBIENT, 3));

		for (Element tagElem : xmr.getTags(elem, TAG_LIGHT))
			sources.addElement(ShadingLightSource.read(xmr, tagElem, this));
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		boolean selfClose = (sources.size() == 0);
		XmlTag profileTag = xmw.createTag(TAG_PROFILE, selfClose);

		if (!name.get().isEmpty())
			xmw.addAttribute(profileTag, ATTR_PROFILE_NAME, name.get());
		else
			xmw.addAttribute(profileTag, ATTR_PROFILE_NAME, String.format("%08X", key));

		if (vanilla)
			xmw.addBoolean(profileTag, ATTR_PROFILE_VANILLA, vanilla);

		xmw.addHex(profileTag, ATTR_PROFILE_POWER, intensity.get());
		xmw.addIntArray(profileTag, ATTR_PROFILE_AMBIENT, getColor());

		if (selfClose) {
			xmw.printTag(profileTag);
		}
		else {
			xmw.openTag(profileTag);
			for (ShadingLightSource source : sources)
				source.toXML(xmw);
			xmw.closeTag(profileTag);
		}
	}

	public void render(MapEditViewport viewport, RenderingOptions opts, Vector3f pos)
	{
		for (ShadingLightSource light : sources)
			light.render(viewport, opts, pos, (selectedSource != null) && (light == selectedSource));
	}

	private transient TransformMatrix viewMtx = new TransformMatrix();
	private transient TransformMatrix modelMtx = new TransformMatrix();
	private transient TransformMatrix projViewMtx = null;

	private transient float[] highlightColor = new float[3];
	private transient float[] shadowColor = new float[3];
	private transient float[] offset = new float[3];

	public void setSpriteRenderingPos(BaseCamera camera, float x, float y, float z, float yaw)
	{
		// these must be built in EXACTLY this manner!

		modelMtx.setIdentity();
		modelMtx.scale(Sprite.WORLD_SCALE, Sprite.WORLD_SCALE, Sprite.WORLD_SCALE);
		modelMtx.rotate(Axis.Y, yaw);
		modelMtx.translate(new Vector3f(x, y, z));

		viewMtx.setIdentity();
		viewMtx.translate(new Vector3f(-camera.pos.x, -camera.pos.y, -camera.pos.z));
		viewMtx.rotate(Axis.Y, camera.yaw);
		viewMtx.rotate(Axis.X, camera.pitch);

		projViewMtx = TransformMatrix.multiply(camera.projMatrix, viewMtx);
	}

	public void calculateShaderParams(TransformMatrix mtx)
	{
		double Myx = modelMtx.get(1, 0);
		double Myy = modelMtx.get(1, 1);
		double Myz = modelMtx.get(1, 2);

		double Mzx = modelMtx.get(2, 0);
		double Pzx = projViewMtx.get(2, 0);

		double Mzy = modelMtx.get(2, 1);

		double Mzz = modelMtx.get(2, 2);
		double Pzz = projViewMtx.get(2, 2);

		float[] lightDir = new float[3];
		float[] commonColor = new float[3];

		float[] frontHighlightColor = new float[3];
		float[] backHighlightColor = new float[3];

		highlightColor = new float[3];
		shadowColor = new float[3];
		offset = new float[2];

		int[] rgb = getColor();
		shadowColor[0] = rgb[0];
		shadowColor[1] = rgb[1];
		shadowColor[2] = rgb[2];

		boolean facingLeft = ((Mzz * Pzz - Mzx * Pzx) < 0);

		for (ShadingLightSource source : sources) {
			if (!source.enabled.get())
				continue;

			// might not be correct, unsure if we're adding component offsets correctly here
			double dx = (modelMtx.get(0, 3) + mtx.get(3, 0)) - source.position.getX();
			double dy = (modelMtx.get(1, 3) + mtx.get(3, 1)) - source.position.getY();
			double dz = (modelMtx.get(2, 3) + mtx.get(3, 2)) - source.position.getZ();
			double dist2 = dx * dx + dy * dy + dz * dz;

			double dist = 0.0;
			double invDist = 0.0;
			if (dist2 > MathUtil.VERY_SMALL_NUMBER) {
				dist = Math.sqrt(dist2);
				invDist = 1.0 / dist;
			}

			double nx = dx * invDist;
			double ny = dy * invDist;
			double nz = dz * invDist;

			double intensityScale = 1.0;

			if (source.falloffType.get() == FalloffType.Linear) {
				// logic seems sus, should be AND i think. bug in original source?
				if (dist != 0.0 || source.falloff.get() != 0.0)
					intensityScale = 1.0 / (dist * source.falloff.get());
			}
			else if (source.falloffType.get() == FalloffType.Quadratic) {
				if (dist2 != 0.0 || source.falloff.get() != 0.0)
					intensityScale = 1.0 / (dist2 * source.falloff.get());
			}

			nx *= intensityScale;
			ny *= intensityScale;
			nz *= intensityScale;

			lightDir[0] += nx;
			lightDir[1] += ny;
			lightDir[2] += nz;

			if (1.0 < intensityScale)
				intensityScale = 1.0;

			double Uzx = facingLeft ? -Mzx : Mzx;
			double Uzz = facingLeft ? Mzz : -Mzz;
			double Su = Uzx * nx + Mzy * ny + Uzz * nz;
			double Ru = intensityScale * Math.abs(Su);

			double Vzx = facingLeft ? -Mzx : Mzx;
			double Vzz = facingLeft ? -Mzz : Mzz;
			double Sv = Vzz * nx + Mzy * ny + Vzx * nz;
			double Rv = intensityScale * Math.abs(Sv);

			int[] lightColor = source.getColor();

			if (0.0 < Su) {
				commonColor[0] += lightColor[0] * Ru;
				commonColor[1] += lightColor[1] * Ru;
				commonColor[2] += lightColor[2] * Ru;
			}
			else {
				shadowColor[0] += lightColor[0] * Ru;
				shadowColor[1] += lightColor[1] * Ru;
				shadowColor[2] += lightColor[2] * Ru;
			}

			if (0.0 < Sv) {
				backHighlightColor[0] += lightColor[0] * Rv;
				backHighlightColor[1] += lightColor[1] * Rv;
				backHighlightColor[2] += lightColor[2] * Rv;
			}
			else {
				frontHighlightColor[0] += lightColor[0] * Rv;
				frontHighlightColor[1] += lightColor[1] * Rv;
				frontHighlightColor[2] += lightColor[2] * Rv;
			}
		}

		double Wzx = facingLeft ? -Mzx : Mzx;
		double Wzz = facingLeft ? -Mzz : Mzz;

		if (0.0 < Wzz * lightDir[0] + Mzy * lightDir[1] + Wzx * lightDir[2]) {
			highlightColor[0] = rgb[0] + commonColor[0] + backHighlightColor[0];
			highlightColor[1] = rgb[1] + commonColor[1] + backHighlightColor[1];
			highlightColor[2] = rgb[2] + commonColor[2] + backHighlightColor[2];
		}
		else {
			highlightColor[0] = rgb[0] + commonColor[0] + frontHighlightColor[0];
			highlightColor[1] = rgb[1] + commonColor[1] + frontHighlightColor[1];
			highlightColor[2] = rgb[2] + commonColor[2] + frontHighlightColor[2];
		}

		// in appendGfx function

		double lightDir2 = lightDir[0] * lightDir[0] + lightDir[1] * lightDir[1] + lightDir[2] * lightDir[2];

		double ambientPower = intensity.get();
		if (lightDir2 < 1.0)
			ambientPower = (int) (ambientPower * lightDir2);

		double invLightDir = 0.0;
		if (lightDir2 > MathUtil.VERY_SMALL_NUMBER)
			invLightDir = 1.0 / Math.sqrt(lightDir2);

		double nlx = lightDir[0] * invLightDir;
		double nly = lightDir[1] * invLightDir;
		double nlz = lightDir[2] * invLightDir;

		// this will be SLIGHTLY off since Pzz != in-game Pzz
		// it should be 1.0, but its not due to floating point shenanigans
		// to see for yourself, the value is loaded at [80148BA0]
		// everything else is accurate
		offset[0] = (float) (ambientPower * (nlz * Pzx - nlx * Pzz));
		if (!facingLeft)
			offset[0] *= -1.0f;

		double R2 = nlx * nlx + nlz * nlz;
		double R = R2;
		if (R2 > MathUtil.VERY_SMALL_NUMBER)
			R = Math.sqrt(R2);

		double K2 = Myx * Myx + Myz * Myz;
		double K = K2;
		if (K2 > MathUtil.VERY_SMALL_NUMBER)
			K = Math.sqrt(K2);

		offset[1] = (float) (-ambientPower * (R * K + Myy * nly));

		if (shadowColor[0] > 255.0)
			shadowColor[0] = 255.0f;
		if (shadowColor[1] > 255.0)
			shadowColor[1] = 255.0f;
		if (shadowColor[2] > 255.0)
			shadowColor[2] = 255.0f;

		if (highlightColor[0] > 255.0)
			highlightColor[0] = 255.0f;
		if (highlightColor[1] > 255.0)
			highlightColor[1] = 255.0f;
		if (highlightColor[2] > 255.0)
			highlightColor[2] = 255.0f;
	}

	public void setShaderParams(SpriteShader shader)
	{
		shader.shadingOffset.set(offset[0], -offset[1]);
		shader.shadingShadow.set(shadowColor[0], shadowColor[1], shadowColor[2]);
		shader.shadingHighlight.set(highlightColor[0], highlightColor[1], highlightColor[2]);
	}

	public static final class SetProfileName extends AbstractCommand
	{
		private final ShadingProfile profile;
		private final String oldName;
		private final String newName;

		public SetProfileName(ShadingProfile profile, String s)
		{
			super("Set Lighting Name");
			this.profile = profile;
			oldName = profile.name.get();
			newName = s;
		}

		@Override
		public boolean modifiesMap()
		{
			return false;
		}

		@Override
		public boolean shouldExec()
		{
			return !(newName == null || newName.isEmpty() || oldName.equals(newName));
		}

		@Override
		public void exec()
		{
			super.exec();
			profile.name.set(newName, false);
			ProjectDatabase.SpriteShading.modified = true;
			ProjectDatabase.SpriteShading.validateNames();
			profile.name.fireCallbacks();
		}

		@Override
		public void undo()
		{
			super.undo();
			profile.name.set(oldName, false);
			ProjectDatabase.SpriteShading.modified = true;
			ProjectDatabase.SpriteShading.validateNames();
			profile.name.fireCallbacks();
		}
	}

	public static final class SetAmbientColor extends AbstractCommand
	{
		private final ShadingProfile profile;
		private final int oldValue;
		private final int newValue;

		public SetAmbientColor(ShadingProfile profile, int newColor)
		{
			super("Set Ambient Color");
			this.profile = profile;
			oldValue = profile.color.get();
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
			profile.color.set(newValue);
			ProjectDatabase.SpriteShading.modified = true;
		}

		@Override
		public void undo()
		{
			super.undo();
			profile.color.set(oldValue);
			ProjectDatabase.SpriteShading.modified = true;
		}
	}

	public static final class SetAmbientIntensity extends AbstractCommand
	{
		private final ShadingProfile profile;
		private final int oldValue;
		private final int newValue;

		public SetAmbientIntensity(ShadingProfile profile, int newIntensity)
		{
			super("Set Ambient Color");
			this.profile = profile;
			oldValue = profile.intensity.get();
			newValue = newIntensity;
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
			profile.intensity.set(newValue);
			ProjectDatabase.SpriteShading.modified = true;
		}

		@Override
		public void undo()
		{
			super.undo();
			profile.intensity.set(oldValue);
			ProjectDatabase.SpriteShading.modified = true;
		}
	}

	public static final class AddLightSource extends AbstractCommand
	{
		private final ShadingProfile profile;
		private final ShadingLightSource source;
		private final int index;

		public AddLightSource(ShadingProfile profile, ShadingLightSource source, int index)
		{
			super("Create Light Source");
			this.profile = profile;
			this.source = source;
			this.index = index;
		}

		@Override
		public boolean modifiesMap()
		{
			return false;
		}

		@Override
		public void exec()
		{
			super.exec();
			if (index < 0)
				profile.sources.addElement(source);
			else
				profile.sources.add(index + 1, source);

			MapEditor.instance().addEditorObject(source);
			ProjectDatabase.SpriteShading.modified = true;
		}

		@Override
		public void undo()
		{
			super.undo();
			profile.sources.removeElement(source);

			MapEditor.instance().removeEditorObject(source);
			ProjectDatabase.SpriteShading.modified = true;
		}
	}

	public static final class RemoveLightSource extends AbstractCommand
	{
		private final ShadingProfile profile;
		private final ShadingLightSource source;
		private final int index;

		public RemoveLightSource(ShadingProfile profile, ShadingLightSource source)
		{
			super("Remove Light Source");
			this.profile = profile;
			this.source = source;
			index = profile.sources.indexOf(source);
		}

		@Override
		public boolean modifiesMap()
		{
			return false;
		}

		@Override
		public boolean shouldExec()
		{
			return (index >= 0);
		}

		@Override
		public void exec()
		{
			super.exec();
			profile.sources.remove(index);

			MapEditor.instance().removeEditorObject(source);
			ProjectDatabase.SpriteShading.modified = true;
		}

		@Override
		public void undo()
		{
			super.undo();
			profile.sources.add(index, source);

			MapEditor.instance().addEditorObject(source);
			ProjectDatabase.SpriteShading.modified = true;
		}
	}

	public static class ShadingProfileComboBoxRenderer extends DefaultListCellRenderer
	{
		private final String nullString;

		public ShadingProfileComboBoxRenderer()
		{
			this.nullString = SpriteShadingData.NO_SHADING_NAME;
		}

		@Override
		public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
		{
			JLabel lbl = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

			lbl.setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 4));
			ShadingProfile profile = (ShadingProfile) value;

			if (profile == null) {
				lbl.setText(nullString);
			}
			else {
				int numLights = profile.sources.size();
				String lightInfo;
				if (numLights == 0)
					lightInfo = "";
				else if (numLights == 1)
					lightInfo = "    (1 light)";
				else
					lightInfo = "    (" + numLights + " lights)";
				lbl.setText(profile.name + lightInfo);

				Color textColor = profile.invalidName ? SwingUtils.getRedTextColor() : null;
				lbl.setForeground(textColor);
			}

			return lbl;
		}
	}
}
