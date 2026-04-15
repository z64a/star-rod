package game.map.scripts;

import static game.map.MapKey.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.w3c.dom.Element;

import app.Environment;
import common.commands.EditableField;
import common.commands.EditableField.EditableFieldFactory;
import common.commands.EditableField.StandardBoolName;
import game.ProjectDatabase;
import game.map.JsonFeatures.JsonMap;
import game.map.JsonFeatures.JsonTexturePanner;
import game.map.Map;
import game.map.editor.MapEditor;
import game.map.editor.UpdateProvider;
import game.map.shading.EditableShadingData.EditableShadingProfile;
import game.map.shape.TexturePanner;
import util.ColorUtils;
import util.IterableListModel;
import util.Logger;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlSerializable;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class Features extends UpdateProvider implements XmlSerializable
{
	private final Map map;

	public final IterableListModel<TexturePanner> texPanners;

	public EditableField<Boolean> overrideShape;
	public EditableField<Boolean> overrideHit;
	public EditableField<Boolean> overrideTex;

	public EditableField<String> shapeOverrideName;
	public EditableField<String> hitOverrideName;
	public EditableField<String> texOverrideName;

	public FogSettings worldFog;
	public FogSettings entityFog;

	public EditableField<Integer> camVfov;
	public EditableField<Integer> camNearClip;
	public EditableField<Integer> camFarClip;
	public EditableField<Integer> bgColorR;
	public EditableField<Integer> bgColorG;
	public EditableField<Integer> bgColorB;
	public boolean camEnabledLast; // hidden, required for matching

	public EditableField<String> locationName;

	public EditableField<Boolean> hasSpriteShading;
	public EditableField<String> shadingProfileName;
	public EditableField<Integer> shadingBaseColor;
	public EditableField<Integer> shadingOffset;
	public transient boolean hasValidShadingProfile;

	public EditableField<Boolean> camLeadsPlayer;

	public final Consumer<Object> notifyGeneral = (o) -> {
		notifyListeners(OptionsPanel.TAG_GENERAL);
	};

	public final Consumer<Object> notifyCamera = (o) -> {
		notifyListeners(OptionsPanel.TAG_CAMERA);
	};

	public final Consumer<Object> notifyShading = (o) -> {
		notifyListeners(OptionsPanel.TAG_SHADING);
	};

	public Features(Map map)
	{
		this.map = map;

		worldFog = new FogSettings(this);
		entityFog = new FogSettings(this);

		texPanners = new IterableListModel<>();
		for (int i = 0; i < 16; i++)
			texPanners.addElement(new TexturePanner(i));

		overrideShape = EditableFieldFactory.create(false)
			.setCallback((o) -> {
				if (!MapEditor.exists() || MapEditor.instance().isLoading())
					return;
				MapEditor.instance().loadOverrides();
				notifyListeners(OptionsPanel.TAG_GENERAL);
			}).setName(new StandardBoolName("Geometry Override")).build();

		overrideHit = EditableFieldFactory.create(false)
			.setCallback((o) -> {
				if (!MapEditor.exists() || MapEditor.instance().isLoading())
					return;
				MapEditor.instance().loadOverrides();
				notifyListeners(OptionsPanel.TAG_GENERAL);
			}).setName(new StandardBoolName("Collision Override")).build();

		overrideTex = EditableFieldFactory.create(true)
			.setCallback(notifyGeneral).setName(new StandardBoolName("Texture Override")).build();

		shapeOverrideName = EditableFieldFactory.create("")
			.setCallback((o) -> {
				if (!MapEditor.exists() || MapEditor.instance().isLoading())
					return;
				MapEditor.instance().loadOverrides();
				notifyListeners(OptionsPanel.TAG_GENERAL);
			}).setName("Set Shape Override Name").build();

		hitOverrideName = EditableFieldFactory.create("")
			.setCallback((o) -> {
				if (!MapEditor.exists() || MapEditor.instance().isLoading())
					return;
				MapEditor.instance().loadOverrides();
				notifyListeners(OptionsPanel.TAG_GENERAL);
			}).setName("Set Hit Override Name").build();

		camVfov = EditableFieldFactory.create(25)
			.setCallback(notifyCamera).setName("Set Vertical FOV").build();

		camNearClip = EditableFieldFactory.create(16)
			.setCallback(notifyCamera).setName("Set Near Clip").build();

		camFarClip = EditableFieldFactory.create(4096)
			.setCallback(notifyCamera).setName("Set Far Clip").build();

		bgColorR = EditableFieldFactory.create(0)
			.setCallback(notifyCamera).setName("Set Background Red").build();

		bgColorG = EditableFieldFactory.create(0)
			.setCallback(notifyCamera).setName("Set Background Green").build();

		bgColorB = EditableFieldFactory.create(0)
			.setCallback(notifyCamera).setName("Set Background Blue").build();

		camLeadsPlayer = EditableFieldFactory.create(true)
			.setCallback(notifyCamera).setName(new StandardBoolName("Player-Leading Camera")).build();

		locationName = EditableFieldFactory.create("LOCATION_TOAD_TOWN")
			.setCallback(notifyGeneral).setName("Set Location").build();

		hasSpriteShading = EditableFieldFactory.create(false)
			.setCallback((o) -> {
				if (!MapEditor.exists() || MapEditor.instance().isLoading())
					return;
				notifyListeners(OptionsPanel.TAG_SHADING);
			}).setName(new StandardBoolName("Set Shading")).build();

		shadingProfileName = EditableFieldFactory.create("")
			.setCallback(notifyShading).setName("Set Shading Profile").build();

		shadingBaseColor = EditableFieldFactory.create(0xB4B4B4)
			.setCallback(notifyShading).setName("Set Color").build();

		shadingOffset = EditableFieldFactory.create(30)
			.setCallback(notifyShading).setName("Set Intensity").build();
	}

	public void toJson(JsonMap out)
	{
		out.overrideShape = overrideShape.get();
		out.overrideHit = overrideHit.get();
		out.overrideTex = overrideTex.get();

		out.shapeOverrideName = shapeOverrideName.get();
		out.hitOverrideName = hitOverrideName.get();

		out.camVfov = camVfov.get();
		out.camNearClip = camNearClip.get();
		out.camFarClip = camFarClip.get();
		out.camLeadsPlayer = camLeadsPlayer.get();
		out.camEnabledLast = camEnabledLast ? null : false; // only keep if false

		out.camBackgroundColor = new int[] { bgColorR.get(), bgColorG.get(), bgColorB.get() };

		out.fogWorld = worldFog.pack();
		out.fogEntity = entityFog.pack();

		out.locationName = locationName.get();

		out.hasSpriteShading = hasSpriteShading.get();

		if (out.hasSpriteShading) {
			if (Environment.isDX()) {
				out.shadingOffset = shadingOffset.get();
				out.shadingBaseColor = ColorUtils.unpack(shadingBaseColor.get());
			}
			else {
				out.shadingProfile = shadingProfileName.get();
			}
		}

		List<JsonTexturePanner> list = new ArrayList<>();
		for (TexturePanner panner : texPanners) {
			list.add(panner.toJson());
		}
		out.texPanners = list.toArray(new JsonTexturePanner[0]);
	}

	public void fromJson(JsonMap in)
	{
		overrideShape.set(in.overrideShape);
		overrideHit.set(in.overrideHit);
		overrideTex.set(in.overrideTex);

		shapeOverrideName.set(in.shapeOverrideName != null ? in.shapeOverrideName : "");
		hitOverrideName.set(in.hitOverrideName != null ? in.hitOverrideName : "");

		camVfov.set(in.camVfov);
		camNearClip.set(in.camNearClip);
		camFarClip.set(in.camFarClip);

		camLeadsPlayer.set(in.camLeadsPlayer);
		camEnabledLast = (in.camEnabledLast != null) && in.camEnabledLast;

		if (in.camBackgroundColor != null && in.camBackgroundColor.length == 3) {
			bgColorR.set(in.camBackgroundColor[0]);
			bgColorG.set(in.camBackgroundColor[1]);
			bgColorB.set(in.camBackgroundColor[2]);
		}

		if (in.fogWorld != null && in.fogWorld.length == 7)
			worldFog.load(in.fogWorld);

		if (in.fogEntity != null && in.fogEntity.length == 7)
			entityFog.load(in.fogEntity);

		locationName.set(in.locationName != null ? in.locationName : "");

		hasSpriteShading.set(in.hasSpriteShading);

		if (in.hasSpriteShading) {
			if (Environment.isDX()) {
				shadingBaseColor.set(ColorUtils.pack(in.shadingBaseColor));
				shadingOffset.set(in.shadingOffset);
			}
			else {
				if (in.shadingProfile != null)
					shadingProfileName.set(in.shadingProfile);
			}
		}

		texPanners.clear();
		if (in.texPanners != null) {
			for (JsonTexturePanner json : in.texPanners)
				texPanners.addElement(TexturePanner.fromJson(json));
		}
	}

	@Override
	public void fromXML(XmlReader xmr, Element scriptElem)
	{
		Element pannersElem = xmr.getUniqueTag(scriptElem, TAG_PANNER_LIST);
		if (pannersElem != null) {
			for (Element pannerElem : xmr.getTags(pannersElem, TAG_PANNER))
				TexturePanner.load(texPanners, xmr, pannerElem);
		}

		Element optionsElem = xmr.getUniqueTag(scriptElem, TAG_OPTIONS);
		if (optionsElem != null) {
			if (xmr.hasAttribute(optionsElem, ATTR_CAM_LEADS))
				camLeadsPlayer.set(xmr.readBoolean(optionsElem, ATTR_CAM_LEADS));

			if (xmr.hasAttribute(optionsElem, ATTR_LOCATION))
				locationName.set(xmr.getAttribute(optionsElem, ATTR_LOCATION));

			if (xmr.hasAttribute(optionsElem, ATTR_HAS_SHADING))
				hasSpriteShading.set(xmr.readBoolean(optionsElem, ATTR_HAS_SHADING));

			String shadingName = null;
			if (xmr.hasAttribute(optionsElem, ATTR_SHADING_NAME)) {
				shadingName = xmr.getAttribute(optionsElem, ATTR_SHADING_NAME);
				if (!Environment.isDX()) {
					shadingProfileName.set(shadingName);

					EditableShadingProfile profile = ProjectDatabase.SpriteShading.find(shadingName);
					if (profile == null) {
						Logger.logError("Could not find shading profile: " + shadingName);
						hasValidShadingProfile = false;
					}
					else {
						map.loadShadingProfile(profile);
						hasValidShadingProfile = true;
					}
				}
			}
		}

		Element overrideElem = xmr.getUniqueTag(scriptElem, TAG_OVERRIDE);
		if (overrideElem != null) {
			if (xmr.hasAttribute(overrideElem, ATTR_SHAPE)) {
				shapeOverrideName.set(xmr.getAttribute(overrideElem, ATTR_SHAPE));
				overrideShape.set(!shapeOverrideName.get().isEmpty());
			}

			if (xmr.hasAttribute(overrideElem, ATTR_HIT)) {
				hitOverrideName.set(xmr.getAttribute(overrideElem, ATTR_HIT));
				overrideHit.set(!hitOverrideName.get().isEmpty());
			}

			if (xmr.hasAttribute(overrideElem, ATTR_TEX))
				overrideTex.set(xmr.readBoolean(overrideElem, ATTR_TEX));
		}

		Element camElem = xmr.getUniqueTag(scriptElem, TAG_CAMERA);
		if (camElem != null) {
			if (xmr.hasAttribute(camElem, ATTR_CAM_VFOV))
				camVfov.set(xmr.readInt(camElem, ATTR_CAM_VFOV));

			if (xmr.hasAttribute(camElem, ATTR_CAM_NEAR))
				camNearClip.set(xmr.readInt(camElem, ATTR_CAM_NEAR));

			if (xmr.hasAttribute(camElem, ATTR_CAM_FAR))
				camFarClip.set(xmr.readInt(camElem, ATTR_CAM_FAR));

			if (xmr.hasAttribute(camElem, ATTR_CAM_BGCOL)) {
				int[] col = xmr.readIntArray(camElem, ATTR_CAM_BGCOL, 3);
				bgColorR.set(col[0]);
				bgColorG.set(col[1]);
				bgColorB.set(col[2]);
			}

			if (xmr.hasAttribute(camElem, ATTR_CAM_LEADS))
				camLeadsPlayer.set(xmr.readBoolean(camElem, ATTR_CAM_LEADS));
		}

		Element fogElem = xmr.getUniqueTag(scriptElem, TAG_FOG);
		if (fogElem != null) {
			if (xmr.hasAttribute(fogElem, ATTR_FOG_WORLD))
				worldFog.load(xmr.readIntArray(fogElem, ATTR_FOG_WORLD, 7));
			if (xmr.hasAttribute(fogElem, ATTR_FOG_ENTITY))
				entityFog.load(xmr.readIntArray(fogElem, ATTR_FOG_ENTITY, 7));
		}
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag pannersTag = xmw.createTag(TAG_PANNER_LIST, false);
		xmw.openTag(pannersTag);
		for (TexturePanner panner : texPanners)
			panner.toXML(xmw);
		xmw.closeTag(pannersTag);

		XmlTag camTag = xmw.createTag(TAG_CAMERA, true);
		xmw.addInt(camTag, ATTR_CAM_VFOV, camVfov.get());
		xmw.addInt(camTag, ATTR_CAM_NEAR, camNearClip.get());
		xmw.addInt(camTag, ATTR_CAM_FAR, camFarClip.get());
		xmw.addIntArray(camTag, ATTR_CAM_BGCOL, bgColorR.get(), bgColorG.get(), bgColorB.get());
		xmw.addBoolean(camTag, ATTR_CAM_LEADS, camLeadsPlayer.get());
		xmw.printTag(camTag);

		XmlTag fogTag = xmw.createTag(TAG_FOG, true);
		xmw.addIntArray(fogTag, ATTR_FOG_WORLD, worldFog.pack());
		xmw.addIntArray(fogTag, ATTR_FOG_ENTITY, entityFog.pack());
		xmw.printTag(fogTag);

		XmlTag optionsTag = xmw.createTag(TAG_OPTIONS, true);

		xmw.addAttribute(optionsTag, ATTR_LOCATION, locationName.get());

		xmw.addBoolean(optionsTag, ATTR_HAS_SHADING, hasSpriteShading.get());
		String shadingName = shadingProfileName.get();
		if (shadingName != null && !shadingName.isEmpty())
			xmw.addAttribute(optionsTag, ATTR_SHADING_NAME, shadingName);

		xmw.printTag(optionsTag);

		if (overrideShape.get() || overrideHit.get() || overrideTex.get()) {
			XmlTag overrideTag = xmw.createTag(TAG_OVERRIDE, true);

			if (overrideShape.get() && !shapeOverrideName.get().isEmpty())
				xmw.addAttribute(overrideTag, ATTR_SHAPE, shapeOverrideName.get());

			if (overrideHit.get() && !hitOverrideName.get().isEmpty())
				xmw.addAttribute(overrideTag, ATTR_HIT, hitOverrideName.get());

			xmw.addBoolean(overrideTag, ATTR_TEX, overrideTex.get());

			xmw.printTag(overrideTag);
		}
	}
}
