package game.map.marker;

import static game.map.MapKey.*;
import static org.lwjgl.opengl.GL11.*;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import org.w3c.dom.Element;

import app.StarRodException;
import common.BaseCamera;
import common.Vector3f;
import game.map.Axis;
import game.map.MutablePoint;
import game.map.MutablePoint.PointBackup;
import game.map.editor.camera.MapEditViewport;
import game.map.editor.camera.ViewType;
import game.map.editor.commands.fields.EditableField;
import game.map.editor.commands.fields.EditableField.EditableFieldFactory;
import game.map.editor.commands.fields.EditableField.StandardBoolName;
import game.map.editor.render.PresetColor;
import game.map.editor.render.RenderMode;
import game.map.editor.render.Renderer;
import game.map.editor.render.RenderingOptions;
import game.map.editor.render.ShadowRenderer.RenderableShadow;
import game.map.editor.render.SortedRenderable;
import game.map.editor.selection.PickRay.PickHit;
import game.map.editor.selection.SelectablePoint;
import game.map.editor.selection.SelectablePoint.SetPointCoord;
import game.map.editor.ui.info.MarkerInfoPanel;
import game.map.scripts.extract.HeaderEntry;
import game.map.shape.TransformMatrix;
import game.sprite.Sprite;
import game.sprite.SpriteLoader;
import game.sprite.SpriteLoader.AnimMetadata;
import game.sprite.SpriteLoader.SpriteSet;
import renderer.buffers.LineRenderQueue;
import renderer.buffers.PointRenderQueue;
import renderer.shaders.RenderState;
import util.Logger;
import util.identity.IdentityHashSet;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class NpcComponent extends BaseMarkerComponent
{
	private final Consumer<Object> notifySpriteChange = (o) -> {
		parentMarker.updateListeners(MarkerInfoPanel.tag_SetSprite);
		parentMarker.npcComponent.needsReloading = true;
	};

	private final Consumer<Object> notifyAnimation = (o) -> {
		parentMarker.updateListeners(MarkerInfoPanel.tag_SetSprite);
	};

	private final Consumer<Object> notifyMovement = (o) -> {
		parentMarker.updateListeners(MarkerInfoPanel.tag_NPCMovementTab);
	};

	public static enum MoveType
	{
		Stationary,
		Wander,
		Patrol
	}

	private static final double SPRITE_TICK_RATE = 1.0 / 30.0;

	private transient SpriteLoader spriteLoader;
	private transient double spriteTime = 0.0;
	private transient boolean needsReloading = false;

	private String animName = null;

	public EditableField<Integer> spriteID = EditableFieldFactory.create(0)
		.setCallback(notifySpriteChange).setName("Set Sprite").build();

	public EditableField<Integer> paletteID = EditableFieldFactory.create(0)
		.setCallback(notifyAnimation).setName("Set Palette").build();

	public EditableField<Integer> animIndex = EditableFieldFactory.create(0)
		.setCallback(notifyAnimation).setName("Set Animation").build();

	// special fields for actor rendering not exposed to the map editor
	public boolean flipX;
	public boolean flipY;

	public transient Sprite previewSprite = null;

	// movement

	public EditableField<MoveType> moveType = EditableFieldFactory.create(MoveType.Stationary)
		.setCallback(notifyMovement).setName("Set Movement Type").build();

	public EditableField<Boolean> flying = EditableFieldFactory.create(false)
		.setCallback(notifyMovement).setName(new StandardBoolName("Flying")).build();

	public EditableField<Boolean> overrideMovementSpeed = EditableFieldFactory.create(false)
		.setCallback(notifyMovement).setName(new StandardBoolName("Speed Override")).build();

	public EditableField<Float> movementSpeedOverride = EditableFieldFactory.create(1.0f)
		.setCallback(notifyMovement).setName("Set Override Speed").build();

	public EditableField<Boolean> useWanderCircle = EditableFieldFactory.create(false)
		.setName((b) -> b ? "Use Box Wander Volume" : "Use Circle Wander Volume")
		.setCallback(notifyMovement).build();

	public EditableField<Integer> wanderSizeX = EditableFieldFactory.create(0)
		.setCallback(notifyMovement).setName("Set Wander Size X").build();

	public EditableField<Integer> wanderSizeZ = EditableFieldFactory.create(0)
		.setCallback(notifyMovement).setName("Set Wander Size Z").build();

	public EditableField<Integer> wanderRadius = EditableFieldFactory.create(0)
		.setCallback(notifyMovement).setName("Set Wander Radius").build();

	public EditableField<Boolean> useDetectCircle = EditableFieldFactory.create(false)
		.setName((b) -> b ? "Use Box Detection Volume" : "Use Circle Detection Volume")
		.setCallback(notifyMovement).build();

	public EditableField<Integer> detectSizeX = EditableFieldFactory.create(0)
		.setCallback(notifyMovement).setName("Set Detection Size X").build();

	public EditableField<Integer> detectSizeZ = EditableFieldFactory.create(0)
		.setCallback(notifyMovement).setName("Set Detection Size Z").build();

	public EditableField<Integer> detectRadius = EditableFieldFactory.create(0)
		.setCallback(notifyMovement).setName("Set Detection Radius").build();

	public SelectablePoint detectCenter;
	public SelectablePoint wanderCenter;

	public PathData patrolPath;

	public NpcComponent(Marker marker)
	{
		super(marker);

		this.moveType.set(MoveType.Stationary);

		MutablePoint wanderPoint = new MutablePoint(
			marker.position.getX(),
			marker.position.getY(),
			marker.position.getZ());

		MutablePoint detectPoint = new MutablePoint(
			marker.position.getX(),
			marker.position.getY(),
			marker.position.getZ());

		wanderCenter = new SelectablePoint(wanderPoint, 2.0f);
		detectCenter = new SelectablePoint(detectPoint, 2.0f);

		patrolPath = new PathData(marker, MarkerInfoPanel.tag_NPCMovementTab, 10);
	}

	@Override
	public NpcComponent deepCopy(Marker copyParent)
	{
		NpcComponent copy = new NpcComponent(copyParent);

		copy.loadTerritoryData(moveType.get(), getTerritoryData());

		copy.spriteID.set(spriteID.get());
		copy.paletteID.set(paletteID.get());
		copy.animIndex.set(animIndex.get());

		copy.needsReloading = true;

		return copy;
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		XmlTag movementTag = xmw.createTag(TAG_MOVEMENT, true);
		xmw.addEnum(movementTag, ATTR_MOVEMENT_TYPE, moveType.get());
		int[] data = getTerritoryData();
		xmw.addHexArray(movementTag, ATTR_MOVEMENT_DATA, data);
		xmw.printTag(movementTag);

		XmlTag spriteTag = xmw.createTag(TAG_SPRITE, true);
		AnimMetadata anim = SpriteLoader.getAnimMetadata(spriteID.get(), paletteID.get(), animIndex.get(), false);
		if (anim != null)
			xmw.addAttribute(spriteTag, ATTR_ANIMATION, anim.enumName);

		if (flipX)
			xmw.addBoolean(spriteTag, ATTR_FLIP_X, flipX);

		if (flipY)
			xmw.addBoolean(spriteTag, ATTR_FLIP_Y, flipY);

		xmw.printTag(spriteTag);
	}

	@Override
	public void fromXML(XmlReader xmr, Element markerElem)
	{
		Element movementElem = xmr.getUniqueRequiredTag(markerElem, TAG_MOVEMENT);

		xmr.requiresAttribute(movementElem, ATTR_MOVEMENT_TYPE);
		moveType.set(xmr.readEnum(movementElem, ATTR_MOVEMENT_TYPE, MoveType.class));

		xmr.requiresAttribute(movementElem, ATTR_MOVEMENT_DATA);
		loadTerritoryData(moveType.get(), xmr.readHexArray(movementElem, ATTR_MOVEMENT_DATA, 48));

		Element spriteElement = xmr.getUniqueTag(markerElem, TAG_SPRITE);
		if (spriteElement != null) {
			if (xmr.hasAttribute(spriteElement, ATTR_ANIMATION)) {
				animName = xmr.getAttribute(spriteElement, ATTR_ANIMATION);
				AnimMetadata anim = SpriteLoader.getAnimMetadata(animName);
				if (anim != null) {
					spriteID.set(anim.spriteIndex);
					paletteID.set(anim.palIndex);
					animIndex.set(anim.animIndex);
				}
			}
		}

		if (xmr.hasAttribute(spriteElement, ATTR_FLIP_X))
			flipX = xmr.readBoolean(spriteElement, ATTR_FLIP_X);

		if (xmr.hasAttribute(spriteElement, ATTR_FLIP_Y))
			flipY = xmr.readBoolean(spriteElement, ATTR_FLIP_Y);
	}

	public int[] getTerritoryData()
	{
		int[] moveData = new int[48];

		if (moveType.get() == MoveType.Wander) {
			moveData[0] = wanderCenter.point.getX();
			moveData[1] = wanderCenter.point.getY();
			moveData[2] = wanderCenter.point.getZ();
			moveData[3] = useWanderCircle.get() ? wanderRadius.get() : wanderSizeX.get();
			moveData[4] = useWanderCircle.get() ? 0 : wanderSizeZ.get();
			moveData[5] = overrideMovementSpeed.get() ? (int) (movementSpeedOverride.get() * 32767) : -32767;
			moveData[6] = useWanderCircle.get() ? 0 : 1;

			moveData[7] = detectCenter.point.getX();
			moveData[8] = detectCenter.point.getY();
			moveData[9] = detectCenter.point.getZ();
			moveData[10] = useDetectCircle.get() ? detectRadius.get() : detectSizeX.get();
			moveData[11] = useDetectCircle.get() ? 0 : detectSizeZ.get();
			moveData[12] = useDetectCircle.get() ? 0 : 1;
			moveData[13] = flying.get() ? 1 : 0;
		}
		else if (moveType.get() == MoveType.Patrol) {
			moveData[0] = patrolPath.points.size();
			for (int i = 0; i < patrolPath.points.size(); i++) {
				PathPoint wp = patrolPath.points.get(i);
				moveData[3 * i + 1] = wp.getX();
				moveData[3 * i + 2] = wp.getY();
				moveData[3 * i + 3] = wp.getZ();
			}
			moveData[31] = overrideMovementSpeed.get() ? (int) (movementSpeedOverride.get() * 32767) : -32767;

			moveData[32] = detectCenter.point.getX();
			moveData[33] = detectCenter.point.getY();
			moveData[34] = detectCenter.point.getZ();
			moveData[35] = useDetectCircle.get() ? detectRadius.get() : detectSizeX.get();
			moveData[36] = useDetectCircle.get() ? 0 : detectSizeZ.get();
			moveData[37] = useDetectCircle.get() ? 0 : 1;
			moveData[38] = flying.get() ? 1 : 0;
		}

		return moveData;
	}

	public void loadTerritoryData(MoveType type, int[] moveData)
	{
		moveType.set(type);

		if (type == MoveType.Wander) {
			wanderCenter.point.setPosition(moveData[0], moveData[1], moveData[2]);
			useWanderCircle.set(moveData[6] == 0);
			if (useWanderCircle.get()) {
				wanderRadius.set(moveData[3]);
			}
			else {
				wanderSizeX.set(moveData[3]);
				wanderSizeZ.set(moveData[4]);
			}
			overrideMovementSpeed.set(moveData[5] != -32767);
			if (overrideMovementSpeed.get())
				movementSpeedOverride.set(moveData[5] / 32767.0f);

			detectCenter.point.setPosition(moveData[7], moveData[8], moveData[9]);
			useDetectCircle.set(moveData[12] == 0);
			if (useDetectCircle.get()) {
				detectRadius.set(moveData[10]);
			}
			else {
				detectSizeX.set(moveData[10]);
				detectSizeZ.set(moveData[11]);
			}

			flying.set(moveData[13] != 0);
		}
		else if (type == MoveType.Patrol) {
			wanderCenter.point.setPosition(moveData[32], moveData[33], moveData[34]);

			int numPatrolPoints = moveData[0];
			patrolPath.points.clear();
			for (int i = 0; i < numPatrolPoints; i++)
				patrolPath.points.addElement(new PathPoint(patrolPath, moveData[3 * i + 1], moveData[3 * i + 2], moveData[3 * i + 3]));

			overrideMovementSpeed.set(moveData[31] != -32767);
			if (overrideMovementSpeed.get())
				movementSpeedOverride.set(moveData[31] / 32767.0f);

			detectCenter.point.setPosition(moveData[32], moveData[33], moveData[34]);
			useDetectCircle.set(moveData[37] == 0);
			if (useDetectCircle.get()) {
				detectRadius.set(moveData[35]);
			}
			else {
				detectSizeX.set(moveData[35]);
				detectSizeZ.set(moveData[36]);
			}

			flying.set(moveData[38] != 0);
		}
		else {
			wanderCenter.point.setPosition(
				parentMarker.position.getX(),
				parentMarker.position.getY(),
				parentMarker.position.getZ());

			detectCenter.point.setPosition(
				parentMarker.position.getX(),
				parentMarker.position.getY(),
				parentMarker.position.getZ());
		}
	}

	@Override
	public boolean hasSelectablePoints()
	{
		return true;
	}

	@Override
	public void addSelectablePoints(List<SelectablePoint> points)
	{
		points.add(detectCenter);

		if (moveType.get() == MoveType.Wander)
			points.add(wanderCenter);
		else if (moveType.get() == MoveType.Patrol) {
			for (PathPoint wp : patrolPath.points)
				points.add(wp);
		}
	}

	@Override
	public void addToBackup(IdentityHashSet<PointBackup> backupList)
	{
		backupList.add(detectCenter.point.getBackup());
		backupList.add(wanderCenter.point.getBackup());

		for (PathPoint wp : patrolPath.points)
			backupList.add(wp.point.getBackup());
	}

	@Override
	public void addPoints(IdentityHashSet<MutablePoint> positions)
	{
		positions.add(detectCenter.point);
		positions.add(wanderCenter.point);

		for (PathPoint wp : patrolPath.points)
			positions.add(wp.point);
	}

	@Override
	public void startTransformation()
	{
		detectCenter.point.startTransform();
		wanderCenter.point.startTransform();

		for (PathPoint wp : patrolPath.points)
			wp.point.startTransform();
	}

	@Override
	public void endTransformation()
	{
		detectCenter.point.endTransform();
		wanderCenter.point.endTransform();

		for (PathPoint wp : patrolPath.points)
			wp.point.endTransform();
	}

	@Override
	public void initialize()
	{
		if (spriteID.get() > 0)
			needsReloading = true;
	}

	@Override
	public void tick(double deltaTime)
	{
		if (previewSprite != null) {
			spriteTime += deltaTime;
			if (spriteTime >= SPRITE_TICK_RATE) {
				if (previewSprite != null)
					previewSprite.updateAnimation(animIndex.get());
				spriteTime -= SPRITE_TICK_RATE;
			}
		}
	}

	@Override
	public void addRenderables(RenderingOptions opts, Collection<SortedRenderable> renderables, PickHit shadowHit)
	{
		if (needsReloading) {
			reloadSprite();
			needsReloading = false;
		}

		if (previewSprite != null)
			renderables.add(new RenderableSprite(this));

		if (shadowHit != null && shadowHit.dist < Float.MAX_VALUE)
			renderables.add(new RenderableShadow(shadowHit.point, shadowHit.norm, shadowHit.dist, false, true, 100.0f));
	}

	@Override
	public void render(RenderingOptions opts, MapEditViewport view, Renderer renderer)
	{
		if ((view.type != ViewType.PERSPECTIVE) || (spriteID.get() <= 0) || (previewSprite == null))
			parentMarker.renderCube(opts, view, renderer);
		parentMarker.renderDirectionIndicator(opts, view, renderer);

		if (!parentMarker.selected)
			return;

		boolean editPointsMode = true;
		boolean drawHiddenPaths = (view.type == ViewType.PERSPECTIVE);

		RenderState.setColor(PresetColor.WHITE);
		RenderState.setLineWidth(2.0f);

		if (useDetectCircle.get())
			drawCircularVolume(
				detectCenter.point.getX(),
				detectCenter.point.getY(),
				detectCenter.point.getZ(),
				detectRadius.get(),
				50);
		else
			drawRectangularVolume(
				detectCenter.point.getX(),
				detectCenter.point.getY(),
				detectCenter.point.getZ(),
				detectSizeX.get(),
				detectSizeZ.get(),
				50);

		if (moveType.get() == MoveType.Wander) {
			if (useWanderCircle.get())
				drawCircularVolume(
					wanderCenter.point.getX(),
					wanderCenter.point.getY(),
					wanderCenter.point.getZ(),
					wanderRadius.get(),
					50);
			else
				drawRectangularVolume(
					wanderCenter.point.getX(),
					wanderCenter.point.getY(),
					wanderCenter.point.getZ(),
					wanderSizeX.get(),
					wanderSizeZ.get(),
					50);
		}
		else if (moveType.get() == MoveType.Patrol) {
			drawPatrolPath(editPointsMode, drawHiddenPaths);
		}

		if (editPointsMode) {
			RenderState.setColor(PresetColor.YELLOW);
			RenderState.setPointSize(editPointsMode ? 12.0f : 8.0f);
			RenderState.setLineWidth(3.0f);
			RenderState.setDepthFunc(GL_ALWAYS);

			SelectablePoint point = detectCenter;
			PointRenderQueue.addPoint().setPosition(point.getX(), point.getY(), point.getZ());

			if (moveType.get() == MoveType.Wander) {
				point = wanderCenter;
				PointRenderQueue.addPoint().setPosition(point.getX(), point.getY(), point.getZ());
			}

			PointRenderQueue.render(true);
		}

		RenderState.initDepthFunc();
	}

	private void drawCircularVolume(int cx, int cy, int cz, int radius, int h)
	{
		Renderer.instance().drawCircularVolume(
			cx, cy, cz, radius, h,
			1.0f, 1.0f, 1.0f, 1.0f);
	}

	private void drawRectangularVolume(int cx, int cy, int cz, int sizeX, int sizeZ, int h)
	{
		Renderer.instance().drawRectangularVolume(
			cx - sizeX, cy, cz - sizeZ,
			cx + sizeX, cy + h, cz + sizeZ,
			1.0f, 1.0f, 1.0f, 1.0f);
	}

	private void drawPatrolPath(boolean editPointsMode, boolean drawHiddenPaths)
	{
		RenderState.setColor(PresetColor.YELLOW);
		RenderState.setPointSize(editPointsMode ? 12.0f : 8.0f);
		RenderState.setLineWidth(3.0f);

		for (int i = 0; i < patrolPath.points.size(); i++) {
			PathPoint wp = patrolPath.points.get(i);
			PointRenderQueue.addPoint().setPosition(wp.getX(), wp.getY(), wp.getZ());
		}

		RenderState.setDepthFunc(GL_ALWAYS);
		PointRenderQueue.render(true);

		for (int i = 0; i < (patrolPath.points.size() - 1); i++) {
			PathPoint wp1 = patrolPath.points.get(i);
			PathPoint wp2 = patrolPath.points.get(i + 1);
			LineRenderQueue.addLine(
				LineRenderQueue.addVertex().setPosition(wp1.getX(), wp1.getY(), wp1.getZ()).getIndex(),
				LineRenderQueue.addVertex().setPosition(wp2.getX(), wp2.getY(), wp2.getZ()).getIndex());
		}

		if (drawHiddenPaths)
			RenderState.setDepthFunc(GL_LEQUAL);
		LineRenderQueue.render(true);

		if (drawHiddenPaths) {

			for (int i = 0; i < (patrolPath.points.size() - 1); i++) {
				PathPoint wp1 = patrolPath.points.get(i);
				PathPoint wp2 = patrolPath.points.get(i + 1);
				Renderer.queueStipple(
					wp1.getX(), wp1.getY(), wp1.getZ(),
					wp2.getX(), wp2.getY(), wp2.getZ(),
					10.0f);
			}

			RenderState.setDepthFunc(GL_GREATER);
			RenderState.setDepthWrite(false);
			LineRenderQueue.render(true);
			RenderState.setDepthWrite(true);
		}

		RenderState.initDepthFunc();
	}

	private void renderSprite(RenderingOptions opts, BaseCamera camera, boolean selected)
	{
		float renderYaw = (float) parentMarker.yaw.getAngle();
		Vector3f deltaPos = Vector3f.sub(camera.pos, parentMarker.position.getVector());
		renderYaw = -(float) Math.toDegrees(Math.atan2(deltaPos.x, deltaPos.z));

		RenderState.setColor(PresetColor.WHITE);

		int x = parentMarker.position.getX();
		int y = parentMarker.position.getY();
		int z = parentMarker.position.getZ();
		y -= Sprite.WORLD_SCALE;

		if (opts.spriteShading != null)
			opts.spriteShading.setSpriteRenderingPos(camera, x, y, z, -renderYaw);

		TransformMatrix mtx = TransformMatrix.identity();
		if (flipX)
			mtx.scale(-1, 1, 1);
		if (flipY)
			mtx.scale(1, -1, 1);

		mtx.scale(Sprite.WORLD_SCALE);

		if (!opts.isStage)
			mtx.rotate(Axis.Y, -renderYaw);

		mtx.translate(x, y, z);
		RenderState.setModelMatrix(mtx);

		int animID = Math.min(animIndex.get(), previewSprite.animations.size() - 1);
		int palID = Math.min(paletteID.get(), previewSprite.palettes.size() - 1);
		if (animID >= 0 && palID >= 0) // watch out for sprites with no animations
			previewSprite.render(opts.spriteShading, animID, palID, false, opts.useFiltering, selected);

		RenderState.setModelMatrix(null);

		Vector3f size = Vector3f.sub(previewSprite.aabb.getMax(), previewSprite.aabb.getMin());
		float w = 0.75f * 0.5f * Math.max(size.x, size.z);
		float h = 0.75f * 0.75f * size.y;

		parentMarker.AABB.clear();
		parentMarker.AABB.encompass(new Vector3f(x - w / 2, y, z - w / 2));
		parentMarker.AABB.encompass(new Vector3f(x + w / 2, y + h, z + w / 2));

		if (opts.showBoundingBoxes)
			parentMarker.AABB.render();
	}

	public static class RenderableSprite implements SortedRenderable
	{
		private final NpcComponent comp;
		private int depth;

		public RenderableSprite(NpcComponent comp)
		{
			this.comp = comp;
		}

		@Override
		public RenderMode getRenderMode()
		{
			return RenderMode.ALPHA_TEST_AA_ZB_2SIDE;
		}

		@Override
		public Vector3f getCenterPoint()
		{
			return comp.parentMarker.position.getVector();
		}

		@Override
		public void render(RenderingOptions opts, BaseCamera camera)
		{
			comp.renderSprite(opts, camera, comp.parentMarker.isSelected());
		}

		@Override
		public void setDepth(int normalizedDepth)
		{
			depth = normalizedDepth;
		}

		@Override
		public int getDepth()
		{
			return depth;
		}
	}

	private void reloadSprite()
	{
		if (previewSprite != null)
			previewSprite.unloadTextures();

		if (spriteLoader == null)
			spriteLoader = new SpriteLoader();

		previewSprite = spriteLoader.getSprite(SpriteSet.Npc, spriteID.get());

		if (previewSprite != null) {
			previewSprite.prepareForEditor();
			previewSprite.loadTextures();
		}

		parentMarker.updateListeners(MarkerInfoPanel.tag_SetSprite);
	}

	public static final class SetWanderPos extends SetPointCoord
	{
		private final Marker m;

		public SetWanderPos(Marker m, int axis, int val)
		{
			super("Set Wander Center Position", m.npcComponent.wanderCenter, axis, val);
			this.m = m;
		}

		@Override
		public void exec()
		{
			super.exec();
			m.updateListeners(MarkerInfoPanel.tag_NPCMovementTab);
		}

		@Override
		public void undo()
		{
			super.undo();
			m.updateListeners(MarkerInfoPanel.tag_NPCMovementTab);
		}
	}

	public static final class SetDetectPos extends SetPointCoord
	{
		private final Marker m;

		public SetDetectPos(Marker m, int axis, int val)
		{
			super("Set Detect Center Position", m.npcComponent.detectCenter, axis, val);
			this.m = m;
		}

		@Override
		public void exec()
		{
			super.exec();
			m.updateListeners(MarkerInfoPanel.tag_NPCMovementTab);
		}

		@Override
		public void undo()
		{
			super.undo();
			m.updateListeners(MarkerInfoPanel.tag_NPCMovementTab);
		}
	}

	public void addHeaderDefines(HeaderEntry h)
	{
		String isFlying = flying.get() ? "TRUE" : "FALSE";
		String speedOverride;
		if (overrideMovementSpeed.get())
			speedOverride = String.format("OVERRIDE_MOVEMENT_SPEED(%f)", movementSpeedOverride.get());
		else
			speedOverride = "NO_OVERRIDE_MOVEMENT_SPEED";

		FormatStringList lines = new FormatStringList();

		if (moveType.get() == MoveType.Wander) {
			lines.add("{");
			lines.addf("    .wander = {");
			lines.addf("        .centerPos   = { %d, %d, %d },",
				wanderCenter.point.getX(), wanderCenter.point.getY(), wanderCenter.point.getZ());
			if (useWanderCircle.get())
				lines.addf("        .wanderSize  = { %d },", wanderRadius.get());
			else
				lines.addf("        .wanderSize  = { %d, %d },", wanderSizeX.get(), wanderSizeZ.get());
			lines.addf("        .moveSpeedOverride = %s,", speedOverride);
			lines.addf("        .wanderShape = %s,", useWanderCircle.get() ? "SHAPE_CYLINDER" : "SHAPE_RECT");
		}
		else if (moveType.get() == MoveType.Patrol) {
			lines.add("{");
			lines.addf("    .patrol = {");
			lines.addf("        .numPoints = %d,", patrolPath.points.size());
			lines.addf("        .points = {");
			for (PathPoint wp : patrolPath.points) {
				lines.addf("            { %d, %d, %d },", wp.point.getX(), wp.point.getY(), wp.point.getZ());
			}
			lines.addf("        },");
			lines.addf("        .moveSpeedOverride = %s,", speedOverride);
		}

		if (moveType.get() != MoveType.Stationary) {
			lines.addf("        .detectPos   = { %d, %d, %d },",
				detectCenter.point.getX(), detectCenter.point.getY(), detectCenter.point.getZ());
			if (useDetectCircle.get())
				lines.addf("        .detectSize  = { %d },", detectRadius.get());
			else
				lines.addf("        .detectSize  = { %d, %d },", detectSizeX.get(), detectSizeZ.get());
			lines.addf("        .detectShape = %s,", useDetectCircle.get() ? "SHAPE_CYLINDER" : "SHAPE_RECT");
			lines.addf("        .isFlying = %s,", isFlying);
			lines.addf("    },");
			lines.addf("}");
		}
		else {
			lines.addf("{}");
		}

		h.addDefine("TERRITORY", lines);

		if (animName != null && !animName.isBlank())
			h.addProperty("anim", animName);
	}

	public void parseTerritory(String territory)
	{
		String original = territory;
		territory = territory.replaceAll("\\s", "");

		assert (territory.startsWith("{"));
		assert (territory.endsWith("}}"));

		territory = territory.substring(1, territory.length() - 2);

		// determine territory type
		if (territory.startsWith(".patrol={"))
			moveType.set(MoveType.Patrol);
		else if (territory.startsWith(".wander={"))
			moveType.set(MoveType.Wander);
		else
			throw new StarRodException("Cannot parse NPC territory: " + original);

		territory = territory.substring(territory.indexOf("{") + 1);
		if (territory.endsWith(","))
			territory = territory.substring(0, territory.length() - 1);

		String[] fields = territory.split(",(?=\\.\\w+\\s*=)");

		for (String field : fields) {
			String[] kv = field.split("=");
			int[] coords;

			switch (kv[0]) {
				case ".isFlying":
					flying.set("TRUE".equalsIgnoreCase(kv[1]));
					break;
				case ".moveSpeedOverride":
					if (!"NO_OVERRIDE_MOVEMENT_SPEED".equals(kv[1])) {
						assert (kv[1].matches("OVERRIDE_MOVEMENT_SPEED\\(\\S+\\)"));
						float speed = Float.parseFloat(kv[1].substring("OVERRIDE_MOVEMENT_SPEED(".length(), kv[1].length() - 1));
						overrideMovementSpeed.set(true);
						movementSpeedOverride.set(speed);
					}
					break;
				case ".wanderShape":
					useWanderCircle.set("SHAPE_CYLINDER".equals(kv[1]));
					break;
				case ".centerPos":
					coords = getIntVec(kv[1], "centerPos", 3);
					wanderCenter.point.setPosition(coords[0], coords[1], coords[2]);
					break;
				case ".wanderSize":
					if (useWanderCircle.get()) {
						coords = getIntVec(kv[1], "wanderSize", -1);
						wanderRadius.set(coords[0]);
					}
					else {
						coords = getIntVec(kv[1], "wanderSize", 2);
						wanderSizeX.set(coords[0]);
						wanderSizeZ.set(coords[1]);
					}
					break;
				case ".detectShape":
					useDetectCircle.set("SHAPE_CYLINDER".equals(kv[1]));
					break;
				case ".detectPos":
					coords = getIntVec(kv[1], "detectPos", 3);
					detectCenter.point.setPosition(coords[0], coords[1], coords[2]);
					break;
				case ".detectSize":
					if (useDetectCircle.get()) {
						coords = getIntVec(kv[1], "detectSize", -1);
						detectRadius.set(coords[0]);
					}
					else {
						coords = getIntVec(kv[1], "detectSize", 2);
						detectSizeX.set(coords[0]);
						detectSizeZ.set(coords[1]);
					}
					break;
				case ".points":
					// examples:
					// {{200,0,75},{300,0,75},}
					// {{-450,0,-160},{-378,0,-81},{-590,0,-100},{-464,0,-46},{-495,0,-147},}
					assert (kv[1].matches("\\{\\{.+\\},\\}")) : kv[1];
					String listString = kv[1].substring(2, kv[1].length() - 3);
					String[] points = listString.split("\\},\\{");
					for (String p : points) {
						// have to add the {} so the function can strip them out
						int[] point = getIntVec("{" + p + "}", "point", 3);
						patrolPath.points.addElement(new PathPoint(patrolPath, point[0], point[1], point[2]));
					}
					break;
				case ".numPoints":
					break;
				default:
					throw new StarRodException(kv[0]);
			}
		}
	}

	private static int[] getIntVec(String s, String fieldName, int len)
	{
		assert (s.matches("\\{\\S+(,\\S+)*\\}")) : s;
		s = s.substring(1, s.length() - 1);
		String[] tokens = s.split(",");

		// check predefined position name
		if (tokens.length == 1 && "NPC_DISPOSE_LOCATION".equals(tokens[0]))
			return new int[] { 0, -1000, 0 };

		if (len > 0 && tokens.length != len)
			throw new StarRodException("Wrong length for %s vector: %s (expected %d)", fieldName, s, len);

		if (len < 0 && tokens.length != -len)
			Logger.logfError("Wrong length for %s vector: %s (expected %d)", fieldName, s, len);

		// convert the coords
		int[] coords = new int[tokens.length];
		for (int i = 0; i < tokens.length; i++) {
			coords[i] = Integer.decode(tokens[i]);
		}
		return coords;
	}

	public void setAnimByName(String animName)
	{
		this.animName = animName;

		SpriteLoader.loadAnimsMetadata(false);
		AnimMetadata mdata = SpriteLoader.getAnimMetadata(animName);
		if (mdata == null) {
			Logger.logError("Could not find animation named: " + animName);
			return;
		}

		spriteID.set(mdata.spriteIndex);
		paletteID.set(mdata.palIndex);
		animIndex.set(mdata.animIndex);
	}
}
