package game.map.marker;

import java.util.Collection;
import java.util.List;

import org.w3c.dom.Element;

import game.map.MutablePoint;
import game.map.MutablePoint.PointBackup;
import game.map.editor.camera.MapEditViewport;
import game.map.editor.render.Renderer;
import game.map.editor.render.RenderingOptions;
import game.map.editor.render.ShadowRenderer.RenderableShadow;
import game.map.editor.render.SortedRenderable;
import game.map.editor.selection.PickRay;
import game.map.editor.selection.PickRay.PickHit;
import game.map.editor.selection.SelectablePoint;
import util.identity.IdentityHashSet;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlWriter;

public abstract class BaseMarkerComponent
{
	public final Marker parentMarker;

	public BaseMarkerComponent(Marker parent)
	{
		this.parentMarker = parent;
	}

	public abstract BaseMarkerComponent deepCopy(Marker copyParent);

	public abstract void toXML(XmlWriter xmw);

	public abstract void fromXML(XmlReader xmr, Element markerElem);

	// point selection
	public boolean hasSelectablePoints()
	{
		return false;
	}

	public void addSelectablePoints(List<SelectablePoint> points)
	{}

	// points which may transform with parent marker
	public void addToBackup(IdentityHashSet<PointBackup> backupList)
	{}

	public void addPoints(IdentityHashSet<MutablePoint> positions)
	{}

	public void startTransformation()
	{}

	public void endTransformation()
	{}

	// update every frame
	public void initialize()
	{}

	public void tick(double deltaTime)
	{}

	// selection and collision
	public boolean hasCollision()
	{
		return false;
	}

	public PickHit trySelectionPick(PickRay ray)
	{
		return PickRay.getIntersection(ray, parentMarker.AABB);
	}

	// rendering
	public void addRenderables(RenderingOptions opts, Collection<SortedRenderable> renderables, PickHit shadowHit)
	{
		if (!opts.thumbnailMode && shadowHit != null && shadowHit.dist < Float.MAX_VALUE)
			renderables.add(new RenderableShadow(shadowHit.point, shadowHit.norm, shadowHit.dist, false, false, 100.0f));
	}

	public void render(RenderingOptions opts, MapEditViewport view, Renderer renderer)
	{
		parentMarker.renderCube(opts, view, renderer);
		parentMarker.renderDirectionIndicator(opts, view, renderer);
	}

	//	public void renderPoints(RenderingOptions opts, MapEditViewport view, Renderer renderer) {} // only in point selection mode
}
