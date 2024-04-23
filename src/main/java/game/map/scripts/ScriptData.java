package game.map.scripts;

import static game.map.MapKey.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.w3c.dom.Element;

import game.ProjectDatabase;
import game.map.editor.MapEditor;
import game.map.editor.UpdateProvider;
import game.map.editor.commands.fields.EditableField;
import game.map.editor.commands.fields.EditableField.EditableFieldFactory;
import game.map.editor.commands.fields.EditableField.StandardBoolName;
import game.map.editor.ui.ScriptManager;
import game.map.editor.ui.SimpleEditableJTree;
import game.map.scripts.generators.Generator;
import game.map.scripts.generators.Generator.GeneratorType;
import game.map.shading.ShadingProfile;
import game.map.shading.SpriteShadingData;
import game.map.shape.TexturePanner;
import game.map.tree.CategoryTreeModel;
import game.map.tree.CategoryTreeModel.CategoryTreeNode;
import util.IterableListModel;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlSerializable;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class ScriptData extends UpdateProvider implements XmlSerializable
{
	public final IterableListModel<TexturePanner> texPanners;
	public final CategoryTreeModel<GeneratorType, Generator> generatorsTreeModel;

	private static final String callbackBeforeEnterKey = "BeforeEnter";
	private static final String callbackAfterEnterKey = "AfterEnter";

	public EditableField<Boolean> addCallbackBeforeEnterMap;
	public EditableField<Boolean> addCallbackAfterEnterMap;

	public EditableField<Boolean> overrideShape;
	public EditableField<Boolean> overrideHit;
	public EditableField<Boolean> overrideTex;

	public EditableField<String> shapeOverrideName;
	public EditableField<String> hitOverrideName;
	// tex override uses texName from map

	public FogSettings worldFogSettings;
	public FogSettings entityFogSettings;

	public EditableField<Integer> camVfov;
	public EditableField<Integer> camNearClip;
	public EditableField<Integer> camFarClip;
	public EditableField<Integer> bgColorR;
	public EditableField<Integer> bgColorG;
	public EditableField<Integer> bgColorB;

	public EditableField<Boolean> hasMusic;
	public EditableField<String> songName;
	public EditableField<Boolean> hasAmbientSFX;
	public EditableField<String> ambientSFX;
	public EditableField<String> locationName;

	public EditableField<Boolean> hasSpriteShading;
	public transient EditableField<ShadingProfile> shadingProfile;

	public EditableField<Boolean> cameraLeadsPlayer;
	public EditableField<Boolean> isDark;

	public final Consumer<Object> notifyGeneral = (o) -> {
		notifyListeners(ScriptManager.tag_General);
	};

	public final Consumer<Object> notifyCamera = (o) -> {
		notifyListeners(ScriptManager.tag_Camera);
	};

	public ScriptData()
	{
		worldFogSettings = new FogSettings(this);
		entityFogSettings = new FogSettings(this);

		texPanners = new IterableListModel<>();
		for (int i = 0; i < 16; i++)
			texPanners.addElement(new TexturePanner(i));

		generatorsTreeModel = new CategoryTreeModel<>("Generators");

		for (GeneratorType type : GeneratorType.values())
			generatorsTreeModel.addCategory(type);

		overrideShape = EditableFieldFactory.create(false)
			.setCallback((o) -> {
				if (!MapEditor.exists() || MapEditor.instance().isLoading())
					return;
				MapEditor.instance().loadOverrides();
				notifyListeners(ScriptManager.tag_General);
			}).setName(new StandardBoolName("Geometry Override")).build();

		overrideHit = EditableFieldFactory.create(false)
			.setCallback((o) -> {
				if (!MapEditor.exists() || MapEditor.instance().isLoading())
					return;
				MapEditor.instance().loadOverrides();
				notifyListeners(ScriptManager.tag_General);
			}).setName(new StandardBoolName("Collision Override")).build();

		overrideTex = EditableFieldFactory.create(true)
			.setCallback(notifyGeneral).setName(new StandardBoolName("Texture Override")).build();

		shapeOverrideName = EditableFieldFactory.create("")
			.setCallback((o) -> {
				if (!MapEditor.exists() || MapEditor.instance().isLoading())
					return;
				MapEditor.instance().loadOverrides();
				notifyListeners(ScriptManager.tag_General);
			}).setName("Set Shape Override Name").build();

		hitOverrideName = EditableFieldFactory.create("")
			.setCallback((o) -> {
				if (!MapEditor.exists() || MapEditor.instance().isLoading())
					return;
				MapEditor.instance().loadOverrides();
				notifyListeners(ScriptManager.tag_General);
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

		cameraLeadsPlayer = EditableFieldFactory.create(false)
			.setCallback(notifyCamera).setName(new StandardBoolName("Player-Leading Camera")).build();

		hasMusic = EditableFieldFactory.create(false)
			.setCallback(notifyGeneral).setName(new StandardBoolName("Music")).build();

		songName = EditableFieldFactory.create("SONG_PLEASANT_PATH")
			.setCallback(notifyGeneral).setName("Set Background Music").build();

		hasAmbientSFX = EditableFieldFactory.create(false)
			.setCallback(notifyGeneral).setName(new StandardBoolName("Ambient Sounds")).build();

		ambientSFX = EditableFieldFactory.create("AMBIENT_WIND")
			.setCallback(notifyGeneral).setName("Set Ambient Sounds").build();

		locationName = EditableFieldFactory.create("LOCATION_GOOMBA_ROAD")
			.setCallback(notifyGeneral).setName("Set Location").build();

		hasSpriteShading = EditableFieldFactory.create(false)
			.setCallback((o) -> {
				if (!MapEditor.exists() || MapEditor.instance().isLoading())
					return;
				notifyListeners(ScriptManager.tag_Shading);
			}).setName(new StandardBoolName("Set Shading")).build();

		shadingProfile = EditableFieldFactory.create((ShadingProfile) null)
			.setCallback((o) -> {
				if (!MapEditor.exists() || MapEditor.instance().isLoading())
					return;
				notifyListeners(ScriptManager.tag_Shading);
			}).setName("Set Sprite Shading").build();

		isDark = EditableFieldFactory.create(false)
			.setCallback(notifyGeneral).setName(new StandardBoolName("Darkness")).build();

		addCallbackBeforeEnterMap = EditableFieldFactory.create(false)
			.setCallback(notifyGeneral).setName(new StandardBoolName("Before EnterMap Callback")).build();

		addCallbackAfterEnterMap = EditableFieldFactory.create(false)
			.setCallback(notifyGeneral).setName(new StandardBoolName("After EnterMap Callback")).build();
	}

	public void addGenerator(String cmdName, SimpleEditableJTree generatorsTree, Generator generator)
	{
		MapEditor.execute(generatorsTreeModel.new AddObject(cmdName, generatorsTree, generator.type, generator));
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void removeGenerator(String cmdName, SimpleEditableJTree generatorsTree, CategoryTreeNode toRemoveNode)
	{
		MapEditor.execute(generatorsTreeModel.new RemoveObject(cmdName, generatorsTree, toRemoveNode));
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
			if (xmr.hasAttribute(optionsElem, ATTR_CALLBACKS)) {
				for (String s : xmr.readStringList(optionsElem, ATTR_CALLBACKS)) {
					if (s.equals(callbackBeforeEnterKey))
						addCallbackBeforeEnterMap.set(true);
					else if (s.equals(callbackAfterEnterKey))
						addCallbackAfterEnterMap.set(true);
				}
			}

			if (xmr.hasAttribute(optionsElem, ATTR_DARK))
				isDark.set(xmr.readBoolean(optionsElem, ATTR_DARK));

			if (xmr.hasAttribute(optionsElem, ATTR_CAM_LEADS))
				cameraLeadsPlayer.set(xmr.readBoolean(optionsElem, ATTR_CAM_LEADS));

			if (xmr.hasAttribute(optionsElem, ATTR_HAS_MUSIC))
				hasMusic.set(xmr.readBoolean(optionsElem, ATTR_HAS_MUSIC));

			if (xmr.hasAttribute(optionsElem, ATTR_SONG))
				songName.set(xmr.getAttribute(optionsElem, ATTR_SONG));

			if (xmr.hasAttribute(optionsElem, ATTR_HAS_SOUNDS))
				hasAmbientSFX.set(xmr.readBoolean(optionsElem, ATTR_HAS_SOUNDS));

			if (xmr.hasAttribute(optionsElem, ATTR_SOUNDS))
				ambientSFX.set(xmr.getAttribute(optionsElem, ATTR_SOUNDS));

			if (xmr.hasAttribute(optionsElem, ATTR_LOCATION))
				locationName.set(xmr.getAttribute(optionsElem, ATTR_LOCATION));

			if (xmr.hasAttribute(optionsElem, ATTR_HAS_SHADING))
				hasSpriteShading.set(xmr.readBoolean(optionsElem, ATTR_HAS_SHADING));

			String shadingName = null;
			if (xmr.hasAttribute(optionsElem, ATTR_SHADING_NAME))
				shadingName = xmr.getAttribute(optionsElem, ATTR_SHADING_NAME);

			if (shadingName != null && !shadingName.isEmpty() && !shadingName.equals(SpriteShadingData.NO_SHADING_NAME))
				shadingProfile.set(ProjectDatabase.SpriteShading.getShadingProfile(shadingName));
			else
				shadingProfile.set((ShadingProfile) null);
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
				cameraLeadsPlayer.set(xmr.readBoolean(camElem, ATTR_CAM_LEADS));
		}

		Element fogElem = xmr.getUniqueTag(scriptElem, TAG_FOG);
		if (fogElem != null) {
			if (xmr.hasAttribute(fogElem, ATTR_FOG_WORLD))
				worldFogSettings.load(xmr.readIntArray(fogElem, ATTR_FOG_WORLD, 7));
			if (xmr.hasAttribute(fogElem, ATTR_FOG_ENTITY))
				entityFogSettings.load(xmr.readIntArray(fogElem, ATTR_FOG_ENTITY, 7));
		}

		Element generatorsElem = xmr.getUniqueTag(scriptElem, TAG_GEN_LIST);
		if (generatorsElem != null)
			Generator.readXml(xmr, generatorsElem, this);
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag pannersTag = xmw.createTag(TAG_PANNER_LIST, false);
		xmw.openTag(pannersTag);
		for (TexturePanner panner : texPanners)
			panner.toXML(xmw);
		xmw.closeTag(pannersTag);

		XmlTag generatorsTag = xmw.createTag(TAG_GEN_LIST, false);
		xmw.openTag(generatorsTag);

		Generator.writeXml(xmw, this);

		xmw.closeTag(generatorsTag);

		XmlTag camTag = xmw.createTag(TAG_CAMERA, true);
		xmw.addInt(camTag, ATTR_CAM_VFOV, camVfov.get());
		xmw.addInt(camTag, ATTR_CAM_NEAR, camNearClip.get());
		xmw.addInt(camTag, ATTR_CAM_FAR, camFarClip.get());
		xmw.addIntArray(camTag, ATTR_CAM_BGCOL, bgColorR.get(), bgColorG.get(), bgColorB.get());
		xmw.addBoolean(camTag, ATTR_CAM_LEADS, cameraLeadsPlayer.get());
		xmw.printTag(camTag);

		XmlTag fogTag = xmw.createTag(TAG_FOG, true);
		xmw.addIntArray(fogTag, ATTR_FOG_WORLD, worldFogSettings.pack());
		xmw.addIntArray(fogTag, ATTR_FOG_ENTITY, entityFogSettings.pack());
		xmw.printTag(fogTag);

		XmlTag optionsTag = xmw.createTag(TAG_OPTIONS, true);

		List<String> callbacks = new ArrayList<>();
		if (addCallbackBeforeEnterMap.get())
			callbacks.add(callbackBeforeEnterKey);
		if (addCallbackAfterEnterMap.get())
			callbacks.add(callbackAfterEnterKey);

		if (callbacks.size() > 0)
			xmw.addStringList(optionsTag, ATTR_CALLBACKS, callbacks);

		xmw.addAttribute(optionsTag, ATTR_LOCATION, locationName.get());

		xmw.addBoolean(optionsTag, ATTR_HAS_MUSIC, hasMusic.get());
		if (hasMusic.get() && songName.get() != null && !songName.get().isEmpty())
			xmw.addAttribute(optionsTag, ATTR_SONG, songName.get());

		xmw.addBoolean(optionsTag, ATTR_HAS_SOUNDS, hasAmbientSFX.get());
		xmw.addAttribute(optionsTag, ATTR_SOUNDS, ambientSFX.get());

		xmw.addBoolean(optionsTag, ATTR_HAS_SHADING, hasSpriteShading.get());
		String shadingName;
		if (shadingProfile != null && shadingProfile.get() != null)
			shadingName = shadingProfile.get().name.get();
		else
			shadingName = SpriteShadingData.NO_SHADING_NAME;
		if (shadingName != null && !shadingName.isEmpty())
			xmw.addAttribute(optionsTag, ATTR_SHADING_NAME, shadingName);

		if (isDark.get())
			xmw.addBoolean(optionsTag, ATTR_DARK, true);
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
