package game.sprite.editor.animators.keyframe;

import java.awt.Color;
import java.awt.Component;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import app.SwingUtils;
import game.sprite.SpritePalette;
import game.sprite.SpriteRaster;
import game.sprite.editor.Editable;
import game.sprite.editor.SpriteEditor;
import game.sprite.editor.animators.keyframe.KeyframeAnimatorEditor.KeyframePanel;

public class Keyframe extends AnimKeyframe
{
	public Keyframe(KeyframeAnimator animator)
	{
		super(animator);

		name = "New Keyframe";
	}

	@Override
	public Keyframe copy()
	{
		Keyframe clone = new Keyframe(animator);
		clone.name = name;
		clone.duration = duration;
		clone.setImage = setImage;
		clone.img = img;
		clone.setPalette = setPalette;
		clone.pal = pal;
		clone.unknown = unknown;
		clone.dx = dx;
		clone.dy = dy;
		clone.dz = dz;
		clone.rx = rx;
		clone.ry = ry;
		clone.rz = rz;
		clone.sx = sx;
		clone.sy = sy;
		clone.sz = sz;
		return clone;
	}

	int listPos = -1;

	public String name = "";

	public int duration;

	public boolean setImage = false;
	public SpriteRaster img = null;

	public boolean setPalette = false;
	public SpritePalette pal = null;

	public boolean unknown;
	public int dx = 0, dy = 0, dz = 0;
	public int rx = 0, ry = 0, rz = 0;
	public int sx = 100, sy = 100, sz = 100;

	@Override
	public AdvanceResult apply()
	{
		if (setImage) {
			owner.sr = img;
			owner.sp = null;
		}

		if (setPalette) {
			owner.sp = pal;
		}

		// these all use default values if not set by the keyframe
		if (duration > 0) {
			owner.dx = dx;
			owner.dy = dy;
			owner.dz = dz;

			owner.rx = rx;
			owner.ry = ry;
			owner.rz = rz;

			owner.scaleX = sx;
			owner.scaleY = sy;
			owner.scaleZ = sz;
		}

		owner.delayCount = duration;

		return (duration > 0) ? AdvanceResult.BLOCK : AdvanceResult.NEXT;
	}

	@Override
	public int length()
	{
		// zero IQ method is fine and less prone to bugs
		List<Short> dummySeq = new LinkedList<>();
		addTo(dummySeq);
		return dummySeq.size();
	}

	@Override
	protected void addTo(List<Short> seq)
	{
		if (duration > 0 && dx != 0 || dy != 0 || dz != 0) {
			seq.add(unknown ? (short) 0x3001 : (short) 0x3000);
			seq.add((short) dx);
			seq.add((short) dy);
			seq.add((short) dz);
		}

		if (duration > 0 && rx != 0 || ry != 0 || rz != 0) {
			seq.add((short) (0x4000 | (rx & 0xFFF)));
			seq.add((short) ry);
			seq.add((short) rz);
		}

		if (duration > 0 && sx != 100 || sy != 100 || sz != 100) {
			if (sx == sy && sy == sz) {
				seq.add((short) 0x5000);
				seq.add((short) sx);
			}
			else {
				if (sx != 100) {
					seq.add((short) 0x5001);
					seq.add((short) sx);
				}
				if (sy != 100) {
					seq.add((short) 0x5002);
					seq.add((short) sy);
				}
				if (sz != 100) {
					seq.add((short) 0x5003);
					seq.add((short) sz);
				}
			}
		}

		if (setImage) {
			int id = (img == null) ? -1 : img.getIndex();
			seq.add((short) (0x1000 | (id & 0xFFF)));
		}

		if (setPalette) {
			int id = (pal == null) ? -1 : pal.getIndex();
			seq.add((short) (0x6000 | (id & 0xFFF)));
		}

		if (duration > 0)
			seq.add((short) (0x0000 | (duration & 0xFFF)));
	}

	// 0VVV : 0 is valid, leads to a duration of 4095
	public int setDuration(Queue<Short> cmdQueue)
	{
		duration = (cmdQueue.poll() & 0xFFF);

		if (duration == 0)
			duration = 4095;

		return 1;
	}

	// 1VVV : FFF is valid value for "no image"
	public int setImage(Queue<Short> cmdQueue)
	{
		// ensure FFF -> FFFF with sign extension
		int id = (cmdQueue.poll() << 20) >> 20;
		img = (id < 0) ? null : owner.parentAnimation.parentSprite.rasters.get(id);
		setImage = true;
		return 1;
	}

	// 3VVV XXXX YYYY ZZZZ
	public int setPosition(Queue<Short> cmdQueue)
	{
		//NOTE: this flag does nothing
		unknown = (cmdQueue.poll() & 0xFFF) == 1;
		dx = cmdQueue.poll();
		dy = cmdQueue.poll();
		dz = cmdQueue.poll();
		return 4;
	}

	// 4XXX YYYY ZZZZ
	// set rotation (euler angles)
	public int setRotation(Queue<Short> cmdQueue)
	{
		// ensure FFF -> FFFF with sign extension
		rx = ((cmdQueue.poll() << 20) >> 20);
		ry = cmdQueue.poll();
		rz = cmdQueue.poll();
		return 3;
	}

	// 5VVV UUUU : scale (%)
	public int setScale(Queue<Short> cmdQueue)
	{
		int type = (cmdQueue.poll() & 0xFFF);
		int scale = cmdQueue.poll();
		switch (type) {
			case 0:
				sx = scale;
				sy = scale;
				sz = scale;
				break;
			case 1:
				sx = scale;
				break;
			case 2:
				sy = scale;
				break;
			case 3:
				sz = scale;
				break;
		}
		return 2;
	}

	// 6VVV
	public int setPalette(Queue<Short> cmdQueue)
	{
		// ensure FFF -> FFFF with sign extension
		int id = (cmdQueue.poll() << 20) >> 20;
		pal = (id < 0) ? null : owner.parentAnimation.parentSprite.palettes.get(id);
		setPalette = true;
		return 1;
	}

	@Override
	public String getName()
	{
		return "Keyframe";
	}

	@Override
	public Color getTextColor()
	{
		if (hasError())
			return SwingUtils.getRedTextColor();
		else if (duration % 2 == 1)
			return SwingUtils.getYellowTextColor();
		else
			return null;
	}

	@Override
	public String toString()
	{
		SpriteEditor editor = SpriteEditor.instance();
		boolean highlight = (highlighted && editor != null && editor.highlightCommand);

		StringBuilder sb = new StringBuilder("<html>");
		if (highlight)
			sb.append("<b>");
		sb.append(name);
		if (highlight)
			sb.append("</b>");
		sb.append("</html>");
		return sb.toString();
	}

	@Override
	public Component getPanel()
	{
		KeyframePanel.instance().set(this);
		return KeyframePanel.instance();
	}

	@Override
	public String checkErrorMsg()
	{
		if (duration == 0)
			return "Keyframe: invalid duration";

		if (setPalette && (pal == null))
			return "Keyframe: undefined palette";

		return null;
	}

	@Override
	public void addEditableDownstream(List<Editable> downstream)
	{
		if (setImage && img != null)
			downstream.add(img);

		if (setPalette && pal != null)
			downstream.add(pal);
	}
}
