package game.sprite;

import static game.sprite.SpriteKey.*;
import static org.lwjgl.opengl.GL11.GL_ALWAYS;
import static org.lwjgl.opengl.GL11.glStencilFunc;

import java.awt.Container;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import org.w3c.dom.Element;

import common.Vector3f;
import game.map.Axis;
import game.map.BoundingBox;
import game.map.editor.render.PresetColor;
import game.map.shading.ShadingProfile;
import game.map.shape.TransformMatrix;
import game.sprite.SpriteLoader.Indexable;
import game.sprite.editor.Editable;
import game.sprite.editor.SpriteCleanup;
import game.sprite.editor.SpriteCleanup.UnusedLabel;
import game.sprite.editor.SpriteEditor;
import game.sprite.editor.animators.AnimElement;
import game.sprite.editor.animators.ComponentAnimator;
import game.sprite.editor.animators.command.AnimCommand;
import game.sprite.editor.animators.command.CommandAnimator;
import game.sprite.editor.animators.command.Goto;
import game.sprite.editor.animators.command.Label;
import game.sprite.editor.animators.command.Loop;
import game.sprite.editor.animators.command.SetImage;
import game.sprite.editor.animators.command.SetPalette;
import game.sprite.editor.animators.command.SetParent;
import game.sprite.editor.animators.keyframe.AnimKeyframe;
import game.sprite.editor.animators.keyframe.Keyframe;
import game.sprite.editor.animators.keyframe.KeyframeAnimator;
import game.sprite.editor.animators.keyframe.ParentKey;
import game.texture.Palette;
import renderer.buffers.LineRenderQueue;
import renderer.shaders.RenderState;
import renderer.shaders.RenderState.PolygonMode;
import renderer.shaders.ShaderManager;
import renderer.shaders.scene.SpriteShader;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlSerializable;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class SpriteComponent implements XmlSerializable, Indexable<SpriteComponent>, Editable
{
	public final Sprite sprite;
	public final SpriteAnimation parentAnimation;

	/** overall position offset */
	public int posx, posy, posz;

	private CommandAnimator cmdAnimator;
	private KeyframeAnimator keyframeAnimator;
	protected ComponentAnimator animator;

	// editor fields
	public transient String name = "";
	public transient int listIndex;

	public transient boolean highlighted;
	public transient boolean selected;
	public transient boolean hidden;

	/** Time since last goto, used to detect goto:self infinite loops and break out */
	public int gotoTime;

	/** Has the animation reached a point where it cannot continue? */
	public boolean complete;

	// animation state
	public SpriteRaster sr = null;
	public SpritePalette sp = null;
	public SpriteComponent parent = null;

	public int delayCount;
	public int repeatCount;

	public int flag = 1;
	public int dx, dy, dz;
	public int rx, ry, rz;
	public int scaleX, scaleY, scaleZ;

	public transient boolean deleted;

	public transient int lastSelectedCommand = -1;

	// used while copying animations
	public transient int parentID;

	// bounding box containing all components as they are rendered
	public transient Vector3f[] corners = new Vector3f[4];

	// temporary offsets using while dragging components are in the viewport
	public transient int dragX, dragY;

	public SpriteComponent(SpriteAnimation parentAnimation)
	{
		this.parentAnimation = parentAnimation;
		this.sprite = parentAnimation.parentSprite;

		cmdAnimator = new CommandAnimator(this);
		keyframeAnimator = new KeyframeAnimator(this);
		animator = sprite.usesKeyframes ? keyframeAnimator : cmdAnimator;
		reset();
	}

	// deep copy contructor
	public SpriteComponent(SpriteAnimation anim, SpriteComponent original)
	{
		this.parentAnimation = anim;
		this.sprite = anim.parentSprite;

		this.cmdAnimator = new CommandAnimator(this, original.cmdAnimator);
		this.keyframeAnimator = new KeyframeAnimator(this, original.keyframeAnimator);
		animator = sprite.usesKeyframes ? keyframeAnimator : cmdAnimator;
		reset();

		this.posx = original.posx;
		this.posy = original.posy;
		this.posz = original.posz;

		this.name = original.name;

		this.sr = original.sr;
		this.sp = original.sp;

		if (original.parent == null)
			this.parentID = -1;
		else
			this.parentID = original.parent.listIndex;

		this.flag = original.flag;
		this.dx = original.dx;
		this.dy = original.dy;
		this.dz = original.dz;

		this.rx = original.rx;
		this.ry = original.ry;
		this.rz = original.rz;

		this.scaleX = original.scaleX;
		this.scaleY = original.scaleY;
		this.scaleZ = original.scaleZ;
	}

	/**
	 * Update SetParent object references from IDs saved when the commands were generated
	 * from a RawAnimation. This must be called for each component after copying an animation,
	 * as the SpriteComponent references would refer to the old SpriteAnimation.
	 */
	public void popParents()
	{
		SpriteAnimation anim = parentAnimation;

		// update anim command lists
		if (sprite.usesKeyframes) {
			for (AnimElement e : keyframeAnimator.keyframes) {
				if (e instanceof ParentKey setParent) {
					int id = setParent.parIndex;
					if (id >= 0 && id < anim.components.size()) {
						setParent.parent = anim.components.get(setParent.parIndex);
					}
				}
			}
		}
		else {
			for (AnimElement e : cmdAnimator.commands) {
				if (e instanceof SetParent setParent) {
					int id = setParent.parIndex;
					if (id >= 0 && id < anim.components.size()) {
						setParent.parent = anim.components.get(setParent.parIndex);
					}
				}
			}
		}

		// update animation state
		if (parentID >= 0 && parentID < anim.components.size()) {
			parent = anim.components.get(parentID);
		}
	}

	public void prepareForEditor()
	{
		lastSelectedCommand = -1;
	}

	public void reset()
	{
		animator.reset();
		corners[0] = new Vector3f(0, 0, 0);
		corners[1] = new Vector3f(0, 0, 0);
		corners[2] = new Vector3f(0, 0, 0);
		corners[3] = new Vector3f(0, 0, 0);
	}

	public void step()
	{
		animator.step();
	}

	public int getX()
	{
		return posx + dx + dragX;
	}

	public int getY()
	{
		return posy + dy + dragY;
	}

	public int getZ()
	{
		return posz + dz;
	}

	public void addCorners(BoundingBox aabb)
	{
		for (Vector3f vec : corners)
			aabb.encompass((int) vec.x, (int) vec.y, (int) vec.z);
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag compTag = xmw.createTag(TAG_COMPONENT, false);

		if (!name.isEmpty())
			xmw.addAttribute(compTag, ATTR_NAME, name);

		xmw.addIntArray(compTag, ATTR_OFFSET, posx, posy, posz);
		xmw.openTag(compTag);

		if (SpriteEditor.instance().optOutputRaw) {
			RawAnimation rawAnim = animator.toRawAnimation();

			for (Short s : rawAnim) {
				XmlTag commandTag = xmw.createTag(TAG_COMMAND, true);
				xmw.addHex(commandTag, ATTR_VAL, s & 0xFFFF);
				xmw.printTag(commandTag);
			}

			for (Entry<Integer, String> e : rawAnim.getAllLabels()) {
				XmlTag labelTag = xmw.createTag(TAG_LABEL, true);
				xmw.addHex(labelTag, ATTR_POS, e.getKey());
				xmw.addAttribute(labelTag, ATTR_POS, e.getValue());
			}
		}
		else {
			if (sprite.usesKeyframes)
				keyframeAnimator.toXML(xmw);
			else
				cmdAnimator.toXML(xmw);
		}
		xmw.closeTag(compTag);
	}

	@Override
	public void fromXML(XmlReader xmr, Element componentElem)
	{
		if (xmr.hasAttribute(componentElem, ATTR_NAME))
			name = xmr.getAttribute(componentElem, ATTR_NAME);

		if (xmr.hasAttribute(componentElem, ATTR_OFFSET)) {
			int[] xyz = xmr.readIntArray(componentElem, ATTR_OFFSET, 3);
			posx = xyz[0];
			posy = xyz[1];
			posz = xyz[2];
		}
		else {
			xmr.requiresAttribute(componentElem, ATTR_X);
			xmr.requiresAttribute(componentElem, ATTR_Y);
			xmr.requiresAttribute(componentElem, ATTR_Z);
			posx = xmr.readInt(componentElem, ATTR_X);
			posy = xmr.readInt(componentElem, ATTR_Y);
			posz = xmr.readInt(componentElem, ATTR_Z);
		}

		List<Element> commandElems = xmr.getTags(componentElem, TAG_COMMAND);

		if (!commandElems.isEmpty()) {
			RawAnimation rawAnim = new RawAnimation();

			// load legacy star rod sprite
			for (Element commandElem : commandElems) {
				xmr.requiresAttribute(commandElem, ATTR_VAL);
				rawAnim.add((short) xmr.readHex(commandElem, ATTR_VAL));
			}

			List<Element> labelElems = xmr.getTags(componentElem, TAG_LABEL);
			for (Element labelElem : labelElems) {
				xmr.requiresAttribute(labelElem, ATTR_NAME);
				xmr.requiresAttribute(labelElem, ATTR_POS);
				rawAnim.setLabel(xmr.readHex(labelElem, ATTR_POS), xmr.getAttribute(labelElem, ATTR_NAME));
			}

			animator.generateFrom(rawAnim);
		}
		else {
			animator.fromXML(xmr, componentElem);
		}
	}

	public void updateReferences(
		HashMap<String, SpriteRaster> imgMap,
		HashMap<String, SpritePalette> palMap,
		HashMap<String, SpriteComponent> compMap)
	{

		animator.updateReferences(imgMap, palMap, compMap);
	}

	public void convertToKeyframes()
	{
		if (sprite.usesKeyframes)
			return;

		List<AnimKeyframe> keyframes = cmdAnimator.toKeyframes(keyframeAnimator);
		keyframeAnimator.useKeyframes(keyframes);

		animator = keyframeAnimator;
		lastSelectedCommand = -1;
	}

	public void convertToCommands()
	{
		if (!sprite.usesKeyframes)
			return;

		List<AnimCommand> commands = keyframeAnimator.toCommands(cmdAnimator);
		cmdAnimator.useCommands(commands);

		animator = cmdAnimator;
		lastSelectedCommand = -1;
	}

	public void calculateTiming()
	{
		animator.calculateTiming();
	}

	public void bind(SpriteEditor editor, Container commandListContainer, Container commandEditContainer)
	{
		animator.bind(editor, commandListContainer, commandEditContainer);
	}

	public void unbind()
	{
		animator.unbind();
	}

	@Override
	public String toString()
	{
		return name.isEmpty() ? String.format("Comp %02X", listIndex) : name;
	}

	@Override
	public SpriteComponent getObject()
	{
		return this;
	}

	@Override
	public int getIndex()
	{
		return listIndex;
	}

	public String createUniqueName(String name)
	{
		String baseName = name;

		for (int iteration = 0; iteration < 256; iteration++) {
			boolean conflict = false;

			// compare to all other names
			for (SpriteComponent other : parentAnimation.components) {
				if (other != this && other.name.equals(name)) {
					conflict = true;
					break;
				}
			}

			if (!conflict) {
				// name is valid
				return name;
			}
			else {
				// try next iteration
				name = baseName + "_" + iteration;
			}
		}

		// could not form a valid name
		return null;
	}

	/*
	 * When composing transformations, the order is:
	 * (1) Translate
	 * (2) Rotate along Y
	 * (3) Rotate along Z
	 * (4) Rotate along X
	 * (5) Scale
	 * The order of animation commands does not matter.
	 */
	public void render(ShadingProfile spriteShading, SpritePalette paletteOverride, boolean useBack,
		boolean enableStencilBuffer, boolean enableSelectedHighlight,
		boolean useSelectShading, boolean drawBounds, boolean useFiltering)
	{
		if (sr == null || hidden)
			return;

		boolean tryBack = parentAnimation.parentSprite.hasBack && sr.hasIndependentBack;
		SpriteRasterFace face = (useBack && tryBack) ? sr.back : sr.front;

		// no image found, skip drawing
		if (face == null || face.asset == null)
			return;

		if (enableStencilBuffer)
			glStencilFunc(GL_ALWAYS, getIndex() + 1, 0xFF);

		Palette renderPalette;
		if (sp != null && sp.hasPal()) {
			// use current palette set by command list
			renderPalette = sp.getPal();
		}
		else if (paletteOverride != null && paletteOverride.hasPal()) {
			// use override palette set in the Animations tab
			renderPalette = paletteOverride.getPal();
		}
		else if (face.pal.hasPal()) {
			// use palette assigned for this side of the SpriteRaster
			renderPalette = face.pal.getPal();
		}
		else {
			// no valid palette could be found, fallback to palette of the image itself
			renderPalette = face.asset.img.palette;
		}

		int x = (parent != null) ? parent.getX() + getX() : getX();
		int y = (parent != null) ? parent.getY() + getY() : getY();
		int z = (parent != null) ? parent.getZ() + getZ() : getZ();

		float w = face.asset.img.width / 2;
		float h = face.asset.img.height;
		corners[0].set(-w, 0, 0);
		corners[1].set(w, 0, 0);
		corners[2].set(w, h, 0);
		corners[3].set(-w, h, 0);

		TransformMatrix mtx = TransformMatrix.identity();

		// this is the rotation order (verified)
		if (rx != 0 || ry != 0 || rz != 0) {
			mtx.rotate(Axis.Y, ry);
			mtx.rotate(Axis.Z, rz);
			mtx.rotate(Axis.X, rx);
		}

		float renderScaleX = (scaleX == 0.0f) ? 0.0f : scaleX / 100.0f;
		float renderScaleY = (scaleY == 0.0f) ? 0.0f : scaleY / 100.0f;
		float renderScaleZ = (scaleZ == 0.0f) ? 0.0f : scaleZ / 100.0f;

		// scale after rotating (verified)
		if (scaleX != 100 || scaleY != 100 || scaleZ != 100)
			mtx.scale(renderScaleX, renderScaleY, renderScaleZ);

		mtx.translate(x, y, z);

		for (int i = 0; i < 4; i++)
			corners[i] = mtx.applyTransform(corners[i]);

		SpriteShader shader = ShaderManager.use(SpriteShader.class);

		boolean useShading = (spriteShading != null);
		shader.useShading.set(useShading);
		if (useShading) {
			spriteShading.calculateShaderParams(mtx);
			spriteShading.setShaderParams(shader);
		}

		face.asset.img.glBind(shader.texture);
		renderPalette.glBind(shader.palette);

		shader.useFiltering.set(useFiltering);
		shader.selectShading.set(useSelectShading);
		shader.selected.set(enableSelectedHighlight && selected);
		shader.highlighted.set(highlighted);

		float x1 = -w;
		float y1 = h;
		float x2 = w;
		float y2 = 0;

		TransformMatrix currentMtx = RenderState.pushModelMatrix();

		RenderState.setPolygonMode(PolygonMode.FILL);
		shader.setXYQuadCoords(x1, y2, x2, y1, 0); //note: y is reversed
		shader.renderQuad(TransformMatrix.multiply(currentMtx, mtx));

		RenderState.popModelMatrix();

		if (drawBounds) {
			RenderState.setColor(PresetColor.TEAL);
			RenderState.setLineWidth(2.0f);

			int v1 = LineRenderQueue.addVertex().setPosition(corners[0].x, corners[0].y, corners[0].z).getIndex();
			int v2 = LineRenderQueue.addVertex().setPosition(corners[1].x, corners[1].y, corners[1].z).getIndex();
			int v3 = LineRenderQueue.addVertex().setPosition(corners[2].x, corners[2].y, corners[2].z).getIndex();
			int v4 = LineRenderQueue.addVertex().setPosition(corners[3].x, corners[3].y, corners[3].z).getIndex();
			LineRenderQueue.addLine(v1, v2, v3, v4, v1);

			LineRenderQueue.render(true);
		}
	}

	public void addUnused(SpriteCleanup cleanup)
	{
		if (sprite.usesKeyframes) {
			for (AnimElement elem : keyframeAnimator.keyframes) {
				if (elem instanceof Keyframe kf) {
					if (kf.img != null)
						kf.img.inUse = true;

					if (kf.pal != null)
						kf.pal.inUse = true;
				}
			}
		}
		else {
			for (AnimElement elem : cmdAnimator.commands) {
				if (elem instanceof Label lbl) {
					lbl.inUse = false;
				}
			}

			for (AnimElement elem : cmdAnimator.commands) {
				if (elem instanceof Goto go2) {
					if (go2.label != null)
						go2.label.inUse = true;
				}

				else if (elem instanceof Loop loop) {
					if (loop.label != null)
						loop.label.inUse = true;
				}

				else if (elem instanceof SetImage setImg) {
					if (setImg.img != null)
						setImg.img.inUse = true;
				}

				else if (elem instanceof SetPalette setPal) {
					if (setPal.pal != null)
						setPal.pal.inUse = true;
				}
			}

			for (int i = 0; i < cmdAnimator.commands.size(); i++) {
				AnimElement elem = cmdAnimator.commands.get(i);
				if (elem instanceof Label lbl && !lbl.inUse) {
					cleanup.unusedLabels.addElement(new UnusedLabel(lbl, cmdAnimator.commands));
				}
			}
		}
	}

	private final EditableData editableData = new EditableData(this);

	@Override
	public EditableData getEditableData()
	{
		return editableData;
	}

	@Override
	public void addEditableDownstream(List<Editable> downstream)
	{
		if (sprite.usesKeyframes) {
			for (AnimElement e : keyframeAnimator.keyframes)
				downstream.add(e);
		}
		else {
			for (AnimElement e : cmdAnimator.commands)
				downstream.add(e);
		}
	}

	@Override
	public String checkErrorMsg()
	{
		if (posz % 2 == 1 && SpriteEditor.instance().optStrictErrorChecking)
			return "Component: odd z-offsets break Actor decorations";

		return null;
	}
}
